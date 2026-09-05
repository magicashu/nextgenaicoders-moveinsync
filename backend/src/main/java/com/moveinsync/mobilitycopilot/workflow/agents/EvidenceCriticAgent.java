package com.moveinsync.mobilitycopilot.workflow.agents;

import tools.jackson.databind.JsonNode;
import com.moveinsync.mobilitycopilot.config.WorkflowProperties;
import com.moveinsync.mobilitycopilot.evidence.application.EvidenceVerifier;
import com.moveinsync.mobilitycopilot.evidence.domain.Claim;
import com.moveinsync.mobilitycopilot.evidence.domain.EvidencePackage;
import com.moveinsync.mobilitycopilot.evidence.domain.VerificationResult;
import com.moveinsync.mobilitycopilot.workflow.application.ports.LanguageModelPort;
import com.moveinsync.mobilitycopilot.workflow.application.ports.WorkerEvidenceDto;
import com.moveinsync.mobilitycopilot.workflow.domain.Critique;
import com.moveinsync.mobilitycopilot.workflow.domain.WorkflowRun;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * A3 Evidence Critic: challenges draft claims. The deterministic rule set is authoritative; a model
 * may add overclaim flags for existing claim ids but can never add facts or clear a deterministic
 * violation. One review, at most one correction cycle (enforced by the engine).
 */
@Component
public final class EvidenceCriticAgent {

    private final EvidenceVerifier verifier;
    private final ModelAssist assist;

    public EvidenceCriticAgent(EvidenceVerifier verifier, LanguageModelPort model, WorkflowProperties properties) {
        this.verifier = verifier;
        this.assist = new ModelAssist(model, properties.modelTimeout(), 600);
    }

    public Critique critique(WorkflowRun run, List<WorkerEvidenceDto.Ranking> vendorRankings) {
        EvidencePackage evidence = run.evidence();
        VerificationResult preview = verifier.verify(evidence, vendorRankings, List.of());
        Set<String> overclaims = new LinkedHashSet<>(preview.removedClaimIds());
        List<String> missingCaveats = new ArrayList<>();
        List<String> contradictions = new ArrayList<>(evidence.contradictions());
        List<String> notes = new ArrayList<>();
        preview.violations().forEach(v -> notes.add(v.code() + (v.claimId() == null ? "" : " [" + v.claimId() + "]") + ": " + v.message()));
        preview.violations().stream().filter(v -> v.code().equals("WARN_MISSING_CAVEAT")).forEach(v -> missingCaveats.add(v.message()));
        for (String gap : evidence.capabilityGaps()) {
            if (evidence.bundle().caveats().stream().noneMatch(c -> c.contains(gap))) {
                missingCaveats.add(gap);
            }
        }
        boolean modelAssisted = false;
        Optional<JsonNode> review = assist.ask("evidence-critic", Map.of(
                "claims", evidence.claims().stream().map(c -> Map.of("id", c.claimId(), "text", c.text(), "kind", c.kind().name(), "evidenceIds", c.evidenceIds())).toList(),
                "evidence", evidence.bundle().items().stream().map(i -> Map.of("id", i.evidenceId(), "metric", i.metricId(), "value", String.valueOf(i.value()),
                        "baseline", String.valueOf(i.baselineValue()), "delta", String.valueOf(i.delta()), "numerator", String.valueOf(i.numerator()),
                        "denominator", String.valueOf(i.denominator()))).toList(),
                "vendorRanking", vendorRankings.stream().map(r -> Map.of("allQualifiedIncreased", r.allQualifiedIncreased(), "qualified", r.qualifiedRows().size())).toList(),
                "capabilityGaps", evidence.capabilityGaps()), run);
        if (review.isPresent()) {
            modelAssisted = true;
            Set<String> knownClaims = new LinkedHashSet<>(evidence.claims().stream().map(Claim::claimId).toList());
            for (JsonNode id : review.get().path("overclaimClaimIds")) {
                if (knownClaims.contains(id.asText())) {
                    overclaims.add(id.asText());
                    notes.add("Model flagged overclaim " + id.asText());
                }
            }
            for (JsonNode caveat : review.get().path("missingCaveats")) {
                missingCaveats.add(caveat.asText());
            }
        }
        List<String> supported = evidence.claims().stream().map(Claim::claimId).filter(id -> !overclaims.contains(id)).toList();
        Critique.Verdict verdict;
        if (supported.isEmpty() && !evidence.claims().isEmpty()) {
            verdict = Critique.Verdict.ABSTAIN;
        } else if (!overclaims.isEmpty() || !contradictions.isEmpty()) {
            verdict = Critique.Verdict.REVISE;
        } else {
            verdict = Critique.Verdict.PASS;
        }
        return new Critique(verdict, supported, new ArrayList<>(overclaims), missingCaveats, contradictions, notes, modelAssisted);
    }
}
