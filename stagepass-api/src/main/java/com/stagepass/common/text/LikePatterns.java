package com.stagepass.common.text;

import java.util.Locale;

/** Safe {@code ILIKE} patterns from user input: {@code %}, {@code _} and {@code \} are matched literally. */
public final class LikePatterns {

    private LikePatterns() {
    }

    public static String escape(String s) {
        return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    /** {@code "%" + escaped lower-case text + "%"}. */
    public static String contains(String q) {
        return "%" + escape(q.strip().toLowerCase(Locale.ROOT)) + "%";
    }
}
