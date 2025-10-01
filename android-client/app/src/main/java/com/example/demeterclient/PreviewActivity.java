package com.example.demeterclient;

import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ImageView;
import androidx.appcompat.app.AppCompatActivity;

public class PreviewActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_preview);

        ImageView previewImageView = findViewById(R.id.preview_image_view);
        Button backButton = findViewById(R.id.back_button);

        String imageUriString = getIntent().getStringExtra("image_uri");
        if (imageUriString != null) {
            Uri imageUri = Uri.parse(imageUriString);
            previewImageView.setImageURI(imageUri);
        }

        backButton.setOnClickListener(v -> {
            finish();
        });
    }
}