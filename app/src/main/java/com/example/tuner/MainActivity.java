package com.example.tuner;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.SystemClock;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.tuner.databinding.ActivityMainBinding;
import com.google.android.material.slider.Slider;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.LimitLine;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.ValueFormatter;

import java.util.ArrayList;
import java.text.DecimalFormat;
import java.util.List;

public class MainActivity extends AppCompatActivity implements TunerEngine.Listener {

    private static final DecimalFormat FREQ_FORMAT = new DecimalFormat("0.00");
    private static final DecimalFormat SEMITONE_FORMAT = new DecimalFormat("+0.00;-0.00");
    private static final float HISTORY_WINDOW_SECONDS = 5f;
    private static final float Y_PADDING_SEMITONES = 0.05f;
    private static final float MIN_Y_RANGE_SEMITONES = 0.1f;

    private ActivityMainBinding binding;
    private TunerEngine tunerEngine;
    private ActivityResultLauncher<String> permissionLauncher;
    private int neutralColor;
    private LineChart deviationChart;
    private LineDataSet deviationDataSet;
    private final List<Entry> deviationEntries = new ArrayList<>();
    private long chartStartMs = 0;
    private TunerSettings currentSettings;
    private boolean isTunerRunning = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        tunerEngine = new TunerEngine(this);
        neutralColor = binding.centsOffset.getCurrentTextColor();
        setupChart(binding.deviationChart);
        loadSettings();
        initSliders();

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
        isTunerRunning = false;
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
        resetChart();
        isTunerRunning = true;
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
            binding.centsOffset.setText("+0.00 半音");
            binding.centsOffset.setTextColor(neutralColor);
            binding.status.setText(R.string.no_signal);
        } else {
            binding.stringName.setText(result.nearestString);
            binding.frequency.setText(FREQ_FORMAT.format(result.frequencyHz) + " Hz");
            binding.centsOffset.setText(SEMITONE_FORMAT.format(result.cents / 100.0) + " 半音");

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

        appendDeviation(result);
        int level = (int) Math.max(0, Math.min(100, (result.amplitudeDb + 60) * 2));
        binding.powerMeter.setProgress(level);
    }

    private void loadSettings() {
        currentSettings = TunerSettings.load(this);
        tunerEngine.applyConfig(currentSettings);
    }

    private void applySettings(TunerSettings updated) {
        currentSettings = updated;
        currentSettings.save(this);
        tunerEngine.stop();
        tunerEngine.applyConfig(currentSettings);
        if (isTunerRunning) {
            tunerEngine.start();
        }
    }

    private void initSliders() {
        binding.labelWindowSize.setText("窗口大小（推荐 16384）");
        binding.labelSmoothing.setText("平滑系数（推荐 0.08）");
        binding.labelNoiseFloor.setText("噪声门限(dB)（推荐 -50）");
        binding.labelYinThreshold.setText("YIN 阈值（推荐 0.12）");

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

    private int windowIndex(int windowSize) {
        for (int i = 0; i < TunerSettings.WINDOW_OPTIONS.length; i++) {
            if (TunerSettings.WINDOW_OPTIONS[i] == windowSize) {
                return i;
            }
        }
        return 3;
    }

    private void setupChart(@NonNull LineChart chart) {
        deviationChart = chart;
        deviationDataSet = new LineDataSet(deviationEntries, "偏差（半音）");
        deviationDataSet.setMode(LineDataSet.Mode.CUBIC_BEZIER);
        deviationDataSet.setLineWidth(2f);
        deviationDataSet.setDrawCircles(false);
        deviationDataSet.setDrawValues(false);
        deviationDataSet.setColor(ContextCompat.getColor(this, android.R.color.holo_blue_light));

        LineData data = new LineData(deviationDataSet);
        chart.setData(data);
        chart.getDescription().setEnabled(false);
        chart.getLegend().setEnabled(false);
        chart.setTouchEnabled(false);
        chart.setDragEnabled(false);
        chart.setScaleEnabled(false);
        chart.setPinchZoom(false);
        chart.setExtraOffsets(4f, 4f, 4f, 4f);

        XAxis xAxis = chart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setDrawGridLines(false);
        xAxis.setTextColor(neutralColor);
        xAxis.setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                return String.format("%.1fs", value);
            }
        });

        YAxis left = chart.getAxisLeft();
        left.removeAllLimitLines();
        LimitLine zeroLine = new LimitLine(0f, "");
        zeroLine.setLineWidth(2f);
        zeroLine.setLineColor(ContextCompat.getColor(this, android.R.color.darker_gray));
        left.addLimitLine(zeroLine);
        left.setAxisMinimum(-0.5f);
        left.setAxisMaximum(0.5f);
        left.setDrawGridLines(true);
        left.setTextColor(neutralColor);
        left.setGranularity(0.1f);

        chart.getAxisRight().setEnabled(false);
    }

    private void resetChart() {
        deviationEntries.clear();
        chartStartMs = 0;
        if (deviationChart != null && deviationChart.getData() != null) {
            deviationDataSet.notifyDataSetChanged();
            deviationChart.getData().notifyDataChanged();
            deviationChart.invalidate();
        }
    }

    private void appendDeviation(@NonNull PitchResult result) {
        long now = SystemClock.elapsedRealtime();
        if (chartStartMs == 0) {
            chartStartMs = now;
        }
        float x = (now - chartStartMs) / 1000f;
        if (result.hasSignal) {
            deviationEntries.add(new Entry(x, (float) (result.cents / 100.0)));
        } else {
            deviationEntries.add(new Entry(x, 0f));
        }

        float minX = x - HISTORY_WINDOW_SECONDS;
        while (!deviationEntries.isEmpty() && deviationEntries.get(0).getX() < minX) {
            deviationEntries.remove(0);
        }

        updateYAxis();
        deviationDataSet.notifyDataSetChanged();
        deviationChart.getData().notifyDataChanged();
        deviationChart.notifyDataSetChanged();
        deviationChart.setVisibleXRangeMaximum(HISTORY_WINDOW_SECONDS);
        deviationChart.moveViewToX(x);
    }

    private void updateYAxis() {
        if (deviationEntries.isEmpty()) {
            return;
        }
        float min = Float.MAX_VALUE;
        float max = -Float.MAX_VALUE;
        for (Entry entry : deviationEntries) {
            float y = entry.getY();
            if (y < min) {
                min = y;
            }
            if (y > max) {
                max = y;
            }
        }
        if (min == Float.MAX_VALUE || max == -Float.MAX_VALUE) {
            return;
        }
        if (min == max) {
            min -= MIN_Y_RANGE_SEMITONES / 2f;
            max += MIN_Y_RANGE_SEMITONES / 2f;
        }
        float padding = Math.max(Y_PADDING_SEMITONES, (max - min) * 0.1f);
        float axisMin = min - padding;
        float axisMax = max + padding;
        if (axisMax - axisMin < MIN_Y_RANGE_SEMITONES) {
            float center = (axisMin + axisMax) / 2f;
            axisMin = center - MIN_Y_RANGE_SEMITONES / 2f;
            axisMax = center + MIN_Y_RANGE_SEMITONES / 2f;
        }
        YAxis left = deviationChart.getAxisLeft();
        left.setAxisMinimum(axisMin);
        left.setAxisMaximum(axisMax);
    }
}


