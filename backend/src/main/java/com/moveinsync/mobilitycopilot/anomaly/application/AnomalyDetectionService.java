package com.moveinsync.mobilitycopilot.anomaly.application;

import com.moveinsync.mobilitycopilot.access.domain.TenantContext;
import com.moveinsync.mobilitycopilot.anomaly.domain.AnomalyDetectionResult;
import com.moveinsync.mobilitycopilot.anomaly.domain.RegimeChangeFinding;

import java.time.LocalDate;
import java.util.List;

/**
 * Deterministic sensing step: benchmark every rate metric against the prior four complete weeks
 * and the configured target, estimate impact, compute confidence, rank, and classify single-type
 * alert step changes as data-regime changes.
 */
public interface AnomalyDetectionService {

    AnomalyDetectionResult detect(TenantContext tenant, LocalDate asOfDate);

    List<RegimeChangeFinding> dataRegimeChanges(TenantContext tenant);
}
