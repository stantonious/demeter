package com.example.demeterclient;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import androidx.annotation.NonNull;
import io.getstream.photoview.PhotoView;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import androidx.lifecycle.ViewModelProvider;

public class ImageFragment extends Fragment {

    private static final String ARG_POSITION = "position";
    private int position;
    private SharedViewModel sharedViewModel;

    public static ImageFragment newInstance(int position) {
        ImageFragment fragment = new ImageFragment();
        Bundle args = new Bundle();
        args.putInt(ARG_POSITION, position);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        sharedViewModel = new ViewModelProvider(requireActivity()).get(SharedViewModel.class);
        if (getArguments() != null) {
            position = getArguments().getInt(ARG_POSITION);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_image, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        PhotoView imageView = view.findViewById(R.id.image_view);
        sharedViewModel.getAugmentedResult().observe(getViewLifecycleOwner(), images -> {
            if (images != null && position < images.size()) {
                byte[] image = images.get(position);
                if (image != null) {
                    Bitmap bitmap = BitmapFactory.decodeByteArray(image, 0, image.length);
                    imageView.setImageBitmap(bitmap);
                }
            }
        });
    }

}