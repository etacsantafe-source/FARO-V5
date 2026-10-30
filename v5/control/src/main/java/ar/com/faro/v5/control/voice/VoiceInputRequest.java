package ar.com.faro.v5.control.voice;

import java.util.Locale;
import java.util.Objects;

public final class VoiceInputRequest {
    public static final int MAX_PROMPT_CHARS = 120;
    public static final int MAX_TRANSCRIPT_CHARS = 12_000;

    private final String prompt;
    private final String languageTag;
    private final int maxTranscriptChars;

    public VoiceInputRequest(String prompt, String languageTag, int maxTranscriptChars) {
        this.prompt = boundedOptional("prompt", prompt, MAX_PROMPT_CHARS);
        this.languageTag = normalizeLanguageTag(languageTag);
        if (maxTranscriptChars <= 0 || maxTranscriptChars > MAX_TRANSCRIPT_CHARS) {
            throw new IllegalArgumentException("maxTranscriptChars must be 1.." + MAX_TRANSCRIPT_CHARS);
        }
        this.maxTranscriptChars = maxTranscriptChars;
    }

    public static VoiceInputRequest defaultRequest() {
        return new VoiceInputRequest("Hablar con FARO", null, MAX_TRANSCRIPT_CHARS);
    }

    public String prompt() { return prompt; }
    public String languageTag() { return languageTag; }
    public int maxTranscriptChars() { return maxTranscriptChars; }

    private static String boundedOptional(String field, String value, int maxChars) {
        if (value == null) return "";
        String normalized = value.trim();
        if (normalized.length() > maxChars || hasControlCharacters(normalized)) {
            throw new IllegalArgumentException(field + " is invalid or exceeds " + maxChars + " chars");
        }
        return normalized;
    }

    private static String normalizeLanguageTag(String value) {
        if (value == null || value.trim().isEmpty()) return "";
        String normalized = value.trim();
        if (normalized.length() > 64 || hasControlCharacters(normalized)) {
            throw new IllegalArgumentException("languageTag is invalid");
        }
        Locale locale = Locale.forLanguageTag(normalized);
        if (locale.getLanguage().isEmpty()) {
            throw new IllegalArgumentException("languageTag is invalid");
        }
        return locale.toLanguageTag();
    }

    private static boolean hasControlCharacters(String value) {
        Objects.requireNonNull(value, "value");
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c < 0x20 || c == 0x7f) return true;
        }
        return false;
    }
}
