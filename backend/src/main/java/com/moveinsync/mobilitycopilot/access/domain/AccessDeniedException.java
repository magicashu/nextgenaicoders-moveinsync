package com.moveinsync.mobilitycopilot.access.domain;

/** Fail-closed authorization failure. The message never reveals other tenants' data. */
public class AccessDeniedException extends SecurityException {

    private final String code;

    public AccessDeniedException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
