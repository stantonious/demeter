package com.example.demeterclient;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

public class SettingsFragment extends Fragment {

    private EditText augmentSizeEditText;
    private MainActivity mainActivity;
    private SwipeRefreshLayout swipeRefreshLayout;
    private SharedViewModel sharedViewModel;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mainActivity = (MainActivity) getActivity();
        sharedViewModel = new ViewModelProvider(requireActivity()).get(SharedViewModel.class);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_settings, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        augmentSizeEditText = view.findViewById(R.id.augment_size_edit_text);
        swipeRefreshLayout = view.findViewById(R.id.settings_swipe_refresh);

        // Set the initial text from the ViewModel
        sharedViewModel.getAugmentSize().observe(getViewLifecycleOwner(), size -> {
            augmentSizeEditText.setText(String.valueOf(size));
        });

        augmentSizeEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                try {
                    int size = Integer.parseInt(s.toString());
                    sharedViewModel.setAugmentSize(Math.max(5, Math.min(200, size)));
                } catch (NumberFormatException e) {
                    // Ignore, or set to default
                }
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        swipeRefreshLayout.setOnRefreshListener(() -> {
            if (mainActivity != null) {
                mainActivity.reconnectBle();
                swipeRefreshLayout.postDelayed(() -> swipeRefreshLayout.setRefreshing(false), 1000);
            }
        });
    }
}