package com.example.demeterclient;

import android.os.Bundle;
import android.util.Log;
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

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class SuggestFragment extends Fragment {

    private static final String TAG = "SuggestFragment";
    private Spinner plantTypeSpinner;
    private Spinner subTypeSpinner;
    private Spinner ageSpinner;
    private EditText numSuggestionsEditText;
    private Button suggestButton;
    private MainActivity mainActivity;
    private SharedViewModel sharedViewModel;
    private OkHttpClient httpClient;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mainActivity = (MainActivity) getActivity();
        sharedViewModel = new ViewModelProvider(requireActivity()).get(SharedViewModel.class);
        if (mainActivity != null) {
            httpClient = mainActivity.getHttpClient();
        }
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

        fetchPlantTypes();
        fetchPlantCharacteristics();

        // Populate spinners
        sharedViewModel.getPlantTypes().observe(getViewLifecycleOwner(), plantTypes -> {
            if (plantTypes != null) {
                ArrayAdapter<String> plantTypeAdapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_item, plantTypes);
                plantTypeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                plantTypeSpinner.setAdapter(plantTypeAdapter);
            }
        });

        sharedViewModel.getPlantCharacteristics().observe(getViewLifecycleOwner(), plantCharacteristics -> {
            if (plantCharacteristics != null) {
                ArrayAdapter<String> characteristicAdapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_item, plantCharacteristics);
                characteristicAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                subTypeSpinner.setAdapter(characteristicAdapter);
            }
        });

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

                mainActivity.requestLocationForSuggestion(plantType, subType, age, numSuggestions);
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

    private void fetchPlantTypes() {
        HttpUrl url = HttpUrl.parse(Constants.BASE_URL).newBuilder()
                .addPathSegment("demeter")
                .addPathSegment("data")
                .addPathSegment("types")
                .build();

        Request request = new Request.Builder().url(url).build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e(TAG, "Failed to fetch plant types", e);
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (response.isSuccessful()) {
                    try {
                        JSONObject json = new JSONObject(response.body().string());
                        org.json.JSONArray typesArray = json.getJSONArray("types");
                        ArrayList<String> types = new ArrayList<>();
                        for (int i = 0; i < typesArray.length(); i++) {
                            types.add(typesArray.getString(i));
                        }
                        sharedViewModel.setPlantTypes(types);
                    } catch (JSONException e) {
                        Log.e(TAG, "Failed to parse plant types", e);
                    }
                }
            }
        });
    }

    private void fetchPlantCharacteristics() {
        HttpUrl url = HttpUrl.parse(Constants.BASE_URL).newBuilder()
                .addPathSegment("demeter")
                .addPathSegment("data")
                .addPathSegment("characteristics")
                .build();

        Request request = new Request.Builder().url(url).build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e(TAG, "Failed to fetch plant characteristics", e);
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (response.isSuccessful()) {
                    try {
                        JSONObject json = new JSONObject(response.body().string());
                        org.json.JSONArray characteristicsArray = json.getJSONArray("characteristics");
                        ArrayList<String> characteristics = new ArrayList<>();
                        for (int i = 0; i < characteristicsArray.length(); i++) {
                            characteristics.add(characteristicsArray.getString(i));
                        }
                        sharedViewModel.setPlantCharacteristics(characteristics);
                    } catch (JSONException e) {
                        Log.e(TAG, "Failed to parse plant characteristics", e);
                    }
                }
            }
        });
    }
}