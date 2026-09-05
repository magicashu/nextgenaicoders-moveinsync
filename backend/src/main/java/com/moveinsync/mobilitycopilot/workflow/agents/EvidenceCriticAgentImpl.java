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
        return review(context, List.of(), investigation);
    }

    @Override
    public VerificationResult review(RunContext context, List<Claim> candidates, InvestigationResult investigation) {
        candidates = List.copyOf(candidates == null ? List.of() : candidates);
        VerificationResult verified = verifier.verify(context, candidates, investigation.evidence());
        List<String> warnings = new ArrayList<>(verified.warnings());
        if (model.isEmpty()) {
            warnings.add("Semantic critique unavailable: no language-model provider is configured; deterministic verification completed.");
            return withWarnings(verified, warnings);
        }
        try {
            CritiqueResult critique = parse(model.get().complete(new LanguageModelPort.ModelRequest(
                    context, LanguageModelPort.AgentRole.EVIDENCE_CRITIC, PROMPT_VERSION,
                    prompt(context, candidates, investigation), investigation.evidence())).structuredOutput());
            critique.globalCaveats().forEach(caveat -> warnings.add("Critic caveat: " + caveat));
            critique.claims().forEach(review -> review.issues().forEach(issue ->
                    warnings.add("Critic " + review.claimId() + " " + issue.type() + ": " + issue.explanation())));
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
