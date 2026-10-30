package ar.com.faro.v5.control.spi;

import java.util.Arrays;
import java.util.Objects;

public final class SecretValue {
    private final byte[] bytes;

    private SecretValue(byte[] bytes) {
        if (bytes.length == 0) {
            throw new IllegalArgumentException("secret material must not be empty");
        }
        this.bytes = Arrays.copyOf(bytes, bytes.length);
    }

    public static SecretValue copyOf(byte[] bytes) {
        return new SecretValue(Objects.requireNonNull(bytes, "bytes"));
    }

    public byte[] copyBytes() {
        return Arrays.copyOf(bytes, bytes.length);
    }

    @Override
    public String toString() {
        return "[REDACTED]";
    }
}
