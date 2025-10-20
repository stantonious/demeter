package com.example.demeterclient;

import android.os.Bundle;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import com.bumptech.glide.Glide;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.IOException;
import okhttp3.Call;
import okhttp3.Callback;
import java.util.concurrent.TimeUnit;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class FeasibilityActivity extends AppCompatActivity {

    private ImageView plantImageView;
    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_feasibility);

        plantImageView = findViewById(R.id.plant_image_view);
        TextView feasibilityScoreTextView = findViewById(R.id.feasibility_score_text_view);
        TextView analysisSummaryTextView = findViewById(R.id.analysis_summary_text_view);
        TextView detailedAnalysisTextView = findViewById(R.id.detailed_analysis_text_view);
        TextView recommendationsTextView = findViewById(R.id.recommendations_text_view);

        String feasibilityText = getIntent().getStringExtra("feasibility_text");
        String plantName = getIntent().getStringExtra("plant_name");
        String plantType = getIntent().getStringExtra("plant_type");

        if (plantName != null && plantType != null) {
            fetchAndLoadImage(plantName, plantType);
        }

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

    private void fetchAndLoadImage(String plantName, String plantType) {
        String url = Constants.BASE_URL + "/demeter/plant/img?plant_name=" + plantName + "&plant_type=" + plantType;
        Request request = new Request.Builder().url(url).build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                e.printStackTrace();
                runOnUiThread(() -> Glide.with(FeasibilityActivity.this).load(R.drawable.led_red).into(plantImageView));
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (response.isSuccessful()) {
                    try {
                        String responseBody = response.body().string();
                        JSONObject json = new JSONObject(responseBody);
                        String imageUrl = json.getString("assetURI");
                        runOnUiThread(() -> Glide.with(FeasibilityActivity.this).load(imageUrl).placeholder(R.drawable.led_grey).into(plantImageView));
                    } catch (JSONException e) {
                        e.printStackTrace();
                        runOnUiThread(() -> Glide.with(FeasibilityActivity.this).load(R.drawable.led_red).into(plantImageView));
                    }
                } else {
                    runOnUiThread(() -> Glide.with(FeasibilityActivity.this).load(R.drawable.led_red).into(plantImageView));
                }
            }
        });
    }
}