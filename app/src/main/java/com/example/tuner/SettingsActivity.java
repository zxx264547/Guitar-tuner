package com.example.tuner;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowCompat;

public class SettingsActivity extends AppCompatActivity {

    private com.example.tuner.databinding.ActivitySettingsMenuBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        binding = com.example.tuner.databinding.ActivitySettingsMenuBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        applyEdgeInsets(binding.getRoot());

        binding.openTuningSettings.setOnClickListener(v ->
                startActivity(new Intent(this, TuningSettingsActivity.class)));
        binding.openAlgorithmSettings.setOnClickListener(v ->
                startActivity(new Intent(this, AlgorithmSettingsActivity.class)));
    }

    private void applyEdgeInsets(android.view.View root) {
        int baseTop = root.getPaddingTop();
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
            int topInset = insets.getInsets(WindowInsetsCompat.Type.systemBars()).top;
            view.setPadding(view.getPaddingLeft(),
                    baseTop + topInset,
                    view.getPaddingRight(),
                    view.getPaddingBottom());
            return insets;
        });
    }
}
