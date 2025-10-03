package com.example.demeterclient;

import static android.app.PendingIntent.getActivity;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class PreviewActivity extends AppCompatActivity {

    private Uri imageUri;
    private int lastTouchX; // Add variable to hold X coordinate
    private int lastTouchY; // Add variable to hold Y coordinate
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
        // 1. Capture touch coordinates when the user taps the image
        //TODO move 512 to a const in mainActivity, this is the file size sent to BLEW
        previewImageView.setOnTouchListener((v, event) -> {
            lastTouchX = (int) ((512./previewImageView.getWidth()) * event.getX());
            lastTouchY = (int) ((512./previewImageView.getHeight()) *event.getY());
            return false; // Allow the event to pass to the OnClickListener
        });

        // 2. When the image is clicked, package the coordinates and the URI into the result
        previewImageView.setOnClickListener(v -> {
            Toast.makeText(PreviewActivity.this, "AOI selected: (" + lastTouchX + ", " + lastTouchY + ")", Toast.LENGTH_SHORT).show();
        });
        backButton.setOnClickListener(v -> {
            setResult(RESULT_CANCELED);
            finish();
        });

        sendButton.setOnClickListener(v -> {
            Intent resultIntent = new Intent();
            resultIntent.putExtra("image_uri", imageUri.toString());
            resultIntent.putExtra("aoi_x", lastTouchX); // Add X to the intent
            resultIntent.putExtra("aoi_y", lastTouchY);
            setResult(RESULT_OK, resultIntent);
            finish();
        });
    }
}