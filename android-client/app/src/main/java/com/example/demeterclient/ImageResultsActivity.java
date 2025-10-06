package com.example.demeterclient;

import android.os.Bundle;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.viewpager2.widget.ViewPager2;
import java.util.ArrayList;

public class ImageResultsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_image_results);

        ViewPager2 imageSlider = findViewById(R.id.image_slider);

        List<byte[]> imageList = ImageDataHolder.getInstance().getImageList();
        if (imageList != null && !imageList.isEmpty()) {
            ImageSliderAdapter adapter = new ImageSliderAdapter(this, imageList);
            imageSlider.setAdapter(adapter);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        ImageDataHolder.getInstance().clearImageList();
    }
}