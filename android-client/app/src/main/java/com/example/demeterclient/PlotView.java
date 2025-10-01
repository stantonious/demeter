package com.example.demeterclient;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public class PlotView extends View {

    private Map<String, ArrayList<Float>> dataSeries = new HashMap<>();
    private Map<String, Integer> seriesColors = new LinkedHashMap<>();
    private Map<String, String> seriesLabels = new LinkedHashMap<>();
    private Paint linePaint;
    private Paint axisPaint;
    private Paint textPaint;
    private Paint legendPaint;
    private Path linePath;

    public PlotView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeWidth(5);

        axisPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        axisPaint.setColor(Color.DKGRAY);
        axisPaint.setStrokeWidth(3);

        textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(Color.BLACK);
        textPaint.setTextSize(30);
        textPaint.setTextAlign(Paint.Align.RIGHT);

        legendPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        legendPaint.setTextSize(24);
        legendPaint.setTextAlign(Paint.Align.LEFT);

        linePath = new Path();

        // Define colors for each series
        seriesColors.put("N", Color.RED);
        seriesColors.put("P", Color.GREEN);
        seriesColors.put("K", Color.BLUE);
        seriesColors.put("pH", Color.CYAN);
        seriesColors.put("Humidity", Color.MAGENTA);
        seriesColors.put("Sun", Color.YELLOW);
        seriesColors.put("Moisture", Color.DKGRAY);
        seriesColors.put("Light", Color.LTGRAY);

        // Define descriptive labels for the legend
        seriesLabels.put("N", "Nitrogen");
        seriesLabels.put("P", "Phosphorus");
        seriesLabels.put("K", "Potassium");
        seriesLabels.put("pH", "pH");
        seriesLabels.put("Humidity", "Humidity");
        seriesLabels.put("Sun", "Sunlight");
        seriesLabels.put("Moisture", "Moisture");
        seriesLabels.put("Light", "Light");
    }

    public void setData(Map<String, ArrayList<Float>> data) {
        this.dataSeries = data;
        invalidate(); // Request a redraw
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (dataSeries == null || dataSeries.isEmpty()) {
            return;
        }

        int width = getWidth();
        int height = getHeight();
        float padding = 60;
        float legendWidth = 220;

        // Draw title
        textPaint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText("Live Sensor Data", width / 2f, 40, textPaint);
        textPaint.setTextAlign(Paint.Align.RIGHT);


        // --- Find Global Min/Max ---
        float globalMin = Float.MAX_VALUE;
        float globalMax = Float.MIN_VALUE;
        int maxDataPoints = 0;

        for (ArrayList<Float> series : dataSeries.values()) {
            if (series == null || series.isEmpty()) continue;
            globalMin = Math.min(globalMin, Collections.min(series));
            globalMax = Math.max(globalMax, Collections.max(series));
            maxDataPoints = Math.max(maxDataPoints, series.size());
        }

        if (globalMin == Float.MAX_VALUE || maxDataPoints == 0) return;

        float range = globalMax - globalMin;
        if (range == 0) range = 1;

        // --- Draw Axes ---
        canvas.drawLine(padding, padding, padding, height - padding, axisPaint); // Y-axis
        canvas.drawLine(padding, height - padding, width - legendWidth, height - padding, axisPaint); // X-axis

        // Draw Y-axis labels
        canvas.drawText(String.format("%.1f", globalMax), padding - 10, padding + 10, textPaint);
        canvas.drawText(String.format("%.1f", globalMin), padding - 10, height - padding, textPaint);

        // --- Draw Data Lines & Labels ---
        float plotHeight = height - (2 * padding);
        float plotWidth = width - padding - legendWidth;

        for (Map.Entry<String, ArrayList<Float>> entry : dataSeries.entrySet()) {
            ArrayList<Float> points = entry.getValue();
            if (points == null || points.isEmpty()) continue;

            linePaint.setColor(seriesColors.getOrDefault(entry.getKey(), Color.BLACK));
            linePath.reset();

            float lastX = 0;
            float lastY = 0;

            if (points.size() > 1) {
                for (int i = 0; i < points.size(); i++) {
                    float x = padding + (i * (plotWidth / (points.size() - 1)));
                    float y = (height - padding) - ((points.get(i) - globalMin) / range * plotHeight);

                    if (i == 0) {
                        linePath.moveTo(x, y);
                    } else {
                        linePath.lineTo(x, y);
                    }
                    if (i == points.size() - 1) {
                        lastX = x;
                        lastY = y;
                    }
                }
                canvas.drawPath(linePath, linePaint);
            } else {
                lastX = padding + (plotWidth / 2);
                lastY = (height - padding) - ((points.get(0) - globalMin) / range * plotHeight);
                canvas.drawCircle(lastX, lastY, 10, linePaint);
            }

            // Draw the value label
            legendPaint.setColor(seriesColors.getOrDefault(entry.getKey(), Color.BLACK));
            float lastValue = points.get(points.size() - 1);
            String label = entry.getKey() + ": " + String.format("%.1f", lastValue);
            canvas.drawText(label, lastX + 15, lastY + 8, legendPaint);
        }

        // --- Draw Legend ---
        float legendX = width - legendWidth + 20;
        float legendY = padding;
        int i = 0;
        for (Map.Entry<String, Integer> entry : seriesColors.entrySet()) {
            String key = entry.getKey();
            int color = entry.getValue();
            String label = seriesLabels.getOrDefault(key, key);

            legendPaint.setColor(color);
            canvas.drawText(label, legendX, legendY + 18 + (i * 30), legendPaint);
            i++;
        }
    }
}