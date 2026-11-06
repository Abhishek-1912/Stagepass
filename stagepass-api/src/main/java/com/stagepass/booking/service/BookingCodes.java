package com.stagepass.booking.service;

import java.security.SecureRandom;

/** Human-friendly codes without look-alike characters (no 0/O, 1/I/L). */
public final class BookingCodes {

    static final String ALPHABET = "23456789ABCDEFGHJKMNPQRSTUVWXYZ";
    private static final SecureRandom RANDOM = new SecureRandom();

    private BookingCodes() {
    }

    /** Booking code, e.g. {@code SP-7KQ2MX}. Callers check uniqueness. */
    public static String booking() {
        return "SP-" + random(6);
    }

    /** Ticket code, e.g. {@code SP-7KQ2-MXA9}. Callers check uniqueness. */
    public static String ticket() {
        return "SP-" + random(4) + "-" + random(4);
    }

    /** Unguessable per-ticket nonce for the QR payload (Plan 5). */
    public static String nonce() {
        return random(24);
    }

    private static String random(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }
}
