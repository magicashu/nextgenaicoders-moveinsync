package com.moveinsync.mobilitycopilot.conversation.application;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/** Cheap, deterministic input checks before any analytical or model work. */
public final class QuestionGuardrails {
    public static final String UNSUPPORTED = "I can help with transport performance for your selected business unit. I don't support that topic. Try asking about delays, pickups, trip costs or safety alerts.";
    private static final Pattern INTERNAL = Pattern.compile("(?i)\\b(agent[s]?|supervisor|investigator|critic|langgraph|langfuse|sarvam|llm|node[s]?|pipeline|backend|architecture|system prompt|instructions?|api.?key|secret[s]?|password|token[s]?|credentials?|sql|duckdb|postgres|curl|shell|jailbreak|base64|decode|encode|developer message)\\b|https?://|<[^>]+>|\\b(ignore|override|bypass|reveal|disclose|pretend|roleplay)\\b");
    private static final Pattern UNRELATED = Pattern.compile("(?i)\\b(weather|recipe[s]?|cook|poem[s]?|joke[s]?|cricket|football|bitcoin|crypto|stock[s]?|president|politic[s]?|movie[s]?|song[s]?|horoscope|capital of|bill gates|device driver|graphics driver|operating system)\\b");
    private static final Pattern TOPIC = Pattern.compile("(?i)\\b(trips?|transport|commut\\w*|delays?|delayed|punctual\\w*|vendors?|suppliers?|sites?|offices?|campus|shifts?|login|logout|traffic|drivers?|employees?|costs?|bills?|billing|spend|ratings?|feedback|complaints?|alerts?|safety|panic|escort|tracking|rosters?|pickups?|drop[s]?|boarded|no[ -]?shows?|occupancy|ev share|evidence|anomal\\w*)\\b");
    private QuestionGuardrails() {}

    public static String normalize(String text) {
        return Normalizer.normalize(text == null ? "" : text, Normalizer.Form.NFKC)
                .replaceAll("\\p{Cf}", "").replaceAll("\\s+", " ").trim();
    }

    public static Optional<String> reply(String input) {
        String text = normalize(input).toLowerCase(Locale.ROOT);
        if (text.isBlank()) return Optional.of("Please enter a transport question, such as: Why were trips delayed?");
        if (text.matches("(hi|hello|hey|good morning|good afternoon)[!?. ]*"))
            return Optional.of("Hello! I can help you understand delays, pickups, trip costs and safety alerts for your selected business unit.");
        if (text.matches("(thanks|thank you|thank you very much)[!?. ]*"))
            return Optional.of("You're welcome. Let me know if you have another question about your transport report.");
        if (INTERNAL.matcher(text).find() || UNRELATED.matcher(text).find()
                || text.matches(".*(.)\\1{7,}.*") || !text.matches(".*[a-z].*"))
            return Optional.of(UNSUPPORTED);
        boolean followUp = text.matches("(what (should|can) i do next|what happened|how reliable is (this|this explanation)|explain (this|the report))[?!. ]*")
                || text.equals("how reliable is this explanation, and what should i do next?");
        if (!TOPIC.matcher(text).find() && !followUp) return Optional.of(UNSUPPORTED);
        return Optional.empty();
    }
}
