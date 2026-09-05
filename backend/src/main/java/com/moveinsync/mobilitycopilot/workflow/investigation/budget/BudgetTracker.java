package com.moveinsync.mobilitycopilot.workflow.investigation.budget;

import com.moveinsync.mobilitycopilot.workflow.domain.WorkflowBudget;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Thread-safe budget tracker for a single investigation run.
 * One instance per RunContext — not a Spring bean, created by InvestigationAgentImpl.
 */
public final class BudgetTracker {

    private final int maxToolCalls;
    private final int maxDepth;
    private final Instant deadline;

    private final AtomicInteger toolCallsUsed = new AtomicInteger(0);

    public BudgetTracker(WorkflowBudget budget, Instant deadline) {
        this.maxToolCalls = budget.maxToolCalls();
        this.maxDepth = budget.maxDepth();
        this.deadline = deadline;
    }

    /** Atomically consume one tool-call slot. Returns true if the slot was granted. */
    public boolean tryConsumeToolCall() {
        int current;
        do {
            current = toolCallsUsed.get();
            if (current >= maxToolCalls) return false;
        } while (!toolCallsUsed.compareAndSet(current, current + 1));
        return true;
    }

    public boolean toolCallsExhausted() {
        return toolCallsUsed.get() >= maxToolCalls;
    }

    public boolean depthExceeded(int currentDepth) {
        return currentDepth >= maxDepth;
    }

    public boolean deadlineExceeded() {
        return Instant.now().isAfter(deadline);
    }

    /** True when a follow-up analysis is permitted within current budget. */
    public boolean canFollowUp(int currentDepth) {
        return !toolCallsExhausted() && !depthExceeded(currentDepth + 1) && !deadlineExceeded();
    }

    public int toolCallsUsed() {
        return toolCallsUsed.get();
    }

    public int maxToolCalls() {
        return maxToolCalls;
    }

    public int maxDepth() {
        return maxDepth;
    }
}
