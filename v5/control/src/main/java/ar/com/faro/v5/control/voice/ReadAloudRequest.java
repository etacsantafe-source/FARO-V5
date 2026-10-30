package ar.com.faro.v5.control.voice;

import java.util.Locale;
import java.util.Objects;

public final class ReadAloudRequest {
    public static final int MAX_TEXT_CHARS = 12_000;

    private final String text;
    private final String languageTag;

    public ReadAloudRequest(String text, String languageTag) {
        Objects.requireNonNull(text, "text");
        String normalized = text.trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException("text must not be blank");
        if (normalized.length() > MAX_TEXT_CHARS) {
            throw new IllegalArgumentException("text exceeds " + MAX_TEXT_CHARS + " chars");
        }
        if (hasControlCharactersExceptWhitespace(normalized)) {
            throw new IllegalArgumentException("text contains unsupported control characters");
        }
        this.text = normalized;
        this.languageTag = normalizeLanguageTag(languageTag);
    }

    public String text() { return text; }
    public String languageTag() { return languageTag; }

    private static String normalizeLanguageTag(String value) {
        if (value == null || value.trim().isEmpty()) return "";
        String normalized = value.trim();
        if (normalized.length() > 64) throw new IllegalArgumentException("languageTag is invalid");
        Locale locale = Locale.forLanguageTag(normalized);
        if (locale.getLanguage().isEmpty()) throw new IllegalArgumentException("languageTag is invalid");
        return locale.toLanguageTag();
    }

    private static boolean hasControlCharactersExceptWhitespace(String value) {
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if ((c < 0x20 && c != '\n' && c != '\r' && c != '\t') || c == 0x7f) return true;
        }
        return false;
    }
}
