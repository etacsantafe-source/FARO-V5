package ar.com.faro.v5.control.spi;

import java.util.Objects;
import java.util.regex.Pattern;

public final class SecretId {
    private static final Pattern FORMAT =
            Pattern.compile("[a-z0-9](?:[a-z0-9._-]{0,79})");

    private final String value;

    public SecretId(String value) {
        this.value = Objects.requireNonNull(value, "value");
        if (!FORMAT.matcher(value).matches()) {
            throw new IllegalArgumentException("invalid SecretId");
        }
    }

    public String value() {
        return value;
    }

    @Override
    public boolean equals(Object other) {
        return this == other ||
                (other instanceof SecretId && value.equals(((SecretId) other).value));
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    @Override
    public String toString() {
        return value;
    }
}
