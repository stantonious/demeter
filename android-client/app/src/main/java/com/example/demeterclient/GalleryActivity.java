package com.example.demeterclient;

import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class GalleryActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private GalleryAdapter adapter;
    private List<File> imageFiles;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_gallery);

        recyclerView = findViewById(R.id.gallery_recycler_view);
        recyclerView.setLayoutManager(new GridLayoutManager(this, 3)); // 3 columns

        imageFiles = loadImages();
        adapter = new GalleryAdapter(this, imageFiles, imageFile -> {
            // Handle image click
            Intent intent = new Intent(GalleryActivity.this, ImageResultsActivity.class);
            try {
                int clickedIndex = imageFiles.indexOf(imageFile);

                ArrayList<byte[]> allImagesBytes = new ArrayList<>();
                for (File file : imageFiles) {
                    allImagesBytes.add(Files.readAllBytes(file.toPath()));
                }

                ImageDataHolder.getInstance().setImageList(allImagesBytes);
                intent.putExtra("start_index", clickedIndex);
                startActivity(intent);
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
        recyclerView.setAdapter(adapter);
    }

    private List<File> loadImages() {
        List<File> files = new ArrayList<>();
        File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        if (storageDir != null && storageDir.exists()) {
            File[] imageFiles = storageDir.listFiles((dir, name) -> name.endsWith(".jpg"));
            if (imageFiles != null) {
                // Sort files by last modified date, newest first
                Arrays.sort(imageFiles, Comparator.comparingLong(File::lastModified).reversed());
                files.addAll(Arrays.asList(imageFiles));
            }
        }
        return files;
    }
}