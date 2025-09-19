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

enum View { HOME, PLOT };
View currentView = HOME;
View lastView = PLOT; // Force initial draw

void handleBLEData();
void startBleScan();

void handleTouch() {
  auto detail = M5.Touch.getDetail();
  if (detail.wasPressed()) {
    touch_x = detail.x;
    touch_y = detail.y;
    Serial.printf("touched %i %i\n",touch_x,touch_y);
  } else if (detail.wasReleased()) {
    if (touch_x != -1) {
      int dx = detail.x - touch_x;
      int dy = detail.y - touch_y;
      if (abs(dx) > abs(dy) && abs(dx) > 50) { // Horizontal swipe
        if (dx > 0) { // Swipe right
          currentView = HOME;
        } else { // Swipe left
          currentView = PLOT;
        }
      } else { // Button press
        if (currentView == HOME && detail.x > 110 && detail.x < 210 && detail.y > 100 && detail.y < 140) {
          startBleScan();
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

  // Draw Connect Button
  M5.Display.drawRect(110, 100, 100, 40, WHITE);
  M5.Display.setCursor(120, 112);
  M5.Display.print("Connect");

  // Draw Status LED
  int ledColor = connected ? GREEN : RED;
  M5.Display.fillCircle(280, 20, 10, ledColor);

  // Status message
  M5.Display.setCursor(10, 220);
  if (scanning) {
    M5.Display.print("Scanning...");
  } else if (connected) {
    M5.Display.print("Connected");
  } else {
    M5.Display.print("Disconnected");
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
        M5.Display.fillScreen(BLACK);
        if (connected) {
          drawPlot();
          drawLabels(lastN, lastK, lastP);
        } else {
          M5.Display.setCursor(10, 10);
          M5.Display.println("Plot View - Disconnected");
        }
        break;
    }
  }

  if (currentView == PLOT && connected) {
    handleBLEData();
  }

  // BLE connection logic
  if (!connected) {
    BLEDevice device = BLE.available();
    Serial.printf("device %s\n",device.address().c_str());
    if (device && device.address() == demeter_mac) {
      BLE.stopScan();
      scanning = false;
      if (device.connect()) {
        peripheral = device;
        connected = true;
        if (peripheral.discoverAttributes()) {
          setupCharacteristics();
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