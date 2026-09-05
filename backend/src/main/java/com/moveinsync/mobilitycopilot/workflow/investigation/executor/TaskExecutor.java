package com.moveinsync.mobilitycopilot.workflow.investigation.executor;

import com.moveinsync.mobilitycopilot.evidence.domain.MetricEvidence;
import com.moveinsync.mobilitycopilot.workflow.domain.InvestigationTask;
import com.moveinsync.mobilitycopilot.workflow.domain.WorkflowBudget;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.function.Function;

/**
 * Runs a batch of investigation tasks with bounded parallelism and per-task timeouts.
 *
 * One instance is created per investigation run (not a Spring bean).
 * The internal thread pool is shut down after each batch via close().
 *
 * Design rules:
 *   - maxParallelTasks from WorkflowBudget caps the thread pool size
 *   - Each task callable is wrapped with a hard timeout (investigationTimeout)
 *   - Timeout or error → task reported as failed, does NOT cancel sibling tasks
 *   - InterruptedException is re-propagated to the caller so the run can be aborted cleanly
 */
public final class TaskExecutor implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(TaskExecutor.class);

    private final ExecutorService pool;
    private final Duration taskTimeout;

    public TaskExecutor(WorkflowBudget budget) {
        this.pool = Executors.newFixedThreadPool(Math.max(1, budget.maxParallelTasks()));
        this.taskTimeout = budget.investigationTimeout();
    }

    /**
     * Submits all tasks in the batch for parallel execution.
     * Blocks until all tasks complete, time out, or the deadline is exceeded.
     *
     * @param tasks   the batch to execute in parallel
     * @param worker  function that runs a single task and returns its outcome
     * @return        outcomes in the same order as the input list
     */
    public List<TaskOutcome> executeBatch(List<InvestigationTask> tasks,
                                          Function<InvestigationTask, TaskOutcome> worker) {
        if (tasks.isEmpty()) return List.of();

        List<Future<TaskOutcome>> futures = new ArrayList<>(tasks.size());
        for (InvestigationTask task : tasks) {
            futures.add(pool.submit(() -> worker.apply(task)));
        }

        List<TaskOutcome> outcomes = new ArrayList<>(tasks.size());
        for (int i = 0; i < futures.size(); i++) {
            InvestigationTask task = tasks.get(i);
            Future<TaskOutcome> future = futures.get(i);
            try {
                // +2 s headroom over the tool-level timeout so the tool can report its own error
                long waitSeconds = taskTimeout.toSeconds() + 2;
                outcomes.add(future.get(waitSeconds, TimeUnit.SECONDS));
            } catch (TimeoutException e) {
                future.cancel(true);
                log.warn("task={} timed out after {}s", task.taskId(), taskTimeout.toSeconds());
                outcomes.add(TaskOutcome.timedOut(task));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                future.cancel(true);
                // Propagate interruption — the run should be aborted
                throw new TaskExecutionException("investigation run interrupted", e);
            } catch (ExecutionException e) {
                Throwable cause = e.getCause();
                log.warn("task={} failed: {}", task.taskId(), cause.getMessage());
                outcomes.add(TaskOutcome.failed(task, cause.getMessage()));
            }
        }
        return outcomes;
    }

    @Override
    public void close() {
        pool.shutdownNow();
    }

    // -------------------------------------------------------------------------
    // Result type
    // -------------------------------------------------------------------------

    public record TaskOutcome(
            InvestigationTask task,
            List<MetricEvidence> evidence,
            List<String> warnings,
            Status status) {

        public enum Status { COMPLETED, PARTIAL, TIMED_OUT, FAILED }

        public boolean isCompleted() { return status == Status.COMPLETED; }
        public boolean hasEvidence() { return evidence != null && !evidence.isEmpty(); }

        public static TaskOutcome timedOut(InvestigationTask task) {
            return new TaskOutcome(task, List.of(),
                    List.of("task timed out: " + task.taskId()), Status.TIMED_OUT);
        }

        public static TaskOutcome failed(InvestigationTask task, String reason) {
            return new TaskOutcome(task, List.of(),
                    List.of("task failed: " + task.taskId() + " — " + reason), Status.FAILED);
        }
    }

    // -------------------------------------------------------------------------
    // Exception type
    // -------------------------------------------------------------------------

    public static final class TaskExecutionException extends RuntimeException {
        public TaskExecutionException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
