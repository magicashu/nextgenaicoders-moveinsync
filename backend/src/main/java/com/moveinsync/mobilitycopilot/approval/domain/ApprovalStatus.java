package com.moveinsync.mobilitycopilot.approval.domain;

/** Lifecycle of one approval request. Only PENDING accepts a decision. */
public enum ApprovalStatus {
    PENDING,
    APPROVED,
    REJECTED,
    EDITED,
    EXPIRED
}
