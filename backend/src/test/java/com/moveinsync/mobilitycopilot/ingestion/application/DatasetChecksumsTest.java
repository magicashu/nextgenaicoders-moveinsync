package com.moveinsync.mobilitycopilot.ingestion.application;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DatasetChecksumsTest {

    @Test
    void computesSha256AndStableDataVersion(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("a.csv");
        Files.writeString(file, "business_unit,trip_id\n");
        String digest = DatasetChecksums.sha256(file);
        assertThat(digest).hasSize(64);
        assertThat(DatasetChecksums.dataVersion(List.of(digest))).startsWith("data-").hasSize(17);
        assertThat(DatasetChecksums.dataVersion(List.of(digest))).isEqualTo(DatasetChecksums.dataVersion(List.of(digest)));
        assertThat(DatasetChecksums.matchesOfficial("alerts_data.csv", digest)).isFalse();
        assertThat(DatasetChecksums.OFFICIAL).hasSize(7);
    }
}
