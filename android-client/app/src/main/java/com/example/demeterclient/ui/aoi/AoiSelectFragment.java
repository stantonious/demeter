package com.example.demeterclient.ui.aoi;

import android.content.Intent;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.demeterclient.MainActivity;
import com.example.demeterclient.R;
import java.util.ArrayList;

public class AoiSelectFragment extends Fragment implements AoiSelectAdapter.OnFeasibilityClickListener {

    private static final int REQUEST_IMAGE_CAPTURE = 1;
    private RecyclerView suggestionsRecyclerView;
    private AoiSelectAdapter adapter;
    private Button takePictureButton;
    private MainActivity mainActivity;
    private ArrayList<String> suggestions;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mainActivity = (MainActivity) getActivity();
        if (getArguments() != null) {
            suggestions = getArguments().getStringArrayList("suggestions");
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_aoi_select, container, false);

        suggestionsRecyclerView = view.findViewById(R.id.suggestions_recycler_view);
        takePictureButton = view.findViewById(R.id.take_picture_button);

        suggestionsRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new AoiSelectAdapter(suggestions, this);
        suggestionsRecyclerView.setAdapter(adapter);

        takePictureButton.setOnClickListener(v -> {
            Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            if (takePictureIntent.resolveActivity(requireActivity().getPackageManager()) != null) {
                startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE);
            }
        });

        return view;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_IMAGE_CAPTURE && resultCode == getActivity().RESULT_OK) {
            Bundle bundle = new Bundle();
            bundle.putStringArrayList("selected_plants", new ArrayList<>(adapter.getSelectedPlants()));
            if (data != null && data.getData() != null) {
                bundle.putString("image_uri", data.getData().toString());
            }
            NavHostFragment.findNavController(this).navigate(R.id.action_aoiSelectFragment_to_aoisFragment, bundle);
        }
    }

    @Override
    public void onFeasibilityClick(String plantName) {
        if (mainActivity != null) {
            mainActivity.requestFeasibilityAnalysis(plantName);
        }
    }
}