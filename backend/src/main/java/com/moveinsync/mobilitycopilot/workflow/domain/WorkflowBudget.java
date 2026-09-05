package com.moveinsync.mobilitycopilot.workflow.domain;

import java.time.Duration;

/** WS2: validate and enforce shared run counters, deadlines and concurrency. */
public record WorkflowBudget(int maxToolCalls, int maxDepth, int maxCorrections,
                             Duration investigationTimeout, int maxParallelTasks) {}
