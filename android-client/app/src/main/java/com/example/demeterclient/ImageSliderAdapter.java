package com.example.demeterclient;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import java.util.List;

public class ImageSliderAdapter extends FragmentStateAdapter {

    private List<byte[]> images;

    public ImageSliderAdapter(@NonNull FragmentActivity fragmentActivity, List<byte[]> images) {
        super(fragmentActivity);
        this.images = images;
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        return ImageFragment.newInstance(images.get(position));
    }

    @Override
    public int getItemCount() {
        return images.size();
    }
}