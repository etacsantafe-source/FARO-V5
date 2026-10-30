package ar.com.faro.v5.control.ingress;

final class IngressBounds {
    static final int MAX_TARGET_CHARS = 64;
    static final int MAX_QUICK_ID_CHARS = 64;
    static final int MAX_TEXT_CHARS = 12_000;
    static final int MAX_REFERENCE_CHARS = 256;
    static final int MAX_ATTACHMENTS = 4;
    static final int MAX_SHORT_LABEL_CHARS = 40;
    static final int MAX_LONG_LABEL_CHARS = 80;

    private IngressBounds() {}

    static String target(String value) {
        String normalized = required(value, "target");
        if (normalized.length() > MAX_TARGET_CHARS
                || !normalized.matches("[a-z][a-z0-9._-]{0,63}")) {
            throw new IllegalArgumentException("invalid ingress target");
        }
        return normalized;
    }

    static String quickId(String value) {
        String normalized = required(value, "quickId");
        if (normalized.length() > MAX_QUICK_ID_CHARS
                || !normalized.matches("[a-z0-9][a-z0-9._-]{0,63}")) {
            throw new IllegalArgumentException("invalid quick id");
        }
        return normalized;
    }

    static String optionalText(String value) {
        if (value == null) return "";
        String normalized = value.trim();
        if (normalized.indexOf('\u0000') >= 0 || normalized.length() > MAX_TEXT_CHARS) {
            throw new IllegalArgumentException("invalid ingress text");
        }
        return normalized;
    }

    static String optionalReference(String value) {
        if (value == null) return "";
        String normalized = value.trim();
        if (normalized.isEmpty()) return "";
        if (normalized.length() > MAX_REFERENCE_CHARS
                || !normalized.matches("[A-Za-z0-9._:@-]{1,256}")) {
            throw new IllegalArgumentException("invalid ingress reference");
        }
        return normalized;
    }

    static String label(String value, String name, int maxChars, boolean optional) {
        if (value == null) {
            if (optional) return "";
            throw new NullPointerException(name);
        }
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            if (optional) return "";
            throw new IllegalArgumentException(name + " is empty");
        }
        if (normalized.indexOf('\u0000') >= 0 || normalized.length() > maxChars) {
            throw new IllegalArgumentException("invalid " + name);
        }
        return normalized;
    }

    private static String required(String value, String name) {
        if (value == null) throw new NullPointerException(name);
        String normalized = value.trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException(name + " is empty");
        return normalized;
    }
}
