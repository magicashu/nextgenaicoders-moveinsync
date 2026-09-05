package com.moveinsync.mobilitycopilot.ingestion.application;

import com.moveinsync.mobilitycopilot.ingestion.domain.DatasetProfile;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/** Profiles immutable official inputs without repairing, copying, or loading them into mutable control storage. */
@Service
public final class OfficialDatasetProfileService implements DatasetProfileService {
    private static final String PARSER_VERSION = "official-csv-profile-v1";
    private final DatasetFileCatalog catalog;

    public OfficialDatasetProfileService(DatasetFileCatalog catalog) {
        this.catalog = catalog;
    }

    @Override
    public DatasetProfile profile(Path sourceDirectory) {
        if (!Files.isDirectory(sourceDirectory)) {
            throw new IllegalArgumentException("Official dataset directory does not exist: " + sourceDirectory);
        }
        List<DatasetProfile.FileProfile> files = new ArrayList<>();
        for (String name : catalog.requiredFiles()) {
            Path file = sourceDirectory.resolve(name);
            rejectLfsPointer(file);
            long physicalRows = countPhysicalRows(file);
            files.add(new DatasetProfile.FileProfile(name, sha256(file), physicalRows, physicalRows,
                    0, physicalRows, 0, 0, Map.of()));
        }
        String dataVersion = sha256(String.join("|", files.stream()
                .map(file -> file.sourceId() + ":" + file.sha256()).toList()));
        return new DatasetProfile(dataVersion, PARSER_VERSION, List.copyOf(files), List.of(
                "Profile counts physical rows only; canonicalization, deduplication, and join coverage are metric-stage responsibilities."));
    }

    private long countPhysicalRows(Path file) {
        try (var lines = Files.lines(file, StandardCharsets.UTF_8)) {
            return Math.max(0, lines.count() - 1); // Header is not a data row.
        } catch (IOException exception) {
            throw new UncheckedIOException("Cannot read official dataset file: " + file, exception);
        }
    }

    private void rejectLfsPointer(Path file) {
        if (!Files.isRegularFile(file)) {
            throw new IllegalArgumentException("Missing required official dataset file: " + file);
        }
        try {
            String firstLine;
            try (var reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                firstLine = reader.readLine();
            }
            if (firstLine != null && firstLine.startsWith("version https://git-lfs.github.com/spec/")) {
                throw new IllegalStateException("Git LFS pointer found for " + file + "; run git lfs pull before profiling.");
            }
        } catch (IOException exception) {
            throw new UncheckedIOException("Cannot inspect official dataset file: " + file, exception);
        }
    }

    private String sha256(Path file) {
        try (var input = Files.newInputStream(file)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            input.transferTo(new java.security.DigestOutputStream(java.io.OutputStream.nullOutputStream(), digest));
            return HexFormat.of().formatHex(digest.digest());
        } catch (IOException exception) {
            throw new UncheckedIOException("Cannot checksum official dataset file: " + file, exception);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
