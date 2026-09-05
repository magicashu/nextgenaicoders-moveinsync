package com.moveinsync.mobilitycopilot.workflow.investigation.registry;

import com.moveinsync.mobilitycopilot.workflow.investigation.InvestigationTool;
import com.moveinsync.mobilitycopilot.workflow.investigation.workers.WorkerType;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Spring-managed registry that maps each WorkerType to its InvestigationTool implementation.
 * Workers self-register via the workerType() method; the registry validates completeness at startup.
 */
@Component
public final class WorkerRegistry {

    private final Map<WorkerType, InvestigationTool<?>> registry = new EnumMap<>(WorkerType.class);

    public WorkerRegistry(List<RegisterableWorker<?>> workers) {
        for (RegisterableWorker<?> worker : workers) {
            registry.put(worker.workerType(), worker);
        }
    }

    @PostConstruct
    void validate() {
        for (WorkerType type : WorkerType.values()) {
            if (!registry.containsKey(type)) {
                throw new IllegalStateException("No InvestigationTool registered for WorkerType." + type
                        + ". All seven workers must be registered before the application starts.");
            }
        }
    }

    @SuppressWarnings("unchecked")
    public <T> Optional<InvestigationTool<T>> resolve(WorkerType type) {
        return Optional.ofNullable((InvestigationTool<T>) registry.get(type));
    }

    public boolean isRegistered(WorkerType type) {
        return registry.containsKey(type);
    }
}
