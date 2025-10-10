package com.example.demeterclient.ui.aoi;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Spinner;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import com.example.demeterclient.MainActivity;
import com.example.demeterclient.R;
import com.example.demeterclient.SharedViewModel;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class AoisFragment extends Fragment {

    private ImageView previewImageView;
    private Spinner aoiTypeSpinner;
    private Button augmentButton;
    private MainActivity mainActivity;
    private SharedViewModel sharedViewModel;

    private Bitmap originalBitmap;
    private Bitmap mutableBitmap;
    private Canvas canvas;
    private Paint paint;
    private ArrayList<Integer> aoiPoints = new ArrayList<>();
    private ArrayList<String> selectedPlants;
    private String imageUriString;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mainActivity = (MainActivity) getActivity();
        sharedViewModel = new ViewModelProvider(requireActivity()).get(SharedViewModel.class);
        if (getArguments() != null) {
            selectedPlants = getArguments().getStringArrayList("selected_plants");
            imageUriString = getArguments().getString("image_uri");
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_aois, container, false);

        previewImageView = view.findViewById(R.id.preview_image_view);
        aoiTypeSpinner = view.findViewById(R.id.aoi_type_spinner);
        augmentButton = view.findViewById(R.id.augment_button);

        setupAoiTypeSpinner();
        setupImageView();

        augmentButton.setOnClickListener(v -> {
            if (mainActivity != null) {
                String plantType = sharedViewModel.getPlantType();
                String subType = sharedViewModel.getSubType();
                String age = sharedViewModel.getAge();

                mainActivity.requestAugmentedImage(imageUriString, selectedPlants, aoiPoints, plantType, subType, age);
            }
        });

        return view;
    }

    private void setupAoiTypeSpinner() {
        if (getContext() != null && selectedPlants != null) {
            ArrayAdapter<String> adapter = new ArrayAdapter<>(getContext(), android.R.layout.simple_spinner_item, selectedPlants);
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            aoiTypeSpinner.setAdapter(adapter);
        }
    }

    private void setupImageView() {
        if (imageUriString != null) {
            try {
                Uri imageUri = Uri.parse(imageUriString);
                originalBitmap = MediaStore.Images.Media.getBitmap(requireActivity().getContentResolver(), imageUri);
                mutableBitmap = originalBitmap.copy(Bitmap.Config.ARGB_8888, true);
                canvas = new Canvas(mutableBitmap);
                paint = new Paint();
                paint.setColor(Color.RED);
                paint.setAlpha(128); // Semi-transparent
                previewImageView.setImageBitmap(mutableBitmap);

                previewImageView.setOnTouchListener((v, event) -> {
                    if (event.getAction() == MotionEvent.ACTION_DOWN) {
                        int x = (int) event.getX();
                        int y = (int) event.getY();
                        aoiPoints.add(x);
                        aoiPoints.add(y);
                        canvas.drawCircle(x, y, 30, paint); // Draw a circle for the AOI
                        previewImageView.invalidate();
                    }
                    return true;
                });

            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}