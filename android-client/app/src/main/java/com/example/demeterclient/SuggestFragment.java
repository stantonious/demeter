package com.example.demeterclient;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.widget.ViewPager2;

import java.util.List;

public class SuggestFragment extends Fragment {

    private Button getSuggestionButton;
    private TextView suggestionTextView;
    private Spinner suggestionSpinner;
    private TextView uploadProgressTextView;
    private ProgressBar uploadProgressBar;
    private Button takePictureButton;
    private Button getAugmentedImageButton;
    private TextView augmentedImageProgressTextView;
    private ProgressBar augmentedImageProgressBar;
    private ViewPager2 imageSlider;
    private ImageSliderAdapter imageSliderAdapter;

    private MainActivity mainActivity;

    public interface OnSuggestionSelectedListener {
        void onSuggestionSelected(String plantName);
    }
    private OnSuggestionSelectedListener listener;

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (context instanceof OnSuggestionSelectedListener) {
            listener = (OnSuggestionSelectedListener) context;
        } else {
            throw new RuntimeException(context.toString()
                    + " must implement OnSuggestionSelectedListener");
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        mainActivity = (MainActivity) getActivity();
        return inflater.inflate(R.layout.fragment_suggest, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        getSuggestionButton = view.findViewById(R.id.get_suggestion_button);
        suggestionTextView = view.findViewById(R.id.suggestion_text_view);
        suggestionSpinner = view.findViewById(R.id.suggestion_spinner);
        uploadProgressTextView = view.findViewById(R.id.upload_progress_text_view);
        uploadProgressBar = view.findViewById(R.id.upload_progress_bar);
        takePictureButton = view.findViewById(R.id.take_picture_button);
        getAugmentedImageButton = view.findViewById(R.id.get_augmented_image_button);
        augmentedImageProgressTextView = view.findViewById(R.id.augmented_image_progress_text_view);
        augmentedImageProgressBar = view.findViewById(R.id.augmented_image_progress_bar);
        imageSlider = view.findViewById(R.id.image_slider);

        imageSliderAdapter = new ImageSliderAdapter(requireActivity(), mainActivity.imageList);
        imageSlider.setAdapter(imageSliderAdapter);

        getSuggestionButton.setOnClickListener(v -> {
            mainActivity.requestSuggestion();
        });

        takePictureButton.setOnClickListener(v -> {
            mainActivity.dispatchTakePictureIntent();
        });

        getAugmentedImageButton.setOnClickListener(v -> {
            mainActivity.fetchAugmentedImage();
        });

        suggestionSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String selectedPlant = (String) parent.getItemAtPosition(position);
                if (listener != null) {
                    listener.onSuggestionSelected(selectedPlant);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        // Initially disable buttons
        getSuggestionButton.setEnabled(true);
        takePictureButton.setEnabled(false);
        getAugmentedImageButton.setEnabled(false);
    }

    // Public methods to be called from MainActivity
    public void setSuggestionText(String text) {
        suggestionTextView.setText(text);
    }

    public void setSuggestions(List<String> suggestions) {
        if (getContext() != null && suggestions != null && !suggestions.isEmpty()) {
            ArrayAdapter<String> adapter = new ArrayAdapter<>(getContext(), android.R.layout.simple_spinner_item, suggestions);
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            suggestionSpinner.setAdapter(adapter);
            suggestionSpinner.setVisibility(View.VISIBLE);
            suggestionTextView.setVisibility(View.VISIBLE);
        } else {
            suggestionSpinner.setVisibility(View.GONE);
            suggestionTextView.setText("No suggestions available.");
        }
    }

    public void setUploadProgressText(String text) {
        uploadProgressTextView.setText(text);
    }

    public void setUploadProgressVisibility(int visibility) {
        uploadProgressTextView.setVisibility(visibility);
    }

    public void setUploadProgressBarVisibility(int visibility) {
        uploadProgressBar.setVisibility(visibility);
    }

    public void setUploadProgress(int progress) {
        uploadProgressBar.setProgress(progress);
    }

    public void setAugmentedImageProgressText(String text) {
        augmentedImageProgressTextView.setText(text);
    }

    public void setAugmentedImageProgressVisibility(int visibility) {
        augmentedImageProgressTextView.setVisibility(visibility);
    }

    public void setAugmentedImageProgressBarVisibility(int visibility) {
        augmentedImageProgressBar.setVisibility(visibility);
    }

    public void setAugmentedImageProgress(int progress) {
        augmentedImageProgressBar.setProgress(progress);
    }

    public void enableGetSuggestionButton(boolean enabled) {
        getSuggestionButton.setEnabled(enabled);
    }

    public void enableTakePictureButton(boolean enabled) {
        takePictureButton.setEnabled(enabled);
    }

    public void enableGetAugmentedImageButton(boolean enabled) {
        getAugmentedImageButton.setEnabled(enabled);
    }

    public void updateImageSlider() {
        imageSliderAdapter.notifyDataSetChanged();
    }

    public void setImageSliderVisibility(int visibility) {
        imageSlider.setVisibility(visibility);
    }

}