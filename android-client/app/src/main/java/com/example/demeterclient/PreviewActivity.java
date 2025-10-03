package com.example.demeterclient;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import java.util.ArrayList;

public class PreviewActivity extends AppCompatActivity {

    private Uri imageUri;
    private ArrayList<Integer> aoiPoints = new ArrayList<>();

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

        previewImageView.setOnTouchListener((v, event) -> {
            // Normalize coordinates to a 512x512 grid
            int touchX = (int) ((512.0 / previewImageView.getWidth()) * event.getX());
            int touchY = (int) ((512.0 / previewImageView.getHeight()) * event.getY());

            aoiPoints.add(touchX);
            aoiPoints.add(touchY);

            // Show a toast with the number of AOIs selected
            int numAois = aoiPoints.size() / 2;
            Toast.makeText(PreviewActivity.this, numAois + " AOI(s) selected", Toast.LENGTH_SHORT).show();

            return false;
        });

        backButton.setOnClickListener(v -> {
            setResult(RESULT_CANCELED);
            finish();
        });

        sendButton.setOnClickListener(v -> {
            Intent resultIntent = new Intent();
            resultIntent.putExtra("image_uri", imageUri.toString());
            resultIntent.putIntegerArrayListExtra("aoi_list", aoiPoints);
            setResult(RESULT_OK, resultIntent);
            finish();
        });
    }
}