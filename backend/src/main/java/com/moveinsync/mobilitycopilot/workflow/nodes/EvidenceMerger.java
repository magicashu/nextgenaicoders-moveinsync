package com.moveinsync.mobilitycopilot.workflow.nodes;

import com.moveinsync.mobilitycopilot.evidence.domain.Claim;
import com.moveinsync.mobilitycopilot.evidence.domain.EvidenceBundle;
import com.moveinsync.mobilitycopilot.evidence.domain.EvidenceItem;
import com.moveinsync.mobilitycopilot.evidence.domain.EvidencePackage;
import com.moveinsync.mobilitycopilot.metrics.domain.MetricResult;
import com.moveinsync.mobilitycopilot.metrics.domain.MetricStatus;
import com.moveinsync.mobilitycopilot.workflow.application.ports.AnalyticsGateway;
import com.moveinsync.mobilitycopilot.workflow.application.ports.DetectionSnapshot;
import com.moveinsync.mobilitycopilot.workflow.application.ports.WorkerEvidenceDto;
import com.moveinsync.mobilitycopilot.workflow.domain.InvestigationResult;
import com.moveinsync.mobilitycopilot.workflow.domain.WorkflowRun;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Node 11: deduplicates typed evidence envelopes, links every claim to evidence ids and versions the
 * bundle. Large rows never enter the package; only metric-level and member-level items do.
 */
public final class EvidenceMerger {

    private static final int MAX_RANKING_ROWS = 40;

    private EvidenceMerger() {
    }

    public static EvidencePackage merge(WorkflowRun run, List<AnalyticsGateway.PeerValueDto> peers) {
        DetectionSnapshot.IssueCandidate issue = run.selectedIssue();
        Map<String, EvidenceItem> items = new LinkedHashMap<>();
        List<Claim> claims = new ArrayList<>();
        Set<String> caveats = new LinkedHashSet<>();
        Map<String, String> branchStatus = new LinkedHashMap<>();
        int claimCounter = 0;

        MetricResult headline = issue.metric();
        String headlineId = issue.anomalyId();
        items.put(headlineId, item(headlineId, headline, headline.filters()));
        String impactId = headlineId + ":impact";
        items.put(impactId, new EvidenceItem(impactId, headline.metricId().name(), BigDecimal.valueOf(issue.excessEvents()), "COUNT",
                null, null, BigDecimal.valueOf(issue.excessRiderLegs()), BigDecimal.valueOf(issue.affectedRiderLegs()), headline.supportingCount(),
                headline.periodStart(), headline.periodEnd(), Map.of("derivation", "excess events and rider legs"), "anomaly-rules", headline.contractVersion(), headline.dataVersion()));
        claims.add(new Claim("c" + (++claimCounter), headlineClaim(run, issue), Claim.Kind.DIRECT, List.of(headlineId), "detector"));
        if (issue.excessEvents() > 0) {
            claims.add(new Claim("c" + (++claimCounter), "About %s excess %s affected %s rider legs (about %s more than the baseline rate implies)."
                    .formatted(group(issue.excessEvents()), noun(issue), group(issue.affectedRiderLegs()), group(issue.excessRiderLegs())),
                    Claim.Kind.INFERRED, List.of(impactId), "detector"));
        }
        for (String reason : issue.reasons()) {
            if (reason.startsWith("Misses") || reason.startsWith("Meets")) {
                claims.add(new Claim("c" + (++claimCounter), reason + ".", Claim.Kind.CAVEAT, List.of(headlineId), "detector"));
                break;
            }
        }

        for (InvestigationResult result : run.investigations()) {
            branchStatus.put(result.worker(), result.status().name());
            if (!result.succeeded()) {
                caveats.add("Investigation branch '" + result.worker() + "' " + result.status().name().toLowerCase(Locale.ROOT)
                        + (result.failureReason() == null ? "" : ": " + result.failureReason()));
                continue;
            }
            List<String> workerIds = new ArrayList<>();
            for (WorkerEvidenceDto dto : result.evidence()) {
                for (MetricResult metric : dto.metrics()) {
                    if (metric.status() == MetricStatus.SUPPORTED) {
                        String id = metricId(run, metric);
                        items.putIfAbsent(id, item(id, metric, metric.filters()));
                        workerIds.add(id);
                    } else {
                        metric.caveats().forEach(caveats::add);
                    }
                }
                for (WorkerEvidenceDto.Ranking ranking : dto.rankings()) {
                    long totalNumerator = ranking.rows().stream().mapToLong(WorkerEvidenceDto.Ranking.Row::currentNumerator).sum();
                    List<WorkerEvidenceDto.Ranking.Row> qualified = ranking.qualifiedRows();
                    long deteriorated = qualified.stream().filter(r -> r.delta() != null && r.delta().signum() > 0).count();
                    items.putIfAbsent(ranking.evidenceId(), new EvidenceItem(ranking.evidenceId(), ranking.metricId().name(), BigDecimal.valueOf(qualified.size()), "COUNT",
                            null, null, BigDecimal.valueOf(deteriorated), BigDecimal.valueOf(ranking.rows().size()), totalNumerator,
                            run.state().asOfDate().minusDays(7), run.state().asOfDate().minusDays(1), Map.of("dimension", ranking.dimension(), "minimumVolume", String.valueOf(ranking.minimumVolume())),
                            ranking.source(), ranking.contractVersion(), ranking.dataVersion()));
                    workerIds.add(ranking.evidenceId());
                    int rows = 0;
                    for (WorkerEvidenceDto.Ranking.Row row : ranking.rows()) {
                        if (rows++ >= MAX_RANKING_ROWS) {
                            break;
                        }
                        if (row.currentValue() == null) {
                            continue;
                        }
                        String id = ranking.evidenceId() + ":" + slug(row.member());
                        items.putIfAbsent(id, new EvidenceItem(id, ranking.metricId().name(), row.currentValue(), "PERCENT", row.baselineValue(), row.delta(),
                                BigDecimal.valueOf(row.currentNumerator()), BigDecimal.valueOf(row.currentDenominator()), row.currentDenominator(),
                                run.state().asOfDate().minusDays(7), run.state().asOfDate().minusDays(1), Map.of(ranking.dimension(), row.member(), "qualified", String.valueOf(row.qualified())),
                                ranking.source(), ranking.contractVersion(), ranking.dataVersion()));
                        if (row.shareOfCurrentNumerator() != null) {
                            String shareId = id + ":share";
                            items.putIfAbsent(shareId, new EvidenceItem(shareId, ranking.metricId().name(), row.shareOfCurrentNumerator(), "PERCENT", null, null,
                                    BigDecimal.valueOf(row.currentNumerator()), BigDecimal.valueOf(totalNumerator), row.currentDenominator(),
                                    run.state().asOfDate().minusDays(7), run.state().asOfDate().minusDays(1), Map.of(ranking.dimension(), row.member(), "measure", "share of numerator"),
                                    ranking.source(), ranking.contractVersion(), ranking.dataVersion()));
                            workerIds.add(shareId);
                        }
                        workerIds.add(id);
                    }
                    ranking.caveats().forEach(caveats::add);
                }
                for (WorkerEvidenceDto.Distribution distribution : dto.distributions()) {
                    for (WorkerEvidenceDto.Distribution.Row row : distribution.rows()) {
                        if (row.share() == null) {
                            continue;
                        }
                        String id = distribution.evidenceId() + ":" + slug(row.category());
                        items.putIfAbsent(id, new EvidenceItem(id, distribution.metricId().name(), row.share(), "PERCENT", row.baselineShare(),
                                row.baselineShare() == null ? null : row.share().subtract(row.baselineShare()), BigDecimal.valueOf(row.count()),
                                BigDecimal.valueOf(distribution.currentTotal()), distribution.currentTotal(), run.state().asOfDate().minusDays(7),
                                run.state().asOfDate().minusDays(1), Map.of(distribution.category(), row.category()), distribution.source(),
                                distribution.contractVersion(), distribution.dataVersion()));
                        workerIds.add(id);
                    }
                }
                dto.caveats().forEach(caveats::add);
            }
            for (String finding : result.directFindings()) {
                claims.add(new Claim("c" + (++claimCounter), finding, Claim.Kind.DIRECT, List.copyOf(new LinkedHashSet<>(workerIds)), result.worker()));
            }
            for (String inference : result.inferences()) {
                claims.add(new Claim("c" + (++claimCounter), inference, Claim.Kind.INFERRED, List.copyOf(new LinkedHashSet<>(workerIds)), result.worker()));
            }
        }
        List<String> gaps = new ArrayList<>();
        for (AnalyticsGateway.CapabilityGap gap : run.capabilities()) {
            if (gap.unsupported() || gap.derivable()) {
                String text = gap.analysis() + ": " + gap.reason();
                gaps.add(text);
                caveats.add(text);
            }
        }
        List<String> notes = new ArrayList<>();
        for (DetectionSnapshot.DataQualityNote note : run.detection().dataQualityNotes()) {
            notes.add(note.note());
            caveats.add("Data-quality note: " + note.note());
        }
        for (AnalyticsGateway.PeerValueDto peer : peers) {
            if (peer.value() == null) {
                continue;
            }
            String id = "peer:" + peer.businessUnit() + ":" + headline.metricId().name().toLowerCase(Locale.ROOT) + ":" + headline.periodEnd();
            items.putIfAbsent(id, new EvidenceItem(id, headline.metricId().name(), peer.value(), "PERCENT", null, null, BigDecimal.valueOf(peer.numerator()),
                    BigDecimal.valueOf(peer.denominator()), peer.denominator(), headline.periodStart(), headline.periodEnd(),
                    Map.of("business_unit", peer.businessUnit(), "audience", "facilities head"), "sql/contributions/cross_tenant_m01.sql", headline.contractVersion(), headline.dataVersion()));
        }
        if (!peers.isEmpty()) {
            StringBuilder text = new StringBuilder("Peer tenants in the same week: ");
            List<String> ids = new ArrayList<>();
            for (AnalyticsGateway.PeerValueDto peer : peers) {
                if (peer.value() != null && !peer.businessUnit().equals(run.state().tenant().businessUnit())) {
                    text.append(peer.businessUnit()).append(' ').append(peer.value().setScale(1, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()).append("%, ");
                    ids.add("peer:" + peer.businessUnit() + ":" + headline.metricId().name().toLowerCase(Locale.ROOT) + ":" + headline.periodEnd());
                }
            }
            if (!ids.isEmpty()) {
                claims.add(new Claim("c" + (++claimCounter), text.substring(0, text.length() - 2) + ".", Claim.Kind.DIRECT, ids, "peers"));
            }
        }
        for (String caveat : caveats) {
            claims.add(new Claim("c" + (++claimCounter), caveat, Claim.Kind.CAVEAT, List.of(headlineId), "system"));
        }
        double confidence = issue.confidence().doubleValue();
        EvidenceBundle bundle = new EvidenceBundle(new ArrayList<>(items.values()), confidence, headline.supportingCount(), new ArrayList<>(caveats));
        return new EvidencePackage(bundle, claims, gaps, notes, branchStatus, List.of(), EvidencePackage.version(bundle));
    }

    static String headlineClaim(WorkflowRun run, DetectionSnapshot.IssueCandidate issue) {
        MetricResult m = issue.metric();
        String unit = m.unit() == com.moveinsync.mobilitycopilot.metrics.domain.MetricUnit.PERCENT ? "%" : m.unit() == com.moveinsync.mobilitycopilot.metrics.domain.MetricUnit.PER_1000_TRIPS ? " per 1,000 trips" : "";
        String population = m.numerator() != null && m.denominator() != null
                ? " (%s of %s)".formatted(group(m.numerator().longValue()), group(m.denominator().longValue())) : "";
        return "%s reached %s%s%s in the week to %s, %s from %s%s in the prior four complete weeks (%s points)."
                .formatted(m.metricName(), plain(m.value()), unit, population, m.periodEnd(),
                        issue.deltaPoints() != null && issue.deltaPoints().signum() >= 0 ? "up" : "down", plain(m.baselineValue()), unit,
                        m.delta() == null ? "n/a" : (m.delta().signum() >= 0 ? "+" : "") + plain(m.delta()));
    }

    public static EvidenceItem item(String id, MetricResult metric, Map<String, String> filters) {
        return new EvidenceItem(id, metric.metricId().name(), metric.value(), metric.unit().name(), metric.baselineValue(), metric.delta(),
                metric.numerator(), metric.denominator(), metric.supportingCount(), metric.periodStart(), metric.periodEnd(), filters,
                metric.source(), metric.contractVersion(), metric.dataVersion());
    }

    static String metricId(WorkflowRun run, MetricResult metric) {
        String filters = metric.filters().isEmpty() ? "" : ":" + Integer.toHexString(new java.util.TreeMap<>(metric.filters()).hashCode());
        return "%s:%s:%s%s".formatted(run.state().tenant().businessUnit(), metric.metricId().name().toLowerCase(Locale.ROOT), metric.periodEnd(), filters);
    }

    private static String slug(String value) {
        return value == null ? "null" : value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-");
    }

    private static String plain(BigDecimal value) {
        return value == null ? "n/a" : value.setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }

    private static String group(long value) {
        return String.format(Locale.ROOT, "%,d", value);
    }

    private static String noun(DetectionSnapshot.IssueCandidate issue) {
        return switch (issue.metricId()) {
            case M01_DELAYED_TRIP_RATE -> "delayed trips";
            case M04_ON_TIME_PICKUP_RATE -> "late pickups";
            case M05_ON_TIME_DROP_RATE -> "late drops";
            case M06_NO_SHOW_RATE -> "no-shows";
            default -> "events";
        };
    }
}
