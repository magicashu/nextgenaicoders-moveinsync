package com.moveinsync.mobilitycopilot.metrics.adapter.duckdb;

import com.moveinsync.mobilitycopilot.access.domain.TenantContext;
import com.moveinsync.mobilitycopilot.ingestion.application.AnalyticsStore;
import com.moveinsync.mobilitycopilot.ingestion.application.SqlResources;
import com.moveinsync.mobilitycopilot.ingestion.domain.DatasetFile;
import com.moveinsync.mobilitycopilot.metrics.application.CapabilityMatrixService;
import com.moveinsync.mobilitycopilot.metrics.application.MetricService;
import com.moveinsync.mobilitycopilot.metrics.domain.CapabilityMatrix;
import com.moveinsync.mobilitycopilot.metrics.domain.CapabilityStatus;
import com.moveinsync.mobilitycopilot.metrics.domain.MetricDefinition;
import com.moveinsync.mobilitycopilot.metrics.domain.MetricQuery;
import com.moveinsync.mobilitycopilot.metrics.domain.MetricRegistry;
import com.moveinsync.mobilitycopilot.metrics.domain.MetricResult;
import com.moveinsync.mobilitycopilot.metrics.domain.MetricStatus;
import com.moveinsync.mobilitycopilot.metrics.domain.MetricWindow;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Executes the governed metric contracts against the loaded DuckDB database. Tenant and window are
 * mandatory prepared parameters, filters are allowlisted dimensions, and every result carries
 * numerator, denominator, supporting count, contract version, data version and caveats.
 */
@Service
public class DuckDbMetricService implements MetricService {

    private final AnalyticsStore store;
    private final CapabilityMatrixService capabilities;

    public DuckDbMetricService(AnalyticsStore store, CapabilityMatrixService capabilities) {
        this.store = store;
        this.capabilities = capabilities;
    }

    @Override
    public MetricResult query(MetricQuery query) {
        MetricDefinition definition = MetricRegistry.definition(query.metricId());
        TenantContext tenant = query.tenant();
        MetricWindow current = new MetricWindow(query.currentStart(), query.currentEnd());
        MetricWindow baseline = new MetricWindow(query.baselineStart(), query.baselineEnd());
        String dataVersion = store.catalog().dataVersion();
        String source = MetricRegistry.sqlResource(query.metricId(), query.dimensions());
        List<String> caveats = new ArrayList<>();

        for (DatasetFile file : definition.requiredFiles()) {
            if (!store.catalog().isPresent(file)) {
                return unsupported(definition, query, dataVersion, source,
                        "Required file " + file.glob() + " is not available in this data version");
            }
        }
        CapabilityMatrix matrix = capabilities.matrix(tenant);
        Optional<CapabilityStatus> capability = matrix.forMetric(query.metricId());
        if (capability.isPresent() && capability.get().support() == CapabilityStatus.Support.UNSUPPORTED) {
            return unsupported(definition, query, dataVersion, source, capability.get().reason());
        }
        capability.filter(c -> c.support() == CapabilityStatus.Support.DERIVABLE).ifPresent(c -> caveats.add(c.reason()));

        Aggregate currentValue = aggregate(definition, tenant, current, query.dimensions(), source);
        Aggregate baselineValue = aggregate(definition, tenant, baseline, query.dimensions(), source);
        if (currentValue == null || currentValue.value() == null) {
            return unsupported(definition, query, dataVersion, source,
                    "No eligible population for " + definition.name() + " in " + current.start() + " to " + current.end());
        }
        if (currentValue.supportingCount() < definition.minimumVolume()) {
            caveats.add("Below minimum volume: " + currentValue.supportingCount() + " < " + definition.minimumVolume());
        }
        if (baselineValue == null || baselineValue.value() == null) {
            caveats.add("No baseline population for the prior window " + baseline.start() + " to " + baseline.end());
        }
        BigDecimal delta = baselineValue == null || baselineValue.value() == null ? null
                : currentValue.value().subtract(baselineValue.value());
        definition.exclusions().stream().map(e -> "Excluded: " + e).forEach(caveats::add);
        return new MetricResult(
                definition.id(),
                definition.name(),
                definition.unit(),
                MetricStatus.SUPPORTED,
                currentValue.value(),
                baselineValue == null ? null : baselineValue.value(),
                delta,
                currentValue.numerator(),
                currentValue.denominator(),
                currentValue.supportingCount(),
                current.start(),
                current.end(),
                query.dimensions(),
                MetricRegistry.CONTRACT_VERSION,
                dataVersion,
                source,
                caveats);
    }

    Aggregate aggregate(MetricDefinition definition, TenantContext tenant, MetricWindow window,
                        Map<String, String> filters, String source) {
        var rendered = GovernedSqlTemplate.render(SqlResources.read(source), tenant.businessUnit(), window, filters,
                definition.allowedDimensions(), definition.variantSelectors(), Optional.empty());
        List<Aggregate> rows = DuckDbQueries.query(store, rendered, rs -> new Aggregate(
                DuckDbQueries.nullableDecimal(rs, "numerator", 2),
                DuckDbQueries.nullableDecimal(rs, "denominator", 2),
                DuckDbQueries.nullableDecimal(rs, "value", 2),
                rs.getLong("supporting_count")));
        return rows.isEmpty() ? null : rows.getFirst();
    }

    private MetricResult unsupported(MetricDefinition definition, MetricQuery query, String dataVersion, String source, String reason) {
        return new MetricResult(definition.id(), definition.name(), definition.unit(), MetricStatus.UNSUPPORTED,
                null, null, null, null, null, 0, query.currentStart(), query.currentEnd(), query.dimensions(),
                MetricRegistry.CONTRACT_VERSION, dataVersion, source, List.of("Unsupported: " + reason));
    }

    record Aggregate(BigDecimal numerator, BigDecimal denominator, BigDecimal value, long supportingCount) {
        static BigDecimal scale(double v) {
            return BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_UP);
        }
    }
}
