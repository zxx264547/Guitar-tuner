package com.example.tuner;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowCompat;

import com.example.tuner.databinding.ActivityAlgorithmSettingsBinding;
import com.google.android.material.slider.Slider;

public class AlgorithmSettingsActivity extends AppCompatActivity {

    private ActivityAlgorithmSettingsBinding binding;
    private TunerSettings currentSettings;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        binding = ActivityAlgorithmSettingsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        applyEdgeInsets(binding.getRoot());

        currentSettings = TunerSettings.load(this);
        setupSliders();
        setupButtons();
    }

    private void setupSliders() {
        int windowIndex = windowIndex(currentSettings.windowSize);
        binding.sliderWindowSize.setValue(windowIndex);
        binding.valueWindowSize.setText(String.valueOf(currentSettings.windowSize));
        binding.sliderWindowSize.addOnChangeListener((slider, value, fromUser) -> {
            int size = TunerSettings.WINDOW_OPTIONS[(int) value];
            binding.valueWindowSize.setText(String.valueOf(size));
        });
        binding.sliderWindowSize.addOnSliderTouchListener(new Slider.OnSliderTouchListener() {
            @Override
            public void onStartTrackingTouch(@NonNull Slider slider) {
            }

            @Override
            public void onStopTrackingTouch(@NonNull Slider slider) {
                int size = TunerSettings.WINDOW_OPTIONS[(int) slider.getValue()];
                applySettings(currentSettings.withWindowSize(size));
            }
        });

        binding.sliderSmoothing.setValue((float) currentSettings.smoothingAlpha);
        binding.valueSmoothing.setText(String.format("%.2f", currentSettings.smoothingAlpha));
        binding.sliderSmoothing.addOnChangeListener((slider, value, fromUser) ->
                binding.valueSmoothing.setText(String.format("%.2f", value)));
        binding.sliderSmoothing.addOnSliderTouchListener(new Slider.OnSliderTouchListener() {
            @Override
            public void onStartTrackingTouch(@NonNull Slider slider) {
            }

            @Override
            public void onStopTrackingTouch(@NonNull Slider slider) {
                applySettings(currentSettings.withSmoothingAlpha(slider.getValue()));
            }
        });

        binding.sliderNoiseFloor.setValue((float) currentSettings.noiseFloorDb);
        binding.valueNoiseFloor.setText(String.format("%.1f", currentSettings.noiseFloorDb));
        binding.sliderNoiseFloor.addOnChangeListener((slider, value, fromUser) ->
                binding.valueNoiseFloor.setText(String.format("%.1f", value)));
        binding.sliderNoiseFloor.addOnSliderTouchListener(new Slider.OnSliderTouchListener() {
            @Override
            public void onStartTrackingTouch(@NonNull Slider slider) {
            }

            @Override
            public void onStopTrackingTouch(@NonNull Slider slider) {
                applySettings(currentSettings.withNoiseFloorDb(slider.getValue()));
            }
        });

        binding.sliderYinThreshold.setValue((float) currentSettings.yinThreshold);
        binding.valueYinThreshold.setText(String.format("%.2f", currentSettings.yinThreshold));
        binding.sliderYinThreshold.addOnChangeListener((slider, value, fromUser) ->
                binding.valueYinThreshold.setText(String.format("%.2f", value)));
        binding.sliderYinThreshold.addOnSliderTouchListener(new Slider.OnSliderTouchListener() {
            @Override
            public void onStartTrackingTouch(@NonNull Slider slider) {
            }

            @Override
            public void onStopTrackingTouch(@NonNull Slider slider) {
                applySettings(currentSettings.withYinThreshold(slider.getValue()));
            }
        });
    }

    private void setupButtons() {
        binding.resetButton.setOnClickListener(v -> {
            TunerSettings defaults = TunerSettings.defaults();
            TunerSettings updated = currentSettings
                    .withWindowSize(defaults.windowSize)
                    .withSmoothingAlpha(defaults.smoothingAlpha)
                    .withNoiseFloorDb(defaults.noiseFloorDb)
                    .withYinThreshold(defaults.yinThreshold);
            applySettings(updated);
            updateSliderValues(updated);
        });

        binding.infoButton.setOnClickListener(v -> showInfoDialog());
    }

    private void updateSliderValues(@NonNull TunerSettings settings) {
        binding.sliderWindowSize.setValue(windowIndex(settings.windowSize));
        binding.valueWindowSize.setText(String.valueOf(settings.windowSize));

        binding.sliderSmoothing.setValue((float) settings.smoothingAlpha);
        binding.valueSmoothing.setText(String.format("%.2f", settings.smoothingAlpha));

        binding.sliderNoiseFloor.setValue((float) settings.noiseFloorDb);
        binding.valueNoiseFloor.setText(String.format("%.1f", settings.noiseFloorDb));

        binding.sliderYinThreshold.setValue((float) settings.yinThreshold);
        binding.valueYinThreshold.setText(String.format("%.2f", settings.yinThreshold));
    }

    private void showInfoDialog() {
        String message = "窗口大小：参与分析的采样点数。越大越稳、抗噪更好，但响应更慢、对快速变化不敏感。\n\n"
                + "平滑系数：频率平滑的权重（指数平滑）。越小越稳、抖动更少，但反应更迟钝；越大越灵敏但更抖。\n\n"
                + "噪声门限(dB)：低于该 RMS dB 时认为无信号。阈值越高越容易忽略弱音，越低越容易把噪声当成信号。\n\n"
                + "YIN 阈值：CMNDF 的置信门槛。越小越严格、误检更少但可能漏检；越大更容易出结果但可能不稳定。";
        new AlertDialog.Builder(this)
                .setTitle("参数说明")
                .setMessage(message)
                .setPositiveButton("知道了", null)
                .show();
    }

    private void applySettings(@NonNull TunerSettings updated) {
        currentSettings = updated;
        currentSettings.save(this);
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

    private int windowIndex(int windowSize) {
        for (int i = 0; i < TunerSettings.WINDOW_OPTIONS.length; i++) {
            if (TunerSettings.WINDOW_OPTIONS[i] == windowSize) {
                return i;
            }
        }
        return 3;
    }
}
