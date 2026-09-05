package com.moveinsync.mobilitycopilot.access.domain;

import java.util.Objects;

public record TenantContext(String businessUnit) {

    public TenantContext {
        Objects.requireNonNull(businessUnit, "businessUnit is required");
        if (businessUnit.isBlank()) {
            throw new IllegalArgumentException("businessUnit must not be blank");
        }
    }
}
