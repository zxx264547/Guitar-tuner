package com.example.tuner;

import android.content.Context;
import android.content.SharedPreferences;

final class TunerSettings {
    static final int[] WINDOW_OPTIONS = {2048, 4096, 8192, 16384};
    static final int DEFAULT_WINDOW_SIZE = 16384;
    static final double DEFAULT_SMOOTHING_ALPHA = 0.08;
    static final double DEFAULT_NOISE_FLOOR_DB = -50.0;
    static final double DEFAULT_YIN_THRESHOLD = 0.12;
    static final String[] DEFAULT_STRING_NOTES = {"E2", "A2", "D3", "G3", "B3", "E4"};
    static final String[] NOTE_OPTIONS = buildNoteOptions(2, 5);

    final int windowSize;
    final double smoothingAlpha;
    final double noiseFloorDb;
    final double yinThreshold;
    final String[] stringNotes;
    final double[] stringFrequencies;

    private TunerSettings(int windowSize,
                          double smoothingAlpha,
                          double noiseFloorDb,
                          double yinThreshold,
                          String[] stringNotes) {
        this.windowSize = windowSize;
        this.smoothingAlpha = smoothingAlpha;
        this.noiseFloorDb = noiseFloorDb;
        this.yinThreshold = yinThreshold;
        this.stringNotes = stringNotes;
        this.stringFrequencies = toFrequencies(stringNotes);
    }

    static TunerSettings load(Context context) {
        SharedPreferences prefs = context.getSharedPreferences("tuner_settings", Context.MODE_PRIVATE);
        int windowSize = prefs.getInt("window_size", DEFAULT_WINDOW_SIZE);
        double smoothingAlpha = prefs.getFloat("smoothing_alpha", (float) DEFAULT_SMOOTHING_ALPHA);
        double noiseFloorDb = prefs.getFloat("noise_floor_db", (float) DEFAULT_NOISE_FLOOR_DB);
        double yinThreshold = prefs.getFloat("yin_threshold", (float) DEFAULT_YIN_THRESHOLD);
        String savedNotes = prefs.getString("string_notes", null);
        String[] stringNotes = savedNotes == null ? DEFAULT_STRING_NOTES : parseNotes(savedNotes);
        return new TunerSettings(windowSize, smoothingAlpha, noiseFloorDb, yinThreshold, stringNotes);
    }

    static TunerSettings defaults() {
        return new TunerSettings(DEFAULT_WINDOW_SIZE,
                DEFAULT_SMOOTHING_ALPHA,
                DEFAULT_NOISE_FLOOR_DB,
                DEFAULT_YIN_THRESHOLD,
                DEFAULT_STRING_NOTES);
    }

    void save(Context context) {
        SharedPreferences prefs = context.getSharedPreferences("tuner_settings", Context.MODE_PRIVATE);
        prefs.edit()
                .putInt("window_size", windowSize)
                .putFloat("smoothing_alpha", (float) smoothingAlpha)
                .putFloat("noise_floor_db", (float) noiseFloorDb)
                .putFloat("yin_threshold", (float) yinThreshold)
                .putString("string_notes", joinNotes(stringNotes))
                .apply();
    }

    TunerSettings withWindowSize(int value) {
        return new TunerSettings(value, smoothingAlpha, noiseFloorDb, yinThreshold, stringNotes);
    }

    TunerSettings withSmoothingAlpha(double value) {
        return new TunerSettings(windowSize, value, noiseFloorDb, yinThreshold, stringNotes);
    }

    TunerSettings withNoiseFloorDb(double value) {
        return new TunerSettings(windowSize, smoothingAlpha, value, yinThreshold, stringNotes);
    }

    TunerSettings withYinThreshold(double value) {
        return new TunerSettings(windowSize, smoothingAlpha, noiseFloorDb, value, stringNotes);
    }

    TunerSettings withStringNotes(String[] value) {
        return new TunerSettings(windowSize, smoothingAlpha, noiseFloorDb, yinThreshold, value);
    }

    private static String joinNotes(String[] notes) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < notes.length; i++) {
            if (i > 0) {
                builder.append(",");
            }
            builder.append(notes[i]);
        }
        return builder.toString();
    }

    private static String[] parseNotes(String value) {
        String[] parts = value.split(",");
        if (parts.length != 6) {
            return DEFAULT_STRING_NOTES;
        }
        return parts;
    }

    private static double[] toFrequencies(String[] notes) {
        double[] result = new double[notes.length];
        for (int i = 0; i < notes.length; i++) {
            result[i] = noteToFrequency(notes[i]);
        }
        return result;
    }

    private static double noteToFrequency(String note) {
        if (note == null || note.length() < 2) {
            return 0;
        }
        int octave = Character.getNumericValue(note.charAt(note.length() - 1));
        String name = note.substring(0, note.length() - 1);
        int semitone = noteToSemitone(name);
        int midi = (octave + 1) * 12 + semitone;
        return 440.0 * Math.pow(2.0, (midi - 69) / 12.0);
    }

    private static int noteToSemitone(String name) {
        switch (name) {
            case "C":
                return 0;
            case "C#":
                return 1;
            case "D":
                return 2;
            case "D#":
                return 3;
            case "E":
                return 4;
            case "F":
                return 5;
            case "F#":
                return 6;
            case "G":
                return 7;
            case "G#":
                return 8;
            case "A":
                return 9;
            case "A#":
                return 10;
            case "B":
                return 11;
            default:
                return 0;
        }
    }

    private static String[] buildNoteOptions(int minOctave, int maxOctave) {
        String[] names = {"C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"};
        int total = (maxOctave - minOctave + 1) * names.length;
        String[] options = new String[total];
        int index = 0;
        for (int octave = minOctave; octave <= maxOctave; octave++) {
            for (String name : names) {
                options[index++] = name + octave;
            }
        }
        return options;
    }
}
