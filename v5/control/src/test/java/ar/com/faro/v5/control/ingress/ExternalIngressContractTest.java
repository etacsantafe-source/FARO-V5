package ar.com.faro.v5.control.ingress;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

final class ExternalIngressContractTest {
    @Test
    void quickAndDeepLinkCarryOnlyBoundedRoutingMetadata() {
        ExternalIngressTrigger quick = ExternalIngressTrigger.quick("composer");
        assertEquals(ExternalIngressTrigger.Source.QUICK, quick.getSource());
        assertEquals("composer", quick.getTarget());
        assertEquals("", quick.getText());
        assertEquals(0, quick.getAttachmentCount());

        ExternalIngressTrigger deep =
                ExternalIngressTrigger.deepLink("matters", "reminder:abc-123");
        assertEquals(ExternalIngressTrigger.Source.DEEP_LINK, deep.getSource());
        assertEquals("matters", deep.getTarget());
        assertEquals("reminder:abc-123", deep.getReference());
    }

    @Test
    void shareIsBoundedAndNeverRepresentsEmptyInput() {
        ExternalIngressTrigger text =
                ExternalIngressTrigger.share("composer", "  hola FARO  ", 0);
        assertEquals("hola FARO", text.getText());

        ExternalIngressTrigger attachment =
                ExternalIngressTrigger.share("composer", "", 4);
        assertEquals(4, attachment.getAttachmentCount());

        assertThrows(
                IllegalArgumentException.class,
                () -> ExternalIngressTrigger.share("composer", "", 0));
        assertThrows(
                IllegalArgumentException.class,
                () -> ExternalIngressTrigger.share("composer", "", 5));
        assertThrows(
                IllegalArgumentException.class,
                () -> ExternalIngressTrigger.share(
                        "composer",
                        "x".repeat(12_001),
                        0));
    }

    @Test
    void routingAndReferencesFailClosed() {
        assertThrows(
                IllegalArgumentException.class,
                () -> ExternalIngressTrigger.quick("../owner"));
        assertThrows(
                IllegalArgumentException.class,
                () -> ExternalIngressTrigger.deepLink("matters", "bad/ref"));
        assertThrows(
                IllegalArgumentException.class,
                () -> ExternalIngressTrigger.deepLink("matters", "x".repeat(257)));
    }

    @Test
    void quickShortcutSpecIsPresentationOnlyAndBounded() {
        QuickTriggerSpec spec =
                new QuickTriggerSpec("quick.chat", "Chat", "Abrir chat de FARO", "composer");
        assertEquals("quick.chat", spec.getId());
        assertEquals("Chat", spec.getShortLabel());
        assertEquals("Abrir chat de FARO", spec.getLongLabel());
        assertEquals("composer", spec.getTarget());

        assertThrows(
                IllegalArgumentException.class,
                () -> new QuickTriggerSpec("BAD ID", "Chat", "", "composer"));
        assertThrows(
                IllegalArgumentException.class,
                () -> new QuickTriggerSpec("quick.chat", "", "", "composer"));
    }
}
