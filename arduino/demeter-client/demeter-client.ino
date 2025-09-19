#include <M5Core2.h>
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

void setup() {
  Serial.begin(115200);
  M5.begin();
  M5.Lcd.setTextSize(2);
  M5.Lcd.setCursor(10, 10);
  M5.Lcd.println("Scanning BLE...");

  if (!BLE.begin()) {
    M5.Lcd.println("BLE init failed!");
    while (1);
  }

  BLE.scan();
}

void loop() {
  if (!connected) {
    BLEDevice device = BLE.available();
    if (device && device.address() == demeter_mac) {
      BLE.stopScan();
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
  } else {
    if (nChar.valueUpdated() && kChar.valueUpdated() ){
      float n_val, k_val, p_val;
      memcpy(&n_val, nChar.value(), sizeof(float));
      memcpy(&k_val, kChar.value(), sizeof(float));
      memcpy(&p_val, pChar.value(), sizeof(float));

      Serial.printf("Received N: %.2f mg/kg\n", n_val);
      Serial.printf("Received K: %.2f mg/kg\n", k_val);
      Serial.printf("Received P: %.2f mg/kg\n", p_val);

      nBuffer[bufferIndex] = n_val;
      kBuffer[bufferIndex] = k_val;
      pBuffer[bufferIndex] = p_val;
      bufferIndex = (bufferIndex + 1) % maxPoints;

      drawPlot();
      drawLabels(n_val, k_val, p_val);
    }else{
      Serial.printf("not all updated");
    }
  }

  delay(250);
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
  M5.Lcd.fillRect(0, 40, 320, 200, BLACK);
  M5.Lcd.drawLine(10, 220, 310, 220, WHITE); // X-axis
  M5.Lcd.drawLine(10, 60, 10, 220, WHITE);   // Y-axis

  for (int i = 1; i < maxPoints; i++) {
    int idx1 = (bufferIndex + i - 1) % maxPoints;
    int idx2 = (bufferIndex + i) % maxPoints;
    int x1 = 10 + i - 1;
    int x2 = 10 + i;

    int y1_n = map(nBuffer[idx1], 0, 100, 220, 60);
    int y2_n = map(nBuffer[idx2], 0, 100, 220, 60);
    M5.Lcd.drawLine(x1, y1_n, x2, y2_n, GREEN);

    int y1_k = map(kBuffer[idx1], 0, 100, 220, 60);
    int y2_k = map(kBuffer[idx2], 0, 100, 220, 60);
    M5.Lcd.drawLine(x1, y1_k, x2, y2_k, YELLOW);

    int y1_p = map(pBuffer[idx1], 0, 100, 220, 60);
    int y2_p = map(pBuffer[idx2], 0, 100, 220, 60);
    M5.Lcd.drawLine(x1, y1_p, x2, y2_p, BLUE);
  }
}

void drawLabels(float n, float k, float p) {
  M5.Lcd.fillRect(0, 0, 320, 30, BLACK);

  M5.Lcd.setCursor(10, 10);
  M5.Lcd.setTextColor(GREEN);
  M5.Lcd.printf("N: %.2f", n);

  M5.Lcd.setCursor(110, 10);
  M5.Lcd.setTextColor(BLUE);
  M5.Lcd.printf("P: %.2f", p);

  M5.Lcd.setCursor(210, 10);
  M5.Lcd.setTextColor(YELLOW);
  M5.Lcd.printf("K: %.2f mg/kg", k);

  M5.Lcd.setTextColor(WHITE);  // Reset to default for other text
}