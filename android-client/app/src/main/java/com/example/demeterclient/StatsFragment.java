package com.example.demeterclient;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import java.util.ArrayList;
import java.util.HashMap;

public class StatsFragment extends Fragment {

    private PlotView livePlotView;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_stats, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        livePlotView = view.findViewById(R.id.live_plot_view);
    }

    public void updatePlot(HashMap<String, ArrayList<Float>> historyData) {
        if (livePlotView != null) {
            livePlotView.setData(historyData);
        }
    }
}