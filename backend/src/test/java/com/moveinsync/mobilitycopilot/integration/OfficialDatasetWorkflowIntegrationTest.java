package com.moveinsync.mobilitycopilot.integration;

import com.moveinsync.mobilitycopilot.access.domain.ActorContext;
import com.moveinsync.mobilitycopilot.access.domain.TenantContext;
import com.moveinsync.mobilitycopilot.action.domain.ActionStatus;
import com.moveinsync.mobilitycopilot.approval.domain.ApprovalDecisionType;
import com.moveinsync.mobilitycopilot.observability.TraceRecorder;
import com.moveinsync.mobilitycopilot.reporting.application.DecisionRunGateway;
import com.moveinsync.mobilitycopilot.reporting.application.RunView;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** Release gate proving the composed product runs, pauses and resumes on the immutable official files. */
@SpringBootTest
class OfficialDatasetWorkflowIntegrationTest {

    private static final String DATASET = "outputs/official dataset/MoveInSync - Anonymised Trip-Log Dataset";
    private static final Path OFFICIAL_DATASET = locateDataset();

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("mobility.data.directory", () -> OFFICIAL_DATASET == null ? "data/fixtures" : OFFICIAL_DATASET.toString());
        registry.add("mobility.workflow.analytics-gateway", () -> "governed");
        registry.add("mobility.workflow.control-plane", () -> "in-memory");
        registry.add("mobility.api.gateway", () -> "workflow");
        registry.add("mobility.api.actor-resolver", () -> "governance");
    }

    @Autowired
    private DecisionRunGateway gateway;

    @Autowired
    private TraceRecorder traces;

    @Test
    void officialG1RunsThroughApprovalRevalidationExecutionAndTrace() {
        Assumptions.assumeTrue(OFFICIAL_DATASET != null, "Official dataset is not available in this checkout");
        ActorContext actor = new ActorContext("integration-transport-manager", "pinnacle-Slc", Set.of("TRANSPORT_MANAGER"));

        RunView pending = gateway.morningBrief(
                actor, new TenantContext("pinnacle-Slc"), LocalDate.parse("2026-06-08"), "TRANSPORT_MANAGER");

        assertThat(pending.brief().metric().value()).isEqualByComparingTo("21.88");
        assertThat(pending.brief().metric().baselineValue()).isEqualByComparingTo("12.28");
        assertThat(pending.finalStep()).isEqualTo("AWAITING_APPROVAL");
        assertThat(pending.approvalStatus()).isEqualTo("PENDING");
        assertThat(pending.claims()).isNotEmpty();
        assertThat(pending.transitions()).extracting(RunView.Transition::node)
                .contains("RUN_INVESTIGATIONS", "EVIDENCE_CRITIC", "APPROVAL_INTERRUPT");

        RunView executed = gateway.decide(
                actor, pending.approvalRequest().approvalId(), ApprovalDecisionType.APPROVE,
                "Official-data integration gate", null);

        assertThat(executed.finalStep()).isEqualTo("EXECUTED");
        assertThat(executed.approvalStatus()).isEqualTo("APPROVED");
        assertThat(executed.receipt()).isNotNull();
        assertThat(executed.receipt().status()).isEqualTo(ActionStatus.EXECUTED);
        assertThat(executed.traceId()).hasSize(32);
        assertThat(traces.find(executed.traceId())).isPresent();
        assertThat(traces.find(executed.traceId()).orElseThrow().spans())
                .anyMatch(span -> span.name().equals("revalidate_and_execute"));
    }

    private static Path locateDataset() {
        for (Path candidate : new Path[]{Path.of(DATASET), Path.of("..").resolve(DATASET)}) {
            Path normalized = candidate.toAbsolutePath().normalize();
            if (Files.isDirectory(normalized)) {
                return normalized;
            }
        }
        return null;
    }
}
