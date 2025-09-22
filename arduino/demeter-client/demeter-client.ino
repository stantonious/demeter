#include <M5Unified.h>
#include <ArduinoBLE.h>

#include "globals.h"
#include "utils.h"
#include "ui_drawing.h"
#include "ble_handling.h"
#include "touch_handling.h"

void setup() {
  auto cfg = M5.config();
  M5.begin(cfg);
  Serial.begin(115200);

  for (int i = 0; i < maxPoints; i++) {
    nBuffer[i] = 0;
    pBuffer[i] = 0;
    kBuffer[i] = 0;
    phBuffer[i] = 0;
    humidBuffer[i] = 0;
    sunBuffer[i] = 0;
  }

  if (!BLE.begin()) {
    M5.Display.println("BLE init failed!");
    while (1);
  }
}

void loop() {
  M5.update();
  handleTouch();

  if (currentView != lastView) {
    lastView = currentView;
    switch (currentView) {
      case HOME:
        drawHomeView();
        break;
      case PLOT:
        if (connected) {
          drawPlotView();
        } else {
          M5.Display.fillScreen(BLACK);
          // Display nothing when disconnected, matching other views
        }
        break;
      case BITMAP:
        drawBitmapView();
        break;
      case CONTROL:
        drawControlView();
        break;
      case SETTINGS:
        drawSettingsView();
        break;
    }
  }

  if ((currentView == PLOT || currentView == SETTINGS) && connected) {
    handleBLEData();
  }

  if (llmStatusChar && llmStatusChar.valueUpdated()) {
    byte status;
    llmStatusChar.readValue(&status, 1);
    Serial.printf("LLM Status updated: %d\n", status);
    if (status == 1) { // Generating
      suggestionText = "Generating...";
      if (currentView == CONTROL) {
        drawControlView();
      }
    } else if (status == 2) { // Ready
      fetchLlmResponse();
    }
  }

  // BLE connection logic
  if (!connected) {
    BLEDevice device = BLE.available();
    if (device && device.address() == demeter_mac) {
      BLE.stopScan();
      scanning = false;
      if (device.connect()) {
        peripheral = device;
        connected = true;
        if (peripheral.discoverAttributes()) {
          ledColor = GREEN;
          setupCharacteristics();
          homeViewDirty = true; // Update view after connecting
        } else {
          Serial.println("Failed to discover attributes");
          ledColor = RED;
          homeViewDirty = true;
          BLE.scan();
        }
      } else {
        Serial.println("Connection failed");
        ledColor = RED;
        homeViewDirty = true;
        BLE.scan();
      }
    }
  }

  // Heartbeat logic
  if (connected) {
    if (millis() - lastHeartbeatTime > 1000) {
      lastHeartbeatTime = millis();
      bool heartbeat_ok = false; // Assume failed until proven otherwise
      if (suggestChar && suggestChar.canRead()) {
        byte buffer[4];
        unsigned long read_start = millis();
        int len = suggestChar.readValue(buffer, 4);
        unsigned long read_end = millis();
        if (len > 0 && (read_end - read_start) <= 1000) {
          heartbeat_ok = true;
        }
      }

      // If the heartbeat is ok, the light is green, otherwise it's yellow.
      // This overrides the "connecting" yellow, which is fine because this only runs when connected.
      int newLedColor = heartbeat_ok ? GREEN : YELLOW;

      if (newLedColor != ledColor) {
        ledColor = newLedColor;
        if (currentView == HOME) {
          homeViewDirty = true;
        }
      }
    }
  }

  if (connected && !peripheral.connected()) {
      connected = false;
      ledColor = RED;
      homeViewDirty = true;
  }

  if (homeViewDirty && currentView == HOME) {
    drawHomeView();
    homeViewDirty = false;
  }

  delay(100);
}
