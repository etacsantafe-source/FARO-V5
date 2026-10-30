package ar.com.faro.v5.control.voice;

import java.util.Objects;

public interface ReadAloudPort extends AutoCloseable {
    enum Event {
        STARTED,
        DONE,
        STOPPED,
        NOT_READY,
        LANGUAGE_UNAVAILABLE,
        TOO_LONG_FOR_ENGINE,
        ERROR
    }

    @FunctionalInterface
    interface Callback {
        void onEvent(Event event);
    }

    boolean isReady();
    void speak(ReadAloudRequest request, Callback callback);
    void stop();

    @Override
    void close();

    static void deliver(Callback callback, Event event) {
        Objects.requireNonNull(callback, "callback").onEvent(Objects.requireNonNull(event, "event"));
    }
}
