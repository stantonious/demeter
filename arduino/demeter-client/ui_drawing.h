#pragma once

#include "globals.h"
#include <M5Unified.h>

void drawConnectionStatus() {
  // Draw Status LED with a border
  M5.Display.fillCircle(LED_X, LED_Y, LED_RADIUS + LED_BORDER, M5.Display.color565(80, 80, 80));  // Border
  M5.Display.fillCircle(LED_X, LED_Y, LED_RADIUS, ledColor);
}

void drawHomeView() {
  M5.Display.fillScreen(COLOR_BACKGROUND);
  drawConnectionStatus();
  M5.Display.setTextSize(2);

  // Draw Connect/Disconnect Button
  uint16_t primaryColor = connected ? COLOR_MUTED : COLOR_SUCCESS;
  uint16_t shadowColor = M5.Display.color565(50, 50, 50);

  M5.Display.fillRoundRect(BUTTON_X + 2, BUTTON_Y + 2, BUTTON_WIDTH, BUTTON_HEIGHT, BUTTON_RADIUS, shadowColor);  // Shadow
  M5.Display.fillRoundRect(BUTTON_X, BUTTON_Y, BUTTON_WIDTH, BUTTON_HEIGHT, BUTTON_RADIUS, primaryColor);         // Main Button
  M5.Display.setTextColor(COLOR_TEXT);
  const char* txt = connected ? "Disconnect" : "Connect";
  M5.Display.drawCenterString(txt, BUTTON_X + BUTTON_WIDTH / 2, BUTTON_Y + BUTTON_HEIGHT / 2);

  // Navigation Dots
  M5.Display.fillCircle(M5.Display.width() / 2, NAV_ARROW_PADDING + NAV_DOT_RADIUS, NAV_DOT_RADIUS, COLOR_TEAL); // Up
  M5.Display.fillCircle(M5.Display.width() / 2, M5.Display.height() - NAV_ARROW_PADDING - NAV_DOT_RADIUS, NAV_DOT_RADIUS, COLOR_TEAL); // Down
  M5.Display.fillCircle(M5.Display.width() - NAV_ARROW_PADDING - NAV_DOT_RADIUS, M5.Display.height() / 2, NAV_DOT_RADIUS, COLOR_TEAL); // Right
  M5.Display.fillCircle(NAV_ARROW_PADDING + NAV_DOT_RADIUS, M5.Display.height() / 2, NAV_DOT_RADIUS, COLOR_TEAL); // Left
}

void drawPlot() {
  M5.Display.fillRect(10, 60, 300, 160, COLOR_BACKGROUND);
  M5.Display.drawRect(10, 60, 300, 160, COLOR_TEXT);  // Plot border

  // Define a modern color palette
  uint16_t nColor = M5.Display.color565(3, 169, 244);      // Light Blue
  uint16_t kColor = M5.Display.color565(255, 152, 0);      // Orange
  uint16_t pColor = M5.Display.color565(76, 175, 80);      // Green
  uint16_t phColor = M5.Display.color565(244, 67, 54);     // Red
  uint16_t humidColor = M5.Display.color565(0, 188, 212);  // Cyan
  uint16_t sunColor = M5.Display.color565(255, 235, 59);   // Yellow
  uint16_t moistureColor = M5.Display.color565(160, 82, 45); // Sienna
  uint16_t lightColor = COLOR_ORANGE;

  for (int i = 1; i < maxPoints; i++) {
    int idx1 = (bufferIndex + i - 1) % maxPoints;
    int idx2 = (bufferIndex + i) % maxPoints;
    float x_scale = 299.0f / (maxPoints - 1);
    int x1 = 10 + (i - 1) * x_scale;
    int x2 = 10 + i * x_scale;

    // Draw thicker lines by drawing three adjacent lines
    for (int j = -1; j <= 1; j++) {
      if (currentPlotState == ALL || currentPlotState == N) {
        int y1_n = map(nBuffer[idx1], 0, 100, 220, 60) + j;
        int y2_n = map(nBuffer[idx2], 0, 100, 220, 60) + j;
        M5.Display.drawLine(x1, y1_n, x2, y2_n, nColor);
      }
      if (currentPlotState == ALL || currentPlotState == K) {
        int y1_k = map(kBuffer[idx1], 0, 100, 220, 60) + j;
        int y2_k = map(kBuffer[idx2], 0, 100, 220, 60) + j;
        M5.Display.drawLine(x1, y1_k, x2, y2_k, kColor);
      }
      if (currentPlotState == ALL || currentPlotState == P) {
        int y1_p = map(pBuffer[idx1], 0, 100, 220, 60) + j;
        int y2_p = map(pBuffer[idx2], 0, 100, 220, 60) + j;
        M5.Display.drawLine(x1, y1_p, x2, y2_p, pColor);
      }
      if (currentPlotState == ALL || currentPlotState == PH) {
        int y1_ph = map(phBuffer[idx1], 0, 14, 220, 60) + j;
        int y2_ph = map(phBuffer[idx2], 0, 14, 220, 60) + j;
        M5.Display.drawLine(x1, y1_ph, x2, y2_ph, phColor);
      }
      if (currentPlotState == ALL || currentPlotState == HUMID) {
        int y1_humid = map(humidBuffer[idx1], 0, 100, 220, 60) + j;
        int y2_humid = map(humidBuffer[idx2], 0, 100, 220, 60) + j;
        M5.Display.drawLine(x1, y1_humid, x2, y2_humid, humidColor);
      }
      if (currentPlotState == ALL || currentPlotState == MOISTURE) {
        int y1_moisture = map(moistureBuffer[idx1], 0, 100, 220, 60) + j;
        int y2_moisture = map(moistureBuffer[idx2], 0, 100, 220, 60) + j;
        M5.Display.drawLine(x1, y1_moisture, x2, y2_moisture, moistureColor);
      }
      if (currentPlotState == ALL || currentPlotState == SUN) {
        int y1_sun = map(sunBuffer[idx1], 0, 24, 220, 60) + j;
        int y2_sun = map(sunBuffer[idx2], 0, 24, 220, 60) + j;
        M5.Display.drawLine(x1, y1_sun, x2, y2_sun, sunColor);
      }
      if (currentPlotState == ALL || currentPlotState == LIGHT) {
        int y1_light = map(lightBuffer[idx1], 0, 1000, 220, 60) + j;
        int y2_light = map(lightBuffer[idx2], 0, 1000, 220, 60) + j;
        M5.Display.drawLine(x1, y1_light, x2, y2_light, lightColor);
      }
    }
  }
  M5.Display.fillCircle(NAV_ARROW_PADDING + NAV_DOT_RADIUS, M5.Display.height() / 2, NAV_DOT_RADIUS, COLOR_TEAL); // Left
}

void drawLabels(float n, float p, float k, float ph, float humid, float sun, float moisture, float light) {
  M5.Display.fillRect(0, 0, 320, 60, COLOR_BACKGROUND);
  M5.Display.setTextSize(1.5);

  // Define colors to match the plot
  uint16_t nColor = (currentPlotState == ALL || currentPlotState == N) ? M5.Display.color565(3, 169, 244) : COLOR_MUTED;
  uint16_t pColor = (currentPlotState == ALL || currentPlotState == P) ? M5.Display.color565(76, 175, 80) : COLOR_MUTED;
  uint16_t kColor = (currentPlotState == ALL || currentPlotState == K) ? M5.Display.color565(255, 152, 0) : COLOR_MUTED;
  uint16_t phColor = (currentPlotState == ALL || currentPlotState == PH) ? M5.Display.color565(244, 67, 54) : COLOR_MUTED;
  uint16_t humidColor = (currentPlotState == ALL || currentPlotState == HUMID) ? M5.Display.color565(0, 188, 212) : COLOR_MUTED;
  uint16_t sunColor = (currentPlotState == ALL || currentPlotState == SUN) ? M5.Display.color565(255, 235, 59) : COLOR_MUTED;
  uint16_t moistureColor = (currentPlotState == ALL || currentPlotState == MOISTURE) ? M5.Display.color565(160, 82, 45) : COLOR_MUTED; // Sienna
  uint16_t lightColor = (currentPlotState == ALL || currentPlotState == LIGHT) ? COLOR_ORANGE : COLOR_MUTED;

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
  M5.Display.printf("Hum: %.0f%%", humid);

  M5.Display.setTextColor(sunColor);
  M5.Display.setCursor(210, 25);
  M5.Display.printf("Sun: %.1fhr", sun);

  // Row 3
  M5.Display.setTextColor(moistureColor);
  M5.Display.setCursor(10, 45);
  M5.Display.printf("Moist: %.0f%%", moisture);

  M5.Display.setTextColor(lightColor);
  M5.Display.setCursor(110, 45);
  M5.Display.printf("Light: %.0f", light);

  M5.Display.setTextColor(WHITE);  // Reset color
}

void drawPlotView() {
  M5.Display.fillScreen(COLOR_BACKGROUND);
  drawLabels(lastN, lastP, lastK, lastPh, lastHumid, lastSun, lastMoisture, lastLight);
  drawPlot();
  drawConnectionStatus();

  // Draw Toggle Button
  M5.Display.fillRoundRect(TOGGLE_BUTTON_X, TOGGLE_BUTTON_Y, TOGGLE_BUTTON_WIDTH, TOGGLE_BUTTON_HEIGHT, 5, COLOR_PRIMARY);
  M5.Display.setTextColor(COLOR_TEXT);
  M5.Display.setTextSize(1.5);
  String buttonLabel = "All";
  switch (currentPlotState) {
    case N: buttonLabel = "N"; break;
    case K: buttonLabel = "K"; break;
    case P: buttonLabel = "P"; break;
    case PH: buttonLabel = "pH"; break;
    case HUMID: buttonLabel = "Hum"; break;
    case SUN: buttonLabel = "Sun"; break;
    case MOISTURE: buttonLabel = "Moist"; break;
    case LIGHT: buttonLabel = "Light"; break;
    case ALL: buttonLabel = "All"; break;
  }
  M5.Display.drawCenterString(buttonLabel, TOGGLE_BUTTON_X + TOGGLE_BUTTON_WIDTH / 2, TOGGLE_BUTTON_Y + TOGGLE_BUTTON_HEIGHT / 2 - 4);
}

void drawSettingsView() {
  M5.Display.fillScreen(COLOR_BACKGROUND);
  drawConnectionStatus();
  M5.Display.setTextColor(COLOR_TEXT);
  M5.Display.setTextSize(2);
  M5.Display.drawString("Plant Type", 50, 20);

  // Draw dropdown
  M5.Display.fillRoundRect(50, 50, 220, 30, 5, M5.Display.color565(80, 80, 80));
  M5.Display.drawString(plantTypes[selectedPlantType], 60, 55);
  M5.Display.fillTriangle(250, 60, 260, 60, 255, 70, COLOR_TEXT);

  // Draw LLM Backend Selection
  M5.Display.drawString("LLM Backend", 50, 100);

  // TinyLLM Radio Button
  M5.Display.drawCircle(RADIO_BUTTON_X, RADIO_BUTTON_Y, RADIO_BUTTON_RADIUS, COLOR_TEXT);
  if (selectedLlmBackend == 0) {
    M5.Display.fillCircle(RADIO_BUTTON_X, RADIO_BUTTON_Y, RADIO_BUTTON_RADIUS - 3, COLOR_TEAL);
  }
  M5.Display.drawString("TinyLLM", RADIO_BUTTON_X + 20, RADIO_BUTTON_Y - 8);

  // ChatGPT Radio Button
  int chat_gpt_x = RADIO_BUTTON_X + RADIO_BUTTON_SPACING;
  M5.Display.drawCircle(chat_gpt_x, RADIO_BUTTON_Y, RADIO_BUTTON_RADIUS, COLOR_TEXT);
  if (selectedLlmBackend == 1) {
    M5.Display.fillCircle(chat_gpt_x, RADIO_BUTTON_Y, RADIO_BUTTON_RADIUS - 3, COLOR_TEAL);
  }
  M5.Display.drawString("ChatGPT", chat_gpt_x + 20, RADIO_BUTTON_Y - 8);

  // Draw Number of Suggestions
  M5.Display.drawString("Suggestions", 50, 160);
  M5.Display.fillRoundRect(50, 180, 220, 40, 5, M5.Display.color565(80, 80, 80));
  M5.Display.drawCenterString(String(numSuggestions), 160, 190);
  M5.Display.fillTriangle(250, 190, 260, 190, 255, 180, COLOR_TEXT); // Up arrow
  M5.Display.fillTriangle(250, 200, 260, 200, 255, 210, COLOR_TEXT); // Down arrow


  if (isDropdownOpen) {
    for (int i = 0; i < 4; i++) {
      M5.Display.fillRoundRect(50, 80 + i * 30, 220, 30, 5, M5.Display.color565(120, 120, 120));
      M5.Display.drawString(plantTypes[i], 60, 85 + i * 30);
    }
  }
  M5.Display.fillCircle(M5.Display.width() - NAV_ARROW_PADDING - NAV_DOT_RADIUS, M5.Display.height() / 2, NAV_DOT_RADIUS, COLOR_TEAL); // Right
}

void drawBitmapView() {
  M5.Display.fillScreen(BLACK);
  M5.Display.pushImage(96, 56, 128, 128, myBitmap);
  drawConnectionStatus();

  M5.Display.fillCircle(M5.Display.width() / 2, NAV_ARROW_PADDING + NAV_DOT_RADIUS, NAV_DOT_RADIUS, COLOR_TEAL); // Up
}

void drawControlView() {
  M5.Display.fillScreen(COLOR_BACKGROUND);
  drawConnectionStatus();
  M5.Display.setTextSize(2);

  // Draw Text Area for Suggestion
  M5.Display.fillRoundRect(10, 10, 300, 220, 10, M5.Display.color565(30, 30, 30));
  M5.Display.drawRoundRect(10, 10, 300, 220, 10, M5.Display.color565(80, 80, 80));
  M5.Display.setTextColor(COLOR_TEXT);
  M5.Display.setTextSize(1.5);
  M5.Display.setCursor(20, 20);

  // Scrolling text logic
  int start = 0;
  int end = suggestionText.indexOf('\n');
  int currentLine = 0;
  int linesDrawn = 0;
  int maxLines = 11;  // approx. 220px height / 20px per line

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
    M5.Display.fillTriangle(290, 225, 300, 225, 295, 235, WHITE);
  }


  // Swipe indicator
  M5.Display.fillCircle(M5.Display.width() / 2, M5.Display.height() - NAV_ARROW_PADDING - NAV_DOT_RADIUS, NAV_DOT_RADIUS, COLOR_TEAL); // Down
}
