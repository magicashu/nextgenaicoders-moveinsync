package com.moveinsync.mobilitycopilot.metrics.adapter.duckdb;

import com.moveinsync.mobilitycopilot.config.*;
import com.moveinsync.mobilitycopilot.evidence.domain.MetricEvidence;
import com.moveinsync.mobilitycopilot.ingestion.application.DatasetProfileService;
import com.moveinsync.mobilitycopilot.metrics.application.GovernedMetricService;
import com.moveinsync.mobilitycopilot.metrics.domain.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** Parameterized registry. Dynamic requests select contracts, never executable SQL. */
@Service
public final class OfficialDuckDbGovernedMetricService implements GovernedMetricService {
    public static final Set<String> DIMENSIONS = Set.of("vendor_id", "site_id", "shift_id", "direction", "mode", "fuel_type", "vehicle_id");
    private final OfficialAnalyticsStore store;
    private final Map<QueryKey, List<MetricEvidence>> cache = new LinkedHashMap<>(16, .75f, true);
    @Autowired public OfficialDuckDbGovernedMetricService(OfficialAnalyticsStore store) { this.store = store; }
    public OfficialDuckDbGovernedMetricService(MobilityDataProperties data, DatasetProfileService profiles) {
        this(new OfficialAnalyticsStore(data, profiles, new AnalyticsProperties(
                java.nio.file.Path.of(data.directory(), "test-analytics.duckdb").toString(), "512MB", 2, 2, 30, 64, 100)));
    }
    public String dataVersion() { return store.dataVersion(); }
    @Override public MetricEvidence compute(MetricRequest request) { return computeGrouped(request, null).getFirst(); }

    public List<MetricEvidence> computeGrouped(MetricRequest request, String dimension) {
        validate(request, dimension);
        QueryKey key = new QueryKey(request, dimension);
        synchronized (cache) { if (cache.containsKey(key)) return cache.get(key); }
        List<MetricEvidence> result = calculate(request, dimension);
        synchronized (cache) {
            if (store.limits().cacheEntries() > 0) {
                cache.put(key, result);
                while (cache.size() > store.limits().cacheEntries()) cache.remove(cache.keySet().iterator().next());
            }
        }
        return result;
    }

    private List<MetricEvidence> calculate(MetricRequest r, String group) {
        if(r.metricId()==MetricId.M10_COST_PER_BILLED_KM && Set.of("vanta-Aus","vanta-Sea").contains(r.tenant().businessUnit()))
            return group==null?List.of(unavailable(r,"Cost per billed km is unavailable under dataset capability contract Q2: billed-distance coverage is unreliable for this tenant.")):List.of();
        Formula f = formula(r);
        if (f == null) return List.of(unavailable(r, "Select an explicit recorded delay reason for M03."));
        boolean rollup = (r.metricId() == MetricId.M01_DELAYED_TRIP_RATE || r.metricId() == MetricId.M17_EV_SHARE)
                && !r.filters().containsKey("vehicle_id") && !"vehicle_id".equals(group);
        if (rollup) f = new Formula("trip_daily t", r.metricId() == MetricId.M01_DELAYED_TRIP_RATE
                ? "sum(t.delayed_count)" : "sum(t.electric_count)", "sum(t.trip_count)", "sum(t.trip_count)", null, "t.trip_date", "", f.warnings);
        List<Object> params = new ArrayList<>(List.of(r.tenant().businessUnit(),r.window().start(),r.window().end()));
        StringBuilder where = new StringBuilder("t.business_unit=? AND " + f.dateColumn + " BETWEEN ? AND ?");
        if (r.metricId() == MetricId.M09_MEDIAN_BILLED_COST_PER_TRIP || r.metricId() == MetricId.M10_COST_PER_BILLED_KM) {
            where.append(" AND b.cycle_end <= ?"); params.add(r.window().end());
        }
        new TreeMap<>(r.filters()).forEach((k,v) -> { where.append(" AND t.").append(k).append("=?"); params.add(v); });
        if (!f.eligibility.isBlank()) where.append(" AND ").append(f.eligibility);
        if (group != null) where.append(" AND t.").append(group).append(" IS NOT NULL");
        String value = f.value == null ? "100.0*(" + f.numerator + ")/nullif(" + f.denominator + ",0)" : f.value;
        String sql = "SELECT " + (group == null ? "NULL" : "t." + group) + " AS dimension_value, "
                + f.numerator + " AS n, " + f.denominator + " AS d, " + f.population + " AS population, "
                + "round(" + value + ",2) AS value FROM " + f.from + " WHERE " + where
                + (group == null ? "" : " GROUP BY t." + group + " ORDER BY population DESC, dimension_value LIMIT " + (store.limits().maxGroups()+1));
        Formula selected = f;
        List<MetricEvidence> result = store.query(sql, params, rows -> {
            List<MetricEvidence> items = new ArrayList<>();
            while (rows.next()) {
                MetricRequest scoped = r;
                if (group != null) {
                    var filters = new TreeMap<>(r.filters()); filters.put(group, rows.getString("dimension_value"));
                    scoped = new MetricRequest(r.tenant(),r.metricId(),r.measure(),r.window(),filters,r.dataVersion());
                }
                BigDecimal n = rows.getBigDecimal("n"), d = rows.getBigDecimal("d"), number = rows.getBigDecimal("value");
                long population = rows.getLong("population");
                List<String> warnings = new ArrayList<>(selected.warnings);
                if (group != null && population < (group.equals("vendor_id") ? 500 : 300)) warnings.add("Below the governed comparison volume; qualified context only, not a ranked conclusion.");
                if (r.metricId() == MetricId.M16_TRACKING_GAP_RATE && (n == null || n.signum()==0)) number = null;
                if (number == null) warnings.add("No eligible population for this metric, period and scope; unavailable is not zero.");
                items.add(new MetricEvidence(id(scoped)+(group==null?"":"-group-"+group),scoped,number==null?MetricStatus.UNAVAILABLE:MetricStatus.AVAILABLE,number,r.metricId().unit(),n,d,
                        population,r.metricId().contractId()+"-v1.1","snapshot:"+r.dataVersion()+"/"+r.metricId().contractId(),List.copyOf(warnings)));
            }
            return items;
        });
        boolean truncated = result.size() > store.limits().maxGroups();
        if (truncated) result = result.subList(0,store.limits().maxGroups());
        List<String> scopeWarnings = scopeWarnings(r);
        List<MetricEvidence> enriched = new ArrayList<>();
        for (MetricEvidence e : result) {
            var warnings = new ArrayList<>(e.warnings());
            warnings.addAll(scopeWarnings);
            if (truncated) warnings.add("Group output truncated at configured cardinality limit; do not make universal group claims.");
            enriched.add(new MetricEvidence(e.evidenceId(),e.request(),e.status(),e.value(),e.unit(),e.numerator(),e.denominator(),e.population(),e.metricVersion(),e.sourceReference(),List.copyOf(warnings)));
        }
        if (enriched.isEmpty() && group==null) enriched.add(unavailable(r,"No matching eligible records."));
        return List.copyOf(enriched);
    }

    private List<String> scopeWarnings(MetricRequest r) {
        // One compact quality query per request, never one source-file scan per group.
        String sql="SELECT count(*) trips,count(DISTINCT site_id) sites,count(*) FILTER(WHERE actualemployee_cnt>capacity) over_capacity,"
                +"count(*) FILTER(WHERE EXISTS(SELECT 1 FROM feedback f WHERE f.business_unit=t.business_unit AND f.trip_id=t.trip_id)) feedback_trips "
                +"FROM trips t WHERE business_unit=? AND trip_date BETWEEN ? AND ?";
        List<Object> parameters=new ArrayList<>(List.of(r.tenant().businessUnit(),r.window().start(),r.window().end()));
        for(var filter:new TreeMap<>(r.filters()).entrySet()){sql+=" AND t."+filter.getKey()+"=?";parameters.add(filter.getValue());}
        return store.query(sql,parameters,rows->{
            rows.next();List<String>warnings=new ArrayList<>();
            if(rows.getLong("sites")==1)warnings.add("Single office in the selected population; cross-office comparison is unavailable.");
            else warnings.add("Office comparison scope: "+rows.getLong("sites")+" observed offices; group comparisons require governed minimum volume.");
            if(r.metricId()==MetricId.M08_OCCUPANCY&&rows.getLong("over_capacity")>0)warnings.add("Over-capacity source trips: "+rows.getLong("over_capacity")+"; capped values do not erase this quality flag.");
            if(r.metricId()==MetricId.M11_LOW_DRIVER_RATING_RATE||r.metricId()==MetricId.M12_MEAN_DRIVER_SAFETY_RATING) {
                long trips=rows.getLong("trips"),participants=rows.getLong("feedback_trips");
                warnings.add("Feedback trip participation coverage: "+participants+"/"+trips+" trips. Ratings describe respondents only.");
                if(participants<trips)warnings.add("Incomplete feedback coverage limits generalization; preserve this caveat.");
            }
            if(r.metricId()==MetricId.M13_ALERT_RATE||r.metricId()==MetricId.M14_SEVERE_ALERT_RATE)
                warnings.add("EMPLOYEE_SIGN_OFF_TIME_VIOLATION is excluded by the documented data-regime-change contract; it must not trigger operational escalation.");
            return List.copyOf(warnings);
        });
    }

    private Formula formula(MetricRequest r) {
        String trips="trips t", legs="legs e JOIN trips t USING(business_unit,trip_id)", feedback="feedback f JOIN trips t USING(business_unit,trip_id)";
        String alert="alerts a JOIN trips t USING(business_unit,trip_id)";
        String countTrips="count(DISTINCT t.trip_id)"; // mandatory single-tenant predicate
        String alertJoin="trips t LEFT JOIN alerts a ON a.business_unit=t.business_unit AND a.trip_id=t.trip_id AND a.event_type <> 'EMPLOYEE_SIGN_OFF_TIME_VIOLATION'";
        return switch(r.metricId()) {
            case M01_DELAYED_TRIP_RATE -> rate(trips,"count(*) FILTER(WHERE delay_minutes>0)","count(*)","",List.of());
            case M02_DELAYED_TRIP_DELAY -> statistic(trips,r.measure()==MetricRequest.Measure.P90_DELAY
                    ? "quantile_cont(least(delay_minutes,600),0.9)" : "avg(least(delay_minutes,600))",
                    "delay_minutes>0 AND delay_minutes<=1440",List.of("Delayed trips only; duration capped at 600 minutes; above 1440 minutes quarantined."));
            case M03_DELAY_REASON_MIX -> {
                String reason=switch(r.measure()) { case REASON_EMPLOYEE -> "EMPLOYEE"; case REASON_DRIVER -> "DRIVER"; case REASON_TRAFFIC -> "TRAFFIC"; default -> null; };
                yield reason==null?null:rate(trips,"count(*) FILTER(WHERE delay_reason='"+reason+"')","count(*)","delay_minutes>0",List.of("Denominator is delayed trips, not all trips; recorded reasons are not proven causes."));
            }
            case M04_ON_TIME_PICKUP_RATE -> rate(legs,"count(*) FILTER(WHERE actual_pickup-planned_pickup<=600)","count(*)",
                    "boarding_status='Boarded' AND actual_pickup IS NOT NULL AND planned_pickup IS NOT NULL",List.of("Eligible boarded employee legs with both pickup epochs; ten-minute tolerance."));
            case M05_ON_TIME_DROP_RATE -> rate(legs,"count(*) FILTER(WHERE actual_drop-planned_drop<=600)","count(*)",
                    "boarding_status='Boarded' AND actual_drop IS NOT NULL AND planned_drop IS NOT NULL",List.of("Eligible boarded employee legs with both drop epochs; ten-minute tolerance."));
            case M06_NO_SHOW_RATE -> rate(legs,"count(*) FILTER(WHERE no_show)","count(*)","",List.of("All valid non-placeholder legs; no boarded-only restriction."));
            case M07_DASHBOARD_CANCELLATION_RATE -> rate(legs,"count(*) FILTER(WHERE not_boarding_reason='TRIP_CANCELLED_FROM_DASHBOARD')","count(*)","",List.of("Dashboard cancellation is distinct from no-show."));
            case M08_OCCUPANCY -> rate(trips,"sum(least(actualemployee_cnt,capacity))","sum(capacity)","capacity>0 AND actualemployee_cnt>=0",
                    List.of("Capacity-weighted occupancy; counts capped at vehicle capacity. Raw counts retained for over-capacity quality review."));
            case M09_MEDIAN_BILLED_COST_PER_TRIP -> new Formula(
                    "(SELECT business_unit,trip_id,cycle_start,cycle_end,sum(cost) AS cost FROM bills GROUP BY ALL HAVING sum(cost)>0) b JOIN trips t USING(business_unit,trip_id)",
                    "NULL","count(*)","count(*)","median(b.cost)","b.cycle_start","",List.of("Median retained positive cost per composite trip within whole billing cycles, not daily incident cost or employee cost."));
            case M10_COST_PER_BILLED_KM -> new Formula("bills b JOIN trips t USING(business_unit,trip_id)","sum(b.cost)","sum(b.km)","count(*)",
                    "sum(b.cost)/nullif(sum(b.km),0)","b.cycle_start","b.km>0",List.of("Whole billing cycles; zero billed kilometres excluded."));
            case M11_LOW_DRIVER_RATING_RATE -> rate(feedback,"count(*) FILTER(WHERE driver_rating IN (1,2))","count(*)","driver_rating>0",List.of("Rated participants only; feedback participation is not universal commuter experience."));
            case M12_MEAN_DRIVER_SAFETY_RATING -> {
                String rating=r.measure()==MetricRequest.Measure.SAFETY_RATING?"safety_rating":"driver_rating";
                yield statistic(feedback,"avg("+rating+")",rating+">0",List.of("Non-positive ratings excluded; "+rating+" only."));
            }
            case M13_ALERT_RATE -> new Formula(alertJoin,"count(a.event_id)",countTrips,countTrips,"1000.0*count(a.event_id)/nullif("+countTrips+",0)","t.trip_date","",List.of("Alert events per 1000 trips, not incident-affected trip percentage; sign-off regime event excluded."));
            case M14_SEVERE_ALERT_RATE -> new Formula(alertJoin,"count(a.event_id) FILTER(WHERE a.severity IN ('Sev-1','Sev-2'))",countTrips,countTrips,
                    "1000.0*count(a.event_id) FILTER(WHERE a.severity IN ('Sev-1','Sev-2'))/nullif("+countTrips+",0)","t.trip_date","",List.of("Sev-1/2 alert events, not confirmed incidents; unknown severity excluded."));
            case M15_SEVERE_ACKNOWLEDGEMENT_P90 -> statistic(alert,"quantile_cont(epoch(a.acknowledged-a.started)/60.0,0.9)",
                    "a.event_type <> 'EMPLOYEE_SIGN_OFF_TIME_VIOLATION' AND a.severity IN ('Sev-1','Sev-2') AND a.acknowledged>=a.started",List.of("P90 eligible alert acknowledgement duration, not average resolution time."));
            case M16_TRACKING_GAP_RATE -> new Formula(alertJoin,"count(a.event_id) FILTER(WHERE a.event_type='DEVICE_NOT_REACHABLE')",countTrips,countTrips,
                    "1000.0*count(a.event_id) FILTER(WHERE a.event_type='DEVICE_NOT_REACHABLE')/nullif("+countTrips+",0)","t.trip_date","",List.of("Device-unreachable alert proxy; no events means unavailable, not guaranteed GPS coverage."));
            case M17_EV_SHARE -> rate(trips,"count(*) FILTER(WHERE fuel_type='Electric')","count(*)","",List.of("Electric trip share, not fleet share or carbon savings."));
            case M18_ESCORT_PRESENT_RATE -> rate(trips,"count(*) FILTER(WHERE escort_present)","count(*)",
                    "EXISTS (SELECT 1 FROM alerts a WHERE a.business_unit=t.business_unit AND a.trip_id=t.trip_id AND a.event_type='WOMAN_TRAVELLING_ALONE')",
                    List.of("Distinct trips with women-travelling-alone alerts only; descriptive escort presence, no compliance claim."));
        };
    }
    private Formula rate(String from,String n,String d,String eligible,List<String> warnings) { return new Formula(from,n,d,"count(*)",null,"t.trip_date",eligible,warnings); }
    private Formula statistic(String from,String value,String eligible,List<String> warnings) { return new Formula(from,"NULL","count(*)","count(*)",value,"t.trip_date",eligible,warnings); }
    private void validate(MetricRequest r,String group) {
        if(r==null||r.tenant()==null||r.metricId()==null||r.measure()==null||r.window()==null||r.window().start()==null||r.window().end()==null
                ||r.window().start().isAfter(r.window().end())||r.dataVersion()==null||!r.dataVersion().equals(store.dataVersion())) throw new IllegalArgumentException("Invalid metric scope, period or pinned dataset version");
        if(group!=null&&!DIMENSIONS.contains(group)) throw new IllegalArgumentException("Unregistered group dimension");
        r.filters().forEach((k,v)->{if(!DIMENSIONS.contains(k)||v==null||v.isBlank()||v.length()>256)throw new IllegalArgumentException("Invalid metric filter");});
        boolean valid=switch(r.metricId()) {
            case M02_DELAYED_TRIP_DELAY -> Set.of(MetricRequest.Measure.VALUE,MetricRequest.Measure.MEAN_DELAY,MetricRequest.Measure.P90_DELAY).contains(r.measure());
            case M12_MEAN_DRIVER_SAFETY_RATING -> Set.of(MetricRequest.Measure.VALUE,MetricRequest.Measure.DRIVER_RATING,MetricRequest.Measure.SAFETY_RATING).contains(r.measure());
            case M03_DELAY_REASON_MIX -> Set.of(MetricRequest.Measure.VALUE,MetricRequest.Measure.REASON_EMPLOYEE,MetricRequest.Measure.REASON_DRIVER,MetricRequest.Measure.REASON_TRAFFIC).contains(r.measure());
            default -> r.measure()==MetricRequest.Measure.VALUE;
        };
        if(!valid) throw new IllegalArgumentException("Measure is incompatible with metric family");
    }
    private MetricEvidence unavailable(MetricRequest r,String reason) { return new MetricEvidence(id(r),r,MetricStatus.UNAVAILABLE,null,r.metricId().unit(),null,null,0,r.metricId().contractId()+"-v1.1","snapshot:"+r.dataVersion(),List.of(reason)); }
    private String id(MetricRequest r) {
        String canonical=r.tenant().businessUnit()+"|"+r.metricId()+"|"+r.measure()+"|"+r.window()+"|"+new TreeMap<>(r.filters())+"|"+r.dataVersion()+"|v1.1";
        try{return "ev-"+HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8)));}
        catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}
    }
    private record QueryKey(MetricRequest request,String group) {}
    private record Formula(String from,String numerator,String denominator,String population,String value,String dateColumn,String eligibility,List<String> warnings) {}
}
