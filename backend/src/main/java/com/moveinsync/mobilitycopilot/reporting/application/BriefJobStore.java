package com.moveinsync.mobilitycopilot.reporting.application;

import com.moveinsync.mobilitycopilot.access.domain.ActorContext;
import com.moveinsync.mobilitycopilot.access.domain.TenantContext;
import com.moveinsync.mobilitycopilot.reporting.domain.BriefJob;
import java.util.Optional;
import java.util.UUID;

/** WS4/WS3 seam: bounded admission, identity-safe lookup and atomic claims are adapter obligations. */
public interface BriefJobStore {
    Admission submit(BriefJob job);
    Optional<BriefJob> find(ActorContext actor, TenantContext tenant, UUID jobId);
    Optional<BriefJob> claimNext(String workerId);
    boolean transition(BriefJob job, BriefJob.Status expectedStatus);

    enum Admission { ACCEPTED, REUSED, CAPACITY_REACHED }
}
