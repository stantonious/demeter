package com.example.demeterclient;

import android.os.Bundle;
import android.widget.TextView;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import org.json.JSONException;
import org.json.JSONObject;

public class FeasibilityActivity extends AppCompatActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_feasibility);

        TextView overallFeasibilityTextView = findViewById(R.id.overall_feasibility_text_view);
        TextView soilAnalysisTextView = findViewById(R.id.soil_analysis_text_view);
        TextView climateAnalysisTextView = findViewById(R.id.climate_analysis_text_view);
        TextView waterAnalysisTextView = findViewById(R.id.water_analysis_text_view);

        String feasibilityText = getIntent().getStringExtra("feasibility_text");
        if (feasibilityText != null) {
            try {
                JSONObject feasibilityJson = new JSONObject(feasibilityText);
                overallFeasibilityTextView.setText(feasibilityJson.optString("overall_feasibility", "N/A"));
                soilAnalysisTextView.setText(feasibilityJson.optString("soil_analysis", "N/A"));
                climateAnalysisTextView.setText(feasibilityJson.optString("climate_analysis", "N/A"));
                waterAnalysisTextView.setText(feasibilityJson.optString("water_analysis", "N/A"));
            } catch (JSONException e) {
                e.printStackTrace();
                overallFeasibilityTextView.setText("Error parsing feasibility data.");
            }
        }
    }
}