package com.stagepass.ticketing.service;

import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.text.NumberFormat;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Text helpers for PDFs set in built-in Helvetica (WinAnsi): IST dates, rupee amounts, safe strings. */
public final class PdfText {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("EEE, d MMM yyyy · h:mm a", Locale.ENGLISH);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);
    private static final Charset WIN_ANSI = Charset.forName("windows-1252");

    private PdfText() {
    }

    /** {@code 105104} → {@code "Rs. 1,051.04"}; {@code 0} → {@code "Free"}. */
    public static String money(long paise) {
        if (paise == 0) {
            return "Free";
        }
        return "Rs. " + amount(paise);
    }

    /** Always an amount, even for zero: {@code 0} → {@code "Rs. 0.00"}. */
    public static String rupees(long paise) {
        return "Rs. " + amount(paise);
    }

    private static String amount(long paise) {
        NumberFormat f = NumberFormat.getNumberInstance(new Locale("en", "IN"));
        f.setMinimumFractionDigits(2);
        f.setMaximumFractionDigits(2);
        return f.format(paise / 100.0);
    }

    /** {@code "Sat, 14 Nov 2026 · 6:30 PM"} in IST. */
    public static String dateTime(OffsetDateTime t) {
        return DATE_TIME.format(t.atZoneSameInstant(IST));
    }

    /** {@code "14 Nov 2026"} in IST. */
    public static String date(OffsetDateTime t) {
        return DATE.format(t.atZoneSameInstant(IST));
    }

    /** Drops characters Helvetica can't draw (other scripts, emoji) and collapses the gaps they leave. */
    public static String safe(String s) {
        if (s == null) {
            return "";
        }
        CharsetEncoder encoder = WIN_ANSI.newEncoder();
        StringBuilder out = new StringBuilder(s.length());
        s.codePoints().forEach(cp -> {
            String ch = new String(Character.toChars(cp));
            out.append(cp < 0x10000 && encoder.canEncode(ch) ? ch : " ");
        });
        return out.toString().replaceAll("[ \\t]+", " ").replaceAll(" *\\n *", "\n").trim();
    }
}
