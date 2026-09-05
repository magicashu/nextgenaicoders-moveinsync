package com.moveinsync.mobilitycopilot.ingestion.adapter.duckdb;

import com.moveinsync.mobilitycopilot.ingestion.application.DatasetFileCatalog;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/** Test helper: shared loaded stores for the fixture and (when present) the official dataset. */
public final class FixtureStores {

    public static final String SEVEN_FILE_FIXTURE = "data/fixtures/seven-file-sample";
    public static final String LEGACY_FIXTURE = "data/fixtures";
    public static final String OFFICIAL = "outputs/official dataset/MoveInSync - Anonymised Trip-Log Dataset";

    private static DuckDbAnalyticsStore fixture;
    private static DuckDbAnalyticsStore official;

    private FixtureStores() {
    }

    public static synchronized DuckDbAnalyticsStore sevenFileFixture() {
        if (fixture == null) {
            fixture = new DuckDbAnalyticsStore(DatasetFileCatalog.resolveDirectory(SEVEN_FILE_FIXTURE), new DatasetFileCatalog());
        }
        return fixture;
    }

    public static Optional<Path> officialDirectory() {
        java.util.List<String> candidates = new java.util.ArrayList<>();
        String configured = System.getenv("MOBILITY_OFFICIAL_DATA_DIR");
        if (configured != null && !configured.isBlank()) {
            candidates.add(configured);
        }
        candidates.addAll(java.util.List.of(OFFICIAL, "../" + OFFICIAL, "../hackathon/" + OFFICIAL, "../../hackathon/" + OFFICIAL));
        for (String candidate : candidates) {
            Path path = Path.of(candidate);
            if (Files.isDirectory(path) && Files.exists(path.resolve("emp_Data.csv"))) {
                return Optional.of(path.toAbsolutePath().normalize());
            }
        }
        return Optional.empty();
    }

    public static synchronized DuckDbAnalyticsStore official() {
        if (official == null) {
            official = new DuckDbAnalyticsStore(officialDirectory().orElseThrow(), new DatasetFileCatalog());
        }
        return official;
    }

    public static DuckDbAnalyticsStore at(Path directory) {
        return new DuckDbAnalyticsStore(directory, new DatasetFileCatalog());
    }
}
