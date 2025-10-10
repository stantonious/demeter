package com.example.demeterclient.ui.aoi;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Spinner;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.exifinterface.media.ExifInterface;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;

import com.example.demeterclient.MainActivity;
import com.example.demeterclient.R;
import com.example.demeterclient.SharedViewModel;

import java.io.IOException;
import java.io.InputStream;
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
    private List<Integer> aoiColors = new ArrayList<>();
    private int currentAugmentSize = 65;

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

        sharedViewModel.getAugmentSize().observe(getViewLifecycleOwner(), size -> {
            currentAugmentSize = size;
        });

        initializeAoiColors();
        setupAoiTypeSpinner();
        setupImageView();

        augmentButton.setOnClickListener(v -> {
            if (mainActivity != null) {
                sharedViewModel.setAugmentedResult(null); // Clear previous results
                sharedViewModel.setIsAugmenting(true);
                NavHostFragment.findNavController(AoisFragment.this).navigate(R.id.action_aoisFragment_to_resultsFragment);

                String plantType = sharedViewModel.getPlantType();
                String subType = sharedViewModel.getSubType();
                String age = sharedViewModel.getAge();
                mainActivity.requestAugmentedImage(imageUriString, selectedPlants, getScaledAoiPoints(), plantType, subType, age);
            }
        });

        return view;
    }

    private void initializeAoiColors() {
        aoiColors.add(Color.RED);
        aoiColors.add(Color.BLUE);
        aoiColors.add(Color.GREEN);
        aoiColors.add(Color.YELLOW);
        aoiColors.add(Color.CYAN);
        aoiColors.add(Color.MAGENTA);
    }

    private ArrayList<Integer> getScaledAoiPoints() {
        if (originalBitmap == null || aoiPoints.isEmpty()) {
            return new ArrayList<>();
        }

        ArrayList<Integer> scaledPoints = new ArrayList<>();
        float scaleX = 512.0f / originalBitmap.getWidth();
        float scaleY = 512.0f / originalBitmap.getHeight();

        for (int i = 0; i < aoiPoints.size(); i += 2) {
            scaledPoints.add((int) (aoiPoints.get(i) * scaleX));
            scaledPoints.add((int) (aoiPoints.get(i + 1) * scaleY));
        }
        return scaledPoints;
    }

    private void setupAoiTypeSpinner() {
        if (getContext() != null && selectedPlants != null) {
            ArrayAdapter<String> adapter = new ArrayAdapter<>(getContext(), android.R.layout.simple_spinner_item, selectedPlants);
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            aoiTypeSpinner.setAdapter(adapter);

            aoiTypeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    int color = aoiColors.get(position % aoiColors.size());
                    paint.setColor(color);
                    paint.setAlpha(128);
                }
                @Override
                public void onNothingSelected(AdapterView<?> parent) {
                    paint.setColor(aoiColors.get(0));
                    paint.setAlpha(128);
                }
            });
        }
    }

    private void setupImageView() {
        if (imageUriString != null) {
            try {
                Uri imageUri = Uri.parse(imageUriString);
                Bitmap bitmap = MediaStore.Images.Media.getBitmap(requireActivity().getContentResolver(), imageUri);

                InputStream inputStream = requireContext().getContentResolver().openInputStream(imageUri);
                ExifInterface exifInterface = new ExifInterface(inputStream);
                int orientation = exifInterface.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_UNDEFINED);
                Matrix matrix = new Matrix();
                switch (orientation) {
                    case ExifInterface.ORIENTATION_ROTATE_90:
                        matrix.postRotate(90);
                        break;
                    case ExifInterface.ORIENTATION_ROTATE_180:
                        matrix.postRotate(180);
                        break;
                    case ExifInterface.ORIENTATION_ROTATE_270:
                        matrix.postRotate(270);
                        break;
                    default:
                        break;
                }
                originalBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
                inputStream.close();

                mutableBitmap = originalBitmap.copy(Bitmap.Config.ARGB_8888, true);
                canvas = new Canvas(mutableBitmap);
                paint = new Paint();
                paint.setAlpha(128);
                if (!aoiColors.isEmpty()) {
                    paint.setColor(aoiColors.get(aoiTypeSpinner.getSelectedItemPosition() % aoiColors.size()));
                }

                previewImageView.setImageBitmap(mutableBitmap);

                previewImageView.setOnTouchListener((v, event) -> {
                    if (event.getAction() == MotionEvent.ACTION_DOWN) {
                        float[] touchPoint = {event.getX(), event.getY()};

                        Matrix inverse = new Matrix();
                        previewImageView.getImageMatrix().invert(inverse);
                        inverse.mapPoints(touchPoint);

                        int x = (int) touchPoint[0];
                        int y = (int) touchPoint[1];

                        float markerRadius = (currentAugmentSize / 512.0f) * (v.getWidth() / 2.0f);


                        aoiPoints.add(x);
                        aoiPoints.add(y);
                        canvas.drawCircle(x, y, markerRadius, paint);
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