package com.moveinsync.mobilitycopilot.workflow.investigation;

import com.moveinsync.mobilitycopilot.access.domain.TenantContext;

import java.util.Map;

public interface InvestigationTool<T> {

    String name();

    T execute(TenantContext tenant, Map<String, String> boundedParameters);
}
