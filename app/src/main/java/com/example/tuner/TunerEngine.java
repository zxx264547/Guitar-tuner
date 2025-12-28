package com.example.tuner;

import android.util.Log;

import androidx.annotation.NonNull;

import java.util.Arrays;

class TunerEngine {

    interface Listener {
        void onPitch(PitchResult result);
        void onAudioApiUsed(boolean isAAudio);
    }

    private static final String TAG = "TunerEngine";
    private static final double MIN_FREQ = 70.0;    // lower than low E to keep margin
    private static final double MAX_FREQ = 1300.0;  // upper bound to avoid octave errors

    private final Listener listener;

    private volatile boolean running;
    private int sampleRate = 44100;
    private int windowSize = 8192;
    private int hopSize = 2048;
    private double smoothingAlpha = 0.1;
    private double noiseFloorDb = -50.0;
    private double yinThreshold = 0.15;
    private double noiseEstimateDb = -70.0;
    private double noiseEstimateAlpha = 0.05;
    private double noiseMarginDb = 6.0;
    private double highFreqDampingHz = 246.94;
    private double highFreqStepFactor = 0.35;
    private double smoothedFrequency = 0;
    private int stableHits = 0;
    private double[] windowCoefficients = new double[windowSize];
    private double[] diffScratch;
    private double[] cmndfScratch;
    private short[] ringBuffer = new short[windowSize];
    private short[] analysisBuffer = new short[windowSize];
    private int ringWritePos = 0;
    private int ringFilled = 0;
    private int pendingSamples = 0;
    private double[] freqHistory = new double[5];
    private double[] freqScratch = new double[5];
    private int freqIndex = 0;
    private int freqCount = 0;
    private double lastFrequency = 0;
    private String[] stringLabels = {"E2", "A2", "D3", "G3", "B3", "E4"};
    private double[] stringFrequencies = {82.4069, 110.0, 146.832, 195.998, 246.942, 329.628};

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
        boolean started = nativeStart(sampleRate, hopSize);
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

        appendToRing(buffer, read);
        pendingSamples += read;

        while (ringFilled >= windowSize && pendingSamples >= hopSize) {
            pendingSamples -= hopSize;
            fillWindow(analysisBuffer);

            double amplitudeDb = computeRmsDb(analysisBuffer, windowSize);
            updateNoiseEstimate(amplitudeDb);
            double dynamicThreshold = Math.max(noiseFloorDb, noiseEstimateDb + noiseMarginDb);
            boolean hasEnergy = amplitudeDb > dynamicThreshold;
            double frequency = hasEnergy ? detectFrequency(analysisBuffer, windowSize) : -1;
            double filtered = frequency > 0 ? addFrequencySample(frequency) : 0;

            if (filtered > 0) {
                double stabilized = stabilizeFrequency(filtered, amplitudeDb, dynamicThreshold);
                smoothedFrequency = smoothFrequency(stabilized);
                lastFrequency = smoothedFrequency;
            } else {
                smoothedFrequency = 0;
                lastFrequency = 0;
                resetFrequencyHistory();
            }

            PitchResult result = mapToString(smoothedFrequency, amplitudeDb, filtered > 0 && hasEnergy);
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
        int windowedSize = Math.min(size, windowSize);
        double[] samples = new double[windowedSize];
        for (int i = 0; i < windowedSize; i++) {
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
            int limit = windowedSize - lag;
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
        for (int lag = minLag; lag <= maxLag; lag++) {
            double value = cmndfScratch[lag];
            if (value < yinThreshold) {
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
        double maxAccept = Math.min(0.5, yinThreshold * 2.0);
        if (bestValue > maxAccept) {
            return -1;
        }
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
        return smoothedFrequency + smoothingAlpha * (measured - smoothedFrequency);
    }

    private PitchResult mapToString(double freq, double amplitudeDb, boolean hasSignal) {
        if (!hasSignal || freq <= 0) {
            stableHits = 0;
            return new PitchResult(false, 0, 0, "", amplitudeDb, false);
        }

        int bestIndex = 0;
        double bestDiff = Double.MAX_VALUE;
        double cents = 0;

        for (int i = 0; i < stringFrequencies.length; i++) {
            double diffCents = 1200 * log2(freq / stringFrequencies[i]);
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

        return new PitchResult(true, freq, cents, stringLabels[bestIndex], amplitudeDb, stable);
    }

    private double log2(double value) {
        return Math.log(value) / Math.log(2.0);
    }

    private void prepareWindow() {
        windowCoefficients = new double[windowSize];
        for (int i = 0; i < windowSize; i++) {
            windowCoefficients[i] = 0.5 * (1 - Math.cos(2 * Math.PI * i / (windowSize - 1)));
        }
    }

    private void updateNoiseEstimate(double amplitudeDb) {
        if (amplitudeDb < noiseEstimateDb) {
            noiseEstimateDb = amplitudeDb;
        } else {
            noiseEstimateDb += noiseEstimateAlpha * (amplitudeDb - noiseEstimateDb);
        }
        if (noiseEstimateDb > -20) {
            noiseEstimateDb = -20;
        }
    }

    private double stabilizeFrequency(double candidate, double amplitudeDb, double thresholdDb) {
        if (lastFrequency <= 0) {
            return candidate;
        }
        double ratio = candidate / lastFrequency;
        if (ratio < 0.85 && amplitudeDb < thresholdDb + 6.0) {
            return lastFrequency;
        }
        if (candidate >= highFreqDampingHz) {
            return lastFrequency + (candidate - lastFrequency) * highFreqStepFactor;
        }
        return candidate;
    }

    private void onStreamConfig(int actualSampleRate) {
        if (actualSampleRate > 0) {
            sampleRate = actualSampleRate;
        }
    }

    private void onAudioApi(int api) {
        // 1 = AAudio (see oboe::AudioApi mapping in native)
        listener.onAudioApiUsed(api == 1);
    }

    void applyConfig(@NonNull TunerSettings settings) {
        windowSize = settings.windowSize;
        hopSize = Math.max(256, windowSize / 4);
        smoothingAlpha = settings.smoothingAlpha;
        noiseFloorDb = settings.noiseFloorDb;
        yinThreshold = settings.yinThreshold;
        stringLabels = settings.stringNotes;
        stringFrequencies = settings.stringFrequencies;
        prepareWindow();
        diffScratch = null;
        cmndfScratch = null;
        ringBuffer = new short[windowSize];
        analysisBuffer = new short[windowSize];
        ringWritePos = 0;
        ringFilled = 0;
        pendingSamples = 0;
        freqHistory = new double[5];
        freqScratch = new double[5];
        freqIndex = 0;
        freqCount = 0;
        noiseEstimateDb = noiseFloorDb - 20.0;
        smoothedFrequency = 0;
        lastFrequency = 0;
        stableHits = 0;
    }

    private void appendToRing(short[] buffer, int read) {
        for (int i = 0; i < read; i++) {
            ringBuffer[ringWritePos] = buffer[i];
            ringWritePos++;
            if (ringWritePos == windowSize) {
                ringWritePos = 0;
            }
            if (ringFilled < windowSize) {
                ringFilled++;
            }
        }
    }

    private void fillWindow(short[] out) {
        int start = ringWritePos - windowSize;
        if (start < 0) {
            start += windowSize;
        }
        for (int i = 0; i < windowSize; i++) {
            int idx = start + i;
            if (idx >= windowSize) {
                idx -= windowSize;
            }
            out[i] = ringBuffer[idx];
        }
    }

    private double addFrequencySample(double frequency) {
        freqHistory[freqIndex] = frequency;
        freqIndex = (freqIndex + 1) % freqHistory.length;
        if (freqCount < freqHistory.length) {
            freqCount++;
        }
        return medianFrequency();
    }

    private double medianFrequency() {
        if (freqCount == 0) {
            return 0;
        }
        System.arraycopy(freqHistory, 0, freqScratch, 0, freqCount);
        Arrays.sort(freqScratch, 0, freqCount);
        int mid = freqCount / 2;
        if (freqCount % 2 == 1) {
            return freqScratch[mid];
        }
        return (freqScratch[mid - 1] + freqScratch[mid]) / 2.0;
    }

    private void resetFrequencyHistory() {
        freqIndex = 0;
        freqCount = 0;
    }

    private native boolean nativeStart(int requestedSampleRate, int framesPerRead);
    private native void nativeStop();
}
