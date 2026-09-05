package com.moveinsync.mobilitycopilot.workflow.support;

import com.moveinsync.mobilitycopilot.access.domain.TenantContext;
import com.moveinsync.mobilitycopilot.metrics.domain.MetricId;
import com.moveinsync.mobilitycopilot.metrics.domain.MetricQuery;
import com.moveinsync.mobilitycopilot.metrics.domain.MetricResult;
import com.moveinsync.mobilitycopilot.metrics.domain.MetricStatus;
import com.moveinsync.mobilitycopilot.metrics.domain.MetricUnit;
import com.moveinsync.mobilitycopilot.workflow.application.ports.AnalyticsGateway;
import com.moveinsync.mobilitycopilot.workflow.application.ports.DetectionSnapshot;
import com.moveinsync.mobilitycopilot.workflow.application.ports.WorkerEvidenceDto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Fake analytics gateway that replays the hand-reconciled G1 numbers from the dataset profile
 * (pinnacle-Slc as of 2026-06-08) plus a healthy tenant. Worker behaviour can be overridden to fail.
 */
public class G1Fixtures implements AnalyticsGateway {

    public static final String DATA_VERSION = "data-8ed5b4eae158";
    public static final LocalDate AS_OF = LocalDate.parse("2026-06-08");
    public static final LocalDate END = LocalDate.parse("2026-06-07");
    public static final LocalDate START = LocalDate.parse("2026-06-01");

    public final AtomicInteger workerCalls = new AtomicInteger();
    public final List<String> workersCalled = new java.util.concurrent.CopyOnWriteArrayList<>();
    public volatile String failingWorker;
    public volatile boolean slowWorkers;
    public volatile String dataVersion = DATA_VERSION;
    public volatile BigDecimal headlineValue = new BigDecimal("21.88");

    public static MetricResult metric(MetricId id, String name, MetricUnit unit, String value, String baseline, String numerator, String denominator, long count, Map<String, String> filters, String source) {
        BigDecimal v = value == null ? null : new BigDecimal(value);
        BigDecimal b = baseline == null ? null : new BigDecimal(baseline);
        return new MetricResult(id, name, unit, MetricStatus.SUPPORTED, v, b, v != null && b != null ? v.subtract(b) : null,
                numerator == null ? null : new BigDecimal(numerator), denominator == null ? null : new BigDecimal(denominator), count,
                START, END, filters, "metrics-v1.1", DATA_VERSION, source, List.of());
    }

    public MetricResult headline() {
        return new MetricResult(MetricId.M01_DELAYED_TRIP_RATE, "Delayed-trip rate", MetricUnit.PERCENT, MetricStatus.SUPPORTED, headlineValue,
                new BigDecimal("12.28"), headlineValue.subtract(new BigDecimal("12.28")), new BigDecimal("4357"), new BigDecimal("19913"), 19913,
                START, END, Map.of(), "metrics-v1.1", dataVersion, "sql/metrics/m01_delayed_trip_rate.sql", List.of());
    }

    @Override
    public DetectionSnapshot detect(TenantContext tenant, LocalDate asOfDate) {
        if (tenant.businessUnit().equals("catalyst-Sac")) {
            MetricResult healthy = metric(MetricId.M01_DELAYED_TRIP_RATE, "Delayed-trip rate", MetricUnit.PERCENT, "4.33", "3.92", "210", "4849", 4849, Map.of(), "sql/metrics/m01_delayed_trip_rate.sql");
            return new DetectionSnapshot("catalyst-Sac", asOfDate, dataVersion, "anomaly-rules-v1", List.of(new DetectionSnapshot.IssueCandidate(
                    "catalyst-Sac:m01_delayed_trip_rate:2026-06-07", MetricId.M01_DELAYED_TRIP_RATE, healthy, "HEALTHY", "HEALTHY", new BigDecimal("0.41"),
                    new BigDecimal("0.1046"), new BigDecimal("10.00"), true, 0, 0, 0, new BigDecimal("0.9"), BigDecimal.ZERO,
                    List.of("Change of 0.41 points (10% relative) is within the materiality rule", "Meets configured target, editable per tenant of 10.00"))), List.of());
        }
        DetectionSnapshot.IssueCandidate m01 = new DetectionSnapshot.IssueCandidate("pinnacle-Slc:m01_delayed_trip_rate:2026-06-07", MetricId.M01_DELAYED_TRIP_RATE,
                headline(), "OPERATIONAL_ANOMALY", "HIGH", new BigDecimal("9.60"), new BigDecimal("0.7818"), new BigDecimal("10.00"), false, 1912, 7780, 3414,
                new BigDecimal("0.84"), new BigDecimal("39.5"), List.of("Adverse change of 9.60 points (78% relative) exceeds 3.00 points and 25%", "Misses configured target, editable per tenant of 10.00"));
        MetricResult m06 = metric(MetricId.M06_NO_SHOW_RATE, "No-show rate", MetricUnit.PERCENT, "0.83", "0.80", "336", "40467", 40467, Map.of(), "sql/metrics/m06_no_show_rate.sql");
        DetectionSnapshot.IssueCandidate noShow = new DetectionSnapshot.IssueCandidate("pinnacle-Slc:m06_no_show_rate:2026-06-07", MetricId.M06_NO_SHOW_RATE, m06,
                "HEALTHY", "HEALTHY", new BigDecimal("0.03"), new BigDecimal("0.0375"), new BigDecimal("10.00"), true, 0, 0, 0, new BigDecimal("0.9"), BigDecimal.ZERO, List.of("within rule"));
        return new DetectionSnapshot("pinnacle-Slc", asOfDate, dataVersion, "anomaly-rules-v1", List.of(m01, noShow), List.of(
                new DetectionSnapshot.DataQualityNote("pinnacle-Slc:regime:employee_sign_off_time_violation:2026-05-18", "EMPLOYEE_SIGN_OFF_TIME_VIOLATION",
                        "EMPLOYEE_SIGN_OFF_TIME_VIOLATION alerts fell from about 3788 per week to 0 in the week of 2026-05-18 while other alert types stayed stable. Classified as a data-regime change (alert configuration), not an operational issue.")));
    }

    @Override
    public WorkerEvidenceDto runWorker(String worker, TenantContext tenant, WindowDto current, WindowDto baseline, Map<String, String> filters) {
        workerCalls.incrementAndGet();
        workersCalled.add(worker);
        if (slowWorkers) {
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        if (worker.equals(failingWorker)) {
            throw new IllegalStateException("simulated tool failure in " + worker);
        }
        String bu = tenant.businessUnit();
        return switch (worker) {
            case "vendor" -> new WorkerEvidenceDto(worker, bu, List.of(), List.of(vendorRanking()), List.of(),
                    List.of("Every vendor with at least 500 trips in both windows rose (14 vendors, range 17.2% to 28.4%); the change is not attributable to a single vendor."),
                    List.of("3 vendor_id members below the minimum volume of 500 in both windows are shown as qualified context only"), true);
            case "site_shift_direction" -> new WorkerEvidenceDto(worker, bu, List.of(), List.of(siteRanking(), shiftRanking(), directionRanking()), List.of(),
                    List.of("Site 'Clearwater Campus' carries 51.1% of delayed trips at 24.07% (baseline 12.86%).",
                            "Shift '10:30' carries 9.8% of delayed trips at 47.38% (baseline 22.11%).",
                            "Direction 'LOGIN' carries 56.1% of delayed trips at 23.91% (baseline 10.98%)."), List.of(), true);
            case "delay_reason" -> new WorkerEvidenceDto(worker, bu, List.of(), List.of(), List.of(new WorkerEvidenceDto.Distribution(
                    "pinnacle-Slc:m03_delay_reason_mix:delay_reason:2026-06-07", MetricId.M03_DELAY_REASON_MIX, "delay_reason", 4357, 9186, List.of(
                    new WorkerEvidenceDto.Distribution.Row("EMPLOYEE", 2084, new BigDecimal("47.8"), 5195, new BigDecimal("56.6")),
                    new WorkerEvidenceDto.Distribution.Row("DRIVER", 1711, new BigDecimal("39.3"), 3065, new BigDecimal("33.4")),
                    new WorkerEvidenceDto.Distribution.Row("TRAFFIC", 562, new BigDecimal("12.9"), 926, new BigDecimal("10.1"))),
                    "sql/contributions/delay_reason_mix.sql", "metrics-v1.1", DATA_VERSION)),
                    List.of("Delay reason DRIVER rose to 39.3% of delayed trips from 33.4%."), List.of(), true);
            case "cost_billing" -> new WorkerEvidenceDto(worker, bu, List.of(
                    metric(MetricId.M09_MEDIAN_COST_PER_TRIP, "Median billed cost per trip", MetricUnit.CURRENCY, "1020.00", "1144.74", null, "88308", 88308, Map.of(), "sql/metrics/m09_median_cost_per_trip.sql"),
                    metric(MetricId.M10_COST_PER_BILLED_KM, "Cost per billed km", MetricUnit.CURRENCY_PER_KM, "94.67", "95.10", "91462685.68", "966158.08", 75573, Map.of(), "sql/metrics/m10_cost_per_billed_km.sql")),
                    List.of(), List.of(), List.of("Median billed cost per trip did not rise (1020.00 versus 1144.74); no cost penalty is visible."), List.of(), true);
            case "feedback" -> new WorkerEvidenceDto(worker, bu, List.of(
                    metric(MetricId.M11_LOW_DRIVER_RATING_RATE, "Low driver-rating rate", MetricUnit.PERCENT, "0.58", "0.37", "147", "25475", 25475, Map.of(), "sql/metrics/m11_low_driver_rating_rate.sql")),
                    List.of(), List.of(), List.of("Low driver-rating rate is flat at 0.58% (baseline 0.37%)."), List.of("Feedback coverage 93.5%"), true);
            case "tracking_safety_alerts" -> new WorkerEvidenceDto(worker, bu, List.of(
                    metric(MetricId.M13_ALERT_RATE, "Alert rate", MetricUnit.PER_1000_TRIPS, "43.74", "35.70", "871", "19913", 19913, Map.of(), "sql/metrics/m13_alert_rate.sql")),
                    List.of(), List.of(), List.of(), List.of("Unsupported: Fewer than 20 Sev-1/2 alerts; P90 not meaningful"), true);
            case "noshow_roster" -> new WorkerEvidenceDto(worker, bu, List.of(
                    metric(MetricId.M04_ON_TIME_PICKUP_RATE, "On-time pickup rate", MetricUnit.PERCENT, "74.23", "78.71", "25729", "34661", 34661, Map.of(), "sql/metrics/m04_on_time_pickup_rate.sql")),
                    List.of(), List.of(), List.of("Leg-level on-time pickups fell from 78.71% to 74.23%, confirming the trip-level trend."), List.of(), true);
            default -> throw new IllegalArgumentException("Unknown worker " + worker);
        };
    }

    public static WorkerEvidenceDto.Ranking vendorRanking() {
        List<WorkerEvidenceDto.Ranking.Row> rows = new ArrayList<>();
        String[][] data = {
                {"Rohan Mikhailov Travel", "22.17", "12.72", "641", "2891"}, {"Aarav Mikhailov Travel", "20.78", "13.02", "449", "2161"},
                {"Karan Mikhailov Travel", "22.37", "14.11", "436", "1949"}, {"Sanjay Mikhailov Travel", "22.68", "12.10", "378", "1667"},
                {"Pooja Mikhailov Travel", "28.37", "15.67", "354", "1248"}, {"Amit Mikhailov Travel", "22.06", "11.84", "283", "1283"},
                {"Rahul Morozov Travel", "19.51", "9.09", "277", "1420"}, {"Divya Mikhailov Travel", "21.83", "12.47", "270", "1237"},
                {"Arjun Mikhailov Travel", "23.22", "11.73", "267", "1150"}, {"Rahul Mikhailov Travel", "17.15", "9.89", "249", "1452"},
                {"Rahul Orlov Travel", "24.95", "12.66", "248", "994"}, {"Amit Volkov Travel", "22.33", "11.22", "199", "891"},
                {"Divya Sokolov Travel", "17.57", "9.06", "146", "831"}, {"Divya Kozlov Travel", "19.82", "8.60", "130", "656"}};
        for (String[] d : data) {
            BigDecimal cur = new BigDecimal(d[1]);
            BigDecimal base = new BigDecimal(d[2]);
            rows.add(new WorkerEvidenceDto.Ranking.Row(d[0], cur, base, cur.subtract(base), Long.parseLong(d[3]), Long.parseLong(d[4]),
                    new BigDecimal(d[3]).multiply(BigDecimal.valueOf(100)).divide(new BigDecimal("4357"), 1, java.math.RoundingMode.HALF_UP), true));
        }
        rows.add(new WorkerEvidenceDto.Ranking.Row("Pooja Sokolov Travel", new BigDecimal("92.86"), new BigDecimal("73.29"), new BigDecimal("19.57"), 13, 14, new BigDecimal("0.3"), false));
        return new WorkerEvidenceDto.Ranking("pinnacle-Slc:m01_delayed_trip_rate:vendor_id:2026-06-07", MetricId.M01_DELAYED_TRIP_RATE, "vendor_id", 500, rows, true,
                "sql/metrics/m01_delayed_trip_rate.sql", "metrics-v1.1", DATA_VERSION, List.of());
    }

    public static WorkerEvidenceDto.Ranking siteRanking() {
        return new WorkerEvidenceDto.Ranking("pinnacle-Slc:m01_delayed_trip_rate:site_id:2026-06-07", MetricId.M01_DELAYED_TRIP_RATE, "site_id", 300, List.of(
                new WorkerEvidenceDto.Ranking.Row("Clearwater Campus", new BigDecimal("24.07"), new BigDecimal("12.86"), new BigDecimal("11.21"), 2226, 9247, new BigDecimal("51.1"), true),
                new WorkerEvidenceDto.Ranking.Row("Willow Bend Campus", new BigDecimal("19.33"), new BigDecimal("9.64"), new BigDecimal("9.69"), 1075, 5560, new BigDecimal("24.7"), true),
                new WorkerEvidenceDto.Ranking.Row("Oakmont Office", new BigDecimal("20.58"), new BigDecimal("13.27"), new BigDecimal("7.31"), 1010, 4907, new BigDecimal("23.2"), true)),
                false, "sql/metrics/m01_delayed_trip_rate.sql", "metrics-v1.1", DATA_VERSION, List.of());
    }

    public static WorkerEvidenceDto.Ranking shiftRanking() {
        return new WorkerEvidenceDto.Ranking("pinnacle-Slc:m01_delayed_trip_rate:shift_id:2026-06-07", MetricId.M01_DELAYED_TRIP_RATE, "shift_id", 300, List.of(
                new WorkerEvidenceDto.Ranking.Row("09:00", new BigDecimal("29.52"), new BigDecimal("13.90"), new BigDecimal("15.62"), 639, 2165, new BigDecimal("14.7"), true),
                new WorkerEvidenceDto.Ranking.Row("09:30", new BigDecimal("37.61"), new BigDecimal("17.02"), new BigDecimal("20.59"), 545, 1449, new BigDecimal("12.5"), true),
                new WorkerEvidenceDto.Ranking.Row("10:30", new BigDecimal("47.38"), new BigDecimal("22.11"), new BigDecimal("25.27"), 425, 897, new BigDecimal("9.8"), true)),
                false, "sql/metrics/m01_delayed_trip_rate.sql", "metrics-v1.1", DATA_VERSION, List.of());
    }

    public static WorkerEvidenceDto.Ranking directionRanking() {
        return new WorkerEvidenceDto.Ranking("pinnacle-Slc:m01_delayed_trip_rate:direction:2026-06-07", MetricId.M01_DELAYED_TRIP_RATE, "direction", 300, List.of(
                new WorkerEvidenceDto.Ranking.Row("LOGIN", new BigDecimal("23.91"), new BigDecimal("10.98"), new BigDecimal("12.93"), 2444, 10223, new BigDecimal("56.1"), true),
                new WorkerEvidenceDto.Ranking.Row("LOGOUT", new BigDecimal("19.74"), new BigDecimal("13.62"), new BigDecimal("6.12"), 1913, 9690, new BigDecimal("43.9"), true)),
                true, "sql/metrics/m01_delayed_trip_rate.sql", "metrics-v1.1", DATA_VERSION, List.of());
    }

    @Override
    public MetricResult metric(MetricQuery query) {
        return headline();
    }

    @Override
    public List<CapabilityGap> capabilities(TenantContext tenant) {
        return List.of(
                new CapabilityGap("delay", "SUPPORTED", "Ride files present", List.of(MetricId.M01_DELAYED_TRIP_RATE)),
                new CapabilityGap("severe_ack", "UNSUPPORTED", "Fewer than 20 Sev-1/2 alerts; P90 not meaningful", List.of(MetricId.M15_SEVERE_ALERT_ACKNOWLEDGEMENT_P90)),
                new CapabilityGap("escort_present", "DERIVABLE", "Very few WOMAN_TRAVELLING_ALONE alerts (17)", List.of(MetricId.M18_ESCORT_PRESENT_RATE)),
                new CapabilityGap("gps_location", "UNSUPPORTED", "Dataset has no coordinates", List.of()));
    }

    @Override
    public List<PeerValueDto> crossTenantPeers(MetricId metricId, WindowDto window) {
        return List.of(new PeerValueDto("pinnacle-Slc", 4357, 19913, new BigDecimal("21.88")), new PeerValueDto("vanta-Sea", 2412, 13922, new BigDecimal("17.33")),
                new PeerValueDto("orbit-Slc", 284, 3720, new BigDecimal("7.63")), new PeerValueDto("catalyst-Sac", 210, 4849, new BigDecimal("4.33")),
                new PeerValueDto("vanta-Aus", 88, 4918, new BigDecimal("1.79")));
    }
}
