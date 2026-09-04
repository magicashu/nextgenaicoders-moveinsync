package com.moveinsync.mobilitycopilot.api;

import com.moveinsync.mobilitycopilot.access.application.AccessAuthorizer;
import com.moveinsync.mobilitycopilot.access.domain.ActorContext;
import com.moveinsync.mobilitycopilot.access.domain.Permission;
import com.moveinsync.mobilitycopilot.access.domain.TenantContext;
import com.moveinsync.mobilitycopilot.action.domain.ActionProposal;
import com.moveinsync.mobilitycopilot.action.domain.ActionStatus;
import com.moveinsync.mobilitycopilot.action.domain.ActionType;
import com.moveinsync.mobilitycopilot.action.domain.ExecutionReceipt;
import com.moveinsync.mobilitycopilot.api.security.AllowlistActorResolver;
import com.moveinsync.mobilitycopilot.approval.domain.ApprovalDecisionType;
import com.moveinsync.mobilitycopilot.approval.domain.ApprovalRequest;
import com.moveinsync.mobilitycopilot.audit.application.AuditSink;
import com.moveinsync.mobilitycopilot.audit.domain.AuditEvent;
import com.moveinsync.mobilitycopilot.evidence.domain.EvidenceBundle;
import com.moveinsync.mobilitycopilot.evidence.domain.EvidenceItem;
import com.moveinsync.mobilitycopilot.metrics.domain.MetricId;
import com.moveinsync.mobilitycopilot.metrics.domain.MetricResult;
import com.moveinsync.mobilitycopilot.metrics.domain.MetricStatus;
import com.moveinsync.mobilitycopilot.metrics.domain.MetricUnit;
import com.moveinsync.mobilitycopilot.reporting.application.ApprovalNotFoundException;
import com.moveinsync.mobilitycopilot.reporting.application.BriefRenderer;
import com.moveinsync.mobilitycopilot.reporting.application.DecisionRunGateway;
import com.moveinsync.mobilitycopilot.reporting.application.RunView;
import com.moveinsync.mobilitycopilot.reporting.domain.DecisionBrief;
import com.moveinsync.mobilitycopilot.conversation.application.ContextualQuestionService;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/** G1-shaped fake gateway with the hand-reconciled numbers; counts calls so refusals can be proven to cost nothing. */
@TestConfiguration
@Import({RequestContext.class, AllowlistActorResolver.class, BriefRenderer.class, ContextualQuestionService.class})
public class FakeGatewayConfig {

    public static final UUID RUN_ID = UUID.fromString("00000000-0000-0000-0000-00000000a001");
    public static final UUID ACTION_ID = UUID.fromString("00000000-0000-0000-0000-00000000a002");
    public static final UUID APPROVAL_ID = UUID.fromString("00000000-0000-0000-0000-00000000a003");
    public static final AtomicInteger GATEWAY_CALLS = new AtomicInteger();
    public static final List<String> DECISIONS = new CopyOnWriteArrayList<>();
    public static final Instant T0 = Instant.parse("2026-06-08T08:00:00Z");

    static MetricResult m01() {
        return new MetricResult(MetricId.M01_DELAYED_TRIP_RATE, "Delayed-trip rate", MetricUnit.PERCENT, MetricStatus.SUPPORTED, new BigDecimal("21.88"), new BigDecimal("12.28"),
                new BigDecimal("9.60"), new BigDecimal("4357"), new BigDecimal("19913"), 19913, LocalDate.parse("2026-06-01"), LocalDate.parse("2026-06-07"), Map.of(),
                "metrics-v1.1", "data-8ed5b4eae158", "sql/metrics/m01_delayed_trip_rate.sql", List.of());
    }

    public static RunView g1(String status, String approvalStatus, ExecutionReceipt receipt, String finalStep) {
        MetricResult metric = m01();
        EvidenceItem headline = new EvidenceItem("pinnacle-Slc:m01_delayed_trip_rate:2026-06-07", "M01_DELAYED_TRIP_RATE", metric.value(), "PERCENT", metric.baselineValue(), metric.delta(),
                metric.numerator(), metric.denominator(), 19913, metric.periodStart(), metric.periodEnd(), Map.of(), metric.source(), "metrics-v1.1", "data-8ed5b4eae158");
        EvidenceItem site = new EvidenceItem("pinnacle-Slc:m01_delayed_trip_rate:site_id:2026-06-07:clearwater-campus", "M01_DELAYED_TRIP_RATE", new BigDecimal("24.07"), "PERCENT",
                new BigDecimal("12.86"), new BigDecimal("11.21"), new BigDecimal("2226"), new BigDecimal("9247"), 9247, metric.periodStart(), metric.periodEnd(),
                Map.of("site_id", "Clearwater Campus"), metric.source(), "metrics-v1.1", "data-8ed5b4eae158");
        EvidenceItem share = new EvidenceItem(site.evidenceId() + ":share", "M01_DELAYED_TRIP_RATE", new BigDecimal("51.1"), "PERCENT", null, null, new BigDecimal("2226"),
                new BigDecimal("4357"), 9247, metric.periodStart(), metric.periodEnd(), Map.of("site_id", "Clearwater Campus", "measure", "share of numerator"), metric.source(), "metrics-v1.1", "data-8ed5b4eae158");
        EvidenceItem m09 = new EvidenceItem("pinnacle-Slc:m09_median_cost_per_trip:2026-06-07", "M09_MEDIAN_COST_PER_TRIP", new BigDecimal("1020.00"), "CURRENCY", new BigDecimal("1144.74"),
                new BigDecimal("-124.74"), null, new BigDecimal("88308"), 88308, metric.periodStart(), metric.periodEnd(), Map.of(), "sql/metrics/m09_median_cost_per_trip.sql", "metrics-v1.1", "data-8ed5b4eae158");
        EvidenceItem impact = new EvidenceItem(headline.evidenceId() + ":impact", "M01_DELAYED_TRIP_RATE", new BigDecimal("1912"), "COUNT", null, null, new BigDecimal("3414"),
                new BigDecimal("7780"), 19913, metric.periodStart(), metric.periodEnd(), Map.of("derivation", "excess"), "anomaly-rules", "metrics-v1.1", "data-8ed5b4eae158");
        EvidenceBundle bundle = new EvidenceBundle(List.of(headline, impact, site, share, m09), 0.84, 19913, List.of("severe_ack: Fewer than 20 Sev-1/2 alerts; P90 not meaningful"));
        ActionProposal action = new ActionProposal(ACTION_ID, RUN_ID, ActionType.CREATE_SITE_SHIFT_WATCHLIST,
                "Place Clearwater Campus 09:00, 09:30 and 10:30 shifts on a one-week watchlist and open an investigation ticket",
                "Every qualified vendor rose together, so this is a site and shift pattern rather than a single-vendor failure.",
                Map.of("businessUnit", "pinnacle-Slc", "site_id", "Clearwater Campus", "shift_id", "09:00,09:30,10:30", "followUp", "CREATE_INVESTIGATION_TICKET", "watchDays", "7"),
                "evidence-3f2a9c1b7d44", T0, T0.plusSeconds(1800), ActionStatus.DRAFT_REQUIRES_APPROVAL);
        List<RunView.Claim> claims = List.of(
                new RunView.Claim("c1", "Delayed-trip rate reached 21.88% (4,357 of 19,913) in the week to 2026-06-07, up from 12.28% in the prior four complete weeks (+9.6 points).", "DIRECT", List.of(headline.evidenceId()), "detector"),
                new RunView.Claim("c2", "About 1,912 excess delayed trips affected 7,780 rider legs (about 3,414 more than the baseline rate implies).", "INFERRED", List.of(impact.evidenceId()), "detector"),
                new RunView.Claim("c3", "Site 'Clearwater Campus' carries 51.1% of delayed trips at 24.07% (baseline 12.86%).", "DIRECT", List.of(site.evidenceId(), share.evidenceId()), "site_shift_direction"),
                new RunView.Claim("c4", "Every vendor with at least 500 trips in both windows rose (14 vendors, range 17.2% to 28.4%); the change is not attributable to a single vendor.", "DIRECT", List.of("pinnacle-Slc:m01_delayed_trip_rate:vendor_id:2026-06-07"), "vendor"),
                new RunView.Claim("c5", "Median billed cost per trip did not rise (1020.00 versus 1144.74); no cost penalty is visible.", "DIRECT", List.of(m09.evidenceId()), "cost_billing"),
                new RunView.Claim("c6", "severe_ack: Fewer than 20 Sev-1/2 alerts; P90 not meaningful", "CAVEAT", List.of(headline.evidenceId()), "system"),
                new RunView.Claim("c7", "Misses configured target, editable per tenant of 10.00.", "CAVEAT", List.of(headline.evidenceId()), "detector"));
        DecisionBrief brief = new DecisionBrief(RUN_ID, "pinnacle-Slc", LocalDate.parse("2026-06-08"),
                "pinnacle-Slc: delayed-trip rate reached 21.88% in the week to 2026-06-07, up from 12.28% in the prior four weeks (configured target 10%)",
                metric, claims.stream().filter(c -> !c.kind().equals("CAVEAT")).map(c -> c.text() + " [" + String.join(", ", c.evidenceIds()) + "]").toList(), action, bundle, status);
        ApprovalRequest approval = new ApprovalRequest(APPROVAL_ID, RUN_ID, "pinnacle-Slc", action, action.evidenceVersion(), T0, T0.plusSeconds(1800));
        return new RunView(RUN_ID, "trace-" + RUN_ID, "pinnacle-Slc", "TRANSPORT_MANAGER", "SCHEDULED", finalStep, T0, 1830, brief,
                brief.findings(), List.of(brief.headline() + ".", "About 1,900 excess delayed trips affected roughly 3,400 rider legs; confidence 0.84.",
                        "Site 'Clearwater Campus' carries 51.1% of delayed trips at 24.07% (baseline 12.86%).", "Recommended: " + action.title() + " (awaiting approval)."),
                claims, List.of("severe_ack: Fewer than 20 Sev-1/2 alerts; P90 not meaningful"), List.of("EMPLOYEE_SIGN_OFF_TIME_VIOLATION step change classified as a data-regime change"),
                Map.of("vendor", "COMPLETE", "site_shift_direction", "COMPLETE", "cost_billing", "COMPLETE"), "evidence-3f2a9c1b7d44",
                new RunView.Verification(true, new BigDecimal("0.84"), List.of("evidence items=5"), List.of(), 0), action, approval, approvalStatus, receipt,
                List.of(new RunView.Transition("INITIALIZE_RUN", null, "INITIALIZED", "INITIALIZED", T0, 1, "initialized", Map.of()),
                        new RunView.Transition("RUN_INVESTIGATIONS", "investigation.vendor.execute_analysis", "PLANNED", "PLANNED", T0, 40, "ok", Map.of("worker", "vendor")),
                        new RunView.Transition("APPROVAL_INTERRUPT", null, "BRIEFED", "AWAITING_APPROVAL", T0, 2, "paused", Map.of("approvalId", APPROVAL_ID.toString()))),
                new RunView.ModelUsageSummary(0, 4, 0, 0, 0, "none"), new RunView.Versions("workflow-v1", "prompts-v1", "metrics-v1.1", "data-8ed5b4eae158", "anomaly-rules-v1", "targets-v1"), List.of());
    }

    @Bean
    public DecisionRunGateway fakeGateway() {
        return new DecisionRunGateway() {
            @Override
            public RunView morningBrief(ActorContext actor, TenantContext tenant, LocalDate asOfDate, String persona) {
                GATEWAY_CALLS.incrementAndGet();
                if (tenant.businessUnit().equals("catalyst-Sac")) {
                    RunView g1 = g1("HEALTHY", null, null, "HEALTHY");
                    return new RunView(g1.runId(), g1.traceId(), "catalyst-Sac", persona, "SCHEDULED", "HEALTHY", T0, 400,
                            new DecisionBrief(g1.runId(), "catalyst-Sac", asOfDate, "catalyst-Sac: no material operational anomaly", m01(), List.of(), g1.recommendedAction(), g1.brief().evidence(), "HEALTHY"),
                            List.of(), List.of("catalyst-Sac: no material operational anomaly.", "No intervention is proposed."), List.of(), List.of(), List.of(), Map.of(), null, null, null, null, null, null,
                            List.of(), g1.modelUsage(), g1.versions(), List.of());
                }
                return g1("AWAITING_APPROVAL", "PENDING", null, "AWAITING_APPROVAL");
            }

            @Override
            public RunView ask(ActorContext actor, TenantContext tenant, LocalDate asOfDate, String persona, String question, UUID relatedRunId) {
                GATEWAY_CALLS.incrementAndGet();
                return g1("AWAITING_APPROVAL", "PENDING", null, "AWAITING_APPROVAL");
            }

            @Override
            public Optional<RunView> find(ActorContext actor, UUID runId) {
                return runId.equals(RUN_ID) && actor.businessUnit().equals("pinnacle-Slc") ? Optional.of(g1("AWAITING_APPROVAL", "PENDING", null, "AWAITING_APPROVAL")) : Optional.empty();
            }

            @Override
            public RunView decide(ActorContext actor, UUID approvalId, ApprovalDecisionType decision, String comment, ActionProposal editedProposal) {
                if (!approvalId.equals(APPROVAL_ID) || !actor.businessUnit().equals("pinnacle-Slc")) {
                    throw new ApprovalNotFoundException(approvalId);
                }
                DECISIONS.add(decision.name() + (editedProposal == null ? "" : ":" + editedProposal.scope().get("watchDays")));
                if (decision == ApprovalDecisionType.REJECT) {
                    return g1("AWAITING_APPROVAL", "REJECTED", null, "REJECTED");
                }
                ExecutionReceipt receipt = new ExecutionReceipt(ACTION_ID, RUN_ID, RUN_ID + ":" + ACTION_ID, ActionStatus.EXECUTED, T0.plusSeconds(60), T0.plusSeconds(61), "WATCH-7f3a", "Mock watchlist created");
                return g1("AWAITING_APPROVAL", decision == ApprovalDecisionType.EDIT ? "EDITED" : "APPROVED", receipt, "EXECUTED");
            }

            @Override
            public Optional<RunView> findByApproval(ActorContext actor, UUID approvalId) {
                return approvalId.equals(APPROVAL_ID) && actor.businessUnit().equals("pinnacle-Slc") ? Optional.of(g1("AWAITING_APPROVAL", "PENDING", null, "AWAITING_APPROVAL")) : Optional.empty();
            }
        };
    }

    @Bean
    public AccessAuthorizer fakeAuthorizer() {
        return (actor, tenant, permission) -> {
            if (!actor.businessUnit().equals(tenant.businessUnit())) {
                throw new SecurityException("cross tenant");
            }
            if (permission == Permission.VIEW_AUDIT && actor.roles().equals(Set.of("SCHEDULER"))) {
                throw new SecurityException("scheduler cannot view audit");
            }
        };
    }

    @Bean
    public AuditSink fakeAudit() {
        return new AuditSink() {
            @Override
            public AuditEvent append(AuditEvent event) {
                return event;
            }

            @Override
            public List<AuditEvent> findByRunId(UUID runId) {
                if (!runId.equals(RUN_ID)) {
                    return List.of();
                }
                return List.of(new AuditEvent(UUID.randomUUID(), RUN_ID, "pinnacle-Slc", "BRIEF_CREATED", Map.of("actor", "scheduler"), T0, "trace-" + RUN_ID),
                        new AuditEvent(UUID.randomUUID(), RUN_ID, "pinnacle-Slc", "ACTION_AWAITING_APPROVAL", Map.of("approvalId", APPROVAL_ID.toString()), T0, "trace-" + RUN_ID));
            }
        };
    }
}
