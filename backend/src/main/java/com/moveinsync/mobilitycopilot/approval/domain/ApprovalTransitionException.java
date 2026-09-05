package com.moveinsync.mobilitycopilot.approval.domain;

/** Illegal approval transition (already decided, expired, wrong tenant, wrong action). */
public class ApprovalTransitionException extends IllegalStateException {

    private final String code;

    public ApprovalTransitionException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
