package com.example.tuner;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.tuner.databinding.ActivityMainBinding;

import java.text.DecimalFormat;

public class MainActivity extends AppCompatActivity implements TunerEngine.Listener {

    private static final DecimalFormat FREQ_FORMAT = new DecimalFormat("0.00");
    private static final DecimalFormat CENTS_FORMAT = new DecimalFormat("+0.0;-0.0");

    private ActivityMainBinding binding;
    private TunerEngine tunerEngine;
    private ActivityResultLauncher<String> permissionLauncher;
    private int neutralColor;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        tunerEngine = new TunerEngine(this);
        neutralColor = binding.centsOffset.getCurrentTextColor();

        permissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                granted -> {
                    if (granted) {
                        startTuner();
                    } else {
                        binding.status.setText(R.string.permission_rationale);
                    }
                });
    }

    @Override
    protected void onResume() {
        super.onResume();
        ensurePermission();
    }

    @Override
    protected void onPause() {
        super.onPause();
        tunerEngine.stop();
    }

    private void ensurePermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED) {
            startTuner();
        } else {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO);
        }
    }

    private void startTuner() {
        tunerEngine.start();
    }

    @Override
    public void onPitch(PitchResult result) {
        runOnUiThread(() -> renderResult(result));
    }

    private void renderResult(@NonNull PitchResult result) {
        if (!result.hasSignal) {
            binding.stringName.setText(getString(R.string.listening));
            binding.frequency.setText("0.00 Hz");
            binding.centsOffset.setText("+0.0 ¢");
            binding.centsOffset.setTextColor(neutralColor);
            binding.status.setText(R.string.no_signal);
        } else {
            binding.stringName.setText(result.nearestString);
            binding.frequency.setText(FREQ_FORMAT.format(result.frequencyHz) + " Hz");
            binding.centsOffset.setText(CENTS_FORMAT.format(result.cents) + " ¢");

            double abs = Math.abs(result.cents);
            int color;
            if (abs < 3) {
                color = ContextCompat.getColor(this, android.R.color.holo_green_light);
            } else if (abs < 10) {
                color = ContextCompat.getColor(this, android.R.color.holo_orange_light);
            } else {
                color = ContextCompat.getColor(this, android.R.color.holo_red_light);
            }
            binding.centsOffset.setTextColor(color);
            binding.status.setText(result.stable ? "已稳定" : "检测中…");
        }

        int level = (int) Math.max(0, Math.min(100, (result.amplitudeDb + 60) * 2));
        binding.powerMeter.setProgress(level);
    }
}
