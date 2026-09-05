package com.moveinsync.mobilitycopilot.api;

import com.moveinsync.mobilitycopilot.access.domain.ActorContext;
import com.moveinsync.mobilitycopilot.action.domain.ActionProposal;
import com.moveinsync.mobilitycopilot.api.dto.ApiDtos;
import com.moveinsync.mobilitycopilot.api.security.TrustedHeaders;
import com.moveinsync.mobilitycopilot.approval.domain.ApprovalDecisionType;
import com.moveinsync.mobilitycopilot.reporting.application.ApprovalNotFoundException;
import com.moveinsync.mobilitycopilot.reporting.application.BriefRenderer;
import com.moveinsync.mobilitycopilot.reporting.application.DecisionRunGateway;
import com.moveinsync.mobilitycopilot.reporting.application.RunView;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** POST /api/v1/approvals/{approvalId}/decision — approve, reject or edit; the workflow revalidates and executes at most once. */
@RestController
@RequestMapping("/api/v1/approvals")
public class ApprovalController {

    private final RequestContext context;
    private final DecisionRunGateway gateway;
    private final BriefRenderer renderer;

    public ApprovalController(RequestContext context, DecisionRunGateway gateway, BriefRenderer renderer) {
        this.context = context;
        this.gateway = gateway;
        this.renderer = renderer;
    }

    @GetMapping("/{approvalId}")
    public ApiDtos.ApprovalView preview(
            @RequestHeader(name = TrustedHeaders.ACTOR, required = false) String actorId,
            @RequestHeader(name = TrustedHeaders.BUSINESS_UNIT) String businessUnit,
            @RequestHeader(name = TrustedHeaders.ROLES, required = false) String roles,
            @PathVariable UUID approvalId) {
        ActorContext actor = context.actor(actorId, businessUnit, roles);
        RunView run = gateway.findByApproval(actor, approvalId).orElseThrow(() -> new ApprovalNotFoundException(approvalId));
        return renderer.approvalView(run);
    }

    @PostMapping("/{approvalId}/decision")
    public ApiDtos.ApprovalDecisionResponse decide(
            @RequestHeader(name = TrustedHeaders.ACTOR, required = false) String actorId,
            @RequestHeader(name = TrustedHeaders.BUSINESS_UNIT) String businessUnit,
            @RequestHeader(name = TrustedHeaders.ROLES, required = false) String roles,
            @PathVariable UUID approvalId,
            @Valid @RequestBody ApiDtos.ApprovalDecisionRequest request) {
        ActorContext actor = context.actor(actorId, businessUnit, roles);
        ApprovalDecisionType type;
        try {
            type = ApprovalDecisionType.valueOf(request.decision().trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("decision must be APPROVE, REJECT or EDIT");
        }
        ActionProposal edited = null;
        if (type == ApprovalDecisionType.EDIT) {
            RunView pending = gateway.findByApproval(actor, approvalId).orElseThrow(() -> new ApprovalNotFoundException(approvalId));
            ActionProposal original = pending.approvalRequest().proposal();
            Map<String, String> scope = new LinkedHashMap<>(original.scope());
            if (request.editedScope() != null) {
                request.editedScope().forEach((k, v) -> {
                    if (!k.equals("businessUnit")) {
                        scope.put(k, v);
                    }
                });
            }
            edited = new ActionProposal(original.actionId(), original.runId(), original.type(), original.title(), original.rationale(), scope,
                    original.evidenceVersion(), original.createdAt(), original.expiresAt(), original.status());
        }
        RunView run = gateway.decide(actor, approvalId, type, request.comment(), edited);
        List<String> revalidation = run.transitions().stream().filter(t -> "REVALIDATE_AND_EXECUTE".equals(t.node()))
                .map(t -> t.outcome() + (t.attributes().containsKey("result") ? " (" + t.attributes().get("result") + ")" : "")).toList();
        return new ApiDtos.ApprovalDecisionResponse(approvalId, run.runId(), type.name(), run.approvalStatus(), run.finalStep(), run.receipt(), revalidation, renderer.trust(run));
    }
}
