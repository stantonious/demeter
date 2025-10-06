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

        ArrayList<byte[]> imageList = (ArrayList<byte[]>) getIntent().getSerializableExtra("image_list");
        if (imageList != null) {
            ImageSliderAdapter adapter = new ImageSliderAdapter(this, imageList);
            imageSlider.setAdapter(adapter);
        }
    }
}