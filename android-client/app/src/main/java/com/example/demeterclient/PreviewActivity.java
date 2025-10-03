package com.example.demeterclient;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.MotionEvent;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import java.io.IOException;
import java.util.ArrayList;

public class PreviewActivity extends AppCompatActivity {

    private Uri imageUri;
    private ArrayList<Integer> aoiPoints = new ArrayList<>();
    private ImageView previewImageView;
    private Button clearButton;

    private Bitmap originalBitmap;
    private Bitmap mutableBitmap;
    private Canvas canvas;
    private Paint paint;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_preview);

        previewImageView = findViewById(R.id.preview_image_view);
        Button backButton = findViewById(R.id.back_button);
        Button sendButton = findViewById(R.id.send_button);
        clearButton = findViewById(R.id.clear_button);

        String imageUriString = getIntent().getStringExtra("image_uri");
        if (imageUriString != null) {
            imageUri = Uri.parse(imageUriString);
            try {
                originalBitmap = MediaStore.Images.Media.getBitmap(this.getContentResolver(), imageUri);
                mutableBitmap = originalBitmap.copy(Bitmap.Config.ARGB_8888, true);
                previewImageView.setImageBitmap(mutableBitmap);

                paint = new Paint();
                paint.setColor(Color.RED);
                paint.setStyle(Paint.Style.FILL);
                paint.setAlpha(128); // 50% transparent

            } catch (IOException e) {
                e.printStackTrace();
                Toast.makeText(this, "Failed to load image.", Toast.LENGTH_SHORT).show();
            }
        }

        previewImageView.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                if (originalBitmap == null) return true;

                float[] touchPoint = { event.getX(), event.getY() };
                android.graphics.Matrix inverse = new android.graphics.Matrix();
                previewImageView.getImageMatrix().invert(inverse);
                inverse.mapPoints(touchPoint);
                int bmpX = (int) touchPoint[0];
                int bmpY = (int) touchPoint[1];

                if (bmpX >= 0 && bmpX < originalBitmap.getWidth() && bmpY >= 0 && bmpY < originalBitmap.getHeight()) {
                    int normalizedX = (int) (bmpX * (512.0 / originalBitmap.getWidth()));
                    int normalizedY = (int) (bmpY * (512.0 / originalBitmap.getHeight()));
                    aoiPoints.add(normalizedX);
                    aoiPoints.add(normalizedY);

                    drawMarkers();

                    int numAois = aoiPoints.size() / 2;
                    Toast.makeText(PreviewActivity.this, numAois + " AOI(s) selected", Toast.LENGTH_SHORT).show();
                }
            }
            return true;
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

        clearButton.setOnClickListener(v -> {
            aoiPoints.clear();
            mutableBitmap = originalBitmap.copy(Bitmap.Config.ARGB_8888, true);
            previewImageView.setImageBitmap(mutableBitmap);
            Toast.makeText(PreviewActivity.this, "AOIs cleared", Toast.LENGTH_SHORT).show();
        });
    }

    private void drawMarkers() {
        mutableBitmap = originalBitmap.copy(Bitmap.Config.ARGB_8888, true);
        canvas = new Canvas(mutableBitmap);

        for (int i = 0; i < aoiPoints.size(); i += 2) {
            float bmpX = aoiPoints.get(i) * (originalBitmap.getWidth() / 512.0f);
            float bmpY = aoiPoints.get(i + 1) * (originalBitmap.getHeight() / 512.0f);
            canvas.drawCircle(bmpX, bmpY, 20f, paint); // Radius 20
        }
        previewImageView.setImageBitmap(mutableBitmap);
    }
}