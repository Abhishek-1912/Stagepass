package com.stagepass.common.validation;

/** Indian identifier formats used in Bean Validation {@code @Pattern}s. */
public final class Formats {

    public static final String PAN = "^[A-Z]{5}[0-9]{4}[A-Z]$";
    public static final String GSTIN = "^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z][1-9A-Z]Z[0-9A-Z]$";
    public static final String IFSC = "^[A-Z]{4}0[A-Z0-9]{6}$";
    public static final String BANK_ACCOUNT = "^[0-9]{9,18}$";
    public static final String PINCODE = "^[1-9][0-9]{5}$";
    public static final String MOBILE = "^[6-9]\\d{9}$";

    private Formats() {
    }

    /** Trimmed, upper-cased value, or null when blank. */
    public static String upper(String value) {
        return value == null || value.isBlank() ? null : value.trim().toUpperCase(java.util.Locale.ROOT);
    }

    /** Trimmed value, or null when blank. */
    public static String trim(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
