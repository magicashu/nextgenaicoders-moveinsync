package com.moveinsync.mobilitycopilot.api;

import com.moveinsync.mobilitycopilot.access.domain.ActorContext;
import com.moveinsync.mobilitycopilot.api.dto.ApiDtos;
import com.moveinsync.mobilitycopilot.api.security.TrustedHeaders;
import com.moveinsync.mobilitycopilot.conversation.application.ContextualQuestionService;
import com.moveinsync.mobilitycopilot.reporting.application.BriefRenderer;
import com.moveinsync.mobilitycopilot.reporting.application.DashboardRunService;
import com.moveinsync.mobilitycopilot.reporting.application.RunView;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/** GET /api/v1/briefs/morning — the proactive landing experience. Thin: resolve identity, delegate, render. */
@RestController
@RequestMapping("/api/v1/briefs")
public class BriefController {

    private final RequestContext context;
    private final DashboardRunService gateway;
    private final BriefRenderer renderer;
    private final ContextualQuestionService questions;

    public BriefController(RequestContext context, DashboardRunService gateway, BriefRenderer renderer, ContextualQuestionService questions) {
        this.context = context;
        this.gateway = gateway;
        this.renderer = renderer;
        this.questions = questions;
    }

    @GetMapping("/morning")
    public ApiDtos.MorningBriefResponse morning(
            @RequestHeader(name = TrustedHeaders.ACTOR, required = false) String actorId,
            @RequestHeader(name = TrustedHeaders.BUSINESS_UNIT) String businessUnit,
            @RequestHeader(name = TrustedHeaders.ROLES, required = false) String roles,
            @RequestParam(name = "asOf", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf,
            @RequestParam(name = "persona", required = false) String persona) {
        ActorContext actor = context.actor(actorId, businessUnit, roles);
        LocalDate asOfDate = asOf == null ? LocalDate.parse("2026-06-08") : asOf;
        RunView run = gateway.capture(actor, context.tenant(actor), asOfDate, RequestContext.persona(actor, persona), false);
        return renderer.render(run, questions.suggestedQuestions());
    }
}
