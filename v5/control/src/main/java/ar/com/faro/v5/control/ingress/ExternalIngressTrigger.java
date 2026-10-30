package ar.com.faro.v5.control.ingress;

/**
 * Bounded, non-authoritative ingress trigger.
 *
 * <p>It carries only enough information to reopen the canonical FARO runtime. It is never an
 * approval, PreparedAction, owner command or durable authorization.
 */
public final class ExternalIngressTrigger {
    public enum Source {
        QUICK,
        DEEP_LINK,
        SHARE
    }

    private final Source source;
    private final String target;
    private final String text;
    private final String reference;
    private final int attachmentCount;

    private ExternalIngressTrigger(
            Source source,
            String target,
            String text,
            String reference,
            int attachmentCount) {
        if (source == null) throw new NullPointerException("source");
        if (attachmentCount < 0 || attachmentCount > IngressBounds.MAX_ATTACHMENTS) {
            throw new IllegalArgumentException("invalid attachment count");
        }
        this.source = source;
        this.target = IngressBounds.target(target);
        this.text = IngressBounds.optionalText(text);
        this.reference = IngressBounds.optionalReference(reference);
        this.attachmentCount = attachmentCount;
    }

    public static ExternalIngressTrigger quick(String target) {
        return new ExternalIngressTrigger(Source.QUICK, target, "", "", 0);
    }

    public static ExternalIngressTrigger deepLink(String target, String reference) {
        return new ExternalIngressTrigger(Source.DEEP_LINK, target, "", reference, 0);
    }

    public static ExternalIngressTrigger share(String target, String text, int attachmentCount) {
        ExternalIngressTrigger trigger =
                new ExternalIngressTrigger(Source.SHARE, target, text, "", attachmentCount);
        if (trigger.text.isEmpty() && trigger.attachmentCount == 0) {
            throw new IllegalArgumentException("share contains no bounded input");
        }
        return trigger;
    }

    public Source getSource() {
        return source;
    }

    public String getTarget() {
        return target;
    }

    public String getText() {
        return text;
    }

    public String getReference() {
        return reference;
    }

    public int getAttachmentCount() {
        return attachmentCount;
    }
}
