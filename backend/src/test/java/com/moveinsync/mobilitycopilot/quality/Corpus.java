package com.moveinsync.mobilitycopilot.quality;

import java.nio.file.Files;
import java.nio.file.Path;

/** Locates repository files from the backend module or the repository root. */
public final class Corpus {

    private Corpus() {
    }

    public static Path path(String relative) {
        for (String prefix : new String[] {"", "../"}) {
            Path candidate = Path.of(prefix + relative);
            if (Files.exists(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Missing repository file " + relative);
    }

    public static boolean exists(String relative) {
        try {
            path(relative);
            return true;
        } catch (IllegalStateException e) {
            return false;
        }
    }
}
