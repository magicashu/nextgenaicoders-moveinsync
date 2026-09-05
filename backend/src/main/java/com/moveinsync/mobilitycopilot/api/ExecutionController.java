package com.moveinsync.mobilitycopilot.api;

import com.moveinsync.mobilitycopilot.api.security.TrustedHeaders;
import com.moveinsync.mobilitycopilot.observability.TraceRecorder;
import com.moveinsync.mobilitycopilot.observability.TraceExporter;
import com.moveinsync.mobilitycopilot.observability.export.OtlpJson;
import com.moveinsync.mobilitycopilot.reporting.application.DecisionRunGateway;
import com.moveinsync.mobilitycopilot.reporting.application.RunNotFoundException;
import com.moveinsync.mobilitycopilot.workflow.application.ResumableWorkflowEngine;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/** Tenant-scoped local trace inspection; business audit remains /api/v1/audit/{runId}. */
@RestController
@RequestMapping("/api/v1/workflows")
public class ExecutionController {
    private final RequestContext context;
    private final DecisionRunGateway gateway;
    private final TraceRecorder recorder;
    private final TraceExporter exporter;
    private final ResumableWorkflowEngine engine;

    public ExecutionController(RequestContext context, DecisionRunGateway gateway, TraceRecorder recorder,
                               TraceExporter exporter, ResumableWorkflowEngine engine) {
        this.context = context; this.gateway = gateway; this.recorder = recorder; this.exporter = exporter; this.engine = engine;
    }

    @GetMapping("/graph")
    public Map<String, String> graph() {
        return Map.of("engine", "langgraph4j", "version", "1.8.25", "mermaid", engine.graphDiagram());
    }

    @GetMapping("/{runId}/execution")
    public Map<String, Object> execution(@PathVariable UUID runId,
            @RequestHeader(name = TrustedHeaders.ACTOR, required = false) String actorId,
            @RequestHeader(name = TrustedHeaders.BUSINESS_UNIT) String businessUnit,
            @RequestHeader(name = TrustedHeaders.ROLES, required = false) String roles) {
        var actor = context.actor(actorId, businessUnit, roles);
        var run = gateway.find(actor, runId).orElseThrow(() -> new RunNotFoundException(runId));
        var trace = recorder.find(TraceRecorder.traceIdFor(runId));
        return Map.of("runId", runId, "traceId", TraceRecorder.traceIdFor(runId), "engine", "langgraph4j",
                "transitions", run.transitions(), "modelUsage", run.modelUsage(), "exporter", exporter.status(),
                "otlp", trace.<Object>map(value -> OtlpJson.request(value, "mobility-decision-copilot", "local")).orElse(Map.of()));
    }
}
