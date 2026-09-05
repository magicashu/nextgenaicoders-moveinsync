package com.moveinsync.mobilitycopilot.ingestion.domain;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/** Discovered files under {@code MOBILITY_DATA_DIR} plus the derived data version. */
public record DatasetCatalog(
        Path directory,
        String dataVersion,
        List<FileProfile> files) {

    public DatasetCatalog {
        Objects.requireNonNull(directory, "directory is required");
        Objects.requireNonNull(dataVersion, "dataVersion is required");
        files = files == null ? List.of() : List.copyOf(files);
    }

    public FileProfile profile(DatasetFile file) {
        return files.stream().filter(p -> p.file() == file).findFirst().orElse(FileProfile.missing(file));
    }

    public boolean isPresent(DatasetFile file) {
        return profile(file).present();
    }

    public List<DatasetFile> missingFiles() {
        return java.util.Arrays.stream(DatasetFile.values()).filter(f -> !isPresent(f)).toList();
    }
}
