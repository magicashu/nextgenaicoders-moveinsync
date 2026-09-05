package com.moveinsync.mobilitycopilot.evidence.application;

import com.moveinsync.mobilitycopilot.evidence.domain.*;
import java.util.*;

/** Canonical public wording is part of the deterministic claim contract. */
public final class MetricClaimText {
    private MetricClaimText(){}
    public static String direct(MetricEvidence e) {
        var r=e.request();
        return r.metricId().contractId()+" "+r.measure()+" = "+e.value().stripTrailingZeros().toPlainString()+" "+e.unit()
                +"; tenant="+r.tenant().businessUnit()+"; period="+r.window().start()+"/"+r.window().end()
                +"; filters="+new TreeMap<>(r.filters())+"; eligible population="+e.population()+".";
    }
    public static List<Claim> candidates(List<MetricEvidence> evidence) {
        List<Claim> claims=new ArrayList<>();
        for(var e:evidence) if(e!=null&&e.value()!=null&&e.status()!=com.moveinsync.mobilitycopilot.metrics.domain.MetricStatus.UNAVAILABLE)
            claims.add(new Claim("claim-"+e.evidenceId(),direct(e),Set.of(e.evidenceId()),VerifiedClaim.Kind.DIRECT));
        // Compare only identical dimensions and statistics across non-overlapping periods.
        for (int i=0;i<evidence.size();i++) {
            for (int j=i+1;j<evidence.size();j++) {
                var a=evidence.get(i);
                var b=evidence.get(j);
                if (!comparable(a,b)) continue;
                var current=a.request().window().start().isAfter(b.request().window().end())?a:b;
                var baseline=current==a?b:a;
                claims.add(new Claim("comparison-"+current.evidenceId()+"-"+baseline.evidenceId(),
                        comparison(current,baseline),Set.of(current.evidenceId(),baseline.evidenceId()),VerifiedClaim.Kind.DIRECT));
            }
        }
        return List.copyOf(claims);
    }

    public static boolean comparable(MetricEvidence a,MetricEvidence b) {
        if(a==null||b==null||a.value()==null||b.value()==null||a.request()==null||b.request()==null
                ||a.status()==com.moveinsync.mobilitycopilot.metrics.domain.MetricStatus.UNAVAILABLE
                ||b.status()==com.moveinsync.mobilitycopilot.metrics.domain.MetricStatus.UNAVAILABLE) return false;
        var x=a.request();var y=b.request();
        return x.tenant().equals(y.tenant())&&x.dataVersion().equals(y.dataVersion())
                &&x.metricId()==y.metricId()&&x.measure()==y.measure()&&x.filters().equals(y.filters())
                &&a.unit()==b.unit()&&Objects.equals(a.metricVersion(),b.metricVersion())
                &&(x.window().start().isAfter(y.window().end())||y.window().start().isAfter(x.window().end()));
    }

    public static String comparison(MetricEvidence current,MetricEvidence baseline) {
        if(!comparable(current,baseline)||!current.request().window().start().isAfter(baseline.request().window().end()))
            throw new IllegalArgumentException("Comparison requires matching scope and disjoint ordered windows");
        String unit=current.unit()==com.moveinsync.mobilitycopilot.metrics.domain.MetricUnit.PERCENT
                ?"percentage points":current.unit().toString();
        return direct(current)+" Compared with "+direct(baseline)+" Change = "
                +current.value().subtract(baseline.value()).stripTrailingZeros().toPlainString()+" "+unit
                +". Descriptive comparison; no causal attribution.";
    }

    public static boolean matches(String text,List<MetricEvidence> cited) {
        if(cited.size()==1) return text.equals(direct(cited.getFirst()));
        if(cited.size()!=2||!comparable(cited.get(0),cited.get(1))) return false;
        var a=cited.get(0);var b=cited.get(1);
        return text.equals(a.request().window().start().isAfter(b.request().window().end())
                ?comparison(a,b):comparison(b,a));
    }
}
