package com.moveinsync.mobilitycopilot.audit.application;

import com.moveinsync.mobilitycopilot.access.domain.TenantContext;
import com.moveinsync.mobilitycopilot.audit.domain.AuditEvent;
import java.util.List;
import java.util.UUID;

/** Business audit is independent of optional diagnostic trace export. */
public interface AuditLedger {
    void append(AuditEvent event);
    List<AuditEvent> findByRun(TenantContext tenant, UUID runId);
}
