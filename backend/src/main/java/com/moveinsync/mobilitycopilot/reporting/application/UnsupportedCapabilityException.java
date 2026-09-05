package com.moveinsync.mobilitycopilot.reporting.application;

import java.util.List;

/** The requested analysis is unsupported for this tenant/data version; the reasons are typed, not invented. */
public class UnsupportedCapabilityException extends RuntimeException {

    private final List<String> reasons;

    public UnsupportedCapabilityException(String message, List<String> reasons) {
        super(message);
        this.reasons = reasons == null ? List.of() : List.copyOf(reasons);
    }

    public List<String> reasons() {
        return reasons;
    }
}
