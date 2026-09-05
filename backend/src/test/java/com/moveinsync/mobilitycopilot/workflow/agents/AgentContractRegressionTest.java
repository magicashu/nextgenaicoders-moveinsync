package com.moveinsync.mobilitycopilot.workflow.agents;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moveinsync.mobilitycopilot.access.domain.*;
import com.moveinsync.mobilitycopilot.action.domain.*;
import com.moveinsync.mobilitycopilot.anomaly.domain.AnomalyIssue;
import com.moveinsync.mobilitycopilot.evidence.application.*;
import com.moveinsync.mobilitycopilot.evidence.domain.*;
import com.moveinsync.mobilitycopilot.metrics.domain.*;
import com.moveinsync.mobilitycopilot.workflow.agents.impl.*;
import com.moveinsync.mobilitycopilot.workflow.application.ports.LanguageModelPort;
import com.moveinsync.mobilitycopilot.workflow.domain.*;
import com.moveinsync.mobilitycopilot.workflow.investigation.executor.BoundedInvestigationExecutor;
import com.moveinsync.mobilitycopilot.workflow.investigation.registry.*;
import com.moveinsync.mobilitycopilot.workflow.investigation.validation.EvidenceValidator;
import com.moveinsync.mobilitycopilot.workflow.investigation.workers.WorkerType;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import static org.assertj.core.api.Assertions.*;

/** Small synthetic unit fixtures test control invariants; these are not official-data golden values. */
class AgentContractRegressionTest {
    RunContext context(int budget) {
        var tenant=new TenantContext("fixture-tenant");
        return new RunContext(UUID.randomUUID(),new ActorContext("tester",Set.of("TRANSPORT_MANAGER"),Set.of(tenant)),tenant,
                "TRANSPORT_MANAGER",LocalDate.of(2026,8,1),new RunVersions("fixture","metrics-v1","v1","v1","none","v1"),
                new WorkflowBudget(budget,2,1,Duration.ofSeconds(2),2),Instant.now().plusSeconds(5));
    }
    MetricRequest request(RunContext c,String start) {return new MetricRequest(c.tenant(),MetricId.M01_DELAYED_TRIP_RATE,MetricRequest.Measure.VALUE,
            new MetricWindow(LocalDate.parse(start),LocalDate.parse(start).plusDays(6)),Map.of(),c.versions().data());}
    MetricEvidence evidence(MetricRequest r){return new MetricEvidence("ev-"+r.window().start(),r,MetricStatus.AVAILABLE,new BigDecimal("20"),r.metricId().unit(),new BigDecimal("2"),new BigDecimal("10"),10,"metrics-v1","unit-fixture",List.of());}
    InvestigationTask task(String id,List<MetricRequest> requests,List<String> dependencies){return new InvestigationTask(id,WorkerType.VENDOR,"Compare",requests,dependencies);}
    InvestigationPlan plan(InvestigationTask...tasks){return new InvestigationPlan("plan","issue",List.of(tasks),Set.of(),List.of("Stop after planned requests"));}
    RegisterableWorker<MetricEvidence> worker(Function<InvestigationTask,MetricEvidence> f){return new RegisterableWorker<>() {
        public WorkerType workerType(){return WorkerType.VENDOR;}public String name(){return "fixture";}
        public MetricEvidence execute(RunContext c,InvestigationTask t){return f.apply(t);}
    };}
    InvestigationAgentImpl investigator(BoundedInvestigationExecutor executor,Function<InvestigationTask,MetricEvidence> f) {
        return new InvestigationAgentImpl(new WorkerRegistry(List.of(worker(f))),new EvidenceValidator(),executor);
    }

    @Test void executes_current_and_baseline_instead_of_silently_dropping_second_request(){
        var c=context(2);var current=request(c,"2026-06-01");var baseline=request(c,"2026-05-04");
        try(var executor=new BoundedInvestigationExecutor(2,4)){
            var result=investigator(executor,t->evidence(t.requests().getFirst())).investigate(c,plan(task("t",List.of(current,baseline),List.of())));
            assertThat(result.evidence()).extracting(e->e.request().window()).containsExactly(current.window(),baseline.window());
            assertThat(result.completedTasks()).hasSize(1);assertThat(result.pendingTasks()).isEmpty();
        }
    }
    @Test void exhausted_shared_budget_preserves_partial_current_evidence(){
        var c=context(1);var calls=new AtomicInteger();
        try(var executor=new BoundedInvestigationExecutor(2,4)){
            var result=investigator(executor,t->{calls.incrementAndGet();return evidence(t.requests().getFirst());}).investigate(c,
                    plan(task("t",List.of(request(c,"2026-06-01"),request(c,"2026-05-04")),List.of())));
            assertThat(calls).hasValue(1);assertThat(result.evidence()).hasSize(1);assertThat(result.pendingTasks()).hasSize(1);
        }
    }
    @Test void wrong_period_from_worker_is_rejected(){var c=context(2);
        try(var executor=new BoundedInvestigationExecutor(1,2)){
            var result=investigator(executor,t->evidence(request(c,"2026-05-04"))).investigate(c,plan(task("t",List.of(request(c,"2026-06-01")),List.of())));
            assertThat(result.evidence()).isEmpty();assertThat(result.pendingTasks()).hasSize(1);
        }
    }
    @Test void cycles_and_missing_dependencies_fail_before_tools(){var c=context(3);var r=request(c,"2026-06-01");
        try(var executor=new BoundedInvestigationExecutor(1,2)){
            var agent=investigator(executor,t->{throw new AssertionError("Must not execute invalid graph");});
            assertThatThrownBy(()->agent.investigate(c,plan(task("a",List.of(r),List.of("b")),task("b",List.of(r),List.of("a"))))).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(()->agent.investigate(c,plan(task("a",List.of(r),List.of("missing"))))).isInstanceOf(IllegalArgumentException.class);
        }
    }
    @Test void failed_dependency_does_not_release_dependents(){var c=context(3);var calls=new AtomicInteger();var r=request(c,"2026-06-01");
        try(var executor=new BoundedInvestigationExecutor(1,2)){
            var result=investigator(executor,t->{calls.incrementAndGet();throw new IllegalStateException("fixture failure");}).investigate(c,
                    plan(task("a",List.of(r),List.of()),task("b",List.of(r),List.of("a"))));
            assertThat(calls).hasValue(1);assertThat(result.pendingTasks()).hasSize(2);
        }
    }
    @Test void cross_tenant_request_is_denied_before_execution(){var c=context(2);var r=request(c,"2026-06-01");var foreign=new MetricRequest(new TenantContext("other"),r.metricId(),r.measure(),r.window(),r.filters(),r.dataVersion());
        try(var executor=new BoundedInvestigationExecutor(1,2)){
            assertThatThrownBy(()->investigator(executor,t->{throw new AssertionError();}).investigate(c,plan(task("t",List.of(foreign),List.of())))).isInstanceOf(IllegalArgumentException.class);
        }
    }
    @Test void queue_saturation_has_explicit_bounded_rejection() throws Exception {
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        try(var executor=new BoundedInvestigationExecutor(1,1)){
            executor.submit(()->{entered.countDown();release.await();return null;});assertThat(entered.await(2,TimeUnit.SECONDS)).isTrue();
            executor.submit(()->null);assertThatThrownBy(()->executor.submit(()->null)).isInstanceOf(RejectedExecutionException.class);
            assertThat(executor.queued()).isEqualTo(1);
        } finally {release.countDown();}
    }
    @Test void duplicate_workers_cannot_override_registry(){var w=worker(t->null);assertThatThrownBy(()->new WorkerRegistry(List.of(w,w))).isInstanceOf(IllegalArgumentException.class);}
    @Test void supervisor_retains_all_required_g1_branches_when_model_selects_none(){
        var c=context(12);var ev=evidence(request(c,"2026-06-01"));var caps=new EnumMap<MetricId,CapabilityMatrix.Capability>(MetricId.class);
        for(var id:MetricId.values())caps.put(id,new CapabilityMatrix.Capability(CapabilityMatrix.Status.SUPPORTED,"fixture"));
        var modelCalls=new AtomicInteger();
        var beans=new DefaultListableBeanFactory();beans.registerSingleton("model",(LanguageModelPort)r->{modelCalls.incrementAndGet();return new LanguageModelPort.ModelResponse("fixture","{\"tasks\":[]}",0,0,0);});beans.registerSingleton("mapper",new ObjectMapper().findAndRegisterModules());
        var agent=new GovernedSupervisorAgent(Optional.empty(),beans.getBeanProvider(LanguageModelPort.class),beans.getBeanProvider(ObjectMapper.class));
        var issue=new AnomalyIssue("issue",c.tenant(),c.versions().data(),"Delay","UNASSESSED","DELAY",List.of(ev),Map.of(),List.of());
        var p=agent.plan(new SupervisorPlanningRequest(c,issue,new CapabilityMatrix(c.tenant(),c.versions().data(),caps)));
        assertThat(p.tasks()).extracting(InvestigationTask::worker).containsExactly(WorkerType.VENDOR,WorkerType.SITE_SHIFT_DIRECTION,WorkerType.DELAY_REASON,WorkerType.FEEDBACK,WorkerType.COST_BILLING);
        assertThat(modelCalls).hasValue(1);
        var limited=context(2);
        assertThatThrownBy(()->agent.plan(new SupervisorPlanningRequest(limited,issue,
                new CapabilityMatrix(limited.tenant(),limited.versions().data(),caps))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("all required");
    }
    @Test void canonical_wrong_number_and_inconsistent_evidence_are_rejected(){var c=context(2);var e=evidence(request(c,"2026-06-01"));
        var verifier=new DeterministicEvidenceVerifier();var valid=new Claim("c",MetricClaimText.direct(e),Set.of(e.evidenceId()),VerifiedClaim.Kind.DIRECT);
        assertThat(verifier.verify(c,List.of(valid),List.of(e)).claims()).hasSize(1);
        var wrong=new Claim("c",valid.text().replace("= 20 ","= 90 "),valid.evidenceIds(),valid.kind());
        assertThat(verifier.verify(c,List.of(wrong),List.of(e)).status()).isEqualTo(VerificationResult.Status.REJECTED);
        var forged=new MetricEvidence(e.evidenceId(),e.request(),e.status(),new BigDecimal("90"),e.unit(),e.numerator(),e.denominator(),e.population(),e.metricVersion(),e.sourceReference(),e.warnings());
        assertThat(verifier.verify(c,List.of(new Claim("c",MetricClaimText.direct(forged),valid.evidenceIds(),valid.kind())),List.of(forged)).status()).isEqualTo(VerificationResult.Status.REJECTED);
    }
    @Test void semantic_rejection_cannot_become_a_verified_claim(){var c=context(2);var e=evidence(request(c,"2026-06-01"));
        LanguageModelPort model=r->new LanguageModelPort.ModelResponse("fixture","{\"overallStatus\":\"NEEDS_CORRECTION\",\"claims\":[{\"claimId\":\"claim-"+e.evidenceId()+"\",\"decision\":\"REJECT\",\"issues\":[],\"requiredCaveats\":[]}],\"globalCaveats\":[]}",0,0,0);
        var agent=new EvidenceCriticAgentImpl(new DeterministicEvidenceVerifier(),Optional.of(model),new ObjectMapper().findAndRegisterModules());
        assertThat(agent.review(c,new InvestigationResult(List.of(e),List.of(),List.of(),List.of())).claims()).isEmpty();
    }
    @Test void rejected_verification_never_renders_claims_or_drafts(){var c=context(2);var e=evidence(request(c,"2026-06-01"));
        var claim=new VerifiedClaim("c",c.tenant(),"fixture","metrics-v1",MetricClaimText.direct(e),Set.of(e.evidenceId()),VerifiedClaim.Kind.DIRECT);
        var brief=new BriefingActionAgentImpl().draft(c,new VerificationResult(VerificationResult.Status.REJECTED,List.of(claim),Set.of(),List.of("Verification failed")));
        assertThat(brief.operationalSummary()).doesNotContain(claim.text()).contains("Verification failed");assertThat(brief.proposedActions()).isEmpty();
    }
    @Test void explicit_policy_can_only_create_a_draft(){var c=context(2);var e=evidence(request(c,"2026-06-01"));
        var verified=new DeterministicEvidenceVerifier().verify(c,MetricClaimText.candidates(List.of(e)),List.of(e));
        var policy=new AllowedActionPolicy("v1",Set.of("INVESTIGATION_TICKET"),Set.of(),Duration.ofHours(1),false,true);
        var brief=new BriefingActionAgentImpl().draftWithAction(c,verified,policy,new ActionTarget(c.tenant(),Set.of(),Map.of()),"INVESTIGATION_TICKET");
        assertThat(brief.proposedActions()).hasSize(1);assertThat(brief.proposedActions().getFirst().status()).isEqualTo("DRAFT");
    }
}
