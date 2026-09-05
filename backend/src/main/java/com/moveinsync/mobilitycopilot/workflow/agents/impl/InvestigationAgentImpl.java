package com.moveinsync.mobilitycopilot.workflow.agents.impl;

import com.moveinsync.mobilitycopilot.evidence.domain.MetricEvidence;
import com.moveinsync.mobilitycopilot.metrics.domain.MetricStatus;
import com.moveinsync.mobilitycopilot.workflow.agents.InvestigationAgent;
import com.moveinsync.mobilitycopilot.workflow.domain.*;
import com.moveinsync.mobilitycopilot.workflow.investigation.InvestigationTool;
import com.moveinsync.mobilitycopilot.workflow.investigation.executor.BoundedInvestigationExecutor;
import com.moveinsync.mobilitycopilot.workflow.investigation.registry.WorkerRegistry;
import com.moveinsync.mobilitycopilot.workflow.investigation.validation.EvidenceValidator;
import com.moveinsync.mobilitycopilot.workflow.investigation.workers.GovernedWorker;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Service;

/** Validated DAG and bounded choose/execute/validate/progress loop, preserving each request. */
@Service
public final class InvestigationAgentImpl implements InvestigationAgent {
    private final WorkerRegistry registry;
    private final EvidenceValidator validator;
    private final BoundedInvestigationExecutor executor;
    public InvestigationAgentImpl(WorkerRegistry registry,EvidenceValidator validator,BoundedInvestigationExecutor executor) {
        this.registry=registry;this.validator=validator;this.executor=executor;
    }
    @Override public InvestigationResult investigate(RunContext context,InvestigationPlan plan) {
        RunGuards.requireAuthorized(context);
        Map<String,InvestigationTask> remaining=validatePlan(context,plan);
        Set<String> completedIds=new HashSet<>();
        List<InvestigationTask> completed=new ArrayList<>(), pending=new ArrayList<>();
        List<String> warnings=new ArrayList<>();
        Map<String,MetricEvidence> evidence=new LinkedHashMap<>();
        AtomicInteger calls=new AtomicInteger();
        while(!remaining.isEmpty()) {
            List<InvestigationTask> ready=remaining.values().stream().filter(t->completedIds.containsAll(t.dependencies()))
                    .limit(context.budget().maxParallelTasks()).toList();
            if(ready.isEmpty()||!Instant.now().isBefore(context.deadline())) {
                pending.addAll(remaining.values());warnings.add("Remaining tasks blocked by incomplete dependencies or run deadline.");break;
            }
            Map<InvestigationTask,Future<Outcome>> futures=new LinkedHashMap<>();
            Instant deadline=Instant.now().plus(context.budget().investigationTimeout());
            if(context.deadline().isBefore(deadline))deadline=context.deadline();
            for(var task:ready) {
                remaining.remove(task.taskId());
                try { futures.put(task,executor.submit(()->run(context,task,calls))); }
                catch(RejectedExecutionException exhausted){pending.add(task);warnings.add("Workflow admission capacity exceeded: "+task.taskId());}
            }
            try {
                for(var entry:futures.entrySet()) {
                    try {
                        long nanos=Math.max(1,Duration.between(Instant.now(),deadline).toNanos());
                        Outcome result=entry.getValue().get(nanos,TimeUnit.NANOSECONDS);
                        warnings.addAll(result.warnings);
                        result.evidence.forEach(e->{MetricEvidence old=evidence.putIfAbsent(e.evidenceId(),e);if(old!=null&&!old.equals(e))throw new IllegalStateException("Conflicting evidence identity");});
                        if(result.complete){completed.add(entry.getKey());completedIds.add(entry.getKey().taskId());}else pending.add(entry.getKey());
                    } catch(TimeoutException failure){entry.getValue().cancel(true);pending.add(entry.getKey());warnings.add("Investigation timed out: "+entry.getKey().taskId());}
                    catch(ExecutionException failure){pending.add(entry.getKey());warnings.add("Investigation failed: "+entry.getKey().taskId());}
                    catch(InterruptedException failure){Thread.currentThread().interrupt();throw new IllegalStateException("Run interrupted",failure);}
                }
            } finally {futures.values().forEach(f->{if(!f.isDone())f.cancel(true);});}
        }
        return new InvestigationResult(List.copyOf(evidence.values()),List.copyOf(completed),List.copyOf(pending),List.copyOf(warnings));
    }
    private Outcome run(RunContext context,InvestigationTask task,AtomicInteger calls) {
        List<MetricEvidence> evidence=new ArrayList<>();List<String>warnings=new ArrayList<>();boolean complete=true;
        InvestigationTool<MetricEvidence> tool=registry.<MetricEvidence>resolve(task.worker()).orElseThrow();
        for(var request:task.requests()) {
            if(calls.incrementAndGet()>context.budget().maxToolCalls()){warnings.add("Shared request budget exhausted.");complete=false;break;}
            try {
                RunGuards.requireTime(context);
                var one=new InvestigationTask(task.taskId(),task.worker(),task.question(),List.of(request),List.of());
                List<MetricEvidence> output=tool.executeAll(context,one);
                if(output.isEmpty()){complete=false;warnings.add("Worker returned no evidence.");}
                for(var item:output) {
                    var errors=validator.validate(item,context);
                    if(!errors.isEmpty()||!matches(request,item)){complete=false;warnings.add("Rejected mismatched worker evidence for "+task.taskId());continue;}
                    evidence.add(item);warnings.addAll(item.warnings());
                    if(item.status()==MetricStatus.UNAVAILABLE)complete=false;
                }
            } catch(RuntimeException failure){complete=false;warnings.add("Worker request failed: "+task.taskId()+" ("+failure.getClass().getSimpleName()+")");}
            // Progress: no identical retry can create new evidence. Move to the next approved comparison.
        }
        return new Outcome(List.copyOf(evidence),List.copyOf(warnings),complete);
    }
    private boolean matches(com.moveinsync.mobilitycopilot.metrics.domain.MetricRequest r,MetricEvidence e) {
        var actual=e.request();
        return actual!=null&&actual.metricId()==r.metricId()&&actual.window().equals(r.window())
                &&(actual.measure()==r.measure()||(r.metricId()==com.moveinsync.mobilitycopilot.metrics.domain.MetricId.M03_DELAY_REASON_MIX
                &&r.measure()==com.moveinsync.mobilitycopilot.metrics.domain.MetricRequest.Measure.VALUE
                &&Set.of(com.moveinsync.mobilitycopilot.metrics.domain.MetricRequest.Measure.REASON_EMPLOYEE,
                com.moveinsync.mobilitycopilot.metrics.domain.MetricRequest.Measure.REASON_DRIVER,
                com.moveinsync.mobilitycopilot.metrics.domain.MetricRequest.Measure.REASON_TRAFFIC).contains(actual.measure())))
                &&com.moveinsync.mobilitycopilot.metrics.adapter.duckdb.OfficialDuckDbGovernedMetricService.DIMENSIONS.containsAll(actual.filters().keySet())
                &&actual.filters().entrySet().containsAll(r.filters().entrySet());
    }
    private Map<String,InvestigationTask> validatePlan(RunContext c,InvestigationPlan plan) {
        if(plan==null||plan.tasks()==null||plan.tasks().isEmpty()||plan.tasks().size()>c.budget().maxToolCalls())throw new IllegalArgumentException("Invalid or oversized plan");
        Map<String,InvestigationTask> tasks=new LinkedHashMap<>();
        for(var t:plan.tasks()) {
            if(t==null||t.taskId()==null||t.taskId().isBlank()||t.dependencies()==null||t.requests()==null||t.requests().isEmpty()
                    ||!registry.isRegistered(t.worker())||tasks.putIfAbsent(t.taskId(),t)!=null)throw new IllegalArgumentException("Invalid, duplicate or unregistered task");
            for(var r:t.requests()){RunGuards.requireRequest(c,r);if(!GovernedWorker.supports(t.worker(),r.metricId()))throw new IllegalArgumentException("Invalid worker/metric pair");}
        }
        Set<String> visited=new HashSet<>();
        while(visited.size()<tasks.size()) {
            int before=visited.size();
            for(var t:tasks.values())if(visited.containsAll(t.dependencies()))visited.add(t.taskId());
            if(before==visited.size())throw new IllegalArgumentException("Cyclic or missing task dependency");
        }
        return tasks;
    }
    private record Outcome(List<MetricEvidence> evidence,List<String>warnings,boolean complete){}
}
