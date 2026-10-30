package ar.com.faro.v5.control.search;

import java.util.Objects;
import java.util.regex.Pattern;

final class SearchProjectionBounds {
    static final int MAX_NAMESPACE_CHARS = 64;
    static final int MAX_DOCUMENT_ID_CHARS = 128;
    static final int MAX_SOURCE_STAMP_CHARS = 128;
    static final int MAX_INDEX_TEXT_CHARS = 16_000;
    static final int MAX_QUERY_CHARS = 256;
    static final int MAX_RESULTS = 50;

    private static final Pattern NAMESPACE = Pattern.compile("[A-Za-z0-9._:-]{1,64}");
    private static final Pattern DOCUMENT_ID = Pattern.compile("[A-Za-z0-9._:-]{1,128}");

    private SearchProjectionBounds() {}

    static String namespace(String value) {
        String normalized = Objects.requireNonNull(value, "namespace").trim();
        if (!NAMESPACE.matcher(normalized).matches()) {
            throw new IllegalArgumentException("namespace must be 1..64 bounded identifier characters");
        }
        return normalized;
    }

    static String documentId(String value) {
        String normalized = Objects.requireNonNull(value, "documentId").trim();
        if (!DOCUMENT_ID.matcher(normalized).matches()) {
            throw new IllegalArgumentException("documentId must be 1..128 bounded identifier characters");
        }
        return normalized;
    }

    static String sourceStamp(String value) {
        String normalized = Objects.requireNonNull(value, "sourceStamp").trim();
        if (normalized.isEmpty() || normalized.length() > MAX_SOURCE_STAMP_CHARS) {
            throw new IllegalArgumentException("sourceStamp must be 1..128 characters");
        }
        return normalized;
    }

    static String indexText(String value) {
        String normalized = Objects.requireNonNull(value, "indexText").trim();
        if (normalized.isEmpty() || normalized.length() > MAX_INDEX_TEXT_CHARS) {
            throw new IllegalArgumentException("indexText must be 1..16000 characters");
        }
        return normalized;
    }

    static String queryText(String value) {
        String normalized = Objects.requireNonNull(value, "queryText").trim();
        if (normalized.isEmpty() || normalized.length() > MAX_QUERY_CHARS) {
            throw new IllegalArgumentException("queryText must be 1..256 characters");
        }
        return normalized;
    }

    static int resultLimit(int value) {
        if (value < 1 || value > MAX_RESULTS) {
            throw new IllegalArgumentException("limit must be 1..50");
        }
        return value;
    }
}
