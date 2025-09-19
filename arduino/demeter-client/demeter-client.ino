#include <M5Unified.h>
#include <ArduinoBLE.h>

// BLE target MAC and UUIDs
const String demeter_mac = "dc:a6:32:d5:4e:9e";
const char* uuidN = "12345678-1234-5678-1234-56789abcdef2";
const char* uuidK = "12345678-1234-5678-1234-56789abcdef3";
const char* uuidP = "12345678-1234-5678-1234-56789abcdef4";

BLEDevice peripheral;
BLECharacteristic nChar, kChar, pChar;

const int maxPoints = 160;
float nBuffer[maxPoints], kBuffer[maxPoints], pBuffer[maxPoints];
int bufferIndex = 0;
bool connected = false;
float lastN = 0, lastK = 0, lastP = 0;

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
            drawHomeView();
          } else {
            startBleScan();
          }
        } else if (currentView == CONTROL) {
          if (detail.x > 20 && detail.x < 120 && detail.y > 200 && detail.y < 240) { // Suggest button
            suggestionText = "BLECharacteristic tempCharacteristic(\n"
                     "  \"273e0002-4c4d-454d-96be-f03bac821358\",\n"
                     "  BLEWrite | BLERead | BLENotify,\n"
                     "  20\n"
                     ");";
            drawControlView();
          } else if (detail.x > 200 && detail.x < 300 && detail.y > 200 && detail.y < 240) { // Clear button
            suggestionText = "";
            drawControlView();
          }
        }

        // --- Navigation buttons
        if (currentView == HOME) {
          if (detail.x > 140 && detail.x < 180 && detail.y > 0 && detail.y < 30) { currentView = BITMAP; } // Up
          else if (detail.x > 140 && detail.x < 180 && detail.y > 210 && detail.y < 240) { currentView = CONTROL; } // Down
          else if (detail.x > 290 && detail.x < 320 && detail.y > 100 && detail.y < 140) { currentView = PLOT; } // Left
        } else if (currentView == PLOT) {
          if (detail.x > 0 && detail.x < 30 && detail.y > 100 && detail.y < 140) { currentView = HOME; } // Right
        } else if (currentView == BITMAP) {
          if (detail.x > 140 && detail.x < 180 && detail.y > 210 && detail.y < 240) { currentView = HOME; } // Down
        } else if (currentView == CONTROL) {
          if (detail.x > 140 && detail.x < 180 && detail.y > 0 && detail.y < 30) { currentView = HOME; } // Up
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
  int16_t x1, y1;
  uint16_t w, h;
  M5.Display.getTextBounds(txt, 0, 0, &x1, &y1, &w, &h);
  M5.Display.setCursor(110 + (120 - w) / 2, 100 + (40 - h) / 2);
  M5.Display.print(txt);

  // Draw Status LED
  int ledColor = connected ? GREEN : RED;
  M5.Display.fillCircle(280, 20, 10, ledColor);

  // Swipe indicators
  M5.Display.fillTriangle(160, 10, 150, 20, 170, 20, WHITE);       // Up arrow (to BITMAP)
  M5.Display.fillTriangle(160, 230, 150, 220, 170, 220, WHITE);    // Down arrow (to CONTROL)
  M5.Display.fillTriangle(300, 120, 310, 110, 310, 130, WHITE); // Left arrow (to PLOT)
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
          setupCharacteristics();
          drawHomeView(); // Update view after connecting
        } else {
          Serial.println("Failed to discover attributes");
          BLE.scan();
        }
      } else {
        Serial.println("Connection failed");
        BLE.scan();
      }
    }
  }

  if (connected && !peripheral.connected()) {
      connected = false;
      drawHomeView();
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
  M5.Display.fillTriangle(20, 120, 10, 110, 10, 130, WHITE); // Right arrow
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

   M5.Display.fillTriangle(160, 230, 150, 220, 170, 220, WHITE); // Down arrow (to HOME)
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
  M5.Display.print("Clear");

  M5.Display.setCursor(10, 60);
  M5.Display.setTextSize(2);
  M5.Display.print(suggestionText);

  // Swipe indicator
  M5.Display.fillTriangle(160, 10, 150, 20, 170, 20, WHITE); // Up arrow (to HOME)
  
}
