package com.moveinsync.mobilitycopilot.metrics.domain;

import java.time.LocalDate;

/** WS1: validate dates and implement the approved current/reference-window rules. */
public record MetricWindow(LocalDate start, LocalDate end) {}
