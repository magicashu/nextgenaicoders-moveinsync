package com.moveinsync.mobilitycopilot.observability;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** One nested span. Attributes are already redacted when they arrive here. */
public final class Span {

    public enum Kind { REQUEST, AUTHORIZATION, WORKFLOW_NODE, AGENT, TOOL, CRITIC, REPORT, APPROVAL, REVALIDATION, EXECUTION, AUDIT_LINK, MODEL }

    private final String spanId;
    private final String parentSpanId;
    private final String name;
    private final Kind kind;
    private final Instant startedAt;
    private Instant endedAt;
    private String status = "OK";
    private final Map<String, String> attributes = new LinkedHashMap<>();
    private final List<Span> children = new ArrayList<>();

    Span(String parentSpanId, String name, Kind kind, Map<String, String> attributes) {
        this.spanId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        this.parentSpanId = parentSpanId;
        this.name = name;
        this.kind = kind;
        this.startedAt = Instant.now();
        this.attributes.putAll(attributes);
    }

    public String spanId() { return spanId; }
    public String parentSpanId() { return parentSpanId; }
    public String name() { return name; }
    public Kind kind() { return kind; }
    public Instant startedAt() { return startedAt; }
    public Instant endedAt() { return endedAt; }
    public String status() { return status; }
    public Map<String, String> attributes() { return Collections.unmodifiableMap(attributes); }
    public List<Span> children() { return Collections.unmodifiableList(children); }

    void addChild(Span child) { children.add(child); }

    void end(String status, Map<String, String> extra) {
        this.endedAt = Instant.now();
        this.status = status;
        this.attributes.putAll(extra);
    }

    public long durationMs() {
        return endedAt == null ? 0 : java.time.Duration.between(startedAt, endedAt).toMillis();
    }

    public boolean ended() {
        return endedAt != null;
    }

    /** Depth-first flatten for export. */
    public List<Span> flatten() {
        List<Span> all = new ArrayList<>();
        all.add(this);
        for (Span child : children) {
            all.addAll(child.flatten());
        }
        return all;
    }
}
