package com.moveinsync.mobilitycopilot.workflow.application.ports;

/** Provider failure categories only: never carries response bodies, prompts or credentials. */
public final class ModelCallException extends RuntimeException {
    public enum Reason { HTTP_AUTH, HTTP_RATE_LIMIT, HTTP_ERROR, TIMEOUT, INTERRUPTED, TRANSPORT,
        INVALID_RESPONSE, INCOMPLETE_RESPONSE, REQUEST_TOO_LARGE, RESPONSE_TOO_LARGE, CAPACITY }

    private final Reason reason;

    public ModelCallException(Reason reason) {
        super(reason.name());
        this.reason = reason;
    }

    public Reason reason() { return reason; }
}
