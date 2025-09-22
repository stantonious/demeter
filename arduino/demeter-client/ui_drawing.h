#pragma once

#include "globals.h"
#include <M5Unified.h>

void drawHomeView() {
    M5.Display.fillScreen(COLOR_BACKGROUND);
    M5.Display.setTextSize(2);

    // Draw Connect/Disconnect Button
    uint16_t primaryColor = connected ? COLOR_ERROR : COLOR_SUCCESS;
    uint16_t shadowColor = M5.Display.color565(50, 50, 50);

    M5.Display.fillRoundRect(BUTTON_X + 2, BUTTON_Y + 2, BUTTON_WIDTH, BUTTON_HEIGHT, BUTTON_RADIUS, shadowColor); // Shadow
    M5.Display.fillRoundRect(BUTTON_X, BUTTON_Y, BUTTON_WIDTH, BUTTON_HEIGHT, BUTTON_RADIUS, primaryColor); // Main Button
    M5.Display.setTextColor(COLOR_TEXT);
    const char* txt = connected ? "Disconnect" : "Connect";
    M5.Display.drawCenterString(txt, BUTTON_X + BUTTON_WIDTH / 2, BUTTON_Y + BUTTON_HEIGHT / 2);

    // Draw Status LED with a border
    M5.Display.fillCircle(LED_X, LED_Y, LED_RADIUS + LED_BORDER, M5.Display.color565(80, 80, 80)); // Border
    M5.Display.fillCircle(LED_X, LED_Y, LED_RADIUS, ledColor);

    // Modern Swipe Indicators (Chevrons)
    M5.Display.setTextColor(COLOR_TEXT);
    M5.Display.drawString("^", M5.Display.width() / 2 - NAV_ARROW_SIZE / 2, NAV_ARROW_PADDING);
    M5.Display.drawString("v", M5.Display.width() / 2 - NAV_ARROW_SIZE / 2, M5.Display.height() - NAV_ARROW_SIZE - NAV_ARROW_PADDING);
    M5.Display.drawString(">", M5.Display.width() - NAV_ARROW_SIZE - NAV_ARROW_PADDING, M5.Display.height() / 2 - NAV_ARROW_SIZE / 2);
    M5.Display.drawString("<", NAV_ARROW_PADDING, M5.Display.height() / 2 - NAV_ARROW_SIZE / 2);
}

void drawPlot() {
    M5.Display.drawRect(10, 60, 300, 160, COLOR_TEXT); // Plot border

    // Define a modern color palette
    uint16_t nColor = M5.Display.color565(3, 169, 244); // Light Blue
    uint16_t kColor = M5.Display.color565(255, 152, 0); // Orange
    uint16_t pColor = M5.Display.color565(76, 175, 80); // Green
    uint16_t phColor = M5.Display.color565(244, 67, 54); // Red
    uint16_t humidColor = M5.Display.color565(0, 188, 212); // Cyan
    uint16_t sunColor = M5.Display.color565(255, 235, 59); // Yellow

    for (int i = 1; i < maxPoints; i++) {
        int idx1 = (bufferIndex + i - 1) % maxPoints;
        int idx2 = (bufferIndex + i) % maxPoints;
        int x1 = 10 + (i - 1) * 2; // Stretch the plot
        int x2 = 10 + i * 2;

        // Draw thicker lines by drawing three adjacent lines
        for (int j = -1; j <= 1; j++) {
            int y1_n = map(nBuffer[idx1], 0, 100, 220, 60) + j;
            int y2_n = map(nBuffer[idx2], 0, 100, 220, 60) + j;
            M5.Display.drawLine(x1, y1_n, x2, y2_n, nColor);

            int y1_k = map(kBuffer[idx1], 0, 100, 220, 60) + j;
            int y2_k = map(kBuffer[idx2], 0, 100, 220, 60) + j;
            M5.Display.drawLine(x1, y1_k, x2, y2_k, kColor);

            int y1_p = map(pBuffer[idx1], 0, 100, 220, 60) + j;
            int y2_p = map(pBuffer[idx2], 0, 100, 220, 60) + j;
            M5.Display.drawLine(x1, y1_p, x2, y2_p, pColor);

            int y1_ph = map(phBuffer[idx1], 0, 14, 220, 60) + j;
            int y2_ph = map(phBuffer[idx2], 0, 14, 220, 60) + j;
            M5.Display.drawLine(x1, y1_ph, x2, y2_ph, phColor);

            int y1_humid = map(humidBuffer[idx1], 0, 100, 220, 60) + j;
            int y2_humid = map(humidBuffer[idx2], 0, 100, 220, 60) + j;
            M5.Display.drawLine(x1, y1_humid, x2, y2_humid, humidColor);

            int y1_sun = map(sunBuffer[idx1], 0, 24, 220, 60) + j;
            int y2_sun = map(sunBuffer[idx2], 0, 24, 220, 60) + j;
            M5.Display.drawLine(x1, y1_sun, x2, y2_sun, sunColor);
        }
    }
    M5.Display.drawString(">", M5.Display.width() - NAV_ARROW_SIZE - NAV_ARROW_PADDING, M5.Display.height() / 2 - NAV_ARROW_SIZE / 2); // Back to Home
}

void drawLabels(float n, float p, float k, float ph, float humid, float sun) {
    M5.Display.fillRect(0, 0, 320, 40, COLOR_BACKGROUND);
    M5.Display.setTextSize(1.5);

    // Define colors to match the plot
    uint16_t nColor = M5.Display.color565(3, 169, 244);
    uint16_t pColor = M5.Display.color565(76, 175, 80);
    uint16_t kColor = M5.Display.color565(255, 152, 0);
    uint16_t phColor = M5.Display.color565(244, 67, 54);
    uint16_t humidColor = M5.Display.color565(0, 188, 212);
    uint16_t sunColor = M5.Display.color565(255, 235, 59);

    // Row 1
    M5.Display.setTextColor(nColor);
    M5.Display.setCursor(10, 5);
    M5.Display.printf("N: %.1f", n);

    M5.Display.setTextColor(pColor);
    M5.Display.setCursor(110, 5);
    M5.Display.printf("P: %.1f", p);

    M5.Display.setTextColor(kColor);
    M5.Display.setCursor(210, 5);
    M5.Display.printf("K: %.1f", k);

    // Row 2
    M5.Display.setTextColor(phColor);
    M5.Display.setCursor(10, 25);
    M5.Display.printf("pH: %.1f", ph);

    M5.Display.setTextColor(humidColor);
    M5.Display.setCursor(110, 25);
    M5.Display.printf("Hum: %.1f%%", humid);

    M5.Display.setTextColor(sunColor);
    M5.Display.setCursor(210, 25);
    M5.Display.printf("Sun: %.1fhr", sun);

    M5.Display.setTextColor(WHITE); // Reset color
}

void drawPlotView() {
    M5.Display.fillScreen(COLOR_BACKGROUND);
    drawLabels(lastN, lastP, lastK, lastPh, lastHumid, lastSun);
    drawPlot();
}

void drawSettingsView() {
    M5.Display.fillScreen(COLOR_BACKGROUND);
    M5.Display.setTextColor(COLOR_TEXT);
    M5.Display.setTextSize(2);
    M5.Display.drawString("Plant Type", 50, 20);

    // Draw dropdown
    M5.Display.fillRoundRect(50, 50, 220, 30, 5, M5.Display.color565(80, 80, 80));
    M5.Display.drawString(plantTypes[selectedPlantType], 60, 55);
    M5.Display.fillTriangle(250, 60, 260, 60, 255, 70, COLOR_TEXT);

    if (isDropdownOpen) {
        for (int i = 0; i < 4; i++) {
            M5.Display.fillRoundRect(50, 80 + i * 30, 220, 30, 5, M5.Display.color565(120, 120, 120));
            M5.Display.drawString(plantTypes[i], 60, 85 + i * 30);
        }
    }

    M5.Display.drawString("<", NAV_ARROW_PADDING, M5.Display.height() / 2 - NAV_ARROW_SIZE / 2);
}

void drawBitmapView() {
    M5.Display.fillScreen(BLACK);
    M5.Display.pushImage(96, 56, 128, 128, myBitmap);

    M5.Display.setTextColor(WHITE);
    M5.Display.drawString("v", 155, 220); // Down arrow (to HOME)
}

void drawControlView() {
    M5.Display.fillScreen(COLOR_BACKGROUND);
    M5.Display.setTextSize(2);

    // Draw Suggest Button
    uint16_t primaryColor = M5.Display.color565(0, 150, 136); // Teal
    uint16_t shadowColor = M5.Display.color565(50, 50, 50);
    M5.Display.fillRoundRect(BUTTON_X + 2, 182, BUTTON_WIDTH, BUTTON_HEIGHT, BUTTON_RADIUS, shadowColor); // Shadow
    M5.Display.fillRoundRect(BUTTON_X, 180, BUTTON_WIDTH, BUTTON_HEIGHT, BUTTON_RADIUS, primaryColor); // Main Button
    M5.Display.setTextColor(COLOR_TEXT);
    M5.Display.drawCenterString("Suggest", BUTTON_X + BUTTON_WIDTH / 2, 200);

    // Draw Text Area for Suggestion
    M5.Display.fillRoundRect(10, 10, 300, 160, 10, M5.Display.color565(30, 30, 30));
    M5.Display.drawRoundRect(10, 10, 300, 160, 10, M5.Display.color565(80, 80, 80));
    M5.Display.setTextColor(COLOR_TEXT);
    M5.Display.setTextSize(1.5);
    M5.Display.setCursor(20, 20);

    // Scrolling text logic
    int start = 0;
    int end = suggestionText.indexOf('\n');
    int currentLine = 0;
    int linesDrawn = 0;
    int maxLines = 8; // approx. 160px height / 20px per line

    while (end != -1 && linesDrawn < maxLines) {
        if (currentLine >= scrollOffset) {
            M5.Display.setCursor(20, 20 + linesDrawn * 20);
            M5.Display.println(suggestionText.substring(start, end));
            linesDrawn++;
        }
        start = end + 1;
        end = suggestionText.indexOf('\n', start);
        currentLine++;
    }

    if (start < suggestionText.length() && linesDrawn < maxLines) {
        if (currentLine >= scrollOffset) {
            M5.Display.setCursor(20, 20 + linesDrawn * 20);
            M5.Display.println(suggestionText.substring(start));
            linesDrawn++;
        }
    }

    totalLines = countLines(suggestionText);

    if (scrollOffset > 0) {
        M5.Display.fillTriangle(290, 15, 300, 15, 295, 5, WHITE);
    }
    if (scrollOffset + maxLines < totalLines) {
        M5.Display.fillTriangle(290, 165, 300, 165, 295, 175, WHITE);
    }


    // Swipe indicator
    M5.Display.setTextColor(COLOR_TEXT);
    M5.Display.drawString("^", M5.Display.width() / 2 - NAV_ARROW_SIZE / 2, NAV_ARROW_PADDING); // Up arrow (to HOME)
}
