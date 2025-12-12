package com.example.tuner;

class PitchResult {
    final boolean hasSignal;
    final double frequencyHz;
    final double cents;
    final String nearestString;
    final double amplitudeDb;
    final boolean stable;

    PitchResult(boolean hasSignal,
                double frequencyHz,
                double cents,
                String nearestString,
                double amplitudeDb,
                boolean stable) {
        this.hasSignal = hasSignal;
        this.frequencyHz = frequencyHz;
        this.cents = cents;
        this.nearestString = nearestString;
        this.amplitudeDb = amplitudeDb;
        this.stable = stable;
    }
}
