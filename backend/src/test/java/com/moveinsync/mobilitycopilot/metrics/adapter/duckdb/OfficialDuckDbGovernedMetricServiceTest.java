package com.moveinsync.mobilitycopilot.metrics.adapter.duckdb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.moveinsync.mobilitycopilot.access.domain.TenantContext;
import com.moveinsync.mobilitycopilot.config.MobilityDataProperties;
import com.moveinsync.mobilitycopilot.evidence.domain.MetricEvidence;
import com.moveinsync.mobilitycopilot.ingestion.application.DatasetFileCatalog;
import com.moveinsync.mobilitycopilot.ingestion.application.OfficialDatasetProfileService;
import com.moveinsync.mobilitycopilot.metrics.domain.MetricId;
import com.moveinsync.mobilitycopilot.metrics.domain.MetricRequest;
import com.moveinsync.mobilitycopilot.metrics.domain.MetricStatus;
import com.moveinsync.mobilitycopilot.metrics.domain.MetricWindow;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class OfficialDuckDbGovernedMetricServiceTest {
    @TempDir Path sourceDirectory;

    @Test
    void computes_m01_from_normalized_trip_sources_with_tenant_and_dimension_scope() throws Exception {
        writeDataset();
        OfficialDatasetProfileService profiles = new OfficialDatasetProfileService(new DatasetFileCatalog());
        String dataVersion = profiles.profile(sourceDirectory).dataVersion();
        OfficialDuckDbGovernedMetricService service = new OfficialDuckDbGovernedMetricService(
                new MobilityDataProperties(sourceDirectory.toString()), profiles);

        MetricEvidence result = service.compute(new MetricRequest(new TenantContext("pinnacle-Slc"),
                MetricId.M01_DELAYED_TRIP_RATE, MetricRequest.Measure.VALUE,
                new MetricWindow(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 7)), Map.of(), dataVersion));
        MetricEvidence vendorResult = service.compute(new MetricRequest(new TenantContext("pinnacle-Slc"),
                MetricId.M01_DELAYED_TRIP_RATE, MetricRequest.Measure.VALUE,
                new MetricWindow(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 7)), Map.of("vendor_id", "Vendor A"), dataVersion));

        assertThat(result.status()).isEqualTo(MetricStatus.AVAILABLE);
        assertThat(result.value()).isEqualByComparingTo(new BigDecimal("66.67"));
        assertThat(result.numerator()).isEqualByComparingTo("2");
        assertThat(result.denominator()).isEqualByComparingTo("3");
        assertThat(vendorResult.value()).isEqualByComparingTo(new BigDecimal("50.00"));
    }

    @Test
    void rejects_lfs_pointers_instead_of_treating_them_as_csv_data() throws Exception {
        Files.writeString(sourceDirectory.resolve("Ride_data _trip-may_2026.csv"),
                "version https://git-lfs.github.com/spec/v1\noid sha256:abc\nsize 100\n");

        assertThatThrownBy(() -> new OfficialDatasetProfileService(new DatasetFileCatalog()).profile(sourceDirectory))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("Git LFS pointer");
    }

    private void writeDataset() throws Exception {
        String header = "business_unit,trip_id,trip_date,delay_minutes,vendor_id,office,shift_type,trip_direction,product_type\n";
        Files.writeString(sourceDirectory.resolve("Ride_data _trip-may_2026.csv"), header +
                "pinnacle-Slc,1,\"June 1, 2026\",10,Vendor A,Site One,09:00,LOGIN,CAB\n");
        Files.writeString(sourceDirectory.resolve("Ride_data _trip-June_2026.csv"), header +
                "pinnacle-Slc,2,\"June 2, 2026\",0,Vendor A,Site One,09:00,LOGIN,CAB\n");
        Files.writeString(sourceDirectory.resolve("Ride_data _trip-July_2026.csv"), header +
                "pinnacle-Slc,3,\"June 3, 2026\",5,Vendor B,Site One,09:00,LOGIN,CAB\n");
        for (String name : new DatasetFileCatalog().requiredFiles().subList(3, 7)) {
            Files.writeString(sourceDirectory.resolve(name), "header\n");
        }
    }
}
