package com.example.tuner;

import android.util.Log;

class TunerEngine {

    interface Listener {
        void onPitch(PitchResult result);
    }

    private static final String TAG = "TunerEngine";
    private static final int WINDOW_SIZE = 4096;
    private static final double MIN_FREQ = 70.0;    // lower than low E to keep margin
    private static final double MAX_FREQ = 1300.0;  // upper bound to avoid octave errors
    private static final double SMOOTHING_ALPHA = 0.2;
    private static final double NOISE_FLOOR_DB = -50.0;

    private static final String[] GUITAR_STRINGS = {"E2", "A2", "D3", "G3", "B3", "E4"};
    private static final double[] STRING_FREQ = {82.4069, 110.0, 146.832, 195.998, 246.942, 329.628};

    private final Listener listener;

    private volatile boolean running;
    private int sampleRate = 44100;
    private double smoothedFrequency = 0;
    private int stableHits = 0;
    private double[] windowCoefficients = new double[WINDOW_SIZE];
    private double[] diffScratch;
    private double[] cmndfScratch;

    static {
        System.loadLibrary("tuner");
    }

    TunerEngine(Listener listener) {
        this.listener = listener;
        prepareWindow();
    }

    void start() {
        if (running) {
            return;
        }
        boolean started = nativeStart(sampleRate, WINDOW_SIZE);
        if (!started) {
            Log.w(TAG, "Native audio engine failed to start");
            return;
        }
        running = true;
    }

    void stop() {
        running = false;
        nativeStop();
    }

    private void onPcm(short[] buffer, int read) {
        if (!running || read <= 0) {
            return;
        }

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

        int minLag = (int) (sampleRate / MAX_FREQ);
        int maxLag = (int) (sampleRate / MIN_FREQ);
        if (diffScratch == null || diffScratch.length < maxLag + 1) {
            diffScratch = new double[maxLag + 1];
            cmndfScratch = new double[maxLag + 1];
        }

        for (int lag = minLag; lag <= maxLag; lag++) {
            double sum = 0;
            int limit = windowSize - lag;
            for (int i = 0; i < limit; i++) {
                double delta = samples[i] - samples[i + lag];
                sum += delta * delta;
            }
            diffScratch[lag] = sum;
        }

        cmndfScratch[minLag] = 1;
        double runningSum = 0;
        for (int lag = minLag + 1; lag <= maxLag; lag++) {
            runningSum += diffScratch[lag];
            if (runningSum == 0) {
                cmndfScratch[lag] = 1;
            } else {
                cmndfScratch[lag] = diffScratch[lag] * lag / runningSum;
            }
        }

        int bestLag = -1;
        double bestValue = Double.MAX_VALUE;
        double threshold = 0.15;
        for (int lag = minLag; lag <= maxLag; lag++) {
            double value = cmndfScratch[lag];
            if (value < threshold) {
                bestLag = lag;
                bestValue = value;
                break;
            }
            if (value < bestValue) {
                bestValue = value;
                bestLag = lag;
            }
        }

        if (bestLag <= 0) {
            return -1;
        }

        double left = bestLag > minLag ? cmndfScratch[bestLag - 1] : bestValue;
        double right = bestLag + 1 <= maxLag ? cmndfScratch[bestLag + 1] : bestValue;
        double shift = parabolicShift(left, bestValue, right);
        double refined = bestLag + shift;
        if (refined <= 0) {
            return -1;
        }
        return sampleRate / refined;
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

    private void onStreamConfig(int actualSampleRate) {
        if (actualSampleRate > 0) {
            sampleRate = actualSampleRate;
        }
    }

    private native boolean nativeStart(int requestedSampleRate, int framesPerRead);
    private native void nativeStop();
}
