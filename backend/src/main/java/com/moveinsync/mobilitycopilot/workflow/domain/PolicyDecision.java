package com.moveinsync.mobilitycopilot.workflow.domain;

import java.util.List;
import java.util.Objects;

/** Deterministic action-policy gate result. */
public record PolicyDecision(Route route, List<String> reasons) {

    public enum Route { APPROVAL_REQUIRED, REPORT_ONLY, REJECTED }

    public PolicyDecision {
        Objects.requireNonNull(route);
        reasons = reasons == null ? List.of() : List.copyOf(reasons);
    }
}
