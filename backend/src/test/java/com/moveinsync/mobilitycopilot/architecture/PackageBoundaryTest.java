package com.moveinsync.mobilitycopilot.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Package-boundary rules from docs/project-structure.md section 4, enforced by scanning import
 * statements (no ArchUnit dependency is available on this branch; the Integration Owner may swap in
 * ArchUnit with identical rules).
 */
class PackageBoundaryTest {

    private static final String ROOT = "com.moveinsync.mobilitycopilot.";
    /** package prefix -> forbidden import fragments */
    private static final Map<String, List<String>> RULES = Map.of(
            "api", List.of("java.sql", "org.duckdb", "metrics.adapter", "ingestion.adapter", "approval.adapter.postgres", "audit.adapter.postgres", "action.adapter.postgres", "workflow.adapter", "workflow.agents", "workflow.nodes"),
            "reporting", List.of("java.sql", "org.duckdb", "metrics.adapter", "ingestion.adapter", "workflow.agents", "workflow.nodes", "workflow.adapter.statemachine"),
            "conversation", List.of("java.sql", "org.duckdb", "metrics.adapter", "workflow.agents", "workflow.nodes"),
            "workflow", List.of("java.sql", "org.duckdb", "metrics.adapter", "ingestion.adapter", "approval.adapter.postgres", "audit.adapter.postgres", "action.adapter.postgres", "org.bsc.langgraph4j"),
            "evidence", List.of("java.sql", "org.duckdb", "metrics.adapter", "ingestion.adapter"),
            "metrics", List.of("workflow", "reporting", "api", "approval", "action", "audit"),
            "ingestion", List.of("workflow", "reporting", "api", "approval", "action", "audit"),
            "anomaly", List.of("workflow", "reporting", "api", "approval", "action", "audit"),
            "observability", List.of("java.sql", "org.duckdb", "workflow.agents", "metrics.adapter"),
            "access", List.of("java.sql", "org.duckdb", "workflow", "metrics", "reporting"));
    private static final List<String> SQL_ALLOWED_PREFIXES = List.of("metrics/adapter", "ingestion/adapter", "ingestion/application", "approval/adapter/postgres",
            "audit/adapter/postgres", "action/adapter/postgres", "anomaly/application");

    private static Path sourceRoot() {
        for (String candidate : new String[] {"backend/src/main/java/com/moveinsync/mobilitycopilot", "src/main/java/com/moveinsync/mobilitycopilot"}) {
            if (Files.isDirectory(Path.of(candidate))) {
                return Path.of(candidate);
            }
        }
        throw new IllegalStateException("source root not found");
    }

    @Test
    void capabilityPackagesRespectTheDependencyRules() throws IOException {
        Path root = sourceRoot();
        List<String> violations = new ArrayList<>();
        try (Stream<Path> files = Files.walk(root)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                String relative = root.relativize(file).toString().replace('\\', '/');
                String capability = relative.split("/")[0];
                List<String> forbidden = RULES.getOrDefault(capability, List.of());
                for (String line : Files.readAllLines(file)) {
                    if (!line.startsWith("import ")) {
                        continue;
                    }
                    String imported = line.substring(7).replace(";", "").trim();
                    for (String fragment : forbidden) {
                        boolean projectFragment = !fragment.contains(".") || fragment.startsWith("metrics") || fragment.startsWith("ingestion") || fragment.startsWith("workflow")
                                || fragment.startsWith("approval") || fragment.startsWith("audit") || fragment.startsWith("action");
                        String needle = projectFragment && !fragment.startsWith("java") && !fragment.startsWith("org.") ? ROOT + fragment : fragment;
                        if (imported.startsWith(needle) || imported.contains("." + fragment + ".") && !fragment.contains(".")) {
                            if (isAllowedException(relative, imported)) {
                                continue;
                            }
                            violations.add(relative + " imports " + imported);
                        }
                    }
                    if (imported.startsWith("java.sql") && SQL_ALLOWED_PREFIXES.stream().noneMatch(relative::startsWith)) {
                        violations.add(relative + " uses java.sql outside an adapter package");
                    }
                }
            }
        }
        assertThat(violations).as("boundary violations").isEmpty();
    }

    @Test
    void noModelGeneratedSqlAndNoGenericDumpingGrounds() throws IOException {
        Path root = sourceRoot();
        List<String> violations = new ArrayList<>();
        try (Stream<Path> files = Files.walk(root)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                String relative = root.relativize(file).toString();
                if (relative.startsWith("utils/") || relative.startsWith("models/") || relative.startsWith("rag/") || relative.startsWith("vector/")) {
                    violations.add("forbidden package: " + relative);
                }
                String source = Files.readString(file);
                if (relative.startsWith("workflow/") && (source.contains("createStatement()") || source.contains("prepareStatement(") || source.contains("executeQuery("))) {
                    violations.add("workflow executes SQL: " + relative);
                }
                if (relative.startsWith("api/") && (source.contains("SELECT ") || source.contains("select "))) {
                    violations.add("controller contains SQL text: " + relative);
                }
            }
        }
        assertThat(violations).isEmpty();
    }

    private static boolean isAllowedException(String relative, String imported) {
        // the metric domain records are frozen shared types used everywhere; adapters may implement ports from other capabilities
        return imported.startsWith(ROOT + "metrics.domain") || imported.startsWith(ROOT + "access.domain") || imported.startsWith(ROOT + "action.domain")
                || imported.startsWith(ROOT + "approval.domain") || imported.startsWith(ROOT + "audit.domain") || imported.startsWith(ROOT + "evidence.domain")
                || imported.startsWith(ROOT + "reporting.domain") || imported.startsWith(ROOT + "workflow.domain") || imported.startsWith(ROOT + "workflow.application")
                || imported.startsWith(ROOT + "metrics.application") || imported.startsWith(ROOT + "anomaly.application") || imported.startsWith(ROOT + "anomaly.domain")
                || imported.startsWith(ROOT + "approval.application") || imported.startsWith(ROOT + "audit.application") || imported.startsWith(ROOT + "action.application")
                || imported.startsWith(ROOT + "access.application") || imported.startsWith(ROOT + "config")
                || (relative.startsWith("approval/adapter") && imported.startsWith(ROOT + "workflow"))
                || (relative.startsWith("reporting/adapter") && imported.startsWith(ROOT + "workflow"));
    }
}
