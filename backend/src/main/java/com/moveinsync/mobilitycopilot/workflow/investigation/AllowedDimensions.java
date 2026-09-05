package com.moveinsync.mobilitycopilot.workflow.investigation;

import java.util.Optional;
import java.util.Set;

/**
 * Plan-level filter allowlist (D-031). Mirrors the analytics {@code Dimension} enum so the workflow
 * can validate model output without depending on the analytics package; the composition root maps
 * these keys straight through to the governed metric layer.
 */
public final class AllowedDimensions {

    public static final Set<String> KEYS = Set.of("vendor_id", "site_id", "shift_id", "direction", "mode", "fuel_type", "vehicle_id");

    private AllowedDimensions() {
    }

    public static Optional<String> normalise(String key) {
        if (key == null) {
            return Optional.empty();
        }
        String lower = key.trim().toLowerCase(java.util.Locale.ROOT);
        return KEYS.contains(lower) ? Optional.of(lower) : Optional.empty();
    }
}
