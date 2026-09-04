package com.moveinsync.mobilitycopilot.api;

import com.moveinsync.mobilitycopilot.access.application.AccessAuthorizer;
import com.moveinsync.mobilitycopilot.access.domain.ActorContext;
import com.moveinsync.mobilitycopilot.access.domain.Permission;
import com.moveinsync.mobilitycopilot.api.dto.ApiDtos;
import com.moveinsync.mobilitycopilot.api.security.TrustedHeaders;
import com.moveinsync.mobilitycopilot.audit.application.AuditSink;
import com.moveinsync.mobilitycopilot.audit.domain.AuditEvent;
import com.moveinsync.mobilitycopilot.reporting.application.RunNotFoundException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** GET /api/v1/audit/{workflowId} — append-only business events for one run, tenant-scoped. */
@RestController
@RequestMapping("/api/v1/audit")
public class AuditController {

    private final RequestContext context;
    private final AuditSink audit;
    private final AccessAuthorizer authorizer;

    public AuditController(RequestContext context, AuditSink audit, AccessAuthorizer authorizer) {
        this.context = context;
        this.audit = audit;
        this.authorizer = authorizer;
    }

    @GetMapping("/{workflowId}")
    public ApiDtos.AuditResponse audit(
            @RequestHeader(name = TrustedHeaders.ACTOR, required = false) String actorId,
            @RequestHeader(name = TrustedHeaders.BUSINESS_UNIT) String businessUnit,
            @RequestHeader(name = TrustedHeaders.ROLES, required = false) String roles,
            @PathVariable UUID workflowId) {
        ActorContext actor = context.actor(actorId, businessUnit, roles);
        authorizer.require(actor, context.tenant(actor), Permission.VIEW_AUDIT);
        List<AuditEvent> events = audit.findByRunId(workflowId);
        if (events.isEmpty()) {
            throw new RunNotFoundException(workflowId);
        }
        // tenant isolation: a run belonging to another business unit is indistinguishable from a missing one
        if (events.stream().anyMatch(e -> !e.businessUnit().equals(actor.businessUnit()))) {
            throw new RunNotFoundException(workflowId);
        }
        return new ApiDtos.AuditResponse(workflowId, actor.businessUnit(), events.getFirst().traceId(), events, events.size());
    }
}
