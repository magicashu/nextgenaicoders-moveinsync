package com.moveinsync.mobilitycopilot.workflow.investigation;

import com.moveinsync.mobilitycopilot.access.domain.TenantContext;
import com.moveinsync.mobilitycopilot.workflow.application.ports.AnalyticsGateway;
import com.moveinsync.mobilitycopilot.workflow.application.ports.WorkerEvidenceDto;

import java.util.Map;

/**
 * A registered, read-only analytical tool. Parameters are a bounded, allowlisted map; the tool
 * returns typed evidence only. Tools never accept SQL, file paths, URLs or free text.
 */
public interface InvestigationTool {

    String name();

    WorkerEvidenceDto execute(TenantContext tenant, AnalyticsGateway.WindowDto current, AnalyticsGateway.WindowDto baseline,
                              Map<String, String> allowlistedFilters);
}
