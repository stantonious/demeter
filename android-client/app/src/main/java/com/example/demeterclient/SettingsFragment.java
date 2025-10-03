package com.example.demeterclient;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

public class SettingsFragment extends Fragment {

    private EditText numSuggestionsEditText;
    private Spinner plantTypeSpinner;
    private ImageView ledIndicator;
    private TextView statusTextView;
    private MainActivity mainActivity;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        mainActivity = (MainActivity) getActivity();
        return inflater.inflate(R.layout.fragment_settings, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        numSuggestionsEditText = view.findViewById(R.id.num_suggestions_edit_text);
        plantTypeSpinner = view.findViewById(R.id.plant_type_spinner);
        ledIndicator = view.findViewById(R.id.led_indicator);
        statusTextView = view.findViewById(R.id.status_text_view);

        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(requireActivity(),
                R.array.plant_types, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        plantTypeSpinner.setAdapter(adapter);

        numSuggestionsEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                mainActivity.setNumSuggestions(getNumSuggestions());
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        plantTypeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                mainActivity.setPlantType(position);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        // Set initial values
        mainActivity.setNumSuggestions(getNumSuggestions());
        mainActivity.setPlantType(getPlantType());
    }

    public int getNumSuggestions() {
        String numSuggestionsStr = numSuggestionsEditText.getText().toString();
        if (!numSuggestionsStr.isEmpty()) {
            try {
                return Integer.parseInt(numSuggestionsStr);
            } catch (NumberFormatException e) {
                return 1;
            }
        }
        return 1;
    }

    public int getPlantType() {
        return plantTypeSpinner.getSelectedItemPosition();
    }

    public void updateLedIndicator(int drawableId) {
        if (ledIndicator != null && isAdded()) {
            ledIndicator.setImageDrawable(ContextCompat.getDrawable(requireContext(), drawableId));
        }
    }

    public void setStatusText(String text) {
        if (statusTextView != null) {
            statusTextView.setText(text);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        updateConnectionStatus(mainActivity.getCurrentConnectionStatus());
    }

    public void updateConnectionStatus(MainActivity.BleConnectionStatus status) {
        if (!isAdded()) return;

        int drawableId;
        String statusText;
        switch (status) {
            case SCANNING:
                drawableId = R.drawable.led_yellow;
                statusText = "Scanning...";
                break;
            case CONNECTING:
                drawableId = R.drawable.led_yellow;
                statusText = "Connecting...";
                break;
            case CONNECTED:
                drawableId = R.drawable.led_green;
                statusText = "Connected";
                break;
            case ERROR:
                drawableId = R.drawable.led_red;
                statusText = "Error";
                break;
            case DISCONNECTED:
            default:
                drawableId = R.drawable.led_grey;
                statusText = "Disconnected";
                break;
        }
        updateLedIndicator(drawableId);
        setStatusText(statusText);
    }
}