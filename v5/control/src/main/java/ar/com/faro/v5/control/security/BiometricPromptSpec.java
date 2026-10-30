package ar.com.faro.v5.control.security;

import java.util.Objects;

public final class BiometricPromptSpec {
    public static final int MAX_TITLE_CHARS = 80;
    public static final int MAX_SUBTITLE_CHARS = 160;
    public static final int MAX_NEGATIVE_ACTION_CHARS = 40;

    private final String title;
    private final String subtitle;
    private final String negativeActionLabel;

    public BiometricPromptSpec(String title, String subtitle, String negativeActionLabel) {
        this.title = requireBounded("title", title, MAX_TITLE_CHARS, false);
        this.subtitle = requireBounded("subtitle", subtitle, MAX_SUBTITLE_CHARS, true);
        this.negativeActionLabel = requireBounded("negativeActionLabel", negativeActionLabel, MAX_NEGATIVE_ACTION_CHARS, false);
    }

    public String title() { return title; }
    public String subtitle() { return subtitle; }
    public String negativeActionLabel() { return negativeActionLabel; }

    private static String requireBounded(String field, String value, int maxChars, boolean allowEmpty) {
        Objects.requireNonNull(value, field);
        String normalized = value.trim();
        if (!allowEmpty && normalized.isEmpty()) throw new IllegalArgumentException(field + " must not be blank");
        if (normalized.length() > maxChars) throw new IllegalArgumentException(field + " exceeds " + maxChars + " chars");
        return normalized;
    }
}
