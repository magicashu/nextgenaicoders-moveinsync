package com.moveinsync.mobilitycopilot.workflow.domain;

import com.moveinsync.mobilitycopilot.metrics.domain.MetricRequest;
import java.time.Instant;

/** Trusted server identity must be supplied by the caller, never populated from model text. */
public final class RunGuards {
    private RunGuards() {}
    public static void requireAuthorized(RunContext c) {
        if(c==null||c.runId()==null||c.actor()==null||c.tenant()==null||c.actor().allowedTenants()==null
                ||!c.actor().allowedTenants().contains(c.tenant())||c.versions()==null||c.asOfDate()==null
                ||c.deadline()==null||c.budget()==null) throw new IllegalArgumentException("Invalid or unauthorized run context");
        if(c.budget().maxToolCalls()<1||c.budget().maxParallelTasks()<1||c.budget().maxDepth()<1
                ||c.budget().investigationTimeout()==null||c.budget().investigationTimeout().isNegative()
                ||c.budget().investigationTimeout().isZero()) throw new IllegalArgumentException("Invalid run budget");
    }
    public static void requireRequest(RunContext c,MetricRequest r) {
        requireAuthorized(c);
        if(r==null||!c.tenant().equals(r.tenant())||!c.versions().data().equals(r.dataVersion())||r.window()==null
                ||r.window().start()==null||r.window().end()==null||r.window().start().isAfter(r.window().end())
                ||r.window().end().isAfter(c.asOfDate())) throw new IllegalArgumentException("Request scope, date or version mismatch");
    }
    public static void requireTime(RunContext c) {
        if(Thread.currentThread().isInterrupted()||!Instant.now().isBefore(c.deadline())) throw new IllegalStateException("Run deadline expired");
    }
}
