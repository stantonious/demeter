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
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

public class SettingsFragment extends Fragment {

    private EditText augmentSizeEditText;
    private MainActivity mainActivity;
    private SwipeRefreshLayout swipeRefreshLayout;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        mainActivity = (MainActivity) getActivity();
        return inflater.inflate(R.layout.fragment_settings, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        augmentSizeEditText = view.findViewById(R.id.augment_size_edit_text);
        swipeRefreshLayout = view.findViewById(R.id.settings_swipe_refresh);

        augmentSizeEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (mainActivity != null) {
                    mainActivity.setAugmentSize(getAugmentSize());
                }
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        swipeRefreshLayout.setOnRefreshListener(() -> {
            if (mainActivity != null) {
                mainActivity.reconnectBle();
                // Hide the refresh indicator after a short delay
                swipeRefreshLayout.postDelayed(() -> swipeRefreshLayout.setRefreshing(false), 1000);
            }
        });
    }

    public int getAugmentSize() {
        String augmentSizeStr = augmentSizeEditText.getText().toString();
        if (!augmentSizeStr.isEmpty()) {
            try {
                int size = Integer.parseInt(augmentSizeStr);
                return Math.max(5, Math.min(200, size));
            } catch (NumberFormatException e) {
                return 65; // Default value
            }
        }
        return 65; // Default value
    }
}