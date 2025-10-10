package com.example.demeterclient.ui.data;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.example.demeterclient.PlotView;
import com.example.demeterclient.R;
import com.example.demeterclient.SharedViewModel;

public class DataFragment extends Fragment {

    private PlotView plotView;
    private SharedViewModel sharedViewModel;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        sharedViewModel = new ViewModelProvider(requireActivity()).get(SharedViewModel.class);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_data, container, false);
        plotView = view.findViewById(R.id.plot_view);

        sharedViewModel.getSensorDataHistory().observe(getViewLifecycleOwner(), history -> {
            if (history != null) {
                plotView.updateData(history);
            }
        });

        return view;
    }
}