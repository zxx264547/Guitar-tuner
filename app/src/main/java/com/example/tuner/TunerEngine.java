package com.example.tuner;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Build;
import android.util.Log;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

class TunerEngine {

    interface Listener {
        void onPitch(PitchResult result);
    }

    private static final String TAG = "TunerEngine";
    private static final int SAMPLE_RATE = 44100;
    private static final int WINDOW_SIZE = 4096;
    private static final double MIN_FREQ = 70.0;    // lower than low E to keep margin
    private static final double MAX_FREQ = 1300.0;  // upper bound to avoid octave errors
    private static final double SMOOTHING_ALPHA = 0.2;
    private static final double NOISE_FLOOR_DB = -50.0;

    private static final String[] GUITAR_STRINGS = {"E2", "A2", "D3", "G3", "B3", "E4"};
    private static final double[] STRING_FREQ = {82.4069, 110.0, 146.832, 195.998, 246.942, 329.628};

    private final Listener listener;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private AudioRecord audioRecord;
    private volatile boolean running;
    private double smoothedFrequency = 0;
    private int stableHits = 0;
    private double[] windowCoefficients = new double[WINDOW_SIZE];
    private double[] autocorrScratch;

    TunerEngine(Listener listener) {
        this.listener = listener;
        prepareWindow();
    }

    void start() {
        if (running) return;

        int minBuffer = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT);
        if (minBuffer == AudioRecord.ERROR || minBuffer == AudioRecord.ERROR_BAD_VALUE) {
            Log.w(TAG, "Unable to get buffer size");
            return;
        }

        int bufferSize = Math.max(minBuffer, WINDOW_SIZE * 2);
        int source = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N
                ? MediaRecorder.AudioSource.UNPROCESSED
                : MediaRecorder.AudioSource.DEFAULT;

        audioRecord = new AudioRecord(
                source,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize);

        if (audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
            Log.w(TAG, "AudioRecord failed with UNPROCESSED, retry DEFAULT");
            audioRecord.release();
            audioRecord = new AudioRecord(
                    MediaRecorder.AudioSource.DEFAULT,
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferSize);
        }

        if (audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
            Log.w(TAG, "AudioRecord still failed to init");
            audioRecord.release();
            audioRecord = null;
            return;
        }

        running = true;
        executor.submit(this::captureLoop);
    }

    void stop() {
        running = false;
        if (audioRecord != null) {
            try {
                audioRecord.stop();
            } catch (IllegalStateException ignored) {
            }
            audioRecord.release();
            audioRecord = null;
        }
    }

    private void captureLoop() {
        if (audioRecord == null) return;
        short[] buffer = new short[WINDOW_SIZE];
        audioRecord.startRecording();

        while (running) {
            int read = audioRecord.read(buffer, 0, buffer.length, AudioRecord.READ_BLOCKING);
            if (read <= 0) continue;

            double amplitudeDb = computeRmsDb(buffer, read);
            boolean hasEnergy = amplitudeDb > NOISE_FLOOR_DB;
            double frequency = hasEnergy ? detectFrequency(buffer, read) : -1;

            if (frequency > 0) {
                smoothedFrequency = smoothFrequency(frequency);
            } else {
                smoothedFrequency = 0;
            }

            PitchResult result = mapToString(smoothedFrequency, amplitudeDb, frequency > 0 && hasEnergy);
            listener.onPitch(result);
        }
    }

    private double computeRmsDb(short[] data, int size) {
        double sum = 0;
        for (int i = 0; i < size; i++) {
            double v = data[i] / 32768.0;
            sum += v * v;
        }
        double rms = Math.sqrt(sum / size);
        return 20 * Math.log10(rms + 1e-10);
    }

    // Core pitch detection: window the buffer, run autocorrelation, then parabolic interpolate.
    private double detectFrequency(short[] data, int size) {
        int windowSize = Math.min(size, WINDOW_SIZE);
        double[] samples = new double[windowSize];
        for (int i = 0; i < windowSize; i++) {
            samples[i] = data[i] * windowCoefficients[i];
        }

        int minLag = (int) (SAMPLE_RATE / MAX_FREQ);
        int maxLag = (int) (SAMPLE_RATE / MIN_FREQ);
        if (autocorrScratch == null || autocorrScratch.length < maxLag + 1) {
            autocorrScratch = new double[maxLag + 1];
        }

        double best = Double.NEGATIVE_INFINITY;
        int bestLag = -1;

        for (int lag = minLag; lag <= maxLag; lag++) {
            double sum = 0;
            int limit = windowSize - lag;
            for (int i = 0; i < limit; i++) {
                sum += samples[i] * samples[i + lag];
            }
            sum /= limit; // normalize to reduce preference for shorter lags
            autocorrScratch[lag] = sum;

            if (sum > best) {
                best = sum;
                bestLag = lag;
            }
        }

        if (bestLag <= 0) return -1;

        double left = bestLag > minLag ? autocorrScratch[bestLag - 1] : best;
        double right = bestLag + 1 <= maxLag ? autocorrScratch[bestLag + 1] : best;
        double shift = parabolicShift(left, best, right);
        double refined = bestLag + shift;
        if (refined <= 0) return -1;
        return SAMPLE_RATE / refined;
    }

    private double parabolicShift(double left, double center, double right) {
        double denominator = (left - 2 * center + right);
        if (denominator == 0) return 0;
        return 0.5 * (left - right) / denominator;
    }

    private double smoothFrequency(double measured) {
        if (smoothedFrequency == 0) return measured;
        return smoothedFrequency + SMOOTHING_ALPHA * (measured - smoothedFrequency);
    }

    private PitchResult mapToString(double freq, double amplitudeDb, boolean hasSignal) {
        if (!hasSignal || freq <= 0) {
            stableHits = 0;
            return new PitchResult(false, 0, 0, "", amplitudeDb, false);
        }

        int bestIndex = 0;
        double bestDiff = Double.MAX_VALUE;
        double cents = 0;

        for (int i = 0; i < STRING_FREQ.length; i++) {
            double diffCents = 1200 * log2(freq / STRING_FREQ[i]);
            double abs = Math.abs(diffCents);
            if (abs < bestDiff) {
                bestDiff = abs;
                bestIndex = i;
                cents = diffCents;
            }
        }

        // Count how many consecutive frames stayed near the same pitch to damp jitter.
        if (bestDiff < 20) {
            stableHits++;
        } else {
            stableHits = 0;
        }
        boolean stable = stableHits > 2;

        return new PitchResult(true, freq, cents, GUITAR_STRINGS[bestIndex], amplitudeDb, stable);
    }

    private double log2(double value) {
        return Math.log(value) / Math.log(2.0);
    }

    private void prepareWindow() {
        for (int i = 0; i < WINDOW_SIZE; i++) {
            windowCoefficients[i] = 0.5 * (1 - Math.cos(2 * Math.PI * i / (WINDOW_SIZE - 1)));
        }
    }
}
