package com.example.demeterclient;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ImageView;
import androidx.appcompat.app.AppCompatActivity;

public class PreviewActivity extends AppCompatActivity {

    private Uri imageUri;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_preview);

        ImageView previewImageView = findViewById(R.id.preview_image_view);
        Button backButton = findViewById(R.id.back_button);
        Button sendButton = findViewById(R.id.send_button);

        String imageUriString = getIntent().getStringExtra("image_uri");
        if (imageUriString != null) {
            imageUri = Uri.parse(imageUriString);
            previewImageView.setImageURI(imageUri);
        }

        backButton.setOnClickListener(v -> {
            setResult(RESULT_CANCELED);
            finish();
        });

        sendButton.setOnClickListener(v -> {
            Intent resultIntent = new Intent();
            resultIntent.putExtra("image_uri", imageUri.toString());
            setResult(RESULT_OK, resultIntent);
            finish();
        });
    }
}