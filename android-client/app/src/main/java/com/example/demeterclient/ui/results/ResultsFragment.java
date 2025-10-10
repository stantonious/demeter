package com.example.demeterclient.ui.results;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.widget.ViewPager2;
import com.example.demeterclient.ImageSliderAdapter;
import com.example.demeterclient.R;
import java.util.ArrayList;

public class ResultsFragment extends Fragment {

    private ViewPager2 viewPager;
    private String assetUri;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            assetUri = getArguments().getString("asset_uri");
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_results, container, false);
        viewPager = view.findViewById(R.id.image_slider);

        // In a real implementation, we would download the image from the assetUri
        // and add it to the adapter. For now, we'll just show a placeholder.
        ArrayList<byte[]> images = new ArrayList<>();
        // images.add(downloadedImage);
        ImageSliderAdapter adapter = new ImageSliderAdapter(images);
        viewPager.setAdapter(adapter);

        return view;
    }
}