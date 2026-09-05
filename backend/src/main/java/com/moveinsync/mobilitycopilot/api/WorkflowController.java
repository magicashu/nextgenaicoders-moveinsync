package com.moveinsync.mobilitycopilot.api;

import com.moveinsync.mobilitycopilot.access.domain.ActorContext;
import com.moveinsync.mobilitycopilot.api.dto.ApiDtos;
import com.moveinsync.mobilitycopilot.api.security.TrustedHeaders;
import com.moveinsync.mobilitycopilot.conversation.application.ContextualQuestionService;
import com.moveinsync.mobilitycopilot.reporting.application.BriefRenderer;
import com.moveinsync.mobilitycopilot.reporting.application.DashboardRunService;
import org.springframework.web.bind.annotation.RequestParam;
import com.moveinsync.mobilitycopilot.reporting.application.DecisionRunGateway;
import com.moveinsync.mobilitycopilot.reporting.application.RunNotFoundException;
import com.moveinsync.mobilitycopilot.reporting.application.RunView;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

/** POST /api/v1/workflows resolves a captured run (refresh=true explicitly replaces it); GET /api/v1/workflows/{id} reads it (tenant-scoped). */
@RestController
@RequestMapping("/api/v1/workflows")
public class WorkflowController {

    private final RequestContext context;
    private final DecisionRunGateway gateway;
    private final BriefRenderer renderer;
    private final DashboardRunService dashboard;
    private final ContextualQuestionService questions;

    public WorkflowController(RequestContext context, DecisionRunGateway gateway, BriefRenderer renderer, ContextualQuestionService questions, DashboardRunService dashboard) {
        this.dashboard = dashboard;
        this.context = context;
        this.gateway = gateway;
        this.renderer = renderer;
        this.questions = questions;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiDtos.MorningBriefResponse start(
            @RequestHeader(name = TrustedHeaders.ACTOR, required = false) String actorId,
            @RequestHeader(name = TrustedHeaders.BUSINESS_UNIT) String businessUnit,
            @RequestHeader(name = TrustedHeaders.ROLES, required = false) String roles,
            @RequestBody(required = false) ApiDtos.WorkflowRunRequest request,
            @RequestParam(defaultValue = "false") boolean refresh) {
        ActorContext actor = context.actor(actorId, businessUnit, roles);
        LocalDate asOf = request == null || request.asOfDate() == null ? LocalDate.parse("2026-06-08") : request.asOfDate();
        RunView run = dashboard.capture(actor, context.tenant(actor), asOf, RequestContext.persona(actor, request == null ? null : request.persona()), refresh);
        return renderer.render(run, questions.suggestedQuestions());
    }

    @GetMapping("/{workflowId}")
    public ApiDtos.MorningBriefResponse get(
            @RequestHeader(name = TrustedHeaders.ACTOR, required = false) String actorId,
            @RequestHeader(name = TrustedHeaders.BUSINESS_UNIT) String businessUnit,
            @RequestHeader(name = TrustedHeaders.ROLES, required = false) String roles,
            @PathVariable UUID workflowId) {
        ActorContext actor = context.actor(actorId, businessUnit, roles);
        RunView run = gateway.find(actor, workflowId).orElseThrow(() -> new RunNotFoundException(workflowId));
        return renderer.render(run, questions.suggestedQuestions());
    }
}
