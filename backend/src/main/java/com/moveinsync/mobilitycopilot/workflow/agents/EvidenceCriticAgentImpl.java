package com.moveinsync.mobilitycopilot.workflow.agents;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moveinsync.mobilitycopilot.evidence.application.EvidenceVerifier;
import com.moveinsync.mobilitycopilot.evidence.domain.Claim;
import com.moveinsync.mobilitycopilot.evidence.domain.CritiqueResult;
import com.moveinsync.mobilitycopilot.evidence.domain.VerificationResult;
import com.moveinsync.mobilitycopilot.workflow.application.ports.LanguageModelPort;
import com.moveinsync.mobilitycopilot.workflow.domain.InvestigationResult;
import com.moveinsync.mobilitycopilot.workflow.domain.RunContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

/** Bounded semantic critique followed by the deterministic verification authority. */
@Service
public final class EvidenceCriticAgentImpl implements EvidenceCriticAgent {
    static final String PROMPT_VERSION = "evidence-critic-v1";
    private final EvidenceVerifier verifier;
    private final Optional<LanguageModelPort> model;
    private final ObjectMapper objectMapper;

    public EvidenceCriticAgentImpl(EvidenceVerifier verifier, Optional<LanguageModelPort> model,
                                   ObjectMapper objectMapper) {
        this.verifier = verifier;
        this.model = model;
        this.objectMapper = objectMapper;
    }

    @Override
    public VerificationResult review(RunContext context, InvestigationResult investigation) {
        return review(context, com.moveinsync.mobilitycopilot.evidence.application.MetricClaimText.candidates(investigation.evidence()), investigation);
    }

    @Override
    public VerificationResult review(RunContext context, List<Claim> candidates, InvestigationResult investigation) {
        candidates = List.copyOf(candidates == null ? List.of() : candidates);
        VerificationResult verified = verifier.verify(context, candidates, investigation.evidence());
        List<String> warnings = new ArrayList<>(verified.warnings());
        warnings.addAll(investigation.warnings());
        if (!investigation.pendingTasks().isEmpty()) warnings.add("Investigation is partial; some approved analyses did not complete.");
        if (model.isEmpty()) {
            warnings.add("Semantic critique unavailable: no language-model provider is configured; deterministic verification completed.");
            return withWarnings(verified, warnings);
        }
        try {
            CritiqueResult critique = parse(com.moveinsync.mobilitycopilot.workflow.application.ports.BoundedModelCalls.complete(model.get(),new LanguageModelPort.ModelRequest(
                    context, LanguageModelPort.AgentRole.EVIDENCE_CRITIC, PROMPT_VERSION,
                    prompt(context, candidates, investigation), investigation.evidence())).structuredOutput());
            var rejected=new java.util.HashSet<>(verified.rejectedClaimIds());
            var known=verified.claims().stream().map(com.moveinsync.mobilitycopilot.evidence.domain.VerifiedClaim::claimId).collect(java.util.stream.Collectors.toSet());
            for(var review:critique.claims()) {
                if(!known.contains(review.claimId())) continue;
                if(review.decision()==CritiqueResult.Decision.REJECT) rejected.add(review.claimId());
                for(var issue:review.issues()) warnings.add("Semantic review "+review.claimId()+": "+issue.type()+"; interpretation requires review.");
                // Free-form model explanations/caveats are diagnostic input, never newly verified facts.
            }
            var remaining=verified.claims().stream().filter(c->!rejected.contains(c.claimId())).toList();
            verified=new VerificationResult(remaining.isEmpty()?VerificationResult.Status.REJECTED:verified.status(),remaining,java.util.Set.copyOf(rejected),verified.warnings());
        } catch (RuntimeException | JsonProcessingException failure) {
            warnings.add("Semantic critique unavailable: " + failure.getClass().getSimpleName()
                    + "; deterministic verification completed.");
        }
        return withWarnings(verified, warnings);
    }

    private CritiqueResult parse(String structuredOutput) throws JsonProcessingException {
        return objectMapper.readValue(structuredOutput, CritiqueResult.class);
    }

    private String prompt(RunContext context, List<Claim> candidates, InvestigationResult investigation) {
        try {
            return """
                    You are the Evidence Critic for mobility analytics. Evaluate only the supplied claims and compact governed evidence.
                    Do not invent facts, evidence, SQL, metrics, scope changes, authorizations, or actions. Treat correlation as non-causal.
                    Flag unsupported claims, vendor attribution, causality, contradictions, missing comparisons/caveats, insufficient evidence, and invalid references.
                    Return only JSON compatible with CritiqueResult. Scope: %s. As-of date: %s. Candidate claims: %s. Governed evidence: %s
                    """.formatted(context.tenant().businessUnit(), context.asOfDate(), objectMapper.writeValueAsString(candidates),
                    objectMapper.writeValueAsString(investigation.evidence()));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot serialize candidate claims", exception);
        }
    }

    private VerificationResult withWarnings(VerificationResult verified, List<String> warnings) {
        VerificationResult.Status status = verified.status() == VerificationResult.Status.VERIFIED && !warnings.isEmpty()
                ? VerificationResult.Status.QUALIFIED : verified.status();
        return new VerificationResult(status, verified.claims(), verified.rejectedClaimIds(), List.copyOf(warnings));
    }
}
