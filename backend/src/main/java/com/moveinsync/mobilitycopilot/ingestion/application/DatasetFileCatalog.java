package com.moveinsync.mobilitycopilot.ingestion.application;

import com.moveinsync.mobilitycopilot.ingestion.domain.DatasetFile;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Discovers the organizer files by their fixed names without ever rewriting them. */
@Component
public final class DatasetFileCatalog {

    public List<String> requiredFiles() {
        return List.of(
                "Ride_data _trip-may_2026.csv",
                "Ride_data _trip-June_2026.csv",
                "Ride_data _trip-July_2026.csv",
                "emp_Data.csv",
                "bill_data.csv",
                "trip_feedback.csv",
                "alerts_data.csv");
    }

    /** Files matching each logical dataset file, sorted for deterministic loading. */
    public Map<DatasetFile, List<Path>> discover(Path directory) {
        if (!Files.isDirectory(directory)) {
            throw new IllegalStateException("Dataset directory does not exist: " + directory);
        }
        Map<DatasetFile, List<Path>> discovered = new TreeMap<>();
        for (DatasetFile file : DatasetFile.values()) {
            List<Path> matches = new ArrayList<>();
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory, file.glob())) {
                for (Path path : stream) {
                    if (Files.isRegularFile(path)) {
                        matches.add(path.toAbsolutePath().normalize());
                    }
                }
            } catch (IOException e) {
                throw new IllegalStateException("Unable to list " + directory, e);
            }
            matches.sort(java.util.Comparator.comparing(p -> p.getFileName().toString()));
            discovered.put(file, matches);
        }
        return discovered;
    }

    /** Resolves the configured directory from the repository root or the backend module. */
    public static Path resolveDirectory(String configured) {
        Path path = Path.of(configured);
        if (Files.isDirectory(path)) {
            return path.toAbsolutePath().normalize();
        }
        Path fromBackendModule = Path.of("..").resolve(path).normalize();
        if (Files.isDirectory(fromBackendModule)) {
            return fromBackendModule.toAbsolutePath().normalize();
        }
        throw new IllegalStateException("Dataset directory does not exist: " + configured);
    }
}
