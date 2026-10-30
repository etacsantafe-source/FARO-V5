package ar.com.faro.v5.control.voice;

import java.util.Objects;

public interface VoiceInputPort {
    enum Kind { TRANSCRIPT, CANCELLED, UNAVAILABLE, ERROR }

    final class Result {
        private final Kind kind;
        private final String transcript;

        private Result(Kind kind, String transcript) {
            this.kind = Objects.requireNonNull(kind, "kind");
            this.transcript = transcript == null ? "" : transcript;
        }

        public static Result transcript(String transcript) {
            Objects.requireNonNull(transcript, "transcript");
            String normalized = transcript.trim();
            if (normalized.isEmpty()) throw new IllegalArgumentException("transcript must not be blank");
            return new Result(Kind.TRANSCRIPT, normalized);
        }

        public static Result cancelled() { return new Result(Kind.CANCELLED, ""); }
        public static Result unavailable() { return new Result(Kind.UNAVAILABLE, ""); }
        public static Result error() { return new Result(Kind.ERROR, ""); }

        public Kind kind() { return kind; }

        public String transcript() {
            if (kind != Kind.TRANSCRIPT) {
                throw new IllegalStateException("result has no transcript");
            }
            return transcript;
        }

        @Override
        public String toString() {
            return kind == Kind.TRANSCRIPT
                    ? "VoiceInputResult(TRANSCRIPT, chars=" + transcript.length() + ")"
                    : "VoiceInputResult(" + kind + ")";
        }
    }
}
