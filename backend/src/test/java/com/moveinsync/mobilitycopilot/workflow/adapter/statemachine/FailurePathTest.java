package com.moveinsync.mobilitycopilot.workflow.adapter.statemachine;

import com.moveinsync.mobilitycopilot.access.domain.ActorContext;
import com.moveinsync.mobilitycopilot.access.domain.TenantContext;
import com.moveinsync.mobilitycopilot.config.WorkflowProperties;
import com.moveinsync.mobilitycopilot.workflow.application.ports.AnalyticsGateway;
import com.moveinsync.mobilitycopilot.workflow.application.ports.LanguageModelPort;
import com.moveinsync.mobilitycopilot.workflow.application.ports.WorkerEvidenceDto;
import com.moveinsync.mobilitycopilot.workflow.domain.Critique;
import com.moveinsync.mobilitycopilot.workflow.domain.RunContext;
import com.moveinsync.mobilitycopilot.workflow.domain.WorkflowNode;
import com.moveinsync.mobilitycopilot.workflow.domain.WorkflowOutcome;
import com.moveinsync.mobilitycopilot.workflow.domain.WorkflowRun;
import com.moveinsync.mobilitycopilot.workflow.domain.WorkflowStep;
import com.moveinsync.mobilitycopilot.workflow.support.EngineHarness;
import com.moveinsync.mobilitycopilot.workflow.support.G1Fixtures;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class FailurePathTest {

    @Test
    void healthyTenantProducesHealthyBriefWithNoPlanNoToolsNoApproval() {
        G1Fixtures analytics = new G1Fixtures();
        EngineHarness harness = new EngineHarness(analytics, new LanguageModelPort.Unavailable());

        WorkflowOutcome outcome = harness.run("catalyst-Sac", G1Fixtures.AS_OF);

        assertThat(outcome.finalStep()).isEqualTo(WorkflowStep.HEALTHY);
        assertThat(outcome.brief().status()).isEqualTo("HEALTHY");
        assertThat(outcome.brief().headline()).contains("no material operational anomaly");
        assertThat(analytics.workerCalls.get()).isZero();
        assertThat(harness.approvals.findRequest(outcome.brief().recommendedAction().actionId())).isEmpty();
        assertThat(harness.transitions.stream().map(t -> t.node())).doesNotContain(WorkflowNode.SUPERVISOR_PLAN, WorkflowNode.APPROVAL_INTERRUPT);
        assertThat(outcome.auditEvents()).extracting("eventType").contains("HEALTHY_BRIEF", "RUN_SUMMARY");
    }

    @Test
    void crossTenantActorIsDeniedBeforeAnyToolCall() {
        G1Fixtures analytics = new G1Fixtures();
        EngineHarness harness = new EngineHarness(analytics, new LanguageModelPort.Unavailable());
        ActorContext orbitManager = new ActorContext("orbit-manager", "orbit-Slc", Set.of("TRANSPORT_MANAGER"));

        WorkflowOutcome outcome = harness.run(orbitManager, "pinnacle-Slc", G1Fixtures.AS_OF, RunContext.Persona.TRANSPORT_MANAGER, null);

        assertThat(outcome.finalStep()).isEqualTo(WorkflowStep.FAILED);
        assertThat(analytics.workerCalls.get()).isZero();
        assertThat(harness.actions.effectCount()).isZero();
        assertThat(outcome.auditEvents()).extracting("eventType").containsExactly("ACCESS_DENIED");
        assertThat(outcome.brief().evidence().items()).hasSize(1);
        assertThat(outcome.brief().metric().status()).isEqualTo(com.moveinsync.mobilitycopilot.metrics.domain.MetricStatus.UNSUPPORTED);
        assertThat(harness.transitions.stream().map(t -> t.node())).containsExactly(WorkflowNode.INITIALIZE_RUN, WorkflowNode.AUTHORIZE_SCOPE);
    }

    @Test
    void lineManagerWithoutInvestigatePermissionFailsClosed() {
        G1Fixtures analytics = new G1Fixtures();
        EngineHarness harness = new EngineHarness(analytics, new LanguageModelPort.Unavailable());
        ActorContext line = new ActorContext("line-1", "pinnacle-Slc", Set.of("LINE_MANAGER"));
        WorkflowOutcome outcome = harness.run(line, "pinnacle-Slc", G1Fixtures.AS_OF, RunContext.Persona.LINE_MANAGER, null);
        assertThat(outcome.finalStep()).isEqualTo(WorkflowStep.FAILED);
        assertThat(analytics.workerCalls.get()).isZero();
    }

    @Test
    void failedBranchIsPreservedAsPartialEvidenceWithLowerConfidence() {
        G1Fixtures analytics = new G1Fixtures();
        analytics.failingWorker = "cost_billing";
        EngineHarness harness = new EngineHarness(analytics, new LanguageModelPort.Unavailable());

        WorkflowOutcome outcome = harness.run("pinnacle-Slc", G1Fixtures.AS_OF);
        WorkflowRun run = harness.engine.find(outcome.brief().runId()).orElseThrow();

        assertThat(outcome.finalStep()).isEqualTo(WorkflowStep.AWAITING_APPROVAL);
        assertThat(run.investigations()).filteredOn(i -> i.worker().equals("cost_billing")).singleElement()
                .satisfies(i -> assertThat(i.status()).isEqualTo(com.moveinsync.mobilitycopilot.workflow.domain.InvestigationResult.Status.FAILED));
        assertThat(run.investigations()).filteredOn(i -> !i.worker().equals("cost_billing")).allMatch(i -> i.succeeded());
        assertThat(run.evidence().branchStatus()).containsEntry("cost_billing", "FAILED").containsEntry("vendor", "COMPLETE");
        assertThat(run.evidence().bundle().caveats()).anyMatch(c -> c.contains("cost_billing") && c.contains("failed"));
        assertThat(run.verification().confidence()).isLessThan(new java.math.BigDecimal("0.84"));
        assertThat(outcome.brief().findings()).anyMatch(f -> f.contains("cost_billing"));
    }

    @Test
    void toolCallBudgetStopsFanOutAndQualifiesSkippedBranches() {
        G1Fixtures analytics = new G1Fixtures();
        EngineHarness harness = new EngineHarness(analytics, new LanguageModelPort.Unavailable(),
                new WorkflowProperties(4, 1, 3, Duration.ofSeconds(10), Duration.ofMinutes(30)));

        WorkflowOutcome outcome = harness.run("pinnacle-Slc", G1Fixtures.AS_OF);
        WorkflowRun run = harness.engine.find(outcome.brief().runId()).orElseThrow();

        assertThat(analytics.workerCalls.get()).isEqualTo(3);
        assertThat(run.state().toolCalls()).isEqualTo(3);
        assertThat(run.plan().tasks()).hasSize(3);
        assertThat(run.investigations()).anyMatch(i -> i.status() != com.moveinsync.mobilitycopilot.workflow.domain.InvestigationResult.Status.FAILED);
        assertThat(outcome.finalStep()).isIn(WorkflowStep.AWAITING_APPROVAL, WorkflowStep.REPORT_ONLY);
    }

    @Test
    void investigationTimeoutBecomesQualifiedFailureNotCrash() {
        G1Fixtures analytics = new G1Fixtures();
        analytics.slowWorkers = true;
        EngineHarness harness = new EngineHarness(analytics, new LanguageModelPort.Unavailable(),
                new WorkflowProperties(4, 1, 12, Duration.ofMillis(60), Duration.ofMinutes(30)));

        WorkflowOutcome outcome = harness.run("pinnacle-Slc", G1Fixtures.AS_OF);
        WorkflowRun run = harness.engine.find(outcome.brief().runId()).orElseThrow();

        assertThat(run.investigations()).anyMatch(i -> i.qualityWarnings().stream().anyMatch(w -> w.startsWith("TIMEOUT")));
        assertThat(outcome.finalStep()).isIn(WorkflowStep.AWAITING_APPROVAL, WorkflowStep.REPORT_ONLY, WorkflowStep.COMPLETED);
        assertThat(harness.actions.effectCount()).isZero();
    }

    @Test
    void criticBlocksVendorBlameAndUsesExactlyOneCorrectionCycle() {
        AnalyticsGateway blaming = new BlamingGateway();
        EngineHarness harness = new EngineHarness(blaming, new LanguageModelPort.Unavailable());

        WorkflowOutcome outcome = harness.run("pinnacle-Slc", G1Fixtures.AS_OF);
        WorkflowRun run = harness.engine.find(outcome.brief().runId()).orElseThrow();

        assertThat(run.critique().verdict()).isEqualTo(Critique.Verdict.REVISE);
        assertThat(run.critique().notes()).anyMatch(n -> n.contains("VENDOR_BLAME") || n.contains("UNSUPPORTED_NUMBER") || n.contains("CAUSAL_LANGUAGE"));
        assertThat(run.state().correctionCycles()).isEqualTo(1);
        assertThat(run.verification().passed()).isTrue();
        assertThat(run.evidence().claims()).noneMatch(c -> c.text().contains("to blame"));
        assertThat(run.evidence().claims()).noneMatch(c -> c.text().contains("99.9%"));
        assertThat(outcome.brief().findings()).noneMatch(f -> f.contains("Pooja Mikhailov Travel is the sole"));
        assertThat(run.action().type()).isNotEqualTo(com.moveinsync.mobilitycopilot.action.domain.ActionType.DRAFT_VENDOR_ESCALATION);
        assertThat(harness.transitions.stream().filter(t -> t.node() == WorkflowNode.VERIFY_EVIDENCE)).singleElement()
                .satisfies(t -> assertThat(t.attributes()).containsEntry("correctionCycle", "1"));
    }

    @Test
    void malformedModelOutputFallsBackDeterministicallyAndIsAccounted() {
        G1Fixtures analytics = new G1Fixtures();
        LanguageModelPort garbage = new LanguageModelPort() {
            @Override
            public Optional<Completion> complete(Request request) {
                return Optional.of(new Completion("Sure! Here is SQL: DROP TABLE trips; {\"tasks\": [{\"worker\": \"gps\"}], \"action\": \"CALL_TOOL\", \"filters\": {\"employee_id\": 7}}", 120, 40, 15));
            }

            @Override
            public String modelId() {
                return "fake-model";
            }
        };
        EngineHarness harness = new EngineHarness(analytics, garbage);
        WorkflowOutcome outcome = harness.run("pinnacle-Slc", G1Fixtures.AS_OF);
        WorkflowRun run = harness.engine.find(outcome.brief().runId()).orElseThrow();

        assertThat(outcome.finalStep()).isEqualTo(WorkflowStep.AWAITING_APPROVAL);
        assertThat(run.plan().modelGenerated()).isFalse();
        assertThat(run.plan().validationNotes()).anyMatch(n -> n.contains("gps"));
        assertThat(run.plan().workers()).doesNotContain("gps");
        assertThat(run.modelUsage()).isNotEmpty().anyMatch(u -> u.modelId().equals("fake-model"));
        assertThat(analytics.workersCalled).doesNotContain("gps");
        assertThat(run.state().toolCalls()).isLessThanOrEqualTo(run.state().maxToolCalls());
    }

    /** Gateway whose vendor worker overclaims: single-vendor blame plus an uncited number and causal language. */
    static final class BlamingGateway extends G1Fixtures {
        @Override
        public WorkerEvidenceDto runWorker(String worker, TenantContext tenant, WindowDto current, WindowDto baseline, Map<String, String> filters) {
            WorkerEvidenceDto base = super.runWorker(worker, tenant, current, baseline, filters);
            if (!worker.equals("vendor")) {
                return base;
            }
            return new WorkerEvidenceDto(worker, tenant.businessUnit(), base.metrics(), base.rankings(), base.distributions(), List.of(
                    "Pooja Mikhailov Travel is the sole driver of the delay and is to blame; 99.9% of delays are caused by this vendor.",
                    base.directFindings().getFirst()), base.caveats(), true);
        }
    }
}
