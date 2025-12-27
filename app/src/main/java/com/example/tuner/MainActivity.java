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
import com.github.mikephil.charting.charts.LineChart;
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
    private static final DecimalFormat CENTS_FORMAT = new DecimalFormat("+0.0;-0.0");
    private static final float HISTORY_WINDOW_SECONDS = 5f;
    private static final float Y_PADDING_CENTS = 5f;
    private static final float MIN_Y_RANGE_CENTS = 10f;

    private ActivityMainBinding binding;
    private TunerEngine tunerEngine;
    private ActivityResultLauncher<String> permissionLauncher;
    private int neutralColor;
    private LineChart deviationChart;
    private LineDataSet deviationDataSet;
    private final List<Entry> deviationEntries = new ArrayList<>();
    private long chartStartMs = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        tunerEngine = new TunerEngine(this);
        neutralColor = binding.centsOffset.getCurrentTextColor();
        setupChart(binding.deviationChart);

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
        resetChart();
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

        appendDeviation(result);
        int level = (int) Math.max(0, Math.min(100, (result.amplitudeDb + 60) * 2));
        binding.powerMeter.setProgress(level);
    }

    private void setupChart(@NonNull LineChart chart) {
        deviationChart = chart;
        deviationDataSet = new LineDataSet(deviationEntries, "Deviation (cents)");
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
        left.setAxisMinimum(-50f);
        left.setAxisMaximum(50f);
        left.setDrawGridLines(true);
        left.setTextColor(neutralColor);
        left.setGranularity(5f);

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
            deviationEntries.add(new Entry(x, (float) result.cents));
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
            min -= MIN_Y_RANGE_CENTS / 2f;
            max += MIN_Y_RANGE_CENTS / 2f;
        }
        float padding = Math.max(Y_PADDING_CENTS, (max - min) * 0.1f);
        float axisMin = min - padding;
        float axisMax = max + padding;
        if (axisMax - axisMin < MIN_Y_RANGE_CENTS) {
            float center = (axisMin + axisMax) / 2f;
            axisMin = center - MIN_Y_RANGE_CENTS / 2f;
            axisMax = center + MIN_Y_RANGE_CENTS / 2f;
        }
        YAxis left = deviationChart.getAxisLeft();
        left.setAxisMinimum(axisMin);
        left.setAxisMaximum(axisMax);
    }
}
