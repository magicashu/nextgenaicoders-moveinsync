package com.moveinsync.mobilitycopilot.workflow.agents;

import com.moveinsync.mobilitycopilot.metrics.domain.MetricId;
import com.moveinsync.mobilitycopilot.workflow.investigation.workers.WorkerType;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Maps explicit question vocabulary to registered metric contracts. It does not calculate facts,
 * authorize scope, generate SQL, or infer a metric from an ambiguous question.
 */
@Service
public final class SupervisorQuestionRouter {
    private static final Map<MetricId, Set<String>> TERMS = Map.ofEntries(
            Map.entry(MetricId.M01_DELAYED_TRIP_RATE, Set.of("delay", "delayed", "late", "ota", "on time")),
            Map.entry(MetricId.M02_DELAYED_TRIP_DELAY, Set.of("delay minutes", "delay duration", "how long late")),
            Map.entry(MetricId.M03_DELAY_REASON_MIX, Set.of("delay reason", "delay cause", "why late")),
            Map.entry(MetricId.M04_ON_TIME_PICKUP_RATE, Set.of("pickup", "pick up")),
            Map.entry(MetricId.M05_ON_TIME_DROP_RATE, Set.of("drop", "dropoff", "drop off")),
            Map.entry(MetricId.M06_NO_SHOW_RATE, Set.of("no show", "no-show", "absent")),
            Map.entry(MetricId.M07_DASHBOARD_CANCELLATION_RATE, Set.of("cancel", "cancellation")),
            Map.entry(MetricId.M08_OCCUPANCY, Set.of("occupancy", "capacity", "full vehicle")),
            Map.entry(MetricId.M09_MEDIAN_BILLED_COST_PER_TRIP, Set.of("cost", "bill", "billed")),
            Map.entry(MetricId.M10_COST_PER_BILLED_KM, Set.of("cost per km", "cost per kilometer", "billed km")),
            Map.entry(MetricId.M11_LOW_DRIVER_RATING_RATE, Set.of("low rating", "feedback", "rating")),
            Map.entry(MetricId.M12_MEAN_DRIVER_SAFETY_RATING, Set.of("safety rating", "driver safety")),
            Map.entry(MetricId.M13_ALERT_RATE, Set.of("alert", "alerts")),
            Map.entry(MetricId.M14_SEVERE_ALERT_RATE, Set.of("severe alert", "severity")),
            Map.entry(MetricId.M15_SEVERE_ACKNOWLEDGEMENT_P90, Set.of("acknowledgement", "acknowledgment", "ack time")),
            Map.entry(MetricId.M16_TRACKING_GAP_RATE, Set.of("tracking gap", "tracking")),
            Map.entry(MetricId.M17_EV_SHARE, Set.of("ev share", "electric vehicle", "electric")),
            Map.entry(MetricId.M18_ESCORT_PRESENT_RATE, Set.of("escort", "escorted")));

    public SupervisorQueryRoute route(String question) {
        if (question == null || question.isBlank()) {
            return new SupervisorQueryRoute(SupervisorQueryRoute.Status.CLARIFICATION_REQUIRED, null, question,
                    List.of(), "Question is required.");
        }

        String normalized = question.toLowerCase(Locale.ROOT).trim();
        int longestMatch = TERMS.values().stream()
                .flatMap(Set::stream)
                .filter(normalized::contains)
                .mapToInt(String::length)
                .max()
                .orElse(0);
        List<MetricId> matches = TERMS.entrySet().stream()
                .filter(entry -> entry.getValue().stream()
                        .anyMatch(term -> term.length() == longestMatch && normalized.contains(term)))
                .map(Map.Entry::getKey)
                .distinct()
                .toList();

        if (matches.size() > 1) {
            return new SupervisorQueryRoute(SupervisorQueryRoute.Status.CLARIFICATION_REQUIRED, null, question,
                    List.of(), "Question matches multiple governed metrics; specify one metric.");
        }
        if (matches.isEmpty()) {
            return new SupervisorQueryRoute(SupervisorQueryRoute.Status.UNSUPPORTED, null, question,
                    List.of(), "No governed metric matches this question.");
        }

        MetricId metric = matches.getFirst();
        return new SupervisorQueryRoute(SupervisorQueryRoute.Status.SUPPORTED, metric, question,
                workersFor(metric), "Question mapped to " + metric.contractId() + ".");
    }

    private static List<WorkerType> workersFor(MetricId metric) {
        List<WorkerType> workers = new ArrayList<>();
        switch (metric) {
            case M01_DELAYED_TRIP_RATE, M02_DELAYED_TRIP_DELAY, M03_DELAY_REASON_MIX -> {
                workers.add(WorkerType.SITE_SHIFT_DIRECTION);
                workers.add(WorkerType.VENDOR);
                workers.add(WorkerType.DELAY_REASON);
            }
            case M04_ON_TIME_PICKUP_RATE, M05_ON_TIME_DROP_RATE -> {
                workers.add(WorkerType.SITE_SHIFT_DIRECTION);
                workers.add(WorkerType.VENDOR);
                workers.add(WorkerType.NO_SHOW_ROSTER);
            }
            case M06_NO_SHOW_RATE -> workers.add(WorkerType.NO_SHOW_ROSTER);
            case M07_DASHBOARD_CANCELLATION_RATE -> workers.add(WorkerType.SITE_SHIFT_DIRECTION);
            case M08_OCCUPANCY, M17_EV_SHARE, M18_ESCORT_PRESENT_RATE ->
                    workers.add(WorkerType.SITE_SHIFT_DIRECTION);
            case M09_MEDIAN_BILLED_COST_PER_TRIP, M10_COST_PER_BILLED_KM ->
                    workers.add(WorkerType.COST_BILLING);
            case M11_LOW_DRIVER_RATING_RATE, M12_MEAN_DRIVER_SAFETY_RATING ->
                    workers.add(WorkerType.FEEDBACK);
            case M13_ALERT_RATE, M14_SEVERE_ALERT_RATE, M15_SEVERE_ACKNOWLEDGEMENT_P90,
                    M16_TRACKING_GAP_RATE -> workers.add(WorkerType.TRACKING_SAFETY);
        }
        return workers;
    }
}