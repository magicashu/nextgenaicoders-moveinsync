package com.moveinsync.mobilitycopilot.metrics.adapter.duckdb;

import com.moveinsync.mobilitycopilot.metrics.domain.Dimension;
import com.moveinsync.mobilitycopilot.metrics.domain.MetricRequestException;
import com.moveinsync.mobilitycopilot.metrics.domain.MetricWindow;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Renders a reviewed SQL template into a prepared statement. Only fixed tokens are substituted:
 * {@code {{bu}}}, {@code {{start}}}, {@code {{end}}}, {@code {{filters}}}, {@code {{dimension}}}
 * and named variant selectors such as {@code {{delay_reason}}}. Column names come from the
 * {@link Dimension} allowlist; every value is bound as a parameter. No caller text reaches the SQL.
 */
public final class GovernedSqlTemplate {

    private static final Pattern TOKEN = Pattern.compile("\\{\\{([a-z_]+)}}");

    private GovernedSqlTemplate() {
    }

    public record Rendered(String sql, List<Object> parameters) {
    }

    public static Rendered render(
            String template,
            String businessUnit,
            MetricWindow window,
            Map<String, String> filters,
            Set<Dimension> allowedDimensions,
            Set<String> variantSelectors,
            Optional<Dimension> groupBy) {
        List<Dimension> filterDimensions = new ArrayList<>();
        List<String> filterValues = new ArrayList<>();
        for (var entry : filters.entrySet()) {
            if (variantSelectors.contains(entry.getKey())) {
                continue;
            }
            Dimension dimension = Dimension.fromKey(entry.getKey())
                    .orElseThrow(() -> new MetricRequestException("UNKNOWN_FILTER",
                            "Filter '" + entry.getKey() + "' is not an allowlisted dimension"));
            if (!allowedDimensions.contains(dimension)) {
                throw new MetricRequestException("INCOMPATIBLE_DIMENSION",
                        "Dimension " + dimension + " is not compatible with this metric");
            }
            filterDimensions.add(dimension);
            filterValues.add(entry.getValue());
        }
        StringBuilder sql = new StringBuilder();
        List<Object> parameters = new ArrayList<>();
        Matcher matcher = TOKEN.matcher(template);
        int last = 0;
        while (matcher.find()) {
            sql.append(template, last, matcher.start());
            String token = matcher.group(1);
            switch (token) {
                case "bu" -> { sql.append('?'); parameters.add(businessUnit); }
                case "start" -> { sql.append('?'); parameters.add(window.start()); }
                case "end" -> { sql.append('?'); parameters.add(window.end()); }
                case "dimension" -> sql.append(groupBy.map(Dimension::column).orElse("'ALL'"));
                case "filters" -> {
                    for (int i = 0; i < filterDimensions.size(); i++) {
                        sql.append(" AND ").append(filterDimensions.get(i).column()).append(" = ?");
                        parameters.add(filterValues.get(i));
                    }
                }
                default -> {
                    if (!variantSelectors.contains(token)) {
                        throw new IllegalStateException("Unknown SQL template token: " + token);
                    }
                    String value = filters.get(token);
                    if (value == null) {
                        throw new MetricRequestException("MISSING_SELECTOR",
                                "This metric requires the '" + token + "' selector");
                    }
                    sql.append('?');
                    parameters.add(value);
                }
            }
            last = matcher.end();
        }
        sql.append(template.substring(last));
        return new Rendered(sql.toString().replace(";", ""), parameters);
    }
}
