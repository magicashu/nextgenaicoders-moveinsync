package com.moveinsync.mobilitycopilot.workflow.investigation.workers;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/** The seven allowlisted investigation workers (D-030). There is no GPS worker and no knowledge worker. */
public enum WorkerType {
    VENDOR("vendor", "vendor_peer", "Did every qualified vendor move together, or is one vendor driving the change?"),
    SITE_SHIFT_DIRECTION("site_shift_direction", "site_shift_direction", "Where is the change concentrated by site, shift and direction?"),
    DELAY_REASON("delay_reason", "delay", "Which governed delay reasons changed among delayed trips?"),
    COST_BILLING("cost_billing", "cost_per_trip", "Did billed cost per trip move with the operational change?"),
    FEEDBACK("feedback", "feedback", "Do rider ratings confirm or contradict the operational signal, and at what coverage?"),
    TRACKING_SAFETY("tracking_safety_alerts", "safety_alerts", "Do tracking and safety alerts show a related change?"),
    NO_SHOW_ROSTER("noshow_roster", "no_show_roster", "Do leg-level punctuality and no-shows confirm the trip-level trend?");

    private final String id;
    private final String capabilityAnalysis;
    private final String question;

    WorkerType(String id, String capabilityAnalysis, String question) {
        this.id = id;
        this.capabilityAnalysis = capabilityAnalysis;
        this.question = question;
    }

    public String id() {
        return id;
    }

    /** Capability-matrix analysis key whose UNSUPPORTED status disables this worker. */
    public String capabilityAnalysis() {
        return capabilityAnalysis;
    }

    public String question() {
        return question;
    }

    public static Optional<WorkerType> fromId(String id) {
        return Arrays.stream(values()).filter(w -> w.id.equals(id)).findFirst();
    }

    public static List<String> allowlist() {
        return Arrays.stream(values()).map(WorkerType::id).toList();
    }
}
