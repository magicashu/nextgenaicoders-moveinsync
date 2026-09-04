package com.moveinsync.mobilitycopilot.metrics.domain;

import com.moveinsync.mobilitycopilot.ingestion.domain.DatasetFile;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Governed metric contract entry (metrics-v1.1). The formula lives in the reviewed SQL resource;
 * this record owns definition, allowed filters, exclusions, minimum volume and required files.
 */
public record MetricDefinition(
        MetricId id,
        String name,
        MetricUnit unit,
        MetricGrain grain,
        String numerator,
        String denominator,
        Set<Dimension> allowedDimensions,
        Set<String> variantSelectors,
        Set<DatasetFile> requiredFiles,
        String sqlResource,
        int minimumVolume,
        List<String> exclusions,
        boolean lowerIsBetter,
        boolean rate) {

    public MetricDefinition {
        Objects.requireNonNull(id);
        Objects.requireNonNull(name);
        Objects.requireNonNull(unit);
        Objects.requireNonNull(grain);
        Objects.requireNonNull(sqlResource);
        allowedDimensions = allowedDimensions == null ? Set.of() : Set.copyOf(allowedDimensions);
        variantSelectors = variantSelectors == null ? Set.of() : Set.copyOf(variantSelectors);
        requiredFiles = requiredFiles == null ? Set.of() : Set.copyOf(requiredFiles);
        exclusions = exclusions == null ? List.of() : List.copyOf(exclusions);
    }

    public boolean allows(Dimension dimension) {
        return allowedDimensions.contains(dimension);
    }
}
