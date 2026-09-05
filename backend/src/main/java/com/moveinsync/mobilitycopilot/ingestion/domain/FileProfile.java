package com.moveinsync.mobilitycopilot.ingestion.domain;

import java.util.List;
import java.util.Objects;

/** Discovery and validation result for one logical dataset file. */
public record FileProfile(
        DatasetFile file,
        List<String> paths,
        List<String> checksums,
        boolean present,
        long rawRows,
        long normalizedRows,
        List<String> missingColumns,
        List<String> unexpectedColumns) {

    public FileProfile {
        Objects.requireNonNull(file, "file is required");
        paths = paths == null ? List.of() : List.copyOf(paths);
        checksums = checksums == null ? List.of() : List.copyOf(checksums);
        missingColumns = missingColumns == null ? List.of() : List.copyOf(missingColumns);
        unexpectedColumns = unexpectedColumns == null ? List.of() : List.copyOf(unexpectedColumns);
    }

    public static FileProfile missing(DatasetFile file) {
        return new FileProfile(file, List.of(), List.of(), false, 0, 0, file.columns(), List.of());
    }
}
