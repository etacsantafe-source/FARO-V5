package ar.com.faro.v5.control.ingress;

/**
 * Presentation-only definition for a launcher Quick trigger.
 *
 * <p>The id and target are bounded routing metadata. A shortcut cannot contain approval or an
 * owner mutation payload.
 */
public final class QuickTriggerSpec {
    private final String id;
    private final String shortLabel;
    private final String longLabel;
    private final String target;

    public QuickTriggerSpec(String id, String shortLabel, String longLabel, String target) {
        this.id = IngressBounds.quickId(id);
        this.shortLabel =
                IngressBounds.label(
                        shortLabel,
                        "shortLabel",
                        IngressBounds.MAX_SHORT_LABEL_CHARS,
                        false);
        this.longLabel =
                IngressBounds.label(
                        longLabel,
                        "longLabel",
                        IngressBounds.MAX_LONG_LABEL_CHARS,
                        true);
        this.target = IngressBounds.target(target);
    }

    public String getId() {
        return id;
    }

    public String getShortLabel() {
        return shortLabel;
    }

    public String getLongLabel() {
        return longLabel;
    }

    public String getTarget() {
        return target;
    }
}
