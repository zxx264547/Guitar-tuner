package com.example.tuner;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

public class SettingsActivity extends AppCompatActivity {

    private com.example.tuner.databinding.ActivitySettingsMenuBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = com.example.tuner.databinding.ActivitySettingsMenuBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.openTuningSettings.setOnClickListener(v ->
                startActivity(new Intent(this, TuningSettingsActivity.class)));
        binding.openAlgorithmSettings.setOnClickListener(v ->
                startActivity(new Intent(this, AlgorithmSettingsActivity.class)));
    }
}
