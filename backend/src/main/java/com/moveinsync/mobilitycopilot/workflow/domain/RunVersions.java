package com.moveinsync.mobilitycopilot.workflow.domain;

/** Integration owner: validate nonempty versions and include them in applicable reuse keys. */
public record RunVersions(String data, String metrics, String workflow, String prompts,
                          String model, String configuration) {}
