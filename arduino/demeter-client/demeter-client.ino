#include <M5Unified.h>
#include <ArduinoBLE.h>

// BLE target MAC and UUIDs
const String demeter_mac = "dc:a6:32:d5:4e:9e";
const char* uuidN = "12345678-1234-5678-1234-56789abcdef2";
const char* uuidK = "12345678-1234-5678-1234-56789abcdef3";
const char* uuidP = "12345678-1234-5678-1234-56789abcdef4";
const char* uuidSuggest = "12345678-1234-5678-1234-56789abcdef5";
const char* uuidLlm = "12345678-1234-5678-1234-56789abcdef6";

BLEDevice peripheral;
BLECharacteristic nChar, kChar, pChar, suggestChar, llmChar;

const int maxPoints = 160;
float nBuffer[maxPoints], kBuffer[maxPoints], pBuffer[maxPoints];
int bufferIndex = 0;
bool connected = false;
int ledColor = DARKGREY;
float lastN = 0, lastK = 0, lastP = 0;
unsigned long lastHeartbeatTime = 0;
bool homeViewDirty = false;

// Touch gesture state
int touch_x = -1;
int touch_y = -1;

enum View { HOME, PLOT, BITMAP, CONTROL };
#include "bitmap_data.h"
View currentView = HOME;
View lastView = PLOT; // Force initial draw
String suggestionText = "";

void handleBLEData();
void startBleScan();
void drawBitmapView();
void drawControlView();

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
      if (abs(dx) > abs(dy) && abs(dx) > 50) { // Horizontal swipe
        if (currentView == HOME) {
          if (dx < 0) { // Swipe left
            currentView = PLOT;
          }
        } else if (currentView == PLOT) {
          if (dx > 0) { // Swipe right
            currentView = HOME;
          }
        }
      } else if (abs(dy) > abs(dx) && abs(dy) > 50) { // Vertical swipe
        if (currentView == HOME) {
          if (dy < 0) { // Swipe up
            currentView = BITMAP;
          } else { // Swipe down
            currentView = CONTROL;
          }
        } else if (currentView == BITMAP) {
          if (dy > 0) { // Swipe down
            currentView = HOME;
          }
        } else if (currentView == CONTROL) {
          if (dy < 0) { // Swipe up
            currentView = HOME;
          }
        }
      } else { // Button press
        // --- App-specific buttons
        if (currentView == HOME && detail.x > 110 && detail.x < 210 && detail.y > 100 && detail.y < 140) {
          if (connected) {
            peripheral.disconnect();
            connected = false;
            ledColor = DARKGREY;
            homeViewDirty = true;
          } else {
            ledColor = YELLOW;
            homeViewDirty = true;
            startBleScan();
          }
        } else if (currentView == CONTROL) {
          if (detail.x > 20 && detail.x < 120 && detail.y > 200 && detail.y < 240) { // Suggest button
            if (suggestChar && suggestChar.canWrite() && llmChar && llmChar.canRead()) {
              int32_t value_to_write = 1;
              Serial.printf("writing ...\n");
              suggestChar.writeValue((byte*)&value_to_write, sizeof(value_to_write));
              Serial.printf("sending suggest\n");              
              delay(100); // Give server a moment to process
              drawControlView();
            }
          } else if (detail.x > 200 && detail.x < 300 && detail.y > 200 && detail.y < 240) { // Clear button

            byte buffer[1024];
              int length = llmChar.readValue(buffer, sizeof(buffer));
              Serial.printf("length %i\n",length);
              buffer[length] = '\0';
              suggestionText = String((char*)buffer);
              Serial.printf("received response %s\n",suggestionText);
            drawControlView();
          }
        }

        // --- Navigation buttons
        if (currentView == HOME) {
          if (detail.x > 140 && detail.x < 180 && detail.y > 0 && detail.y < 30) { currentView = CONTROL; } // Up
          else if (detail.x > 140 && detail.x < 180 && detail.y > 210 && detail.y < 240) { currentView = BITMAP; } // Down
          else if (detail.x > 290 && detail.x < 320 && detail.y > 100 && detail.y < 140) { currentView = PLOT; } // Left
        } else if (currentView == PLOT) {
          if (detail.x > 0 && detail.x < 30 && detail.y > 100 && detail.y < 140) { currentView = HOME; } // Left
        } else if (currentView == BITMAP) {
          if (detail.x > 140 && detail.x < 180 && detail.y > 0 && detail.y < 30) { currentView = HOME; } // Up
        } else if (currentView == CONTROL) {
          if (detail.x > 140 && detail.x < 180 && detail.y > 210 && detail.y < 240) { currentView = HOME; } // Down
        }
      }
    }
    touch_x = -1;
    touch_y = -1;
  }
}

bool scanning = false;
void setup() {
  auto cfg = M5.config();
  M5.begin(cfg);
  Serial.begin(115200);

  if (!BLE.begin()) {
    M5.Display.println("BLE init failed!");
    while (1);
  }
}

void startBleScan() {
  if (!connected) {
    scanning = true;
    BLE.scan();
  }
}

void drawHomeView() {
  M5.Display.fillScreen(BLACK);
  M5.Display.setTextSize(2);

  // Draw Connect/Disconnect Button
  M5.Display.fillRoundRect(110, 100, 120, 40, 10, BLUE);
  M5.Display.setTextColor(WHITE);
  const char* txt = connected ? "Disconnect" : "Connect";
  // The button is at (110, 100) with size 120x40, so center is (170, 120)
  M5.Display.drawCenterString(txt, 170, 120);

  // Draw Status LED
  M5.Display.fillCircle(280, 20, 10, ledColor);

  // Swipe indicators
  M5.Display.fillTriangle(160, 10, 150, 20, 170, 20, WHITE);       // Up arrow (to BITMAP)
  M5.Display.fillTriangle(160, 230, 150, 220, 170, 220, WHITE);    // Down arrow (to CONTROL)
  M5.Display.fillTriangle(310, 120, 300, 110, 300, 130, WHITE); // Right arrow (for left swipe to PLOT)
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
        M5.Display.fillScreen(BLACK);
        if (connected) {
          drawPlot();
          drawLabels(lastN, lastK, lastP);
        } else {
          // Display nothing when disconnected, matching other views
        }
        break;
      case BITMAP:
        drawBitmapView();
        break;
      case CONTROL:
        drawControlView();
        break;
    }
  }

  if (currentView == PLOT && connected) {
    handleBLEData();
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

void handleBLEData() {
  if (nChar.valueUpdated() && kChar.valueUpdated() && pChar.valueUpdated()){
    memcpy(&lastN, nChar.value(), sizeof(float));
    memcpy(&lastK, kChar.value(), sizeof(float));
    memcpy(&lastP, pChar.value(), sizeof(float));

    nBuffer[bufferIndex] = lastN;
    kBuffer[bufferIndex] = lastK;
    pBuffer[bufferIndex] = lastP;
    bufferIndex = (bufferIndex + 1) % maxPoints;

    drawPlot();
    drawLabels(lastN, lastK, lastP);
  }
}

void setupCharacteristics() {
  nChar = peripheral.characteristic(uuidN);
  kChar = peripheral.characteristic(uuidK);
  pChar = peripheral.characteristic(uuidP);
  suggestChar = peripheral.characteristic(uuidSuggest);
  llmChar = peripheral.characteristic(uuidLlm);

  if (nChar && nChar.canSubscribe()) {
    nChar.subscribe();
    Serial.println("Subscribed to N");
  }

  if (kChar && kChar.canSubscribe()) {
    kChar.subscribe();
    Serial.println("Subscribed to K");
  }

  if (pChar && pChar.canSubscribe()) {
    pChar.subscribe();
    Serial.println("Subscribed to P");
  }
}

void drawPlot() {
  M5.Display.fillRect(0, 40, 320, 200, BLACK);
  M5.Display.drawLine(10, 220, 310, 220, WHITE); // X-axis
  M5.Display.drawLine(10, 60, 10, 220, WHITE);   // Y-axis

  for (int i = 1; i < maxPoints; i++) {
    int idx1 = (bufferIndex + i - 1) % maxPoints;
    int idx2 = (bufferIndex + i) % maxPoints;
    int x1 = 10 + i - 1;
    int x2 = 10 + i;

    int y1_n = map(nBuffer[idx1], 0, 100, 220, 60);
    int y2_n = map(nBuffer[idx2], 0, 100, 220, 60);
    M5.Display.drawLine(x1, y1_n, x2, y2_n, GREEN);

    int y1_k = map(kBuffer[idx1], 0, 100, 220, 60);
    int y2_k = map(kBuffer[idx2], 0, 100, 220, 60);
    M5.Display.drawLine(x1, y1_k, x2, y2_k, YELLOW);

    int y1_p = map(pBuffer[idx1], 0, 100, 220, 60);
    int y2_p = map(pBuffer[idx2], 0, 100, 220, 60);
    M5.Display.drawLine(x1, y1_p, x2, y2_p, BLUE);
  }
  M5.Display.fillTriangle(10, 120, 20, 110, 20, 130, WHITE); // Left arrow
}

void drawLabels(float n, float k, float p) {
  M5.Display.fillRect(0, 0, 320, 30, BLACK);

  M5.Display.setCursor(10, 10);
  M5.Display.setTextColor(GREEN);
  M5.Display.printf("N: %.2f", n);

  M5.Display.setCursor(110, 10);
  M5.Display.setTextColor(BLUE);
  M5.Display.printf("P: %.2f", p);

  M5.Display.setCursor(210, 10);
  M5.Display.setTextColor(YELLOW);
  M5.Display.printf("K: %.2f mg/kg", k);

  M5.Display.setTextColor(WHITE);  // Reset to default for other text
}

void drawBitmapView() {
  M5.Display.fillScreen(BLACK);
  M5.Display.pushImage(96, 56, 128, 128, myBitmap);

   M5.Display.fillTriangle(160, 10, 150, 20, 170, 20, WHITE); // Up arrow (to HOME)
}

void drawControlView() {
  M5.Display.fillScreen(BLACK);
  M5.Display.setCursor(10, 10);
  M5.Display.setTextSize(2);

  // Draw Suggest Button
  M5.Display.drawRect(20, 200, 100, 40, WHITE);
  M5.Display.setCursor(30, 212);
  M5.Display.print("Suggest");

  // Draw Clear Button
  M5.Display.drawRect(200, 200, 100, 40, WHITE);
  M5.Display.setCursor(210, 212);
  M5.Display.print("Refresh");

  M5.Display.setCursor(10, 0);
  M5.Display.setTextSize(1.5);
  M5.Display.print(suggestionText);
  Serial.printf("print text %s\n",suggestionText.c_str());

  // Swipe indicator
  M5.Display.fillTriangle(160, 230, 150, 220, 170, 220, WHITE); // Down arrow (to HOME)
  
}
