package com.example.demeterclient.ui.aoi;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.demeterclient.FeasibilityActivity;
import com.example.demeterclient.MainActivity;
import com.example.demeterclient.R;
import com.example.demeterclient.SharedViewModel;
import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;

public class AoiSelectFragment extends Fragment implements AoiSelectAdapter.OnItemClickListener {

    private static final int REQUEST_IMAGE_CAPTURE = 1;
    private RecyclerView suggestionsRecyclerView;
    private AoiSelectAdapter adapter;
    private Button takePictureButton;
    private MainActivity mainActivity;
    private SharedViewModel sharedViewModel;
    private Uri photoURI;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mainActivity = (MainActivity) getActivity();
        sharedViewModel = new ViewModelProvider(requireActivity()).get(SharedViewModel.class);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_aoi_select, container, false);

        suggestionsRecyclerView = view.findViewById(R.id.suggestions_recycler_view);
        takePictureButton = view.findViewById(R.id.take_picture_button);

        suggestionsRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new AoiSelectAdapter(new ArrayList<>(), this);
        suggestionsRecyclerView.setAdapter(adapter);

        takePictureButton.setOnClickListener(v -> dispatchTakePictureIntent());

        sharedViewModel.getPlantSuggestions().observe(getViewLifecycleOwner(), suggestions -> {
            if (suggestions != null) {
                adapter.setSuggestions(suggestions);
            }
        });

        return view;
    }

    private void dispatchTakePictureIntent() {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (takePictureIntent.resolveActivity(requireActivity().getPackageManager()) != null) {
            File photoFile = null;
            try {
                photoFile = createImageFile();
            } catch (IOException ex) {
                // Handle the error
            }
            if (photoFile != null) {
                photoURI = FileProvider.getUriForFile(requireContext(),
                        "com.example.demeterclient.fileprovider",
                        photoFile);
                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI);
                startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE);
            }
        }
    }

    private File createImageFile() throws IOException {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String imageFileName = "JPEG_" + timeStamp + "_";
        File storageDir = requireActivity().getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        File image = File.createTempFile(
                imageFileName,  /* prefix */
                ".jpg",         /* suffix */
                storageDir      /* directory */
        );
        return image;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_IMAGE_CAPTURE && resultCode == getActivity().RESULT_OK) {
            Bundle bundle = new Bundle();
            bundle.putStringArrayList("selected_plants", new ArrayList<>(adapter.getSelectedPlants()));
            if (photoURI != null) {
                bundle.putString("image_uri", photoURI.toString());
            }
            NavHostFragment.findNavController(this).navigate(R.id.action_aoiSelectFragment_to_aoisFragment, bundle);
        }
    }

    @Override
    public void onItemClick(com.example.demeterclient.PlantSuggestion suggestion) {
        Intent intent = new Intent(getActivity(), FeasibilityActivity.class);
        intent.putExtra("feasibility_text", suggestion.getFeasibilityJson());
        startActivity(intent);
    }
}