package com.moveinsync.mobilitycopilot.api.error;

import com.moveinsync.mobilitycopilot.reporting.application.ApprovalNotFoundException;
import com.moveinsync.mobilitycopilot.reporting.application.RunNotFoundException;
import com.moveinsync.mobilitycopilot.reporting.application.UnsupportedCapabilityException;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Maps failures to the stable envelope. Messages never include other tenants' data or stack traces. */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler({SecurityException.class})
    public ResponseEntity<ApiError> forbidden(SecurityException e) {
        return respond(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "The authenticated identity is not authorized for this tenant or action", List.of());
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, ConstraintViolationException.class, MissingRequestHeaderException.class,
            MethodArgumentTypeMismatchException.class, HttpMessageNotReadableException.class, IllegalArgumentException.class})
    public ResponseEntity<ApiError> badRequest(Exception e) {
        String detail = e instanceof MethodArgumentNotValidException m
                ? String.join("; ", m.getBindingResult().getFieldErrors().stream().map(f -> f.getField() + ": " + f.getDefaultMessage()).toList())
                : sanitize(e.getMessage());
        return respond(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "The request is invalid", List.of(detail));
    }

    @ExceptionHandler({RunNotFoundException.class, ApprovalNotFoundException.class})
    public ResponseEntity<ApiError> notFound(RuntimeException e) {
        return respond(HttpStatus.NOT_FOUND, "NOT_FOUND", sanitize(e.getMessage()), List.of());
    }

    @ExceptionHandler(UnsupportedCapabilityException.class)
    public ResponseEntity<ApiError> unsupported(UnsupportedCapabilityException e) {
        return respond(HttpStatus.UNPROCESSABLE_ENTITY, "UNSUPPORTED_CAPABILITY", sanitize(e.getMessage()), e.reasons());
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiError> conflict(IllegalStateException e) {
        String message = sanitize(e.getMessage());
        if (message.contains("Unable to load dataset") || message.contains("Dataset directory") || message.contains("Governed query failed")) {
            log.warn("Dependency failure: {}", message);
            return respond(HttpStatus.SERVICE_UNAVAILABLE, "DEPENDENCY_UNAVAILABLE", "A required dependency is unavailable; no number was fabricated", List.of(message));
        }
        return respond(HttpStatus.CONFLICT, "INVALID_TRANSITION", message, List.of());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> unexpected(Exception e) {
        log.error("Unhandled API failure", e);
        return respond(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Unexpected failure; the request was not completed and no action was executed", List.of());
    }

    private static ResponseEntity<ApiError> respond(HttpStatus status, String code, String message, List<String> details) {
        return ResponseEntity.status(status).body(new ApiError(code, message, UUID.randomUUID().toString(), Instant.now(), details));
    }

    private static String sanitize(String message) {
        return message == null ? "" : message.replaceAll("\\s+", " ").trim();
    }
}
