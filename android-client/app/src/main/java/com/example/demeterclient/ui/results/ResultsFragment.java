package com.example.demeterclient.ui.results;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.viewpager2.widget.ViewPager2;
import com.example.demeterclient.ImageSliderAdapter;
import com.example.demeterclient.R;
import com.example.demeterclient.SharedViewModel;

public class ResultsFragment extends Fragment {

    private ViewPager2 viewPager;
    private ProgressBar loadingIndicator;
    private SharedViewModel sharedViewModel;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        sharedViewModel = new ViewModelProvider(requireActivity()).get(SharedViewModel.class);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_results, container, false);
        viewPager = view.findViewById(R.id.image_slider);
        loadingIndicator = view.findViewById(R.id.loading_indicator);
        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        sharedViewModel.getIsAugmenting().observe(getViewLifecycleOwner(), isAugmenting -> {
            if (isAugmenting) {
                loadingIndicator.setVisibility(View.VISIBLE);
                viewPager.setVisibility(View.GONE);
            } else {
                loadingIndicator.setVisibility(View.GONE);
            }
        });

        sharedViewModel.getAugmentedResult().observe(getViewLifecycleOwner(), images -> {
            if (images != null && !images.isEmpty()) {
                ImageSliderAdapter adapter = new ImageSliderAdapter(images);
                viewPager.setAdapter(adapter);
                viewPager.setVisibility(View.VISIBLE);
                loadingIndicator.setVisibility(View.GONE);
                sharedViewModel.setIsAugmenting(false); // Reset loading state
            }
        });
    }
}