package com.moveinsync.mobilitycopilot.metrics.adapter.duckdb;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moveinsync.mobilitycopilot.access.domain.*;
import com.moveinsync.mobilitycopilot.config.*;
import com.moveinsync.mobilitycopilot.evidence.application.*;
import com.moveinsync.mobilitycopilot.evidence.domain.*;
import com.moveinsync.mobilitycopilot.ingestion.application.*;
import com.moveinsync.mobilitycopilot.metrics.domain.*;
import com.moveinsync.mobilitycopilot.workflow.agents.*;
import com.moveinsync.mobilitycopilot.workflow.agents.impl.*;
import com.moveinsync.mobilitycopilot.workflow.application.AgentWorkflowService;
import com.moveinsync.mobilitycopilot.workflow.domain.*;
import com.moveinsync.mobilitycopilot.workflow.investigation.executor.BoundedInvestigationExecutor;
import com.moveinsync.mobilitycopilot.workflow.investigation.registry.WorkerRegistry;
import com.moveinsync.mobilitycopilot.workflow.investigation.validation.EvidenceValidator;
import com.moveinsync.mobilitycopilot.workflow.investigation.workers.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import static org.assertj.core.api.Assertions.*;

/** Immutable official-data acceptance. Enable explicitly; no synthetic replacement or provider calls. */
@EnabledIfSystemProperty(named="officialDataset",matches=".+")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OfficialDatasetScenariosTest {
    OfficialAnalyticsStore store; OfficialDuckDbGovernedMetricService metrics; AgentWorkflowService agents;
    BoundedInvestigationExecutor executor; String version;
    @BeforeAll void initialize() {
        Path source=Path.of(System.getProperty("officialDataset")).toAbsolutePath();
        Path database=Path.of(System.getProperty("java.io.tmpdir"),"mobility-official-scenarios.duckdb");
        long started=System.nanoTime();
        store=new OfficialAnalyticsStore(new MobilityDataProperties(source.toString()),new OfficialDatasetProfileService(new DatasetFileCatalog()),
                new AnalyticsProperties(database.toString(),"512MB",2,4,30,256,100));
        version=store.dataVersion();metrics=new OfficialDuckDbGovernedMetricService(store);
        executor=new BoundedInvestigationExecutor(4,32);
        var registry=new WorkerRegistry(List.of(new VendorWorker(metrics),new SiteShiftDirectionWorker(metrics),new DelayReasonWorker(metrics),
                new CostBillingWorker(metrics),new FeedbackWorker(metrics),new TrackingSafetyWorker(metrics),new NoShowRosterWorker(metrics)));
        agents=new AgentWorkflowService(new GovernedSupervisorAgent((SupervisorIssueSource)null),new InvestigationAgentImpl(registry,new EvidenceValidator(),executor),
                new com.moveinsync.mobilitycopilot.workflow.agents.EvidenceCriticAgentImpl(new DeterministicEvidenceVerifier(),Optional.empty(),new ObjectMapper().findAndRegisterModules()),
                new BriefingActionAgentImpl(),metrics);
        System.out.println("OFFICIAL_SNAPSHOT version="+version+" load_or_reopen_ms="+(System.nanoTime()-started)/1_000_000);
    }
    @AfterAll void close(){if(executor!=null)executor.close();if(store!=null)store.close();}
    @Test void source_reconciliation_and_profile_corrections_are_measured_in_duckdb() {
        String directory=Path.of(System.getProperty("officialDataset")).toAbsolutePath().toString().replace("'","''");
        long trips=store.query("SELECT count(*) FROM read_csv('"+directory+"/Ride_data _trip-*.csv',header=true,all_varchar=true,union_by_name=true)",List.of(),r->{r.next();return r.getLong(1);});
        assertThat(trips).isEqualTo(615546);
        long legs=store.query("SELECT count(*) FROM read_csv('"+directory+"/emp_Data.csv',header=true,all_varchar=true)",List.of(),r->{r.next();return r.getLong(1);});
        assertThat(legs).isEqualTo(1637906);
        var offices=store.query("SELECT site_id,count(*) FROM trips WHERE business_unit=? GROUP BY 1 ORDER BY 2 DESC",List.of("vanta-Aus"),r->{var out=new LinkedHashMap<String,Long>();while(r.next())out.put(r.getString(1),r.getLong(2));return out;});
        assertThat(offices).containsEntry("Cedar Ridge Office",69801L).containsEntry("Santa Clara Office",398L);
        String sql="SELECT round(median(total),2) FROM (SELECT replace(trip_id,',','') trip_id,sum(cast(replace(trip_cost,',','') AS DECIMAL(18,2))) total FROM "
                +"(SELECT DISTINCT * FROM read_csv('"+directory+"/bill_data.csv',header=true,all_varchar=true)) WHERE business_unit='vanta-Sea' AND cycle_start LIKE 'May%' "
                +"AND trip_id IS NOT NULL AND cast(replace(trip_cost,',','') AS DECIMAL(18,2))>=0 GROUP BY 1 HAVING sum(cast(replace(trip_cost,',','') AS DECIMAL(18,2)))>0)";
        var median=store.query(sql,List.of(),r->{r.next();return r.getBigDecimal(1);});
        assertThat(median).isEqualByComparingTo(metric("vanta-Sea",MetricId.M09_MEDIAN_BILLED_COST_PER_TRIP,"2026-05-01","2026-05-31").value());
        System.out.println("OFFICIAL_RECONCILIATION trips="+trips+" legs="+legs+" vanta_offices="+offices+" vanta_sea_may_median="+median);
    }
    @Test void concurrent_dynamic_queries_preserve_results_and_use_ordered_rollups() throws Exception {
        var requests=new ArrayList<MetricRequest>();
        for(String tenant:List.of("pinnacle-Slc","vanta-Aus","vanta-Sea","catalyst-Sac","orbit-Slc"))
            for(int month=5;month<=7;month++){var first=LocalDate.of(2026,month,1);requests.add(request(tenant,MetricId.M01_DELAYED_TRIP_RATE,first.toString(),first.withDayOfMonth(first.lengthOfMonth()).toString()));}
        var service=new OfficialDuckDbGovernedMetricService(store);long start=System.nanoTime();
        try(var pool=java.util.concurrent.Executors.newFixedThreadPool(4)){
            var tasks=requests.stream().<java.util.concurrent.Callable<MetricEvidence>>map(r->()->service.compute(r)).toList();
            var results=pool.invokeAll(tasks,30,java.util.concurrent.TimeUnit.SECONDS);
            for(int i=0;i<results.size();i++)assertThat(results.get(i).get()).isEqualTo(service.compute(requests.get(i)));
        }
        long elapsed=(System.nanoTime()-start)/1_000_000;
        var settings=store.query("SELECT current_setting('threads'),current_setting('memory_limit')",List.of(),r->{r.next();return r.getString(1)+"/"+r.getString(2);});
        assertThat(settings).startsWith("2/");
        String explain=store.query("EXPLAIN SELECT sum(delayed_count),sum(trip_count) FROM trip_daily WHERE business_unit=? AND trip_date BETWEEN ? AND ?",
                List.of("pinnacle-Slc",LocalDate.of(2026,6,1),LocalDate.of(2026,6,7)),r->{StringBuilder text=new StringBuilder();while(r.next())text.append(r.getString(2));return text.toString();});
        assertThat(explain).contains("trip_daily").doesNotContain("READ_CSV");
        System.out.println("OFFICIAL_CONCURRENCY queries="+requests.size()+" client_threads=4 db_settings="+settings+" query_batch_ms="+elapsed+" JVM="+System.getProperty("java.version")+" arch="+System.getProperty("os.arch"));
    }
    MetricRequest request(String tenant,MetricId id,String start,String end){return new MetricRequest(new TenantContext(tenant),id,MetricRequest.Measure.VALUE,new MetricWindow(LocalDate.parse(start),LocalDate.parse(end)),Map.of(),version);}
    MetricEvidence metric(String tenant,MetricId id,String start,String end){return metrics.compute(request(tenant,id,start,end));}
    MetricRequest variant(MetricRequest r,MetricRequest.Measure m){return new MetricRequest(r.tenant(),r.metricId(),m,r.window(),r.filters(),r.dataVersion());}
    RunContext context(String tenant){var t=new TenantContext(tenant);return new RunContext(UUID.randomUUID(),new ActorContext("official-evaluator",Set.of("TRANSPORT_MANAGER"),Set.of(t)),t,"TRANSPORT_MANAGER",LocalDate.of(2026,8,1),new RunVersions(version,OfficialAnalyticsStore.REGISTRY_VERSION,"agents-v2","v1","none","v1"),new WorkflowBudget(24,2,1,Duration.ofSeconds(30),4),Instant.now().plusSeconds(90));}

    @Test void ds01_g1_exact_population_and_four_agent_path(){
        var r=request("pinnacle-Slc",MetricId.M01_DELAYED_TRIP_RATE,"2026-06-01","2026-06-07");
        var e=metrics.compute(r);assertThat(e.numerator()).isEqualByComparingTo("4357");assertThat(e.denominator()).isEqualByComparingTo("19913");
        var result=agents.investigate(context("pinnacle-Slc"),r);
        assertThat(result.investigation().evidence()).anyMatch(x->x.request().window().start().equals(LocalDate.of(2026,5,4)));
        assertThat(result.brief().verification().claims()).isNotEmpty();assertThat(result.brief().operationalSummary()).contains("4357");
        assertThat(result.brief().proposedActions()).isEmpty();
    }
    @Test void ds02_site_concentration_uses_group_population(){
        var groups=metrics.computeGrouped(request("pinnacle-Slc",MetricId.M01_DELAYED_TRIP_RATE,"2026-06-01","2026-06-07"),"site_id");
        assertThat(groups).anyMatch(e->"Clearwater Campus".equals(e.request().filters().get("site_id"))&&e.value()!=null);
        assertThat(groups).allMatch(e->e.request().filters().containsKey("site_id"));
    }
    @Test void ds03_all_qualified_vendors_rose(){
        var current=metrics.computeGrouped(request("pinnacle-Slc",MetricId.M01_DELAYED_TRIP_RATE,"2026-06-01","2026-06-07"),"vendor_id");
        var baseline=metrics.computeGrouped(request("pinnacle-Slc",MetricId.M01_DELAYED_TRIP_RATE,"2026-05-04","2026-05-31"),"vendor_id");
        int compared=0;for(var c:current)for(var b:baseline)if(c.request().filters().equals(b.request().filters())&&c.population()>=500&&b.population()>=500){assertThat(c.value()).isGreaterThan(b.value());compared++;}
        assertThat(compared).isPositive();
    }
    @Test void ds04_reason_mix_has_delayed_denominator(){
        var r=request("pinnacle-Slc",MetricId.M03_DELAY_REASON_MIX,"2026-06-01","2026-06-07");
        for(var m:List.of(MetricRequest.Measure.REASON_EMPLOYEE,MetricRequest.Measure.REASON_DRIVER,MetricRequest.Measure.REASON_TRAFFIC))assertThat(metrics.compute(variant(r,m)).denominator()).isEqualByComparingTo("4357");
    }
    @Test void ds05_pickup_and_drop_deterioration(){
        for(var id:List.of(MetricId.M04_ON_TIME_PICKUP_RATE,MetricId.M05_ON_TIME_DROP_RATE))assertThat(metric("vanta-Aus",id,"2026-07-27","2026-07-31").value()).isLessThan(metric("vanta-Aus",id,"2026-05-01","2026-05-31").value());
    }
    @Test void ds06_no_shows_improved(){assertThat(metric("vanta-Aus",MetricId.M06_NO_SHOW_RATE,"2026-07-01","2026-07-31").value()).isLessThan(metric("vanta-Aus",MetricId.M06_NO_SHOW_RATE,"2026-05-01","2026-05-31").value());}
    @Test void ds07_cancellation_is_eligible_leg_rate(){var e=metric("pinnacle-Slc",MetricId.M07_DASHBOARD_CANCELLATION_RATE,"2026-06-01","2026-06-30");assertThat(e.status()).isEqualTo(MetricStatus.AVAILABLE);assertThat(e.numerator()).isLessThanOrEqualTo(e.denominator());}
    @Test void ds08_occupancy_retains_over_capacity_caveat(){var e=metric("pinnacle-Slc",MetricId.M08_OCCUPANCY,"2026-06-01","2026-06-30");assertThat(e.value()).isBetween(java.math.BigDecimal.ZERO,new java.math.BigDecimal("100"));assertThat(e.warnings()).anyMatch(w->w.contains("capped"));}
    @Test void ds09_billing_median_reconciles_golden(){assertThat(metric("vanta-Sea",MetricId.M09_MEDIAN_BILLED_COST_PER_TRIP,"2026-05-01","2026-05-31").value().setScale(0,java.math.RoundingMode.HALF_UP)).isEqualByComparingTo("1390");}
    @Test void ds10_cost_does_not_support_delay_cost_penalty(){assertThat(metric("pinnacle-Slc",MetricId.M09_MEDIAN_BILLED_COST_PER_TRIP,"2026-06-01","2026-06-30").value()).isLessThan(metric("pinnacle-Slc",MetricId.M09_MEDIAN_BILLED_COST_PER_TRIP,"2026-05-01","2026-05-31").value());}
    @Test void ds11_billed_distance_capability(){assertThat(metric("pinnacle-Slc",MetricId.M10_COST_PER_BILLED_KM,"2026-06-01","2026-06-30").value()).isPositive();for(String tenant:List.of("vanta-Aus","vanta-Sea"))assertThat(metric(tenant,MetricId.M10_COST_PER_BILLED_KM,"2026-06-01","2026-06-30").status()).isEqualTo(MetricStatus.UNAVAILABLE);}
    @Test void ds12_feedback_has_coverage_caveat(){var e=metric("vanta-Aus",MetricId.M11_LOW_DRIVER_RATING_RATE,"2026-07-01","2026-07-31");assertThat(e.value()).isNotNull();assertThat(e.warnings()).anyMatch(w->w.contains("coverage"));}
    @Test void ds13_rating_variants_are_distinct(){var r=request("pinnacle-Slc",MetricId.M12_MEAN_DRIVER_SAFETY_RATING,"2026-06-01","2026-06-30");var d=metrics.compute(variant(r,MetricRequest.Measure.DRIVER_RATING));var s=metrics.compute(variant(r,MetricRequest.Measure.SAFETY_RATING));assertThat(d.evidenceId()).isNotEqualTo(s.evidenceId());assertThat(d.value()).isBetween(new java.math.BigDecimal("1"),new java.math.BigDecimal("5"));assertThat(s.value()).isBetween(new java.math.BigDecimal("1"),new java.math.BigDecimal("5"));}
    @Test void ds14_regime_change_is_explicitly_excluded(){var e=metric("pinnacle-Slc",MetricId.M13_ALERT_RATE,"2026-05-04","2026-05-17");assertThat(e.warnings()).anyMatch(w->w.contains("data-regime-change")&&w.contains("not trigger"));}
    @Test void ds15_severe_alert_events_are_subset(){var all=metric("pinnacle-Slc",MetricId.M13_ALERT_RATE,"2026-06-01","2026-06-30");var severe=metric("pinnacle-Slc",MetricId.M14_SEVERE_ALERT_RATE,"2026-06-01","2026-06-30");assertThat(all.unit()).isEqualTo(MetricUnit.PER_THOUSAND_TRIPS);assertThat(severe.numerator()).isLessThanOrEqualTo(all.numerator());assertThat(severe.denominator()).isEqualByComparingTo(all.denominator());}
    @Test void ds16_acknowledgement_p90_has_correct_unit(){assertThat(metric("pinnacle-Slc",MetricId.M15_SEVERE_ACKNOWLEDGEMENT_P90,"2026-06-01","2026-06-30").status()).isEqualTo(MetricStatus.UNAVAILABLE);var e=metric("catalyst-Sac",MetricId.M15_SEVERE_ACKNOWLEDGEMENT_P90,"2026-06-01","2026-06-30");assertThat(e.value()).isGreaterThanOrEqualTo(java.math.BigDecimal.ZERO);assertThat(e.unit()).isEqualTo(MetricUnit.MINUTES);}
    @Test void ds17_tracking_is_event_proxy(){var e=metric("vanta-Aus",MetricId.M16_TRACKING_GAP_RATE,"2026-07-01","2026-07-31");assertThat(e.value()).isPositive();assertThat(e.warnings()).anyMatch(w->w.contains("proxy"));}
    @Test void ds18_ev_trip_share_increased(){assertThat(metric("vanta-Aus",MetricId.M17_EV_SHARE,"2026-07-01","2026-07-31").value()).isGreaterThan(metric("vanta-Aus",MetricId.M17_EV_SHARE,"2026-05-01","2026-05-31").value());}
    @Test void ds19_escort_population_is_restricted(){var e=metric("vanta-Aus",MetricId.M18_ESCORT_PRESENT_RATE,"2026-07-01","2026-07-31");assertThat(e.value()).isNotNull();assertThat(e.numerator()).isLessThanOrEqualTo(e.denominator());assertThat(e.warnings()).anyMatch(w->w.contains("no compliance"));}
    @Test void ds20_cross_domain_briefs_preserve_actual_caveats(){
        var tasks=new ArrayList<InvestigationTask>();
        var ids=List.of(MetricId.M01_DELAYED_TRIP_RATE,MetricId.M04_ON_TIME_PICKUP_RATE,MetricId.M05_ON_TIME_DROP_RATE,MetricId.M06_NO_SHOW_RATE,MetricId.M11_LOW_DRIVER_RATING_RATE,MetricId.M16_TRACKING_GAP_RATE,MetricId.M17_EV_SHARE,MetricId.M10_COST_PER_BILLED_KM);
        for(var id:ids)tasks.add(new InvestigationTask(id.name(),WorkerType.SITE_SHIFT_DIRECTION,"Compare actual dataset periods",List.of(request("vanta-Aus",id,"2026-07-01","2026-07-31"),request("vanta-Aus",id,"2026-05-01","2026-05-31")),List.of()));
        var result=agents.execute(context("vanta-Aus"),new InvestigationPlan("DS20","DS20",tasks,Set.of(),List.of("Stop after approved comparisons")));
        assertThat(result.brief().verification().claims()).isNotEmpty();
        assertThat(result.brief().caveats()).anyMatch(w->w.contains("Office comparison scope")||w.contains("Single office")).anyMatch(w->w.contains("coverage")).anyMatch(w->w.contains("unavailable"));
        for(var claim:result.brief().verification().claims()){assertThat(result.brief().operationalSummary()).contains(claim.text());assertThat(result.brief().leadershipSummary()).contains(claim.text());}
    }
}
