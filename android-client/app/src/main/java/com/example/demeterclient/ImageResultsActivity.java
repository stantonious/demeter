package com.example.demeterclient;

import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.widget.ImageView;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.viewpager2.widget.ViewPager2;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

public class ImageResultsActivity extends AppCompatActivity {

    private static final String TAG = "ImageResultsActivity";
    private ViewPager2 imageSlider;
    private ImageSliderAdapter adapter;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_image_results);

        imageSlider = findViewById(R.id.image_slider);
        ImageView saveIcon = findViewById(R.id.save_icon);
        ImageView galleryIcon = findViewById(R.id.gallery_icon);

        List<byte[]> imageList = ImageDataHolder.getInstance().getImageList();
        if (imageList != null && !imageList.isEmpty()) {
            adapter = new ImageSliderAdapter(this, imageList);
            imageSlider.setAdapter(adapter);
        }

        saveIcon.setOnClickListener(v -> saveCurrentImage());
        galleryIcon.setOnClickListener(v -> {
            Intent intent = new Intent(ImageResultsActivity.this, GalleryActivity.class);
            startActivity(intent);
        });
    }

    private void saveCurrentImage() {
        if (adapter == null || adapter.getItemCount() == 0) {
            Toast.makeText(this, "No image to save.", Toast.LENGTH_SHORT).show();
            return;
        }

        int currentPosition = imageSlider.getCurrentItem();
        byte[] imageData = adapter.getImageData(currentPosition);

        if (imageData == null) {
            Toast.makeText(this, "Error: Could not get image data.", Toast.LENGTH_SHORT).show();
            return;
        }

        File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        if (storageDir != null) {
            try {
                String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
                String fileName = (currentPosition == 0) ? "OG_" + timeStamp + ".jpg" : "AUG_" + timeStamp + ".jpg";
                File imageFile = new File(storageDir, fileName);
                try (FileOutputStream fos = new FileOutputStream(imageFile)) {
                    fos.write(imageData);
                    Log.d(TAG, "Image saved to " + imageFile.getAbsolutePath());
                    Toast.makeText(this, "Image saved to gallery.", Toast.LENGTH_SHORT).show();
                }
            } catch (IOException e) {
                Log.e(TAG, "Failed to save image", e);
                Toast.makeText(this, "Failed to save image.", Toast.LENGTH_SHORT).show();
            }
        } else {
            Toast.makeText(this, "Could not access storage.", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        ImageDataHolder.getInstance().clearImageList();
    }
}