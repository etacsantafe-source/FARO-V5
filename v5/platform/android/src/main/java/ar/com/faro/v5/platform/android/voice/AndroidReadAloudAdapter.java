package ar.com.faro.v5.platform.android.voice;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import ar.com.faro.v5.control.voice.ReadAloudPort;
import ar.com.faro.v5.control.voice.ReadAloudRequest;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public final class AndroidReadAloudAdapter implements ReadAloudPort {
    private final Handler main = new Handler(Looper.getMainLooper());
    private final AtomicBoolean ready = new AtomicBoolean(false);
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final AtomicLong nextUtterance = new AtomicLong(1);
    private final AtomicReference<String> activeUtterance = new AtomicReference<>();
    private final AtomicReference<Callback> activeCallback = new AtomicReference<>();
    private final TextToSpeech engine;

    public AndroidReadAloudAdapter(Context context) {
        Context appContext = Objects.requireNonNull(context, "context").getApplicationContext();
        engine = new TextToSpeech(appContext, status -> {
            if (closed.get()) return;
            ready.set(status == TextToSpeech.SUCCESS);
        });
        engine.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override
            public void onStart(String utteranceId) {
                if (isActive(utteranceId)) deliverActive(Event.STARTED, false);
            }

            @Override
            public void onDone(String utteranceId) {
                if (isActive(utteranceId)) deliverActive(Event.DONE, true);
            }

            @Override
            @SuppressWarnings("deprecation")
            public void onError(String utteranceId) {
                if (isActive(utteranceId)) deliverActive(Event.ERROR, true);
            }

            @Override
            public void onError(String utteranceId, int errorCode) {
                if (isActive(utteranceId)) deliverActive(Event.ERROR, true);
            }

            @Override
            public void onStop(String utteranceId, boolean interrupted) {
                if (isActive(utteranceId)) deliverActive(Event.STOPPED, true);
            }
        });
    }

    @Override
    public boolean isReady() {
        return ready.get() && !closed.get();
    }

    @Override
    public void speak(ReadAloudRequest request, Callback callback) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(callback, "callback");
        main.post(() -> speakOnMain(request, callback));
    }

    private void speakOnMain(ReadAloudRequest request, Callback callback) {
        if (closed.get() || !ready.get()) {
            ReadAloudPort.deliver(callback, Event.NOT_READY);
            return;
        }
        if (request.text().length() > Math.min(ReadAloudRequest.MAX_TEXT_CHARS, TextToSpeech.getMaxSpeechInputLength())) {
            ReadAloudPort.deliver(callback, Event.TOO_LONG_FOR_ENGINE);
            return;
        }

        Locale locale = request.languageTag().isEmpty()
                ? Locale.getDefault()
                : Locale.forLanguageTag(request.languageTag());
        int availability = engine.isLanguageAvailable(locale);
        if (availability == TextToSpeech.LANG_MISSING_DATA || availability == TextToSpeech.LANG_NOT_SUPPORTED) {
            ReadAloudPort.deliver(callback, Event.LANGUAGE_UNAVAILABLE);
            return;
        }
        int languageResult = engine.setLanguage(locale);
        if (languageResult == TextToSpeech.LANG_MISSING_DATA || languageResult == TextToSpeech.LANG_NOT_SUPPORTED) {
            ReadAloudPort.deliver(callback, Event.LANGUAGE_UNAVAILABLE);
            return;
        }

        stopActive(true);
        String utteranceId = "faro-v5-read-" + nextUtterance.getAndIncrement();
        activeUtterance.set(utteranceId);
        activeCallback.set(callback);
        int result = engine.speak(
                request.text(),
                TextToSpeech.QUEUE_FLUSH,
                null,
                utteranceId);
        if (result == TextToSpeech.ERROR) {
            clearIfActive(utteranceId);
            ReadAloudPort.deliver(callback, Event.ERROR);
        }
    }

    @Override
    public void stop() {
        main.post(() -> stopActive(true));
    }

    private void stopActive(boolean notifyFallback) {
        String utteranceId = activeUtterance.get();
        Callback callback = activeCallback.get();
        engine.stop();
        if (utteranceId != null && callback != null && notifyFallback && clearIfActive(utteranceId)) {
            ReadAloudPort.deliver(callback, Event.STOPPED);
        } else if (!notifyFallback) {
            activeUtterance.set(null);
            activeCallback.set(null);
        }
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) return;
        ready.set(false);
        main.post(() -> {
            stopActive(false);
            engine.shutdown();
        });
    }

    private boolean isActive(String utteranceId) {
        return utteranceId != null && utteranceId.equals(activeUtterance.get());
    }

    private boolean clearIfActive(String utteranceId) {
        if (!activeUtterance.compareAndSet(utteranceId, null)) return false;
        activeCallback.set(null);
        return true;
    }

    private void deliverActive(Event event, boolean terminal) {
        String utteranceId = activeUtterance.get();
        Callback callback = activeCallback.get();
        if (utteranceId == null || callback == null) return;
        if (terminal && !clearIfActive(utteranceId)) return;
        ReadAloudPort.deliver(callback, event);
    }
}
