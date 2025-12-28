package com.example.tuner;

import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Spinner;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.example.tuner.databinding.ActivityTuningSettingsBinding;

public class TuningSettingsActivity extends AppCompatActivity {

    private ActivityTuningSettingsBinding binding;
    private TunerSettings currentSettings;
    private boolean initializingTunings = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityTuningSettingsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        currentSettings = TunerSettings.load(this);
        setupTunings();
        setupButtons();
    }

    private void setupTunings() {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_dropdown_item,
                TunerSettings.NOTE_OPTIONS);

        initializingTunings = true;
        bindSpinner(binding.spinnerString6, adapter, currentSettings.stringNotes[0]);
        bindSpinner(binding.spinnerString5, adapter, currentSettings.stringNotes[1]);
        bindSpinner(binding.spinnerString4, adapter, currentSettings.stringNotes[2]);
        bindSpinner(binding.spinnerString3, adapter, currentSettings.stringNotes[3]);
        bindSpinner(binding.spinnerString2, adapter, currentSettings.stringNotes[4]);
        bindSpinner(binding.spinnerString1, adapter, currentSettings.stringNotes[5]);
        initializingTunings = false;
    }

    private void bindSpinner(Spinner spinner, ArrayAdapter<String> adapter, String value) {
        spinner.setAdapter(adapter);
        int index = findNoteIndex(value);
        if (index >= 0) {
            spinner.setSelection(index);
        }
        spinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                if (initializingTunings) {
                    return;
                }
                saveTuningsFromUi();
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {
            }
        });
    }

    private void saveTuningsFromUi() {
        String[] notes = new String[6];
        notes[0] = (String) binding.spinnerString6.getSelectedItem();
        notes[1] = (String) binding.spinnerString5.getSelectedItem();
        notes[2] = (String) binding.spinnerString4.getSelectedItem();
        notes[3] = (String) binding.spinnerString3.getSelectedItem();
        notes[4] = (String) binding.spinnerString2.getSelectedItem();
        notes[5] = (String) binding.spinnerString1.getSelectedItem();
        applySettings(currentSettings.withStringNotes(notes));
    }

    private void setupButtons() {
        binding.resetButton.setOnClickListener(v -> {
            TunerSettings defaults = TunerSettings.defaults();
            TunerSettings updated = currentSettings.withStringNotes(defaults.stringNotes);
            applySettings(updated);
            updateTuningValues(updated);
        });
    }

    private void updateTuningValues(@NonNull TunerSettings settings) {
        initializingTunings = true;
        binding.spinnerString6.setSelection(findNoteIndex(settings.stringNotes[0]));
        binding.spinnerString5.setSelection(findNoteIndex(settings.stringNotes[1]));
        binding.spinnerString4.setSelection(findNoteIndex(settings.stringNotes[2]));
        binding.spinnerString3.setSelection(findNoteIndex(settings.stringNotes[3]));
        binding.spinnerString2.setSelection(findNoteIndex(settings.stringNotes[4]));
        binding.spinnerString1.setSelection(findNoteIndex(settings.stringNotes[5]));
        initializingTunings = false;
    }

    private void applySettings(@NonNull TunerSettings updated) {
        currentSettings = updated;
        currentSettings.save(this);
    }

    private int findNoteIndex(String value) {
        for (int i = 0; i < TunerSettings.NOTE_OPTIONS.length; i++) {
            if (TunerSettings.NOTE_OPTIONS[i].equals(value)) {
                return i;
            }
        }
        return -1;
    }
}
