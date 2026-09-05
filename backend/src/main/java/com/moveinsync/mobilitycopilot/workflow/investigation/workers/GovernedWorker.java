package com.moveinsync.mobilitycopilot.workflow.investigation.workers;

import com.moveinsync.mobilitycopilot.evidence.domain.MetricEvidence;
import com.moveinsync.mobilitycopilot.metrics.application.GovernedMetricService;
import com.moveinsync.mobilitycopilot.metrics.domain.*;
import com.moveinsync.mobilitycopilot.workflow.domain.*;
import com.moveinsync.mobilitycopilot.workflow.investigation.registry.RegisterableWorker;
import java.util.*;

/** Workers are scoped query strategies over one authority, not independent SQL implementations. */
public abstract class GovernedWorker implements RegisterableWorker<MetricEvidence> {
    private final GovernedMetricService metrics;
    private final WorkerType type;
    protected GovernedWorker(GovernedMetricService metrics,WorkerType type) { this.metrics=metrics; this.type=type; }
    @Override public WorkerType workerType(){return type;}
    @Override public String name(){return type.name();}
    @Override public MetricEvidence execute(RunContext c,InvestigationTask t){return executeAll(c,t).getFirst();}
    @Override public List<MetricEvidence> executeAll(RunContext c,InvestigationTask task) {
        if(task==null||task.worker()!=type||task.requests()==null||task.requests().isEmpty())throw new IllegalArgumentException("Invalid worker task");
        List<MetricEvidence> results=new ArrayList<>();
        for(MetricRequest r:task.requests()) {
            RunGuards.requireRequest(c,r); RunGuards.requireTime(c);
            if(!supports(type,r.metricId()))throw new IllegalArgumentException("Worker cannot execute requested metric family");
            List<MetricRequest.Measure> measures = r.metricId()==MetricId.M03_DELAY_REASON_MIX&&r.measure()==MetricRequest.Measure.VALUE
                    ?List.of(MetricRequest.Measure.REASON_EMPLOYEE,MetricRequest.Measure.REASON_DRIVER,MetricRequest.Measure.REASON_TRAFFIC):List.of(r.measure());
            for(var measure:measures) {
                var request=new MetricRequest(r.tenant(),r.metricId(),measure,r.window(),r.filters(),r.dataVersion());
                results.add(metrics.compute(request));
                for(String dimension:dimensions()) {
                    RunGuards.requireTime(c);
                    if(!request.filters().containsKey(dimension)) results.addAll(metrics.computeGrouped(request,dimension));
                }
            }
        }
        return List.copyOf(results);
    }
    private List<String> dimensions() {
        return switch(type) {case VENDOR -> List.of("vendor_id");case SITE_SHIFT_DIRECTION -> List.of("site_id","shift_id","direction");default -> List.of();};
    }
    public static boolean supports(WorkerType worker,MetricId metric) {
        if(worker==null||metric==null)return false;
        return switch(worker) {
            case VENDOR,SITE_SHIFT_DIRECTION -> true;
            case DELAY_REASON -> metric==MetricId.M03_DELAY_REASON_MIX||metric==MetricId.M02_DELAYED_TRIP_DELAY;
            case COST_BILLING -> metric==MetricId.M09_MEDIAN_BILLED_COST_PER_TRIP||metric==MetricId.M10_COST_PER_BILLED_KM;
            case FEEDBACK -> metric==MetricId.M11_LOW_DRIVER_RATING_RATE||metric==MetricId.M12_MEAN_DRIVER_SAFETY_RATING;
            case TRACKING_SAFETY -> Set.of(MetricId.M13_ALERT_RATE,MetricId.M14_SEVERE_ALERT_RATE,MetricId.M15_SEVERE_ACKNOWLEDGEMENT_P90,MetricId.M16_TRACKING_GAP_RATE,MetricId.M18_ESCORT_PRESENT_RATE).contains(metric);
            case NO_SHOW_ROSTER -> metric==MetricId.M06_NO_SHOW_RATE||metric==MetricId.M07_DASHBOARD_CANCELLATION_RATE;
        };
    }
}
