#include <M5Unified.h>
#include <ArduinoBLE.h>

// BLE target MAC and UUIDs
const String demeter_mac = "dc:a6:32:d5:4e:9e";
const char* uuidN = "12345678-1234-5678-1234-56789abcdef2";
const char* uuidK = "12345678-1234-5678-1234-56789abcdef3";
const char* uuidP = "12345678-1234-5678-1234-56789abcdef4";
const char* uuidSuggest = "12345678-1234-5678-1234-56789abcdef5";
const char* uuidLlm = "12345678-1234-5678-1234-56789abcdef6";
const char* uuidPh = "12345678-1234-5678-1234-56789abcdef7";
const char* uuidHumid = "12345678-1234-5678-1234-56789abcdef8";
const char* uuidSun = "12345678-1234-5678-1234-56789abcdef9";
const char* uuidLlmStatus = "12345678-1234-5678-1234-56789abcdeff";

// UI Constants
const uint16_t COLOR_BACKGROUND = BLACK;
const uint16_t COLOR_TEXT = WHITE;
const uint16_t COLOR_PRIMARY = BLUE;
const uint16_t COLOR_SUCCESS = GREEN;
const uint16_t COLOR_WARNING = YELLOW;
const uint16_t COLOR_ERROR = RED;
const uint16_t COLOR_MUTED = DARKGREY;

const int BUTTON_WIDTH = 120;
const int BUTTON_HEIGHT = 40;
const int BUTTON_RADIUS = 10;
const int BUTTON_X = 110;
const int BUTTON_Y = 100;

const int LED_X = 280;
const int LED_Y = 20;
const int LED_RADIUS = 10;
const int LED_BORDER = 2;

const int NAV_ARROW_SIZE = 10;
const int NAV_ARROW_PADDING = 10;

BLEDevice peripheral;
BLECharacteristic nChar, kChar, pChar, suggestChar, llmChar, phChar, humidChar, sunChar, llmStatusChar;

const int maxPoints = 160;
float nBuffer[maxPoints], kBuffer[maxPoints], pBuffer[maxPoints], phBuffer[maxPoints], humidBuffer[maxPoints], sunBuffer[maxPoints];
int bufferIndex = 0;
bool connected = false;
uint16_t ledColor = COLOR_MUTED;
float lastN = 0, lastK = 0, lastP = 0, lastPh = 0, lastHumid = 0, lastSun = 0;
unsigned long lastHeartbeatTime = 0;
bool homeViewDirty = false;

// Touch gesture state
int touch_x = -1;
int touch_y = -1;

enum View { HOME, PLOT, BITMAP, CONTROL, STATUS_V };
// #include "bitmap_data.h" // No longer needed
View currentView = HOME;
View lastView = PLOT; // Force initial draw
String suggestionText = "";

void handleBLEData();
void startBleScan();
void drawBitmapView();
void drawControlView();
void drawStatusView();

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
          } else { // Swipe right
            currentView = STATUS_V;
          }
        } else if (currentView == PLOT) {
          if (dx > 0) { // Swipe right
            currentView = HOME;
          }
        } else if (currentView == STATUS_V) {
            if (dx < 0) { // Swipe left
                currentView = HOME;
            }
        }
      } else if (abs(dy) > abs(dx) && abs(dy) > 50) { // Vertical swipe
        if (currentView == HOME) {
          if (dy < 0) { // Swipe up
            currentView = CONTROL;
          } else { // Swipe down
            currentView = BITMAP;
          }
        } else if (currentView == BITMAP) {
          if (dy < 0) { // Swipe up
            currentView = HOME;
          }
        } else if (currentView == CONTROL) {
          if (dy > 0) { // Swipe down
            currentView = HOME;
          }
        }
      } else { // Button press
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
          // Suggest button
          if (detail.x > BUTTON_X && detail.x < BUTTON_X + BUTTON_WIDTH && detail.y > 180 && detail.y < 220) {
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
            if (detail.x > M5.Display.width() / 2 - NAV_ARROW_SIZE && detail.x < M5.Display.width() / 2 + NAV_ARROW_SIZE && detail.y > 0 && detail.y < NAV_ARROW_PADDING * 2 + NAV_ARROW_SIZE) { currentView = CONTROL; } // Up
            else if (detail.x > M5.Display.width() / 2 - NAV_ARROW_SIZE && detail.x < M5.Display.width() / 2 + NAV_ARROW_SIZE && detail.y > M5.Display.height() - NAV_ARROW_PADDING * 2 - NAV_ARROW_SIZE && detail.y < M5.Display.height()) { currentView = BITMAP; } // Down
            else if (detail.x > M5.Display.width() - NAV_ARROW_SIZE * 2 - NAV_ARROW_PADDING && detail.y > M5.Display.height() / 2 - NAV_ARROW_SIZE && detail.y < M5.Display.height() / 2 + NAV_ARROW_SIZE) { currentView = PLOT; } // Right
            else if (detail.x < NAV_ARROW_SIZE * 2 + NAV_ARROW_PADDING && detail.y > M5.Display.height() / 2 - NAV_ARROW_SIZE && detail.y < M5.Display.height() / 2 + NAV_ARROW_SIZE) { currentView = STATUS_V; } // Left
        } else if (currentView == PLOT) {
            if (detail.x < NAV_ARROW_SIZE * 2 + NAV_ARROW_PADDING && detail.y > M5.Display.height() / 2 - NAV_ARROW_SIZE && detail.y < M5.Display.height() / 2 + NAV_ARROW_SIZE) { currentView = HOME; } // Back to Home
        } else if (currentView == BITMAP) {
            if (detail.x > M5.Display.width() / 2 - NAV_ARROW_SIZE && detail.x < M5.Display.width() / 2 + NAV_ARROW_SIZE && detail.y > M5.Display.height() - NAV_ARROW_PADDING * 2 - NAV_ARROW_SIZE && detail.y < M5.Display.height()) { currentView = HOME; } // Back to Home
        } else if (currentView == CONTROL) {
            if (detail.x > M5.Display.width() / 2 - NAV_ARROW_SIZE && detail.x < M5.Display.width() / 2 + NAV_ARROW_SIZE && detail.y > 0 && detail.y < NAV_ARROW_PADDING * 2 + NAV_ARROW_SIZE) { currentView = HOME; } // Back to Home
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

void startBleScan() {
  if (!connected) {
    scanning = true;
    BLE.scan();
  }
}

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
          drawLabels(lastN, lastP, lastK, lastPh, lastHumid, lastSun);
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
      case STATUS_V:
        drawStatusView();
        break;
    }
  }

  if ((currentView == PLOT || currentView == STATUS_V) && connected) {
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

void handleBLEData() {
  bool needsRedraw = false;
  if (nChar.valueUpdated()) {
    memcpy(&lastN, nChar.value(), sizeof(float));
    nBuffer[bufferIndex] = lastN;
    needsRedraw = true;
  }
  if (kChar.valueUpdated()) {
    memcpy(&lastK, kChar.value(), sizeof(float));
    kBuffer[bufferIndex] = lastK;
    needsRedraw = true;
  }
  if (pChar.valueUpdated()) {
    memcpy(&lastP, pChar.value(), sizeof(float));
    pBuffer[bufferIndex] = lastP;
    needsRedraw = true;
  }
  if (phChar.valueUpdated()) {
    memcpy(&lastPh, phChar.value(), sizeof(float));
    phBuffer[bufferIndex] = lastPh;
    needsRedraw = true;
  }
  if (humidChar.valueUpdated()) {
    memcpy(&lastHumid, humidChar.value(), sizeof(float));
    humidBuffer[bufferIndex] = lastHumid;
    needsRedraw = true;
  }
  if (sunChar.valueUpdated()) {
    memcpy(&lastSun, sunChar.value(), sizeof(float));
    sunBuffer[bufferIndex] = lastSun;
    needsRedraw = true;
  }

  if (needsRedraw) {
    bufferIndex = (bufferIndex + 1) % maxPoints;
    if (currentView == PLOT) {
        drawPlot();
        drawLabels(lastN, lastP, lastK, lastPh, lastHumid, lastSun);
    } else if (currentView == STATUS_V) {
        drawStatusView();
    }
  }
}

void setupCharacteristics() {
  nChar = peripheral.characteristic(uuidN);
  kChar = peripheral.characteristic(uuidK);
  pChar = peripheral.characteristic(uuidP);
  suggestChar = peripheral.characteristic(uuidSuggest);
  llmChar = peripheral.characteristic(uuidLlm);
  phChar = peripheral.characteristic(uuidPh);
  humidChar = peripheral.characteristic(uuidHumid);
  sunChar = peripheral.characteristic(uuidSun);

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

  if (phChar && phChar.canSubscribe()) {
    phChar.subscribe();
    Serial.println("Subscribed to Ph");
  }

  if (humidChar && humidChar.canSubscribe()) {
    humidChar.subscribe();
    Serial.println("Subscribed to Humid");
  }

  if (sunChar && sunChar.canSubscribe()) {
    sunChar.subscribe();
    Serial.println("Subscribed to Sun");
  }

  llmStatusChar = peripheral.characteristic(uuidLlmStatus);
  if (llmStatusChar && llmStatusChar.canSubscribe()) {
    llmStatusChar.subscribe();
    Serial.println("Subscribed to LlmStatus");
  }
}

void drawPlot() {
    M5.Display.fillRect(0, 40, 320, 200, COLOR_BACKGROUND);
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
    M5.Display.drawString("<", 10, 115); // Back to Home
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

void drawPlantIcon() {
    int centerX = 160;
    int baseY = 180; // Adjusted for centering
    uint16_t stemColor = M5.Display.color565(139, 69, 19); // Brown
    uint16_t leafColor = M5.Display.color565(34, 139, 34); // Forest Green

    // Pot
    M5.Display.fillRoundRect(centerX - 60, baseY, 120, 60, 10, stemColor);
    M5.Display.fillRect(centerX - 70, baseY - 15, 140, 15, stemColor);

    // Stem
    M5.Display.fillRect(centerX - 10, baseY - 100, 20, 85, leafColor);

    // Leaves
    M5.Display.fillTriangle(centerX, baseY - 100, centerX - 60, baseY - 50, centerX, baseY - 20, leafColor);
    M5.Display.fillTriangle(centerX, baseY - 100, centerX + 60, baseY - 50, centerX, baseY - 20, leafColor);
    M5.Display.fillTriangle(centerX, baseY - 60, centerX - 50, baseY - 20, centerX, baseY, leafColor);
    M5.Display.fillTriangle(centerX, baseY - 60, centerX + 50, baseY - 20, centerX, baseY, leafColor);
}

void drawBitmapView() {
    M5.Display.fillScreen(BLACK);
    drawPlantIcon();

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
    M5.Display.printf(suggestionText.c_str());

    // Swipe indicator
    M5.Display.setTextColor(COLOR_TEXT);
    M5.Display.drawString("^", M5.Display.width() / 2 - NAV_ARROW_SIZE / 2, NAV_ARROW_PADDING); // Up arrow (to HOME)
}

String wordWrap(String text, unsigned int lineLength) {
    String result = "";
    String currentLine = "";
    String currentWord = "";

    for (char c : text) {
        if (c == ' ' || c == '\n') {
            if (currentLine.length() + currentWord.length() + (c == ' ' ? 1 : 0) <= lineLength) {
                currentLine += currentWord + (c == ' ' ? " " : "");
            } else {
                result += currentLine + "\n";
                currentLine = currentWord + (c == ' ' ? " " : "");
            }
            currentWord = "";
        } else {
            currentWord += c;
        }
    }

    if (currentLine.length() + currentWord.length() <= lineLength) {
        result += currentLine + currentWord;
    } else {
        result += currentLine + "\n" + currentWord;
    }

    return result;
}

void fetchLlmResponse() {
  if (suggestChar && suggestChar.canWrite() && llmChar && llmChar.canRead()) {
    suggestionText = "";
    byte buffer[226]; // 225 bytes for data + 1 for null terminator
    int32_t offset = 1;
    int length = 0;

    do {
      suggestChar.writeValue((byte*)&offset, sizeof(offset));
      delay(100);
      length = llmChar.readValue(buffer, 225);

      if (length > 0) {
        buffer[length] = '\0';
        suggestionText += String((char*)buffer);
        offset += length;
      }
    } while (length == 225);

    suggestionText = wordWrap(suggestionText, 35);
    Serial.printf("Received response: %s\n", suggestionText.c_str());
    if (currentView == CONTROL) {
      drawControlView();
    }
  }
}

void drawSensorCard(int x, int y, const char* label, float value, const char* unit, uint16_t color) {
    M5.Display.fillRoundRect(x, y, 140, 60, 10, M5.Display.color565(40, 40, 40));
    M5.Display.setTextColor(color);
    M5.Display.setTextSize(2);
    M5.Display.drawString(label, x + 10, y + 10);
    M5.Display.setTextSize(3);
    M5.Display.drawString(String(value, 1), x + 10, y + 30);
    M5.Display.setTextSize(2);
    M5.Display.drawString(unit, x + 90, y + 35);
}

void drawStatusView() {
    M5.Display.fillScreen(COLOR_BACKGROUND);

    uint16_t nColor = M5.Display.color565(3, 169, 244);
    uint16_t pColor = M5.Display.color565(76, 175, 80);
    uint16_t kColor = M5.Display.color565(255, 152, 0);
    uint16_t phColor = M5.Display.color565(244, 67, 54);
    uint16_t humidColor = M5.Display.color565(0, 188, 212);
    uint16_t sunColor = M5.Display.color565(255, 235, 59);

    drawSensorCard(10, 20, "Nitrogen", lastN, "mg/kg", nColor);
    drawSensorCard(170, 20, "Phosphorus", lastP, "mg/kg", pColor);
    drawSensorCard(10, 90, "Potassium", lastK, "mg/kg", kColor);
    drawSensorCard(170, 90, "pH", lastPh, "", phColor);
    drawSensorCard(10, 160, "Humidity", lastHumid, "%", humidColor);
    drawSensorCard(170, 160, "Sunlight", lastSun, "hr", sunColor);

    // Right arrow to go back to HOME
    M5.Display.setTextColor(COLOR_TEXT);
    M5.Display.drawString("<", NAV_ARROW_PADDING, M5.Display.height() / 2 - NAV_ARROW_SIZE / 2);
}
