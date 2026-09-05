package com.moveinsync.mobilitycopilot.conversation.application;

import com.moveinsync.mobilitycopilot.api.dto.ApiDtos;
import com.moveinsync.mobilitycopilot.reporting.application.RunView;
import java.util.List;

/** Plain-language presentation of verified statements; no additional metric calculation or model call. */
public final class QuestionExplanation {
    private QuestionExplanation() {}

    public static String plain(String text) {
        return text.replace("delayed-trip rate", "share of late trips")
                .replace("Delayed-trip rate", "The share of late trips")
                .replace("prior four complete weeks", "previous four weeks")
                .replace("prior four weeks", "previous four weeks")
                .replace("rider legs", "passenger trip legs")
                .replace("attributable to", "explained by")
                .replace("deteriorate", "get worse")
                .replace("Leg-level on-time pickups", "On-time passenger pickups")
                .replace("Median billed cost per trip", "The middle billed cost per trip")
                .replace(";", ".");
    }

    public static String explain(RunView run, List<ApiDtos.Finding> findings, boolean causalQuestion) {
        StringBuilder answer = new StringBuilder("For ").append(run.businessUnit()).append(" from ")
                .append(run.brief().metric().periodStart()).append(" to ").append(run.brief().metric().periodEnd()).append(":\n\n");
        if (findings.isEmpty()) answer.append(plain(run.brief().headline()));
        else answer.append(String.join("\n\n", findings.stream().limit(3).map(f -> plain(f.text())).toList()));
        if (causalQuestion) answer.append("\n\nThese records help explain the pattern. They do not prove that any one factor caused it.");
        return answer.toString();
    }
}
