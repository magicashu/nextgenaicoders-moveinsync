package com.moveinsync.mobilitycopilot.conversation.application;

import java.util.Locale;

/** Extracts optional presentation tone without granting instructions any workflow authority. */
public final class ResponseTonePolicy {
    private ResponseTonePolicy() {}

    public enum Tone { STANDARD, ROYAL }

    public static Tone resolve(String input) {
        if (input == null) return Tone.STANDARD;
        String normalized = input.toLowerCase(Locale.ROOT);
        return normalized.contains("royal briefing")
                || normalized.contains("speak like a king")
                || normalized.contains("address me as sire")
                || normalized.contains("call me my lord")
                ? Tone.ROYAL : Tone.STANDARD;
    }

    public static String modelDirective(String input) {
        return switch (resolve(input)) {
            case ROYAL -> "Use respectful royal wording only in explanatory prose, such as 'Sire' or 'My lord'. "
                    + "Keep JSON, metric values, caveats, evidence, scope, approvals and actions unchanged.";
            case STANDARD -> "Use concise professional wording.";
        };
    }
}