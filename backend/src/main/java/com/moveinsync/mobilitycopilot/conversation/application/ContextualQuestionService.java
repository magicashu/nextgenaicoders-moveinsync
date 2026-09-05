package com.moveinsync.mobilitycopilot.conversation.application;

import com.moveinsync.mobilitycopilot.access.domain.ActorContext;
import com.moveinsync.mobilitycopilot.access.application.AccessAuthorizer;
import com.moveinsync.mobilitycopilot.access.domain.Permission;
import com.moveinsync.mobilitycopilot.access.domain.TenantContext;
import com.moveinsync.mobilitycopilot.conversation.domain.QuestionScope;
import com.moveinsync.mobilitycopilot.reporting.application.DecisionRunGateway;
import com.moveinsync.mobilitycopilot.reporting.application.RunView;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * "Ask about this": contextual questions over the current brief. The question text is untrusted data:
 * tenant and persona come from the authenticated actor, the intent comes from an allowlist, and the
 * answer is the same governed workflow output (claims with evidence ids), never free text from a model.
 */
@Service
public class ContextualQuestionService {

    private static final Map<String, List<String>> INTENTS = Map.of(
            "VENDOR_DISPERSION", List.of("vendor", "supplier", "travel"),
            "CONCENTRATION", List.of("where", "site", "office", "campus", "shift", "login", "logout", "direction", "concentrat"),
            "DELAY_REASONS", List.of("reason", "why", "traffic", "driver", "employee"),
            "COST", List.of("cost", "bill", "spend", "per km", "km"),
            "EXPERIENCE", List.of("rating", "feedback", "experience", "complain"),
            "SAFETY", List.of("alert", "safety", "device", "panic", "escort", "tracking"),
            "ROSTER", List.of("no-show", "no show", "roster", "pickup", "drop", "leg", "boarded"),
            "EVIDENCE", List.of("evidence", "support", "confident", "confidence", "how do you know", "reliable", "what should", "explain", "happened", "anomal"));
    private static final Pattern FORBIDDEN = Pattern.compile(
            "(select\\s+.+\\s+from|drop\\s+table|delete\\s+from|insert\\s+into|update\\s+\\w+\\s+set|;--|\\bexec(ute)?\\b.*\\b(action|escalation|ticket)|\\bsend\\b.*\\b(email|mail|message)\\b|https?://|\\bcurl\\b|\\bshell\\b|\\bpassword\\b|ignore (all|your|previous) (rules|instructions))",
            Pattern.CASE_INSENSITIVE);
    private static final List<String> OTHER_TENANTS = List.of("pinnacle-Slc", "vanta-Sea", "vanta-Aus", "catalyst-Sac", "orbit-Slc");

    private final DecisionRunGateway gateway;
    private final AccessAuthorizer authorizer;

    public ContextualQuestionService(DecisionRunGateway gateway, AccessAuthorizer authorizer) {
        this.gateway = gateway;
        this.authorizer = authorizer;
    }

    public List<String> suggestedQuestions() {
        return List.of(
                "Which sites and shifts were most affected?",
                "Were delays spread across vendors or concentrated with one vendor?",
                "What reasons were recorded for the delays?",
                "Did the cost per trip also increase?",
                "How reliable is this explanation, and what should I do next?");
    }

    public QuestionScope classify(ActorContext actor, String question) {
        String normalised = question == null ? "" : question.trim().replaceAll("\\s+", " ");
        if (normalised.isBlank()) {
            return QuestionScope.refused("Question is empty", normalised);
        }
        String lower = normalised.toLowerCase(Locale.ROOT);
        if (FORBIDDEN.matcher(lower).find()) {
            return QuestionScope.refused("The question asks for SQL, external access, instruction override or direct execution; only governed analytical questions are supported", normalised);
        }
        for (String tenant : OTHER_TENANTS) {
            if (!tenant.equals(actor.businessUnit()) && lower.contains(tenant.toLowerCase(Locale.ROOT))) {
                return QuestionScope.refused("Cross-tenant comparison is not available to this identity", normalised);
            }
        }
        List<String> intents = new ArrayList<>();
        for (var entry : INTENTS.entrySet()) {
            if (entry.getValue().stream().anyMatch(lower::contains)) {
                intents.add(entry.getKey());
            }
        }
        if (intents.isEmpty()) {
            return QuestionScope.refused("The question does not map to a supported analytical intent; try one of the suggested questions", normalised);
        }
        List<String> workers = intents.stream().map(ContextualQuestionService::workerFor).filter(Optional::isPresent).map(Optional::get).distinct().toList();
        return new QuestionScope(String.join("+", intents), workers, false, null, normalised);
    }

    public Optional<RunView> answer(ActorContext actor, TenantContext tenant, LocalDate asOfDate, String persona, QuestionScope scope, UUID relatedRunId) {
        if (scope.refused()) {
            return Optional.empty();
        }
        authorizer.require(actor, tenant, Permission.READ_TENANT_METRICS);
        if (relatedRunId != null) {
            var captured = gateway.find(actor, relatedRunId);
            if (captured.isPresent()) {
                var run = captured.get();
                boolean sameScope = run.businessUnit().equals(tenant.businessUnit())
                        && run.brief().asOfDate().equals(asOfDate) && run.persona().equals(persona);
                boolean covered = scope.workers().stream().allMatch(worker ->
                        run.claims().stream().anyMatch(claim -> worker.equals(claim.worker()) && !"CAVEAT".equals(claim.kind())));
                if (sameScope && (covered || "LINE_MANAGER".equals(persona)) && !"FAILED".equals(run.finalStep())) return captured;
            }
        }
        if ("LINE_MANAGER".equals(persona)) {
            return Optional.of(gateway.morningBrief(actor, tenant, asOfDate, persona));
        }
        return Optional.of(gateway.ask(actor, tenant, asOfDate, persona, scope.normalisedQuestion(), relatedRunId));
    }

    public List<String> followUps(QuestionScope scope) {
        return suggestedQuestions().stream().filter(q -> !q.toLowerCase(Locale.ROOT).contains(scope.intent().toLowerCase(Locale.ROOT).split("\\+")[0].split("_")[0].toLowerCase(Locale.ROOT))).limit(3).toList();
    }

    static Optional<String> workerFor(String intent) {
        return Optional.ofNullable(switch (intent) {
            case "VENDOR_DISPERSION" -> "vendor";
            case "CONCENTRATION" -> "site_shift_direction";
            case "DELAY_REASONS" -> "delay_reason";
            case "COST" -> "cost_billing";
            case "EXPERIENCE" -> "feedback";
            case "SAFETY" -> "tracking_safety_alerts";
            case "ROSTER" -> "noshow_roster";
            default -> null;
        });
    }
}
