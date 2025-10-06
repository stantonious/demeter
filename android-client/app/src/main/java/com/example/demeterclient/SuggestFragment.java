package com.example.demeterclient;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.widget.ViewPager2;

public class SuggestFragment extends Fragment {

    private Button getSuggestionButton;
    private TextView suggestionTextView;
    private TextView uploadProgressTextView;
    private ProgressBar uploadProgressBar;
    private Button takePictureButton;
    private Button getAugmentedImageButton;
    private TextView augmentedImageProgressTextView;
    private ProgressBar augmentedImageProgressBar;
    private ViewPager2 imageSlider;
    private ImageSliderAdapter imageSliderAdapter;

    private MainActivity mainActivity;

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

        // Initially disable buttons
        getSuggestionButton.setEnabled(true);
        takePictureButton.setEnabled(false);
        getAugmentedImageButton.setEnabled(false);
    }

    // Public methods to be called from MainActivity
    public void setSuggestionText(String text) {
        suggestionTextView.setText(text);
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