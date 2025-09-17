#include <M5Core2.h>
#include <ArduinoBLE.h>

String demeter_mac = "dc:a6:32:d5:4e:9e";
BLEDevice peripheral;
BLECharacteristic nChar;
BLECharacteristic kChar;
BLECharacteristic pChar;
const int maxPoints = 160;
float nBuffer[maxPoints];
float kBuffer[maxPoints];
float pBuffer[maxPoints];

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
    Serial.println("BLE.begin() failed");
    while (1)
      ;
  }

  Serial.println("BLE initialized, starting scan...");
  BLE.scan();
}

void loop() {
  if (!connected) {
    BLEDevice device = BLE.available();

    if (device) {
      Serial.printf("Found device: %s | RSSI: %d\n", device.address().c_str(), device.rssi());

      if (device.address() == demeter_mac) {
        Serial.println("Target device matched. Attempting connection...");
        BLE.stopScan();

        if (device.connect()) {
          Serial.println("Connected to peripheral");
          connected = true;
          peripheral = device;

          if (peripheral.discoverAttributes()) {
            Serial.println("Attributes discovered");

            nChar = peripheral.characteristic("12345678-1234-5678-1234-56789abcdef2");
            kChar = peripheral.characteristic("12345678-1234-5678-1234-56789abcdef3");
            pChar = peripheral.characteristic("12345678-1234-5678-1234-56789abcdef4");
            if (nChar) {
              Serial.println("Nit characteristic found");
              if (nChar.canSubscribe()) {
                nChar.subscribe();
                Serial.println("Subscribed to n notifications");
              } else {
                Serial.println("Characteristic does not support notifications");
              }
            } else {
              Serial.println("Nit characteristic NOT found");
            }
          } else {
            Serial.println("Failed to discover attributes");
          }
          if (kChar) {
            Serial.println("K characteristic found");

            if (kChar.canSubscribe()) {
              kChar.subscribe();
              Serial.println("Subscribed to K notifications");
            } else {
              Serial.println("Characteristic does not support notifications");
            }
          } else {
            Serial.println("K characteristic NOT found");
          }
          if (pChar) {
            Serial.println("P characteristic found");

            if (pChar.canSubscribe()) {
              pChar.subscribe();
              Serial.println("Subscribed to P notifications");
            } else {
              Serial.println("Characteristic does not support notifications");
            }
          } else {
            Serial.println("P characteristic NOT found");
          }

        } else {
          Serial.println("Connection failed");
          BLE.scan();
        }
      }
    }
  } else {
    if (nChar && nChar.valueUpdated()) {
      const uint8_t* data = nChar.value();
      float n_val;
      memcpy(&n_val, data, sizeof(float));
      Serial.printf("Received N: %.2f C\n", n_val);
      nBuffer[bufferIndex] = n_val;

      data = kChar.value();
      float k_val;
      memcpy(&k_val, data, sizeof(float));
      Serial.printf("Received K: %.2f C\n", k_val);
      kBuffer[bufferIndex] = k_val;

      data = pChar.value();
      float p_val;
      memcpy(&p_val, data, sizeof(float));
      Serial.printf("Received P: %.2f C\n", k_val);
      pBuffer[bufferIndex] = p_val;

      bufferIndex = (bufferIndex + 1) % maxPoints;

      M5.Lcd.fillRect(0, 40, 320, 200, BLACK);
      M5.Lcd.drawLine(10, 220, 310, 220, WHITE);
      M5.Lcd.drawLine(10, 60, 10, 220, WHITE);

      for (int i = 1; i < maxPoints; i++) {
        int idx1 = (bufferIndex + i - 1) % maxPoints;
        int idx2 = (bufferIndex + i) % maxPoints;

        float t1 = nBuffer[idx1];
        float t2 = nBuffer[idx2];

        int x1 = 10 + i - 1;
        int x2 = 10 + i;

        int y1 = map(t1, 0, 100, 220, 60);
        int y2 = map(t2, 0, 100, 220, 60);

        M5.Lcd.drawLine(x1, y1, x2, y2, GREEN);

        t1 = kBuffer[idx1];
        t2 = kBuffer[idx2];

        y1 = map(t1, 0, 100, 220, 60);
        y2 = map(t2, 0, 100, 220, 60);

        M5.Lcd.drawLine(x1, y1, x2, y2, YELLOW);
        t1 = pBuffer[idx1];
        t2 = pBuffer[idx2];
        y1 = map(t1, 0, 100, 220, 60);
        y2 = map(t2, 0, 100, 220, 60);

        M5.Lcd.drawLine(x1, y1, x2, y2, BLUE);
      }

      M5.Lcd.setCursor(10, 10);
      M5.Lcd.fillRect(0, 0, 320, 30, BLACK);
      M5.Lcd.printf("N: %.2f mg/kg", n_val);
    }
  }

  delay(5);
}