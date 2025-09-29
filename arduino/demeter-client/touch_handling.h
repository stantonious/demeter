#pragma once

#include "globals.h"

void handleTouch() {
  auto detail = M5.Touch.getDetail();
  if (detail.wasPressed()) {
    touch_x = detail.x;
    touch_y = detail.y;
    Serial.printf("touched %i %i\n", touch_x, touch_y);
  } else if (detail.wasReleased()) {
    if (touch_x != -1) {
      int dx = detail.x - touch_x;
      int dy = detail.y - touch_y;
      if (abs(dx) > abs(dy) && abs(dx) > 50) {  // Horizontal swipe
        if (currentView == HOME) {
          if (dx < 0) {  // Swipe left
            currentView = PLOT;
          } else {  // Swipe right
            currentView = SETTINGS;
          }
        } else if (currentView == PLOT) {
          if (dx > 0) {  // Swipe right
            currentView = HOME;
          }
        } else if (currentView == SETTINGS) {
          if (dx < 0) {  // Swipe left
            currentView = HOME;
          }
        }
      } else if (abs(dy) > abs(dx) && abs(dy) > 50) {  // Vertical swipe
        if (currentView == HOME) {
          if (dy < 0) {  // Swipe up
            currentView = CONTROL;
          } else {  // Swipe down
            currentView = BITMAP;
          }
        } else if (currentView == BITMAP) {
          if (dy > 0) {  // Swipe down
            currentView = HOME;
          }
        } else if (currentView == CONTROL) {
          if (dy < 0) {  // Swipe up
            currentView = HOME;
          }
        }
      } else {  // Button press
        // --- App-specific buttons
        if (currentView == HOME && detail.x > BUTTON_X && detail.x < BUTTON_X + BUTTON_WIDTH && detail.y > BUTTON_Y && detail.y < BUTTON_Y + BUTTON_HEIGHT) {
          if (connected) {
            peripheral.disconnect();
            connected = false;
            ledColor = COLOR_MUTED;
            homeViewDirty = true;
          } else {
            ledColor = COLOR_WARNING;
            homeViewDirty = true;
            startBleScan();
          }
        } else if (currentView == CONTROL) {
          // Scroll up
          if (detail.x > 280 && detail.y < 25) {
            if (scrollOffset > 0) {
              scrollOffset--;
              drawControlView();
            }
          }
          // Scroll down
          else if (detail.x > 280 && detail.y > 215 && detail.y < 245) {
            if (scrollOffset + 11 < totalLines) {  // 11 is maxLines
              scrollOffset++;
              drawControlView();
            }
          }
          // Suggest action
          else if (detail.x > 10 && detail.x < 310 && detail.y > 10 && detail.y < 230) {
            if (plantTypeChar && plantTypeChar.canWrite()) {
              int32_t value_to_write = selectedPlantType;
              Serial.println("Writing plant type to plantTypeChar...");
              plantTypeChar.writeValue((byte*)&value_to_write, sizeof(value_to_write));
            }
            if (suggestChar && suggestChar.canWrite()) {
              int32_t value_to_write = 0;  // 0 triggers LLM generation
              Serial.println("Writing 0 to suggestChar to trigger LLM...");
              suggestChar.writeValue((byte*)&value_to_write, sizeof(value_to_write));
              // The notification handler will update the text to "Generating..."
            }
          }
        }

        // --- Navigation buttons
        if (currentView == HOME) {
          if (detail.x > M5.Display.width() / 2 - NAV_ARROW_SIZE && detail.x < M5.Display.width() / 2 + NAV_ARROW_SIZE && detail.y > 0 && detail.y < NAV_ARROW_PADDING * 2 + NAV_ARROW_SIZE) { currentView = CONTROL; }  // Up
          else if (detail.x > M5.Display.width() / 2 - NAV_ARROW_SIZE && detail.x < M5.Display.width() / 2 + NAV_ARROW_SIZE && detail.y > M5.Display.height() - NAV_ARROW_PADDING * 2 - NAV_ARROW_SIZE && detail.y < M5.Display.height()) {
            currentView = BITMAP;
          }                                                                                                                                                                                                                       // Down
          else if (detail.x > M5.Display.width() - NAV_ARROW_SIZE * 2 - NAV_ARROW_PADDING && detail.y > M5.Display.height() / 2 - NAV_ARROW_SIZE && detail.y < M5.Display.height() / 2 + NAV_ARROW_SIZE) { currentView = PLOT; }  // Right
          else if (detail.x < NAV_ARROW_SIZE * 2 + NAV_ARROW_PADDING && detail.y > M5.Display.height() / 2 - NAV_ARROW_SIZE && detail.y < M5.Display.height() / 2 + NAV_ARROW_SIZE) {
            currentView = SETTINGS;
          }  // Left
        } else if (currentView == PLOT) {
          if (detail.x < NAV_ARROW_SIZE * 2 + NAV_ARROW_PADDING && detail.y > M5.Display.height() / 2 - NAV_ARROW_SIZE && detail.y < M5.Display.height() / 2 + NAV_ARROW_SIZE) { currentView = HOME; }  // Back to Home

          if (detail.x > TOGGLE_BUTTON_X && detail.x < TOGGLE_BUTTON_X + TOGGLE_BUTTON_WIDTH && detail.y > TOGGLE_BUTTON_Y && detail.y < TOGGLE_BUTTON_Y + TOGGLE_BUTTON_HEIGHT) {
            currentPlotState = (PlotState)((currentPlotState + 1) % 9); // Cycle through ALL, N, K, P, PH, HUMID, SUN, MOISTURE, LIGHT
            drawPlotView();
          }
        } else if (currentView == SETTINGS) {
          if (detail.x > M5.Display.width() - NAV_ARROW_SIZE * 2 - NAV_ARROW_PADDING && detail.y > M5.Display.height() / 2 - NAV_ARROW_SIZE && detail.y < M5.Display.height() / 2 + NAV_ARROW_SIZE) { currentView = HOME; }  // Back to Home


          // Dropdown toggle
          if (detail.x > 50 && detail.x < 270 && detail.y > 50 && detail.y < 80) {
            isDropdownOpen = !isDropdownOpen;
            drawSettingsView();
          }

          if (isDropdownOpen) {
            for (int i = 0; i < 4; i++) {
              if (detail.x > 50 && detail.x < 270 && detail.y > 80 + i * 30 && detail.y < 80 + (i + 1) * 30) {
                selectedPlantType = i;
                isDropdownOpen = false;
                drawSettingsView();
                break;
              }
            }
          }
        } else if (currentView == BITMAP) {
          if (detail.x > M5.Display.width() / 2 - NAV_ARROW_SIZE && detail.x < M5.Display.width() / 2 + NAV_ARROW_SIZE && detail.y > 0 && detail.y < NAV_ARROW_PADDING * 2 + NAV_ARROW_SIZE) { currentView = HOME; }  // Back to Home
        } else if (currentView == CONTROL) {

          if (detail.x > M5.Display.width() / 2 - NAV_ARROW_SIZE && detail.x < M5.Display.width() / 2 + NAV_ARROW_SIZE && detail.y > M5.Display.height() - NAV_ARROW_PADDING * 2 - NAV_ARROW_SIZE && detail.y < M5.Display.height()) { currentView = HOME; }  // Back to Home
        }
      }
    }
    touch_x = -1;
    touch_y = -1;
  }
}
