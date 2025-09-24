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
            if (scrollOffset + 11 < totalLines) { // 11 is maxLines
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
            int32_t value_to_write = 0; // 0 triggers LLM generation
            Serial.println("Writing 0 to suggestChar to trigger LLM...");
            suggestChar.writeValue((byte*)&value_to_write, sizeof(value_to_write));
            // The notification handler will update the text to "Generating..."
          }
        }
      }

      // --- Navigation buttons
      if (currentView == HOME) {
          if (detail.x > 10 && detail.x < 10 + NAV_BUTTON_WIDTH && detail.y > 180 && detail.y < 180 + NAV_BUTTON_HEIGHT) { currentView = PLOT; }
          else if (detail.x > 80 && detail.x < 80 + NAV_BUTTON_WIDTH && detail.y > 180 && detail.y < 180 + NAV_BUTTON_HEIGHT) { currentView = BITMAP; }
          else if (detail.x > 150 && detail.x < 150 + NAV_BUTTON_WIDTH && detail.y > 180 && detail.y < 180 + NAV_BUTTON_HEIGHT) { currentView = CONTROL; }
          else if (detail.x > 220 && detail.x < 220 + NAV_BUTTON_WIDTH && detail.y > 180 && detail.y < 180 + NAV_BUTTON_HEIGHT) { currentView = SETTINGS; }
      } else if (currentView == PLOT || currentView == BITMAP || currentView == CONTROL || currentView == SETTINGS) {
          if (detail.x > 10 && detail.x < 10 + NAV_BUTTON_WIDTH && detail.y > 180 && detail.y < 180 + NAV_BUTTON_HEIGHT) { currentView = HOME; }
      }

      if (currentView == SETTINGS) {
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
      }
    }
    touch_x = -1;
    touch_y = -1;
  }
}
