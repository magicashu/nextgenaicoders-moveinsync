package com.moveinsync.mobilitycopilot.reporting.application;

import com.moveinsync.mobilitycopilot.reporting.domain.DecisionBrief;

/** WS4: deterministic escaping, tables, methodology and claim-preserving leadership rendering. */
public interface BriefRenderer {
    String printableHtml(DecisionBrief brief);
}
