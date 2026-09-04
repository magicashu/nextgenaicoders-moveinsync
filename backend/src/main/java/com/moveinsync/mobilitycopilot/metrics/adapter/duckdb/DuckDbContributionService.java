package com.moveinsync.mobilitycopilot.metrics.adapter.duckdb;

import com.moveinsync.mobilitycopilot.access.domain.TenantContext;
import com.moveinsync.mobilitycopilot.ingestion.application.AnalyticsStore;
import com.moveinsync.mobilitycopilot.ingestion.application.SqlResources;
import com.moveinsync.mobilitycopilot.metrics.application.ContributionService;
import com.moveinsync.mobilitycopilot.metrics.domain.ContributionRanking;
import com.moveinsync.mobilitycopilot.metrics.domain.ContributionRow;
import com.moveinsync.mobilitycopilot.metrics.domain.Dimension;
import com.moveinsync.mobilitycopilot.metrics.domain.Distribution;
import com.moveinsync.mobilitycopilot.metrics.domain.DistributionRow;
import com.moveinsync.mobilitycopilot.metrics.domain.MetricDefinition;
import com.moveinsync.mobilitycopilot.metrics.domain.MetricId;
import com.moveinsync.mobilitycopilot.metrics.domain.MetricRegistry;
import com.moveinsync.mobilitycopilot.metrics.domain.MetricRequestException;
import com.moveinsync.mobilitycopilot.metrics.domain.MetricWindow;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
public class DuckDbContributionService implements ContributionService {

    private final AnalyticsStore store;

    public DuckDbContributionService(AnalyticsStore store) {
        this.store = store;
    }

    @Override
    public ContributionRanking rankContributors(TenantContext tenant, MetricId metricId, Dimension dimension,
                                                MetricWindow current, MetricWindow baseline, Map<String, String> filters) {
        MetricDefinition definition = MetricRegistry.definition(metricId);
        if (!definition.allows(dimension)) {
            throw new MetricRequestException("INCOMPATIBLE_DIMENSION", dimension + " is not allowed for " + metricId);
        }
        String source = MetricRegistry.sqlResource(metricId, filters);
        Map<String, Member> currentRows = grouped(definition, tenant, current, filters, source, dimension);
        Map<String, Member> baselineRows = grouped(definition, tenant, baseline, filters, source, dimension);
        int minVolume = MetricRegistry.minimumVolume(metricId, dimension);
        long totalNumerator = currentRows.values().stream().mapToLong(m -> m.numerator() == null ? 0 : m.numerator()).sum();
        List<ContributionRow> rows = new ArrayList<>();
        for (var entry : currentRows.entrySet()) {
            Member cur = entry.getValue();
            Member base = baselineRows.get(entry.getKey());
            boolean qualified = cur.supportingCount() >= minVolume && base != null && base.supportingCount() >= minVolume && cur.value() != null && base.value() != null;
            BigDecimal delta = cur.value() != null && base != null && base.value() != null ? cur.value().subtract(base.value()) : null;
            BigDecimal share = totalNumerator == 0 || cur.numerator() == null ? null
                    : BigDecimal.valueOf(cur.numerator() * 100.0 / totalNumerator).setScale(1, RoundingMode.HALF_UP);
            rows.add(new ContributionRow(entry.getKey(), cur.value(), base == null ? null : base.value(), delta,
                    cur.numerator() == null ? 0 : cur.numerator(), cur.denominator() == null ? 0 : cur.denominator(),
                    base == null || base.numerator() == null ? 0 : base.numerator(), base == null || base.denominator() == null ? 0 : base.denominator(),
                    share, qualified));
        }
        rows.sort(Comparator.comparing((ContributionRow r) -> r.currentNumerator()).reversed());
        List<String> caveats = new ArrayList<>();
        long unqualified = rows.stream().filter(r -> !r.qualified()).count();
        if (unqualified > 0) {
            caveats.add(unqualified + " " + dimension.name().toLowerCase(Locale.ROOT) + " members below the minimum volume of "
                    + minVolume + " in both windows are shown as qualified context only");
        }
        String evidenceId = evidenceId(tenant, metricId, dimension.name().toLowerCase(Locale.ROOT), current, filters);
        return new ContributionRanking(evidenceId, tenant.businessUnit(), metricId, dimension, current, baseline, filters,
                minVolume, rows, MetricRegistry.CONTRACT_VERSION, store.catalog().dataVersion(), source, caveats);
    }

    @Override
    public Distribution delayReasonMix(TenantContext tenant, MetricWindow current, MetricWindow baseline, Map<String, String> filters) {
        return distribution(tenant, MetricId.M03_DELAY_REASON_MIX, "delay_reason", "sql/contributions/delay_reason_mix.sql", current, baseline, filters);
    }

    @Override
    public Distribution alertTypeMix(TenantContext tenant, MetricWindow current, MetricWindow baseline, Map<String, String> filters) {
        return distribution(tenant, MetricId.M13_ALERT_RATE, "event_type", "sql/contributions/alert_type_mix.sql", current, baseline, filters);
    }

    @Override
    public ImpactCounts delayedRiderLegs(TenantContext tenant, MetricWindow window, Map<String, String> filters) {
        var rendered = GovernedSqlTemplate.render(SqlResources.read("sql/contributions/delayed_rider_legs.sql"), tenant.businessUnit(), window,
                filters, Set.of(Dimension.values()), Set.of(), Optional.empty());
        return DuckDbQueries.query(store, rendered, rs -> new ImpactCounts(rs.getLong("trips"), rs.getLong("delayed_trips"),
                rs.getLong("delayed_rider_legs"), rs.getLong("rider_legs"))).getFirst();
    }

    @Override
    public List<PeerValue> crossTenantDelayedTripRate(MetricWindow window) {
        var rendered = GovernedSqlTemplate.render(SqlResources.read("sql/contributions/cross_tenant_m01.sql"), "*", window,
                Map.of(), Set.of(), Set.of(), Optional.empty());
        return DuckDbQueries.query(store, rendered, rs -> new PeerValue(rs.getString("member"), rs.getLong("numerator"),
                rs.getLong("denominator"), DuckDbQueries.nullableDecimal(rs, "value", 2)));
    }

    private Distribution distribution(TenantContext tenant, MetricId metricId, String category, String source,
                                      MetricWindow current, MetricWindow baseline, Map<String, String> filters) {
        Map<String, Long> cur = counts(tenant, source, current, filters);
        Map<String, Long> base = counts(tenant, source, baseline, filters);
        long curTotal = cur.values().stream().mapToLong(Long::longValue).sum();
        long baseTotal = base.values().stream().mapToLong(Long::longValue).sum();
        List<DistributionRow> rows = new ArrayList<>();
        java.util.LinkedHashSet<String> keys = new java.util.LinkedHashSet<>(cur.keySet());
        keys.addAll(base.keySet());
        for (String key : keys) {
            long c = cur.getOrDefault(key, 0L);
            long b = base.getOrDefault(key, 0L);
            rows.add(new DistributionRow(key, c, share(c, curTotal), b, share(b, baseTotal)));
        }
        rows.sort(Comparator.comparing(DistributionRow::count).reversed());
        return new Distribution(evidenceId(tenant, metricId, category, current, filters), tenant.businessUnit(), metricId, category,
                current, baseline, filters, curTotal, baseTotal, rows, MetricRegistry.CONTRACT_VERSION, store.catalog().dataVersion(), source,
                curTotal == 0 ? List.of("No events in the current window") : List.of());
    }

    private Map<String, Long> counts(TenantContext tenant, String source, MetricWindow window, Map<String, String> filters) {
        var rendered = GovernedSqlTemplate.render(SqlResources.read(source), tenant.businessUnit(), window, filters,
                Set.of(Dimension.values()), Set.of(), Optional.empty());
        Map<String, Long> result = new LinkedHashMap<>();
        DuckDbQueries.query(store, rendered, rs -> result.put(String.valueOf(rs.getString("category")), rs.getLong("count")));
        return result;
    }

    private Map<String, Member> grouped(MetricDefinition definition, TenantContext tenant, MetricWindow window,
                                        Map<String, String> filters, String source, Dimension dimension) {
        var rendered = GovernedSqlTemplate.render(SqlResources.read(source), tenant.businessUnit(), window, filters,
                definition.allowedDimensions(), definition.variantSelectors(), Optional.of(dimension));
        Map<String, Member> result = new LinkedHashMap<>();
        DuckDbQueries.query(store, rendered, rs -> {
            String member = rs.getString("member");
            return result.put(member == null ? "(unknown)" : member, new Member(
                    DuckDbQueries.nullableLong(rs, "numerator"),
                    DuckDbQueries.nullableLong(rs, "denominator"),
                    DuckDbQueries.nullableDecimal(rs, "value", 2),
                    rs.getLong("supporting_count")));
        });
        return result;
    }

    private static BigDecimal share(long count, long total) {
        return total == 0 ? null : BigDecimal.valueOf(count * 100.0 / total).setScale(1, RoundingMode.HALF_UP);
    }

    static String evidenceId(TenantContext tenant, MetricId metricId, String qualifier, MetricWindow window, Map<String, String> filters) {
        String filterPart = filters.isEmpty() ? "" : ":" + Integer.toHexString(new java.util.TreeMap<>(filters).hashCode());
        return "%s:%s:%s:%s%s".formatted(tenant.businessUnit(), metricId.name().toLowerCase(Locale.ROOT), qualifier, window.end(), filterPart);
    }

    record Member(Long numerator, Long denominator, BigDecimal value, long supportingCount) {
    }
}
