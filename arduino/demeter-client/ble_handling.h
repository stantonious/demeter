#pragma once

#include "globals.h"
#include "utils.h"
#include <ArduinoBLE.h>

void startBleScan() {
  if (!connected) {
    scanning = true;
    BLE.scan();
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
  moistureChar = peripheral.characteristic(uuidMoisture);
  lightChar = peripheral.characteristic(uuidLight);

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

  if (moistureChar && moistureChar.canSubscribe()) {
    moistureChar.subscribe();
    Serial.println("Subscribed to Moisture");
  }

  if (lightChar && lightChar.canSubscribe()) {
    lightChar.subscribe();
    Serial.println("Subscribed to Light");
  }

  llmStatusChar = peripheral.characteristic(uuidLlmStatus);
  if (llmStatusChar && llmStatusChar.canSubscribe()) {
    llmStatusChar.subscribe();
    Serial.println("Subscribed to LlmStatus");
  }

  plantTypeChar = peripheral.characteristic(uuidPlantType);
}

void handleBLEData() {
  bool anyValueUpdated = nChar.valueUpdated() || kChar.valueUpdated() || pChar.valueUpdated() ||
                         phChar.valueUpdated() || humidChar.valueUpdated() || sunChar.valueUpdated() ||
                         moistureChar.valueUpdated() || lightChar.valueUpdated();

  if (anyValueUpdated) {
    if (nChar.valueUpdated()) {
      memcpy(&lastN, nChar.value(), sizeof(float));
    }
    if (kChar.valueUpdated()) {
      memcpy(&lastK, kChar.value(), sizeof(float));
    }
    if (pChar.valueUpdated()) {
      memcpy(&lastP, pChar.value(), sizeof(float));
    }
    if (phChar.valueUpdated()) {
      memcpy(&lastPh, phChar.value(), sizeof(float));
    }
    if (humidChar.valueUpdated()) {
      memcpy(&lastHumid, humidChar.value(), sizeof(float));
    }
    if (sunChar.valueUpdated()) {
      memcpy(&lastSun, sunChar.value(), sizeof(float));
    }
    if (moistureChar.valueUpdated()) {
      memcpy(&lastMoisture, moistureChar.value(), sizeof(float));
    }
    if (lightChar.valueUpdated()) {
      memcpy(&lastLight, lightChar.value(), sizeof(float));
    }

    nBuffer[bufferIndex] = lastN;
    kBuffer[bufferIndex] = lastK;
    pBuffer[bufferIndex] = lastP;
    phBuffer[bufferIndex] = lastPh;
    humidBuffer[bufferIndex] = lastHumid;
    sunBuffer[bufferIndex] = lastSun;
    moistureBuffer[bufferIndex] = lastMoisture;
    lightBuffer[bufferIndex] = lastLight;

    bufferIndex = (bufferIndex + 1) % maxPoints;

    if (currentView == PLOT) {
        drawPlot();
        drawLabels(lastN, lastP, lastK, lastPh, lastHumid, lastSun, lastMoisture, lastLight);
    } else if (currentView == SETTINGS) {
        drawSettingsView();
    }
  }
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
    scrollOffset = 0;
    totalLines = countLines(suggestionText);
    Serial.printf("Received response: %s\n", suggestionText.c_str());
    if (currentView == CONTROL) {
      drawControlView();
    }
  }
}
