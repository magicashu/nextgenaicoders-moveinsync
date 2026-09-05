package com.moveinsync.mobilitycopilot.metrics.domain;

import java.util.Arrays;
import java.util.Optional;

/** Allowlisted analytical dimensions (D-031). Any other grouping or filter key is rejected. */
public enum Dimension {
    VENDOR_ID("vendor_id"),
    SITE_ID("site_id"),
    SHIFT_ID("shift_id"),
    DIRECTION("direction"),
    MODE("mode"),
    FUEL_TYPE("fuel_type"),
    VEHICLE_ID("vehicle_id");

    private final String column;

    Dimension(String column) {
        this.column = column;
    }

    /** Column name present on every normalised table; used only through prepared parameters. */
    public String column() {
        return column;
    }

    public static Optional<Dimension> fromKey(String key) {
        return Arrays.stream(values()).filter(d -> d.column.equalsIgnoreCase(key) || d.name().equalsIgnoreCase(key)).findFirst();
    }
}
