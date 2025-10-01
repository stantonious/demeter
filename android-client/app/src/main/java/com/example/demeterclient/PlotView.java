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

public class PlotView extends View {

    private ArrayList<Float> dataPoints = new ArrayList<>();
    private String title = "";
    private Paint linePaint;
    private Paint axisPaint;
    private Paint textPaint;
    private Path linePath;

    public PlotView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        linePaint.setColor(Color.parseColor("#FF6200EE")); // A nice purple
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeWidth(5);

        axisPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        axisPaint.setColor(Color.DKGRAY);
        axisPaint.setStrokeWidth(3);

        textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(Color.BLACK);
        textPaint.setTextSize(30);

        linePath = new Path();
    }

    public void setData(ArrayList<Float> data, String title) {
        this.dataPoints = new ArrayList<>(data); // Make a copy
        this.title = title;
        invalidate(); // Request a redraw
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (dataPoints == null || dataPoints.isEmpty()) {
            return;
        }

        int width = getWidth();
        int height = getHeight();
        float padding = 50;

        // Draw title
        canvas.drawText(title, padding, padding, textPaint);

        // Draw axes
        canvas.drawLine(padding, padding, padding, height - padding, axisPaint); // Y-axis
        canvas.drawLine(padding, height - padding, width - padding, height - padding, axisPaint); // X-axis

        // Find data range
        float minVal = Collections.min(dataPoints);
        float maxVal = Collections.max(dataPoints);
        float range = maxVal - minVal;
        if (range == 0) range = 1; // Avoid division by zero

        // Draw axis labels
        canvas.drawText(String.format("%.1f", maxVal), 5, padding + 10, textPaint);
        canvas.drawText(String.format("%.1f", minVal), 5, height - padding, textPaint);

        // Prepare path
        linePath.reset();
        float plotHeight = height - (2 * padding);
        float plotWidth = width - (2 * padding);

        if (dataPoints.size() > 1) {
            for (int i = 0; i < dataPoints.size(); i++) {
                float x = padding + (i * (plotWidth / (dataPoints.size() - 1)));
                float y = (height - padding) - ((dataPoints.get(i) - minVal) / range * plotHeight);

                if (i == 0) {
                    linePath.moveTo(x, y);
                } else {
                    linePath.lineTo(x, y);
                }
            }
            canvas.drawPath(linePath, linePaint);
        } else if (dataPoints.size() == 1) {
            float x = padding + (plotWidth / 2);
            float y = (height - padding) - ((dataPoints.get(0) - minVal) / range * plotHeight);
            canvas.drawCircle(x, y, 10, linePaint);
        }
    }
}