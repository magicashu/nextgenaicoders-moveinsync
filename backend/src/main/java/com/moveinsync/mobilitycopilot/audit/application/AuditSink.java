package com.moveinsync.mobilitycopilot.audit.application;

import com.moveinsync.mobilitycopilot.audit.domain.AuditEvent;

import java.util.List;
import java.util.UUID;

public interface AuditSink {

    AuditEvent append(AuditEvent event);

    List<AuditEvent> findByRunId(UUID runId);
}
