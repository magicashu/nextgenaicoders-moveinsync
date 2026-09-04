package com.moveinsync.mobilitycopilot.metrics.adapter.duckdb;

import com.moveinsync.mobilitycopilot.metrics.domain.Dimension;
import com.moveinsync.mobilitycopilot.metrics.domain.MetricRequestException;
import com.moveinsync.mobilitycopilot.metrics.domain.MetricWindow;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GovernedSqlTemplateTest {

    private static final MetricWindow WINDOW = new MetricWindow(LocalDate.parse("2026-06-01"), LocalDate.parse("2026-06-07"));
    private static final String TEMPLATE = "SELECT {{dimension}} AS member FROM trips WHERE business_unit = {{bu}} AND trip_date BETWEEN {{start}} AND {{end}} {{filters}} GROUP BY 1;";

    @Test
    void bindsTenantWindowAndAllowlistedFiltersAsParameters() {
        Map<String, String> filters = new LinkedHashMap<>();
        filters.put("site_id", "Clearwater Campus");
        filters.put("direction", "LOGIN");

        var rendered = GovernedSqlTemplate.render(TEMPLATE, "pinnacle-Slc", WINDOW, filters,
                Set.of(Dimension.values()), Set.of(), Optional.of(Dimension.VENDOR_ID));

        assertThat(rendered.sql()).isEqualTo(
                "SELECT vendor_id AS member FROM trips WHERE business_unit = ? AND trip_date BETWEEN ? AND ?  AND site_id = ? AND direction = ? GROUP BY 1");
        assertThat(rendered.parameters()).containsExactly("pinnacle-Slc", WINDOW.start(), WINDOW.end(), "Clearwater Campus", "LOGIN");
    }

    @Test
    void neverInterpolatesFilterValuesIntoSql() {
        var rendered = GovernedSqlTemplate.render(TEMPLATE, "pinnacle-Slc", WINDOW,
                Map.of("vendor_id", "x' OR 1=1 --"), Set.of(Dimension.values()), Set.of(), Optional.empty());

        assertThat(rendered.sql()).doesNotContain("OR 1=1");
        assertThat(rendered.parameters()).contains("x' OR 1=1 --");
    }

    @Test
    void rejectsUnknownAndIncompatibleDimensions() {
        assertThatThrownBy(() -> GovernedSqlTemplate.render(TEMPLATE, "pinnacle-Slc", WINDOW, Map.of("employee_id", "7"),
                Set.of(Dimension.values()), Set.of(), Optional.empty()))
                .isInstanceOf(MetricRequestException.class)
                .extracting("code").isEqualTo("UNKNOWN_FILTER");
        assertThatThrownBy(() -> GovernedSqlTemplate.render(TEMPLATE, "pinnacle-Slc", WINDOW, Map.of("vendor_id", "v"),
                Set.of(Dimension.SITE_ID), Set.of(), Optional.empty()))
                .isInstanceOf(MetricRequestException.class)
                .extracting("code").isEqualTo("INCOMPATIBLE_DIMENSION");
    }

    @Test
    void requiresVariantSelectorWhenTemplateUsesIt() {
        String template = "SELECT count(*) FILTER (WHERE delay_reason = {{delay_reason}}) FROM trips WHERE business_unit = {{bu}}";
        assertThatThrownBy(() -> GovernedSqlTemplate.render(template, "pinnacle-Slc", WINDOW, Map.of(),
                Set.of(), Set.of("delay_reason"), Optional.empty()))
                .isInstanceOf(MetricRequestException.class)
                .extracting("code").isEqualTo("MISSING_SELECTOR");
        var rendered = GovernedSqlTemplate.render(template, "pinnacle-Slc", WINDOW, Map.of("delay_reason", "DRIVER"),
                Set.of(), Set.of("delay_reason"), Optional.empty());
        assertThat(rendered.parameters()).containsExactly("DRIVER", "pinnacle-Slc");
    }
}
