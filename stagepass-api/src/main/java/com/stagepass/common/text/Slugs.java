package com.stagepass.common.text;

import java.security.SecureRandom;
import java.text.Normalizer;
import java.util.Locale;

/** URL slugs for events and organizers. */
public final class Slugs {

    private static final int MAX_LENGTH = 60;
    private static final String SUFFIX_ALPHABET = "abcdefghijklmnopqrstuvwxyz0123456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private Slugs() {
    }

    public static String slugify(String title) {
        if (title == null) {
            return "event";
        }
        String ascii = Normalizer.normalize(title, Normalizer.Form.NFKD).replaceAll("\\p{M}+", "");
        String slug = ascii.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
        if (slug.length() > MAX_LENGTH) {
            slug = slug.substring(0, MAX_LENGTH).replaceAll("-+$", "");
        }
        return slug.isEmpty() ? "event" : slug;
    }

    /** {@code base} plus a random 6-character suffix, so two events with the same title never clash. */
    public static String unique(String base) {
        StringBuilder sb = new StringBuilder(base).append('-');
        for (int i = 0; i < 6; i++) {
            sb.append(SUFFIX_ALPHABET.charAt(RANDOM.nextInt(SUFFIX_ALPHABET.length())));
        }
        return sb.toString();
    }
}
