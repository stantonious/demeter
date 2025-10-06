package com.example.demeterclient;

import android.os.Bundle;
import android.widget.TextView;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

public class FeasibilityActivity extends AppCompatActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_feasibility);

        TextView feasibilityTextView = findViewById(R.id.feasibility_text_view);

        String feasibilityText = getIntent().getStringExtra("feasibility_text");
        if (feasibilityText != null) {
            feasibilityTextView.setText(feasibilityText);
        }
    }
}