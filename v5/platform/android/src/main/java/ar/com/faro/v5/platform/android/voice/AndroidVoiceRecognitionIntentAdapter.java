package ar.com.faro.v5.platform.android.voice;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.speech.RecognizerIntent;
import ar.com.faro.v5.control.voice.VoiceInputPort;
import ar.com.faro.v5.control.voice.VoiceInputRequest;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Objects;

public final class AndroidVoiceRecognitionIntentAdapter {
    public boolean isAvailable(Context context) {
        Objects.requireNonNull(context, "context");
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        return intent.resolveActivity(context.getPackageManager()) != null;
    }

    public Intent createIntent(VoiceInputRequest request) {
        Objects.requireNonNull(request, "request");
        String languageTag = request.languageTag().isEmpty()
                ? Locale.getDefault().toLanguageTag()
                : request.languageTag();

        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
                .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
        if (!request.prompt().isEmpty()) {
            intent.putExtra(RecognizerIntent.EXTRA_PROMPT, request.prompt());
        }
        return intent;
    }

    public VoiceInputPort.Result parseActivityResult(
            int resultCode,
            Intent data,
            VoiceInputRequest request) {
        Objects.requireNonNull(request, "request");
        if (resultCode == Activity.RESULT_CANCELED) return VoiceInputPort.Result.cancelled();
        if (resultCode != Activity.RESULT_OK || data == null) return VoiceInputPort.Result.error();

        ArrayList<String> results;
        try {
            results = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
        } catch (RuntimeException ignored) {
            return VoiceInputPort.Result.error();
        }
        if (results == null) return VoiceInputPort.Result.error();
        for (String candidate : results) {
            if (candidate == null) continue;
            String normalized = candidate.trim();
            if (normalized.isEmpty()) continue;
            if (normalized.length() > request.maxTranscriptChars()) {
                return VoiceInputPort.Result.error();
            }
            return VoiceInputPort.Result.transcript(normalized);
        }
        return VoiceInputPort.Result.error();
    }
}
