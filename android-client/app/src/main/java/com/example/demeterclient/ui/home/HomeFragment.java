package com.example.demeterclient.ui.home;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.example.demeterclient.R;

public class HomeFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_home, container, false);

        Button settingsButton = view.findViewById(R.id.button_settings);
        Button suggestButton = view.findViewById(R.id.button_suggest);
        Button dataButton = view.findViewById(R.id.button_data);

        settingsButton.setOnClickListener(v -> NavHostFragment.findNavController(HomeFragment.this)
                .navigate(R.id.action_homeFragment_to_settingsFragment));

        suggestButton.setOnClickListener(v -> NavHostFragment.findNavController(HomeFragment.this)
                .navigate(R.id.action_homeFragment_to_suggestFragment));

        dataButton.setOnClickListener(v -> NavHostFragment.findNavController(HomeFragment.this)
                .navigate(R.id.action_homeFragment_to_dataFragment));

        return view;
    }
}