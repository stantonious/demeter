package com.example.demeterclient;

import android.content.Intent;
import android.os.Bundle;
import android.widget.ImageView;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.viewpager2.widget.ViewPager2;
import java.util.List;

public class ImageResultsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_image_results);

        Toolbar toolbar = findViewById(R.id.toolbar_image_results);
        setSupportActionBar(toolbar);

        ImageView galleryIcon = findViewById(R.id.gallery_icon);
        galleryIcon.setOnClickListener(v -> {
            Intent intent = new Intent(ImageResultsActivity.this, GalleryActivity.class);
            startActivity(intent);
        });

        ViewPager2 imageSlider = findViewById(R.id.image_slider);

        List<byte[]> imageList = ImageDataHolder.getInstance().getImageList();
        if (imageList != null && !imageList.isEmpty()) {
            ImageSliderAdapter adapter = new ImageSliderAdapter(this, imageList);
            imageSlider.setAdapter(adapter);
            int startIndex = getIntent().getIntExtra("start_index", 0);
            imageSlider.setCurrentItem(startIndex, false);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        ImageDataHolder.getInstance().clearImageList();
    }
}