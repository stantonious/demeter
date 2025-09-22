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
