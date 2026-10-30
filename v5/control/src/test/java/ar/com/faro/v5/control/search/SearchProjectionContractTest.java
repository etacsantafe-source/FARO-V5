package ar.com.faro.v5.control.search;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

final class SearchProjectionContractTest {
    @Test
    void acceptsBoundedFailClosedProjectionValues() {
        SearchProjectionKey key = new SearchProjectionKey("finance.entries", "entry:123");
        SearchProjectionDocument document =
                new SearchProjectionDocument(key, "mercado octubre tarjeta", "rev-42");
        SearchProjectionQuery query =
                new SearchProjectionQuery("finance.entries", "mercado oct", 20);
        SearchProjectionHit hit = new SearchProjectionHit(key, "rev-42", 1.25d);

        assertEquals("finance.entries", document.getKey().getNamespace());
        assertEquals("entry:123", document.getKey().getDocumentId());
        assertEquals("mercado octubre tarjeta", document.getIndexText());
        assertEquals("rev-42", document.getSourceStamp());
        assertEquals(20, query.getLimit());
        assertEquals("mercado oct", query.getQueryText());
        assertEquals(1.25d, hit.getRankingSignal());
    }

    @Test
    void rejectsWideningOrUnboundedInputs() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new SearchProjectionQuery("", "anything", 10));
        assertThrows(
                IllegalArgumentException.class,
                () -> new SearchProjectionQuery("finance entries", "anything", 10));
        assertThrows(
                IllegalArgumentException.class,
                () -> new SearchProjectionQuery("finance.entries", "", 10));
        assertThrows(
                IllegalArgumentException.class,
                () -> new SearchProjectionQuery("finance.entries", "anything", 51));
        assertThrows(
                IllegalArgumentException.class,
                () -> new SearchProjectionDocument(
                        new SearchProjectionKey("finance.entries", "entry:123"),
                        "x".repeat(16_001),
                        "rev-42"));
        assertThrows(
                IllegalArgumentException.class,
                () -> new SearchProjectionHit(
                        new SearchProjectionKey("finance.entries", "entry:123"),
                        "",
                        1.0d));
    }
}
