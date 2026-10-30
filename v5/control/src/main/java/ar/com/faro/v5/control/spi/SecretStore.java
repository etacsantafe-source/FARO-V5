package ar.com.faro.v5.control.spi;

import java.util.Objects;

public interface SecretStore extends SecretReader {
    ReadResult read(SecretId id);

    @Override
    default SecretValue readValueOrNull(SecretId id) {
        ReadResult result = read(id);
        return result.status() == ReadStatus.AVAILABLE
                ? result.valueOrNull()
                : null;
    }

    MutationResult put(SecretId id, SecretValue value);
    MutationResult delete(SecretId id);

    enum UnavailableReason {
        PLATFORM_UNAVAILABLE,
        KEY_UNAVAILABLE,
        INVALID_PAYLOAD,
        CRYPTO_FAILURE,
        IO_FAILURE
    }

    enum ReadStatus {
        AVAILABLE,
        MISSING,
        UNAVAILABLE
    }

    final class ReadResult {
        private final ReadStatus status;
        private final SecretValue value;
        private final UnavailableReason reason;

        private ReadResult(ReadStatus status, SecretValue value, UnavailableReason reason) {
            this.status = Objects.requireNonNull(status, "status");
            this.value = value;
            this.reason = reason;
        }

        public static ReadResult available(SecretValue value) {
            return new ReadResult(ReadStatus.AVAILABLE, Objects.requireNonNull(value, "value"), null);
        }

        public static ReadResult missing() {
            return new ReadResult(ReadStatus.MISSING, null, null);
        }

        public static ReadResult unavailable(UnavailableReason reason) {
            return new ReadResult(ReadStatus.UNAVAILABLE, null, Objects.requireNonNull(reason, "reason"));
        }

        public ReadStatus status() { return status; }
        public SecretValue valueOrNull() { return value; }
        public UnavailableReason reasonOrNull() { return reason; }

        @Override
        public String toString() {
            return "ReadResult(status=" + status + ", value=" +
                    (value == null ? "null" : "[REDACTED]") + ", reason=" + reason + ")";
        }
    }

    enum MutationStatus {
        STORED,
        DELETED,
        MISSING,
        UNAVAILABLE
    }

    final class MutationResult {
        private final MutationStatus status;
        private final UnavailableReason reason;

        private MutationResult(MutationStatus status, UnavailableReason reason) {
            this.status = Objects.requireNonNull(status, "status");
            this.reason = reason;
        }

        public static MutationResult stored() {
            return new MutationResult(MutationStatus.STORED, null);
        }

        public static MutationResult deleted() {
            return new MutationResult(MutationStatus.DELETED, null);
        }

        public static MutationResult missing() {
            return new MutationResult(MutationStatus.MISSING, null);
        }

        public static MutationResult unavailable(UnavailableReason reason) {
            return new MutationResult(MutationStatus.UNAVAILABLE, Objects.requireNonNull(reason, "reason"));
        }

        public MutationStatus status() { return status; }
        public UnavailableReason reasonOrNull() { return reason; }

        @Override
        public String toString() {
            return "MutationResult(status=" + status + ", reason=" + reason + ")";
        }
    }
}
