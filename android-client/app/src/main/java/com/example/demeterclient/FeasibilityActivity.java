package com.example.demeterclient;

import android.os.Bundle;
import android.widget.TextView;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class FeasibilityActivity extends AppCompatActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_feasibility);

        TextView feasibilityScoreTextView = findViewById(R.id.feasibility_score_text_view);
        TextView analysisSummaryTextView = findViewById(R.id.analysis_summary_text_view);
        TextView detailedAnalysisTextView = findViewById(R.id.detailed_analysis_text_view);
        TextView recommendationsTextView = findViewById(R.id.recommendations_text_view);

        String feasibilityText = getIntent().getStringExtra("feasibility_text");
        if (feasibilityText != null) {
            try {
                JSONObject feasibilityJson = new JSONObject(feasibilityText);
                feasibilityScoreTextView.setText(String.valueOf(feasibilityJson.optDouble("feasibility_score", 0.0)));
                analysisSummaryTextView.setText(feasibilityJson.optString("analysis_summary", "N/A"));

                JSONObject detailedAnalysis = feasibilityJson.optJSONObject("detailed_analysis");
                if (detailedAnalysis != null) {
                    StringBuilder detailedAnalysisBuilder = new StringBuilder();
                    detailedAnalysisBuilder.append("Soil: ").append(detailedAnalysis.optString("soil", "N/A")).append("\n");
                    detailedAnalysisBuilder.append("pH: ").append(detailedAnalysis.optString("ph", "N/A")).append("\n");
                    detailedAnalysisBuilder.append("Moisture: ").append(detailedAnalysis.optString("moisture", "N/A")).append("\n");
                    detailedAnalysisBuilder.append("Sunlight: ").append(detailedAnalysis.optString("sunlight", "N/A")).append("\n");
                    detailedAnalysisBuilder.append("Climate: ").append(detailedAnalysis.optString("climate", "N/A"));
                    detailedAnalysisTextView.setText(detailedAnalysisBuilder.toString());
                }

                JSONObject recommendations = feasibilityJson.optJSONObject("recommendations");
                if (recommendations != null) {
                    StringBuilder recommendationsBuilder = new StringBuilder();
                    JSONArray amendments = recommendations.optJSONArray("amendments");
                    if (amendments != null) {
                        recommendationsBuilder.append("Amendments:\n");
                        for (int i = 0; i < amendments.length(); i++) {
                            recommendationsBuilder.append("- ").append(amendments.getString(i)).append("\n");
                        }
                    }
                    JSONArray actions = recommendations.optJSONArray("actions");
                    if (actions != null) {
                        recommendationsBuilder.append("Actions:\n");
                        for (int i = 0; i < actions.length(); i++) {
                            recommendationsBuilder.append("- ").append(actions.getString(i)).append("\n");
                        }
                    }
                    recommendationsTextView.setText(recommendationsBuilder.toString());
                }

            } catch (JSONException e) {
                e.printStackTrace();
                analysisSummaryTextView.setText("Error parsing feasibility data.");
            }
        }
    }
}