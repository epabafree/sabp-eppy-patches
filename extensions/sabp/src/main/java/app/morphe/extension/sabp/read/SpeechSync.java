package app.morphe.extension.sabp.read;

import android.content.Context;
import android.content.Intent;
import android.media.AudioFormat;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;

import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

/**
 * Transcribes a short clip of the audiobook with Android's own speech recogniser, entirely on the
 * device. The clip is decoded from the audio file and handed to the recogniser as an audio
 * source (Android 13+), so the microphone is never used and nothing is uploaded.
 */
public final class SpeechSync {
    public interface Callback {
        void onTranscript(List<String> words);

        void onError(String reason);
    }

    private static final int RATE = 16000;
    private final Context c;
    private final Handler main = new Handler(Looper.getMainLooper());
    private boolean busy;

    public SpeechSync(Context c) {
        this.c = c.getApplicationContext();
    }

    /** True if this device can do on-device recognition from an audio file. */
    public static boolean supported(Context c) {
        if (Build.VERSION.SDK_INT < 33) return false;
        try {
            return onDevice(c);
        } catch (Throwable t) {
            return false;
        }
    }

    @android.annotation.TargetApi(33)
    private static boolean onDevice(Context c) {
        return SpeechRecognizer.isOnDeviceRecognitionAvailable(c);
    }

    public boolean isBusy() {
        return busy;
    }

    /** Builds the document URI of a file inside SABP's book folder (same as SABP does). */
    public static Uri fileUri(String folderUri, String fileName) {
        Uri folder = Uri.parse(folderUri);
        if ("file".equals(folder.getScheme()) || folderUri.startsWith("/")) {
            return Uri.fromFile(new java.io.File(folder.getPath() != null ? folder.getPath() : folderUri, fileName));
        }
        return DocumentsContract.buildDocumentUriUsingTree(folder, DocumentsContract.getDocumentId(folder) + "/" + fileName);
    }

    /** Transcribes seconds [start, start+length) of an audio file. Callback runs on the main thread. */
    public void transcribe(Uri audio, double start, double length, String language, Callback cb) {
        if (busy) {
            cb.onError("busy");
            return;
        }
        if (!supported(c)) {
            cb.onError("On-device speech recognition from files needs Android 13+ with an offline speech model");
            return;
        }
        busy = true;
        new Thread(() -> {
            short[] pcm;
            try {
                pcm = decode(audio, start, length);
            } catch (Throwable t) {
                finish(cb, null, "Couldn't decode audio: " + t.getMessage());
                return;
            }
            if (pcm == null || pcm.length < RATE * 2) {
                finish(cb, null, "Audio clip too short");
                return;
            }
            main.post(() -> recognize(pcm, language, cb));
        }, "eppy-decode").start();
    }

    private void finish(Callback cb, List<String> words, String error) {
        main.post(() -> {
            busy = false;
            if (words != null) cb.onTranscript(words);
            else cb.onError(error);
        });
    }

    @android.annotation.TargetApi(33)
    private void recognize(short[] pcm, String language, Callback cb) {
        final SpeechRecognizer sr;
        final ParcelFileDescriptor[] pipe;
        try {
            sr = SpeechRecognizer.createOnDeviceSpeechRecognizer(c);
            pipe = ParcelFileDescriptor.createPipe();
        } catch (Throwable t) {
            finish(cb, null, "Speech recogniser unavailable");
            return;
        }
        final boolean[] done = {false};
        Runnable timeout = () -> {
            if (!done[0]) {
                done[0] = true;
                try {
                    sr.destroy();
                } catch (Throwable ignored) {
                }
                finish(cb, null, "Speech recogniser timed out");
            }
        };
        sr.setRecognitionListener(new RecognitionListener() {
            public void onReadyForSpeech(Bundle b) {
            }

            public void onBeginningOfSpeech() {
            }

            public void onRmsChanged(float v) {
            }

            public void onBufferReceived(byte[] b) {
            }

            public void onEndOfSpeech() {
            }

            public void onError(int error) {
                if (done[0]) return;
                done[0] = true;
                main.removeCallbacks(timeout);
                sr.destroy();
                finish(cb, null, errorText(error));
            }

            public void onResults(Bundle results) {
                if (done[0]) return;
                done[0] = true;
                main.removeCallbacks(timeout);
                sr.destroy();
                ArrayList<String> list = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                ArrayList<String> words = new ArrayList<>();
                if (list != null && !list.isEmpty()) {
                    for (String w : list.get(0).split("\\s+")) {
                        String n = BookText.normalize(w);
                        if (!n.isEmpty()) words.add(n);
                    }
                }
                finish(cb, words, null);
            }

            public void onPartialResults(Bundle b) {
            }

            public void onEvent(int t, Bundle b) {
            }
        });

        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        if (language != null && !language.isEmpty()) intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, language);
        intent.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true);
        intent.putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE, pipe[0]);
        intent.putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_CHANNEL_COUNT, 1);
        intent.putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_ENCODING, AudioFormat.ENCODING_PCM_16BIT);
        intent.putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_SAMPLING_RATE, RATE);
        intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 4000L);
        try {
            sr.startListening(intent);
        } catch (Throwable t) {
            done[0] = true;
            sr.destroy();
            finish(cb, null, "Speech recogniser refused the request");
            return;
        }
        try {
            pipe[0].close(); // the recogniser holds its own copy
        } catch (Throwable ignored) {
        }
        new Thread(() -> {
            try (OutputStream out = new ParcelFileDescriptor.AutoCloseOutputStream(pipe[1])) {
                ByteBuffer bb = ByteBuffer.allocate(pcm.length * 2).order(ByteOrder.LITTLE_ENDIAN);
                for (short s : pcm) bb.putShort(s);
                out.write(bb.array());
            } catch (Throwable ignored) {
            }
        }, "eppy-feed").start();
        main.postDelayed(timeout, 30_000);
    }

    private static String errorText(int e) {
        switch (e) {
            case SpeechRecognizer.ERROR_NO_MATCH:
                return "No speech recognised in this clip";
            case SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED:
            case SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE:
                return "No offline speech model for this book's language";
            case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS:
                return "The speech service needs the microphone permission";
            case SpeechRecognizer.ERROR_RECOGNIZER_BUSY:
                return "Speech recogniser busy";
            default:
                return "Speech recogniser error " + e;
        }
    }

    // ---- audio decoding -----------------------------------------------------------------------

    /** Decodes [start, start+length) seconds to 16 kHz mono 16-bit PCM. */
    private short[] decode(Uri uri, double start, double length) throws Exception {
        MediaExtractor ex = new MediaExtractor();
        MediaCodec codec = null;
        try {
            ex.setDataSource(c, uri, null);
            int track = -1;
            MediaFormat fmt = null;
            for (int i = 0; i < ex.getTrackCount(); i++) {
                MediaFormat f = ex.getTrackFormat(i);
                String mime = f.getString(MediaFormat.KEY_MIME);
                if (mime != null && mime.startsWith("audio/")) {
                    track = i;
                    fmt = f;
                    break;
                }
            }
            if (track < 0) throw new IllegalStateException("no audio track");
            ex.selectTrack(track);
            long startUs = (long) (Math.max(0, start) * 1_000_000);
            long endUs = startUs + (long) (length * 1_000_000);
            ex.seekTo(startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
            codec = MediaCodec.createDecoderByType(fmt.getString(MediaFormat.KEY_MIME));
            codec.configure(fmt, null, null, 0);
            codec.start();
            int srcRate = fmt.containsKey(MediaFormat.KEY_SAMPLE_RATE) ? fmt.getInteger(MediaFormat.KEY_SAMPLE_RATE) : 44100;
            int channels = fmt.containsKey(MediaFormat.KEY_CHANNEL_COUNT) ? fmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT) : 2;
            ShortList mono = new ShortList((int) (length * srcRate) + 4096);
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            boolean inputDone = false, outputDone = false;
            long guard = System.currentTimeMillis() + 20_000;
            while (!outputDone && System.currentTimeMillis() < guard) {
                if (!inputDone) {
                    int in = codec.dequeueInputBuffer(10_000);
                    if (in >= 0) {
                        ByteBuffer buf = codec.getInputBuffer(in);
                        int size = ex.readSampleData(buf, 0);
                        long t = ex.getSampleTime();
                        if (size < 0 || t > endUs + 500_000) {
                            codec.queueInputBuffer(in, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            inputDone = true;
                        } else {
                            codec.queueInputBuffer(in, 0, size, t, 0);
                            ex.advance();
                        }
                    }
                }
                int out = codec.dequeueOutputBuffer(info, 10_000);
                if (out == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    MediaFormat of = codec.getOutputFormat();
                    if (of.containsKey(MediaFormat.KEY_SAMPLE_RATE)) srcRate = of.getInteger(MediaFormat.KEY_SAMPLE_RATE);
                    if (of.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) channels = of.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
                } else if (out >= 0) {
                    if (info.size > 0 && info.presentationTimeUs + 1 >= startUs && info.presentationTimeUs <= endUs) {
                        ByteBuffer ob = codec.getOutputBuffer(out);
                        ob.position(info.offset);
                        ob.limit(info.offset + info.size);
                        java.nio.ShortBuffer sb = ob.order(ByteOrder.nativeOrder()).asShortBuffer();
                        int frames = sb.remaining() / Math.max(1, channels);
                        for (int f = 0; f < frames; f++) {
                            int sum = 0;
                            for (int ch = 0; ch < channels; ch++) sum += sb.get(f * channels + ch);
                            mono.add((short) (sum / channels));
                        }
                    }
                    codec.releaseOutputBuffer(out, false);
                    if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0 || info.presentationTimeUs > endUs) outputDone = true;
                }
            }
            return resample(mono.toArray(), srcRate, RATE);
        } finally {
            try {
                if (codec != null) {
                    codec.stop();
                    codec.release();
                }
            } catch (Throwable ignored) {
            }
            ex.release();
        }
    }

    private static short[] resample(short[] in, int from, int to) {
        if (from == to || in.length == 0) return in;
        int n = (int) ((long) in.length * to / from);
        short[] out = new short[n];
        double step = from / (double) to;
        for (int i = 0; i < n; i++) {
            double x = i * step;
            int a = (int) x;
            int b = Math.min(in.length - 1, a + 1);
            double f = x - a;
            out[i] = (short) Math.round(in[a] * (1 - f) + in[b] * f);
        }
        return out;
    }

    private static final class ShortList {
        short[] a;
        int n;

        ShortList(int cap) {
            a = new short[Math.max(1024, cap)];
        }

        void add(short s) {
            if (n == a.length) a = java.util.Arrays.copyOf(a, a.length * 2);
            a[n++] = s;
        }

        short[] toArray() {
            return java.util.Arrays.copyOf(a, n);
        }
    }
}
