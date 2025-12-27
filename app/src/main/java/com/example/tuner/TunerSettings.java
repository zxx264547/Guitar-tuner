package com.example.tuner;

import android.content.Context;
import android.content.SharedPreferences;

final class TunerSettings {
    static final int[] WINDOW_OPTIONS = {2048, 4096, 8192, 16384};
    static final int DEFAULT_WINDOW_SIZE = 16384;
    static final double DEFAULT_SMOOTHING_ALPHA = 0.08;
    static final double DEFAULT_NOISE_FLOOR_DB = -50.0;
    static final double DEFAULT_YIN_THRESHOLD = 0.12;

    final int windowSize;
    final double smoothingAlpha;
    final double noiseFloorDb;
    final double yinThreshold;

    private TunerSettings(int windowSize,
                          double smoothingAlpha,
                          double noiseFloorDb,
                          double yinThreshold) {
        this.windowSize = windowSize;
        this.smoothingAlpha = smoothingAlpha;
        this.noiseFloorDb = noiseFloorDb;
        this.yinThreshold = yinThreshold;
    }

    static TunerSettings load(Context context) {
        SharedPreferences prefs = context.getSharedPreferences("tuner_settings", Context.MODE_PRIVATE);
        int windowSize = prefs.getInt("window_size", DEFAULT_WINDOW_SIZE);
        double smoothingAlpha = prefs.getFloat("smoothing_alpha", (float) DEFAULT_SMOOTHING_ALPHA);
        double noiseFloorDb = prefs.getFloat("noise_floor_db", (float) DEFAULT_NOISE_FLOOR_DB);
        double yinThreshold = prefs.getFloat("yin_threshold", (float) DEFAULT_YIN_THRESHOLD);
        return new TunerSettings(windowSize, smoothingAlpha, noiseFloorDb, yinThreshold);
    }

    void save(Context context) {
        SharedPreferences prefs = context.getSharedPreferences("tuner_settings", Context.MODE_PRIVATE);
        prefs.edit()
                .putInt("window_size", windowSize)
                .putFloat("smoothing_alpha", (float) smoothingAlpha)
                .putFloat("noise_floor_db", (float) noiseFloorDb)
                .putFloat("yin_threshold", (float) yinThreshold)
                .apply();
    }

    TunerSettings withWindowSize(int value) {
        return new TunerSettings(value, smoothingAlpha, noiseFloorDb, yinThreshold);
    }

    TunerSettings withSmoothingAlpha(double value) {
        return new TunerSettings(windowSize, value, noiseFloorDb, yinThreshold);
    }

    TunerSettings withNoiseFloorDb(double value) {
        return new TunerSettings(windowSize, smoothingAlpha, value, yinThreshold);
    }

    TunerSettings withYinThreshold(double value) {
        return new TunerSettings(windowSize, smoothingAlpha, noiseFloorDb, value);
    }
}
