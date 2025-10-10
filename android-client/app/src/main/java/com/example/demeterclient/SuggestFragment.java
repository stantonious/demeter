package com.example.demeterclient;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

public class SuggestFragment extends Fragment {

    private Spinner plantTypeSpinner;
    private Spinner subTypeSpinner;
    private Spinner ageSpinner;
    private EditText numSuggestionsEditText;
    private Button suggestButton;
    private MainActivity mainActivity;
    private SharedViewModel sharedViewModel;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mainActivity = (MainActivity) getActivity();
        sharedViewModel = new ViewModelProvider(requireActivity()).get(SharedViewModel.class);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_suggest, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        plantTypeSpinner = view.findViewById(R.id.plant_type_spinner);
        subTypeSpinner = view.findViewById(R.id.sub_type_spinner);
        ageSpinner = view.findViewById(R.id.age_spinner);
        numSuggestionsEditText = view.findViewById(R.id.num_suggestions_edit_text);
        suggestButton = view.findViewById(R.id.suggest_button);

        // Populate spinners
        ArrayAdapter<CharSequence> plantTypeAdapter = ArrayAdapter.createFromResource(requireContext(),
                R.array.plant_types, android.R.layout.simple_spinner_item);
        plantTypeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        plantTypeSpinner.setAdapter(plantTypeAdapter);

        ArrayAdapter<CharSequence> subTypeAdapter = ArrayAdapter.createFromResource(requireContext(),
                R.array.sub_types, android.R.layout.simple_spinner_item);
        subTypeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        subTypeSpinner.setAdapter(subTypeAdapter);

        ArrayAdapter<CharSequence> ageAdapter = ArrayAdapter.createFromResource(requireContext(),
                R.array.age_types, android.R.layout.simple_spinner_item);
        ageAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        ageSpinner.setAdapter(ageAdapter);

        suggestButton.setOnClickListener(v -> {
            if (mainActivity != null) {
                String plantType = plantTypeSpinner.getSelectedItem().toString();
                String subType = subTypeSpinner.getSelectedItem().toString();
                String age = ageSpinner.getSelectedItem().toString();
                String numSuggestionsStr = numSuggestionsEditText.getText().toString();

                if (numSuggestionsStr.isEmpty()) {
                    Toast.makeText(getContext(), "Please enter the number of suggestions.", Toast.LENGTH_SHORT).show();
                    return;
                }
                int numSuggestions = Integer.parseInt(numSuggestionsStr);

                sharedViewModel.setPlantType(plantType);
                sharedViewModel.setSubType(subType);
                sharedViewModel.setAge(age);

                mainActivity.requestPlantSuggestion(plantType, subType, age, numSuggestions);
            }
        });

        sharedViewModel.getIsSuggesting().observe(getViewLifecycleOwner(), isSuggesting -> {
            if (isSuggesting) {
                view.findViewById(R.id.suggest_progress_bar).setVisibility(View.VISIBLE);
            } else {
                view.findViewById(R.id.suggest_progress_bar).setVisibility(View.GONE);
            }
        });
    }
}