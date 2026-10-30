package ar.com.faro.v5.app;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;
import android.os.Parcel;
import android.os.SystemClock;
import android.speech.RecognizerIntent;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import ar.com.faro.v5.control.search.SearchProjectionDocument;
import ar.com.faro.v5.control.search.SearchProjectionHit;
import ar.com.faro.v5.control.search.SearchProjectionKey;
import ar.com.faro.v5.control.search.SearchProjectionQuery;
import ar.com.faro.v5.control.voice.ReadAloudPort;
import ar.com.faro.v5.control.voice.ReadAloudRequest;
import ar.com.faro.v5.control.voice.VoiceInputPort;
import ar.com.faro.v5.control.voice.VoiceInputRequest;
import ar.com.faro.v5.evidence.AttachmentIngress;
import ar.com.faro.v5.platform.android.evidence.AndroidContentUriAttachmentSource;
import ar.com.faro.v5.platform.android.ingress.AndroidExternalIngressParser;
import ar.com.faro.v5.platform.android.search.AndroidAppSearchProjection;
import ar.com.faro.v5.platform.android.voice.AndroidReadAloudAdapter;
import ar.com.faro.v5.platform.android.voice.AndroidVoiceRecognitionIntentAdapter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public final class FaroV5P6AndroidAdaptersInstrumentationTest {
    @Test
    public void appSearchProjectionRoundTripsAndRemainsRebuildable() throws Exception {
        android.content.Context target =
                InstrumentationRegistry.getInstrumentation().getTargetContext();
        AndroidAppSearchProjection projection = new AndroidAppSearchProjection(target);
        try {
            projection.clear();
            SearchProjectionKey key = new SearchProjectionKey("p6cert", "doc-1");
            projection.upsert(
                    new SearchProjectionDocument(key, "alpha bravo certification", "stamp-1"));
            List<SearchProjectionHit> hits =
                    projection.search(new SearchProjectionQuery("p6cert", "alpha", 5));
            assertEquals(1, hits.size());
            assertEquals("doc-1", hits.get(0).getKey().getDocumentId());
            assertEquals("stamp-1", hits.get(0).getSourceStamp());

            projection.remove(key);
            assertTrue(
                    projection.search(new SearchProjectionQuery("p6cert", "alpha", 5)).isEmpty());
        } finally {
            projection.close();
        }

        AndroidAppSearchProjection reopened = new AndroidAppSearchProjection(target);
        try {
            assertTrue(
                    reopened.search(new SearchProjectionQuery("p6cert", "alpha", 5)).isEmpty());
            reopened.upsert(
                    new SearchProjectionDocument(
                            new SearchProjectionKey("p6cert", "doc-2"),
                            "charlie delta certification",
                            "stamp-2"));
            reopened.clear();
            assertTrue(
                    reopened.search(new SearchProjectionQuery("p6cert", "charlie", 5)).isEmpty());
        } finally {
            reopened.close();
        }
    }

    @Test
    public void contentUriIngressReadsOnlyBoundedTestOwnedContent() {
        android.content.Context target =
                InstrumentationRegistry.getInstrumentation().getTargetContext();
        Uri uri =
                Uri.parse(
                        "content://"
                                + target.getPackageName()
                                + ".p6cert/"
                                + ar.com.faro.v5.platform.android.cert.FaroV5P6CertificationProvider.SAMPLE_PATH);
        AndroidContentUriAttachmentSource source =
                new AndroidContentUriAttachmentSource(target, uri);
        AttachmentIngress.Result result =
                AttachmentIngress.readEphemeral(source, AttachmentIngress.Policy.defaultPolicy());

        assertTrue(result.isSuccess());
        AttachmentIngress.EphemeralAttachment attachment = result.attachment();
        assertEquals("faro-p6-cert.txt", attachment.displayName());
        assertEquals("text/plain", attachment.mediaType());
        assertEquals(64, attachment.sourceFingerprintSha256().length());
        assertArrayEquals(
                ar.com.faro.v5.platform.android.cert.FaroV5P6CertificationProvider.SAMPLE_TEXT
                        .getBytes(StandardCharsets.UTF_8),
                attachment.copyBytes());
        attachment.wipe();
        assertTrue(attachment.isWiped());
    }

    @Test
    public void voiceIntentAndResultMappingAreTypedAndFailClosed() {
        android.content.Context target =
                InstrumentationRegistry.getInstrumentation().getTargetContext();
        AndroidVoiceRecognitionIntentAdapter voice = new AndroidVoiceRecognitionIntentAdapter();
        boolean available = voice.isAvailable(target);
        assertEquals(available, voice.isAvailable(target));

        VoiceInputRequest request = VoiceInputRequest.defaultRequest();
        Intent requestIntent = voice.createIntent(request);
        assertEquals(RecognizerIntent.ACTION_RECOGNIZE_SPEECH, requestIntent.getAction());
        assertEquals(
                1,
                requestIntent.getIntExtra(RecognizerIntent.EXTRA_MAX_RESULTS, -1));

        Intent success = new Intent();
        ArrayList<String> transcripts = new ArrayList<>();
        transcripts.add("  hola FARO  ");
        success.putStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS, transcripts);
        VoiceInputPort.Result parsed =
                voice.parseActivityResult(Activity.RESULT_OK, success, request);
        assertEquals(VoiceInputPort.Kind.TRANSCRIPT, parsed.kind());
        assertEquals("hola FARO", parsed.transcript());

        assertEquals(
                VoiceInputPort.Kind.CANCELLED,
                voice.parseActivityResult(Activity.RESULT_CANCELED, null, request).kind());

        Intent malformed = new Intent();
        malformed.putExtra(RecognizerIntent.EXTRA_RESULTS, new String[] {"wrong-container"});
        assertEquals(
                VoiceInputPort.Kind.ERROR,
                voice.parseActivityResult(Activity.RESULT_OK, malformed, request).kind());
    }

    @Test
    public void externalIntentParserAcceptsOnlyBoundedParcelledPayload() {
        android.content.Context target =
                InstrumentationRegistry.getInstrumentation().getTargetContext();
        Uri uri =
                Uri.parse(
                        "content://"
                                + target.getPackageName()
                                + ".p6cert/"
                                + ar.com.faro.v5.platform.android.cert.FaroV5P6CertificationProvider.SAMPLE_PATH);
        AndroidExternalIngressParser parser =
                new AndroidExternalIngressParser(
                        Collections.singleton("https://faro.example"),
                        Collections.singletonMap("/open", "chat"),
                        Collections.singletonMap("compras", "compras"),
                        "chat");

        Intent original =
                new Intent(Intent.ACTION_SEND)
                        .putExtra(Intent.EXTRA_TEXT, "hola desde share")
                        .putExtra(Intent.EXTRA_STREAM, uri);
        original.setClipData(ClipData.newRawUri("p6", uri));

        Parcel parcel = Parcel.obtain();
        Intent roundTrip;
        try {
            original.writeToParcel(parcel, 0);
            parcel.setDataPosition(0);
            roundTrip = Intent.CREATOR.createFromParcel(parcel);
        } finally {
            parcel.recycle();
        }

        AndroidExternalIngressParser.Result parsed = parser.parse(roundTrip);
        assertTrue(parsed.isAccepted());
        assertEquals(1, parsed.contentUris().size());
        assertEquals(uri, parsed.contentUris().get(0));

        Intent hostile =
                new Intent(Intent.ACTION_VIEW, Uri.parse("https://evil.example/open"));
        assertFalse(parser.parse(hostile).isAccepted());
    }

    @Test
    public void readAloudLifecycleIsTypedAcrossReadyAndUnavailableEngines() throws Exception {
        android.content.Context target =
                InstrumentationRegistry.getInstrumentation().getTargetContext();
        AndroidReadAloudAdapter tts = new AndroidReadAloudAdapter(target);
        LinkedBlockingQueue<ReadAloudPort.Event> events = new LinkedBlockingQueue<>();
        try {
            long readyDeadline = SystemClock.uptimeMillis() + 8_000L;
            while (!tts.isReady() && SystemClock.uptimeMillis() < readyDeadline) {
                SystemClock.sleep(100L);
            }

            if (!tts.isReady()) {
                tts.speak(new ReadAloudRequest("Prueba FARO", "es-AR"), events::offer);
                assertEquals(
                        ReadAloudPort.Event.NOT_READY,
                        events.poll(3, TimeUnit.SECONDS));
                return;
            }

            tts.speak(
                    new ReadAloudRequest(
                            "Prueba de lectura FARO para certificación física.",
                            "es-AR"),
                    events::offer);
            SystemClock.sleep(350L);
            tts.stop();

            ReadAloudPort.Event terminal = awaitTerminal(events, 8_000L);
            assertNotNull(terminal);
            assertTrue(
                    terminal == ReadAloudPort.Event.STOPPED
                            || terminal == ReadAloudPort.Event.DONE
                            || terminal == ReadAloudPort.Event.LANGUAGE_UNAVAILABLE);
        } finally {
            tts.close();
        }

        LinkedBlockingQueue<ReadAloudPort.Event> afterClose = new LinkedBlockingQueue<>();
        tts.speak(new ReadAloudRequest("Cerrado", "es-AR"), afterClose::offer);
        assertEquals(
                ReadAloudPort.Event.NOT_READY,
                afterClose.poll(3, TimeUnit.SECONDS));
    }

    private static ReadAloudPort.Event awaitTerminal(
            LinkedBlockingQueue<ReadAloudPort.Event> events,
            long timeoutMillis)
            throws InterruptedException {
        long deadline = SystemClock.uptimeMillis() + timeoutMillis;
        while (SystemClock.uptimeMillis() < deadline) {
            long remaining = Math.max(1L, deadline - SystemClock.uptimeMillis());
            ReadAloudPort.Event event = events.poll(remaining, TimeUnit.MILLISECONDS);
            if (event == null) return null;
            if (event != ReadAloudPort.Event.STARTED) return event;
        }
        return null;
    }
}
