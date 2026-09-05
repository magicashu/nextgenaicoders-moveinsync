package com.moveinsync.mobilitycopilot.api;

import com.moveinsync.mobilitycopilot.access.domain.ActorContext;
import com.moveinsync.mobilitycopilot.api.dto.ApiDtos;
import com.moveinsync.mobilitycopilot.api.security.TrustedHeaders;
import com.moveinsync.mobilitycopilot.conversation.application.ContextualQuestionService;
import com.moveinsync.mobilitycopilot.conversation.domain.QuestionScope;
import com.moveinsync.mobilitycopilot.evidence.domain.EvidenceBundle;
import com.moveinsync.mobilitycopilot.reporting.application.BriefRenderer;
import com.moveinsync.mobilitycopilot.reporting.application.RunView;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** POST /api/v1/questions — contextual "Ask about this". Refusals are typed and cost zero tool calls. */
@RestController
@RequestMapping("/api/v1/questions")
public class QuestionController {

    private final RequestContext context;
    private final ContextualQuestionService questions;
    private final BriefRenderer renderer;

    public QuestionController(RequestContext context, ContextualQuestionService questions, BriefRenderer renderer) {
        this.context = context;
        this.questions = questions;
        this.renderer = renderer;
    }

    @PostMapping
    public ApiDtos.QuestionResponse ask(
            @RequestHeader(name = TrustedHeaders.ACTOR, required = false) String actorId,
            @RequestHeader(name = TrustedHeaders.BUSINESS_UNIT) String businessUnit,
            @RequestHeader(name = TrustedHeaders.ROLES, required = false) String roles,
            @Valid @RequestBody ApiDtos.QuestionRequest request) {
        ActorContext actor = context.actor(actorId, businessUnit, roles);
        QuestionScope scope = questions.classify(actor, request.question());
        if (scope.refused()) {
            return new ApiDtos.QuestionResponse(null, actor.businessUnit(), scope.intent(), List.of(), true, scope.refusalReason(),
                    "I can only answer governed analytical questions about " + actor.businessUnit() + ". " + scope.refusalReason() + ".",
                    List.of(), List.of(), null, null, null, questions.suggestedQuestions());
        }
        LocalDate asOf = request.asOfDate() == null ? LocalDate.parse("2026-06-08") : request.asOfDate();
        Optional<RunView> answer = questions.answer(actor, context.tenant(actor), asOf, RequestContext.persona(actor, request.persona()), scope, request.relatedRunId());
        RunView run = answer.orElseThrow();
        List<ApiDtos.Finding> findings = run.claims().stream().filter(c -> !"CAVEAT".equals(c.kind()))
                .map(c -> new ApiDtos.Finding(c.claimId(), c.text(), c.kind(), c.evidenceIds(), c.worker())).toList();
        List<ApiDtos.Finding> caveats = run.claims().stream().filter(c -> "CAVEAT".equals(c.kind()))
                .map(c -> new ApiDtos.Finding(c.claimId(), c.text(), c.kind(), c.evidenceIds(), c.worker())).toList();
        String direct = findings.isEmpty() ? run.brief().headline() : findings.stream().filter(f -> !f.worker().equals("detector")).findFirst().map(ApiDtos.Finding::text).orElse(findings.getFirst().text());
        EvidenceBundle evidence = run.brief().evidence();
        return new ApiDtos.QuestionResponse(run.runId(), run.businessUnit(), scope.intent(), scope.workers(), false, null, direct, findings, caveats, evidence,
                renderer.trust(run), run.recommendedAction(), questions.followUps(scope));
    }
}
