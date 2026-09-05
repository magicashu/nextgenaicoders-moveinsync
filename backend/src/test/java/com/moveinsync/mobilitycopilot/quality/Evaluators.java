package com.moveinsync.mobilitycopilot.quality;

import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deterministic evaluators over run artifacts (JSON produced by the API or the workflow). They decide
 * schema shape, evidence support, tenant scope, state transitions and idempotency without any model.
 * The same code runs in CI against fixtures and, through scripts/demo/scorecard.sh, against live output.
 */
public final class Evaluators {

    private static final Pattern NUMBER = Pattern.compile("(?<![\\w-])(\\d{1,3}(?:,\\d{3})+|\\d+)(?:\\.(\\d+))?");
    private static final Pattern CAUSAL = Pattern.compile("\\b(caused|causes|because of|due to|responsible for|to blame|sole driver)\\b", Pattern.CASE_INSENSITIVE);
    private static final Set<String> IGNORED = Set.of("1", "2", "3", "4", "5", "7", "10", "28", "300", "500", "600", "1000", "1440", "2026");
    private static final List<String> TENANTS = List.of("pinnacle-Slc", "vanta-Sea", "vanta-Aus", "catalyst-Sac", "orbit-Slc");
    private static final List<String> ORDER = List.of("INITIALIZE_RUN", "AUTHORIZE_SCOPE", "PROFILE_DATASET", "BUILD_CAPABILITY_MATRIX", "COMPUTE_METRIC_SNAPSHOT",
            "DETECT_ANOMALIES", "PRIORITIZE_ISSUE", "SUPERVISOR_PLAN", "VALIDATE_PLAN", "RUN_INVESTIGATIONS", "MERGE_EVIDENCE", "EVIDENCE_CRITIC", "VERIFY_EVIDENCE",
            "COMPOSE_DECISION_BRIEF", "ACTION_POLICY_GATE", "APPROVAL_INTERRUPT", "REVALIDATE_AND_EXECUTE", "APPEND_AUDIT_EVENT");

    private Evaluators() {
    }

    public record Finding(String evaluator, String code, String detail) {
    }

    /** Every evidence item and the brief carry the required fields and the frozen contract version. */
    public static List<Finding> schema(JsonNode artifact) {
        List<Finding> findings = new ArrayList<>();
        for (String field : List.of("runId", "traceId", "businessUnit", "finalStep", "evidence", "claims")) {
            if (artifact.path(field).isMissingNode()) {
                findings.add(new Finding("schema", "MISSING_FIELD", field));
            }
        }
        for (JsonNode item : artifact.path("evidence").path("items")) {
            for (String field : List.of("evidenceId", "metricId", "value", "unit", "supportingCount", "periodStart", "periodEnd", "filters", "source", "contractVersion", "dataVersion")) {
                if (item.path(field).isMissingNode()) {
                    findings.add(new Finding("schema", "MISSING_EVIDENCE_FIELD", item.path("evidenceId").asText() + "." + field));
                }
            }
            if (!"metrics-v1.1".equals(item.path("contractVersion").asText())) {
                findings.add(new Finding("schema", "CONTRACT_VERSION", item.path("evidenceId").asText() + " has " + item.path("contractVersion").asText()));
            }
        }
        return findings;
    }

    /** Every number displayed in a claim or narrative resolves to cited evidence; no causal language; no uncited claims. */
    public static List<Finding> evidenceSupport(JsonNode artifact) {
        List<Finding> findings = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        Set<String> numbers = new HashSet<>();
        for (JsonNode item : artifact.path("evidence").path("items")) {
            ids.add(item.path("evidenceId").asText());
            for (String field : List.of("value", "baselineValue", "delta", "numerator", "denominator", "supportingCount")) {
                JsonNode v = item.path(field);
                if (v.isNumber()) {
                    addForms(numbers, new BigDecimal(v.asText()));
                }
            }
        }
        JsonNode confidence = artifact.path("evidence").path("confidence");
        if (confidence.isNumber()) {
            addForms(numbers, new BigDecimal(confidence.asText()));
        }
        for (JsonNode claim : artifact.path("claims")) {
            String text = claim.path("text").asText();
            String kind = claim.path("kind").asText();
            if ("CAVEAT".equals(kind)) {
                continue;
            }
            if (claim.path("evidenceIds").isEmpty()) {
                findings.add(new Finding("evidence", "UNCITED_CLAIM", claim.path("claimId").asText()));
            }
            for (JsonNode id : claim.path("evidenceIds")) {
                if (!ids.contains(id.asText())) {
                    findings.add(new Finding("evidence", "UNKNOWN_EVIDENCE", id.asText()));
                }
            }
            checkNumbers("claim " + claim.path("claimId").asText(), text, numbers, findings);
            if (CAUSAL.matcher(text).find()) {
                findings.add(new Finding("evidence", "CAUSAL_LANGUAGE", claim.path("claimId").asText()));
            }
        }
        for (JsonNode line : artifact.path("leadershipNarrative")) {
            checkNumbers("leadership", line.asText(), numbers, findings);
        }
        return findings;
    }

    /** No evidence, claim or action may reference another tenant; every evidence id must start with the run's tenant or a permitted prefix. */
    public static List<Finding> tenantScope(JsonNode artifact) {
        List<Finding> findings = new ArrayList<>();
        String tenant = artifact.path("businessUnit").asText();
        for (JsonNode item : artifact.path("evidence").path("items")) {
            String id = item.path("evidenceId").asText();
            if (!id.startsWith(tenant + ":") && !id.startsWith("peer:")) {
                findings.add(new Finding("tenant", "FOREIGN_EVIDENCE", id));
            }
            for (String other : TENANTS) {
                if (!other.equals(tenant) && item.path("filters").toString().contains(other) && !id.startsWith("peer:")) {
                    findings.add(new Finding("tenant", "FOREIGN_FILTER", id));
                }
            }
        }
        JsonNode scope = artifact.path("action").path("scope").path("businessUnit");
        if (!scope.isMissingNode() && !scope.asText().equals(tenant)) {
            findings.add(new Finding("tenant", "FOREIGN_ACTION_SCOPE", scope.asText()));
        }
        for (JsonNode claim : artifact.path("claims")) {
            for (String other : TENANTS) {
                if (!other.equals(tenant) && claim.path("text").asText().contains(other) && !claim.path("text").asText().startsWith("Peer tenants")) {
                    findings.add(new Finding("tenant", "FOREIGN_CLAIM", claim.path("claimId").asText()));
                }
            }
        }
        return findings;
    }

    /** Node order, bounded loops, and no execution without an approval interrupt and a decision. */
    public static List<Finding> transitions(JsonNode artifact) {
        List<Finding> findings = new ArrayList<>();
        List<String> nodes = new ArrayList<>();
        artifact.path("transitions").forEach(n -> nodes.add(n.asText()));
        int last = -1;
        for (String node : nodes) {
            int index = ORDER.indexOf(node);
            if (index < 0) {
                findings.add(new Finding("transitions", "UNKNOWN_NODE", node));
                continue;
            }
            if (index < last && !(node.equals("SUPERVISOR_PLAN") && ORDER.get(last).equals("VERIFY_EVIDENCE"))) {
                findings.add(new Finding("transitions", "OUT_OF_ORDER", node + " after " + ORDER.get(last)));
            }
            last = Math.max(last, index);
        }
        boolean executed = nodes.contains("REVALIDATE_AND_EXECUTE") || "EXECUTED".equals(artifact.path("finalStep").asText());
        boolean approvalRaised = nodes.contains("APPROVAL_INTERRUPT") || (!artifact.path("approval").isNull() && !artifact.path("approval").isMissingNode());
        if (executed && !approvalRaised) {
            findings.add(new Finding("transitions", "EXECUTION_WITHOUT_APPROVAL", "REVALIDATE_AND_EXECUTE reached without APPROVAL_INTERRUPT"));
        }
        if (nodes.contains("RUN_INVESTIGATIONS") && !nodes.contains("VALIDATE_PLAN")) {
            findings.add(new Finding("transitions", "UNVALIDATED_PLAN", "investigations ran without VALIDATE_PLAN"));
        }
        if (nodes.contains("COMPOSE_DECISION_BRIEF") && !nodes.contains("VERIFY_EVIDENCE")) {
            findings.add(new Finding("transitions", "UNVERIFIED_BRIEF", "brief composed without VERIFY_EVIDENCE"));
        }
        if (artifact.path("toolCalls").asInt() > artifact.path("maxToolCalls").asInt(12)) {
            findings.add(new Finding("transitions", "TOOL_BUDGET_EXCEEDED", artifact.path("toolCalls").asText()));
        }
        if (artifact.path("correctionCycles").asInt() > artifact.path("maxCorrectionCycles").asInt(1)) {
            findings.add(new Finding("transitions", "CORRECTION_BUDGET_EXCEEDED", artifact.path("correctionCycles").asText()));
        }
        return findings;
    }

    /** At most one EXECUTED receipt per idempotency key, all identical. */
    public static List<Finding> idempotency(JsonNode artifact) {
        List<Finding> findings = new ArrayList<>();
        Set<String> keys = new HashSet<>();
        Set<String> references = new HashSet<>();
        for (JsonNode receipt : artifact.path("receipts")) {
            if ("EXECUTED".equals(receipt.path("status").asText())) {
                String key = receipt.path("idempotencyKey").asText();
                if (!keys.add(key)) {
                    if (!references.contains(receipt.path("externalReference").asText())) {
                        findings.add(new Finding("idempotency", "DUPLICATE_EFFECT", key));
                    }
                }
                references.add(receipt.path("externalReference").asText());
            }
        }
        if ("EXECUTED".equals(artifact.path("finalStep").asText()) && artifact.path("receipts").isEmpty()) {
            findings.add(new Finding("idempotency", "EXECUTED_WITHOUT_RECEIPT", "finalStep EXECUTED but no receipt"));
        }
        return findings;
    }

    private static void checkNumbers(String where, String text, Set<String> allowed, List<Finding> findings) {
        Matcher m = NUMBER.matcher(text);
        while (m.find()) {
            String token = m.group();
            String plain = token.replace(",", "");
            if (IGNORED.contains(token) || IGNORED.contains(plain) || isDateOrTime(text, m.start())) {
                continue;
            }
            if (!allowed.contains(plain) && !allowed.contains(strip(plain))) {
                findings.add(new Finding("evidence", "UNSUPPORTED_NUMBER", token + " in " + where));
            }
        }
    }

    private static boolean isDateOrTime(String text, int position) {
        String before = text.substring(Math.max(0, position - 8), position);
        String after = text.substring(position, Math.min(text.length(), position + 12));
        return after.matches("\\d{4}-\\d{2}-\\d{2}.*") || before.matches(".*\\d{4}-$") || before.matches(".*\\d{4}-\\d{2}-$") || after.matches("\\d{2}:\\d{2}.*") || before.matches(".*\\d{2}:$");
    }

    private static void addForms(Set<String> allowed, BigDecimal value) {
        BigDecimal abs = value.abs();
        allowed.add(abs.stripTrailingZeros().toPlainString());
        allowed.add(abs.toPlainString());
        allowed.add(abs.setScale(0, RoundingMode.HALF_UP).toPlainString());
        allowed.add(abs.setScale(1, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString());
        allowed.add(abs.setScale(2, RoundingMode.HALF_UP).toPlainString());
        if (abs.compareTo(BigDecimal.valueOf(1000)) >= 0) {
            allowed.add(abs.setScale(-2, RoundingMode.HALF_UP).toPlainString());
        }
    }

    private static String strip(String number) {
        try {
            return new BigDecimal(number).stripTrailingZeros().toPlainString();
        } catch (NumberFormatException e) {
            return number.toLowerCase(Locale.ROOT);
        }
    }
}
