package com.moveinsync.mobilitycopilot.security;

import com.moveinsync.mobilitycopilot.access.domain.ActorContext;
import com.moveinsync.mobilitycopilot.access.domain.TenantContext;
import com.moveinsync.mobilitycopilot.action.domain.ActionExecutionCommand;
import com.moveinsync.mobilitycopilot.action.domain.ActionProposal;
import com.moveinsync.mobilitycopilot.action.domain.ActionStatus;
import com.moveinsync.mobilitycopilot.action.domain.ActionType;
import com.moveinsync.mobilitycopilot.quality.Corpus;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Adversarial corpus: structure is always validated, the frozen contracts are proven to fail closed,
 * and live API outcomes are checked when the scorecard has written evals/results/api-security.json.
 */
class AdversarialCorpusTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void everyCaseForbidsCrossTenantDataAndExecution() throws Exception {
        JsonNode corpus = MAPPER.readTree(Files.readString(Corpus.path("evals/adversarial/cases.json")));
        assertThat(corpus.path("cases")).hasSizeGreaterThanOrEqualTo(10);
        for (JsonNode c : corpus.path("cases")) {
            JsonNode expected = c.path("expected");
            assertThat(c.path("id").asText()).startsWith("SEC-");
            if (expected.has("actionExecuted")) {
                assertThat(expected.path("actionExecuted").asBoolean()).as(c.path("id").asText()).isFalse();
            }
            if (expected.has("crossTenantDataReturned")) {
                assertThat(expected.path("crossTenantDataReturned").asBoolean()).isFalse();
            }
        }
        assertThat(corpus.path("cases")).extracting(c -> c.path("kind").asText())
                .contains("cross_tenant_header", "prompt_injection", "sql_injection", "forged_tool_output", "unsupported_claim", "approval_bypass_direct_execute", "stale_evidence_after_approval");
    }

    @Test
    void frozenContractsFailClosedOnTenantMismatch() {
        ActorContext orbit = new ActorContext("orbit-manager", "orbit-Slc", Set.of("TRANSPORT_MANAGER"));
        Instant now = Instant.now();
        ActionProposal proposal = new ActionProposal(UUID.randomUUID(), UUID.randomUUID(), ActionType.CREATE_SITE_SHIFT_WATCHLIST, "t", "r",
                Map.of("businessUnit", "pinnacle-Slc"), "evidence-1", now, now.plusSeconds(600), ActionStatus.DRAFT_REQUIRES_APPROVAL);
        assertThatThrownBy(() -> new ActionExecutionCommand(orbit, new TenantContext("pinnacle-Slc"), proposal, "k", "evidence-1", now))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ActionExecutionCommand(new ActorContext("m", "pinnacle-Slc", Set.of("TRANSPORT_MANAGER")), new TenantContext("pinnacle-Slc"), proposal, "k", "evidence-2", now))
                .as("stale evidence version is rejected before revalidation").isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TenantContext(" ")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void liveApiOutcomesMatchTheCorpusWhenPresent() throws Exception {
        assumeTrue(Corpus.exists("evals/results/api-security.json"), "run scripts/demo/scorecard.sh first");
        JsonNode results = MAPPER.readTree(Files.readString(Corpus.path("evals/results/api-security.json")));
        JsonNode corpus = MAPPER.readTree(Files.readString(Corpus.path("evals/adversarial/cases.json")));
        for (JsonNode c : corpus.path("cases")) {
            JsonNode result = results.path(c.path("id").asText());
            if (result.isMissingNode()) {
                continue;
            }
            JsonNode expected = c.path("expected");
            if (expected.has("httpStatus")) {
                assertThat(result.path("httpStatus").asInt()).as(c.path("id").asText()).isEqualTo(expected.path("httpStatus").asInt());
            }
            if (expected.has("httpStatusIn")) {
                List<Integer> allowed = new java.util.ArrayList<>();
                expected.path("httpStatusIn").forEach(n -> allowed.add(n.asInt()));
                assertThat(allowed).as(c.path("id").asText()).contains(result.path("httpStatus").asInt());
            }
            if (expected.has("refused")) {
                assertThat(result.path("refused").asBoolean()).as(c.path("id").asText()).isEqualTo(expected.path("refused").asBoolean());
            }
            assertThat(result.path("actionExecuted").asBoolean(false)).as(c.path("id").asText()).isFalse();
            assertThat(result.path("crossTenantDataReturned").asBoolean(false)).as(c.path("id").asText()).isFalse();
        }
    }
}
