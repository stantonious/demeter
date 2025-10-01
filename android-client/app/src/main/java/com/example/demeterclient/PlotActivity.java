package com.example.demeterclient;

import android.os.Bundle;
import android.widget.LinearLayout;
import androidx.appcompat.app.AppCompatActivity;
import java.util.ArrayList;
import java.util.HashMap;

public class PlotActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_plot);

        LinearLayout plotsContainer = findViewById(R.id.plots_container);

        HashMap<String, ArrayList<Float>> historyData =
                (HashMap<String, ArrayList<Float>>) getIntent().getSerializableExtra("history_data");

        if (historyData != null) {
            String[] keys = {"N", "P", "K", "pH", "Humidity", "Sun", "Moisture", "Light"};
            for (String key : keys) {
                ArrayList<Float> data = historyData.get(key);
                if (data != null && !data.isEmpty()) {
                    PlotView plotView = new PlotView(this, null);
                    plotView.setData(data, key + " History");
                    // Set layout params to define height
                    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            400 // Height in pixels
                    );
                    params.setMargins(0, 0, 0, 20); // Bottom margin
                    plotView.setLayoutParams(params);
                    plotsContainer.addView(plotView);
                }
            }
        }
    }
}