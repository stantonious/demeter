#pragma once

#include <M5Unified.h>
#include <ArduinoBLE.h>
#include "bitmap_data.h"

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
const char* uuidMoisture = "12345678-1234-5678-1234-56789abcdefb";
const char* uuidLight = "12345678-1234-5678-1234-56789abcdefc";
const char* uuidLlmStatus = "12345678-1234-5678-1234-56789abcdeff";
const char* uuidPlantType = "12345678-1234-5678-1234-56789abcdefa";
const char* uuidLlmSelection = "12345678-1234-5678-1234-56789abcdefd";

// UI Constants
const uint16_t COLOR_BACKGROUND = BLACK;
const uint16_t COLOR_TEXT = WHITE;
const uint16_t COLOR_PRIMARY = BLUE;
const uint16_t COLOR_SUCCESS = GREEN;
const uint16_t COLOR_WARNING = YELLOW;
const uint16_t COLOR_ERROR = RED;
const uint16_t COLOR_MUTED = DARKGREY;
const uint16_t COLOR_TEAL = 0x0493;
const uint16_t COLOR_ORANGE = 0xFC00;

const int BUTTON_WIDTH = 120;
const int BUTTON_HEIGHT = 40;
const int BUTTON_RADIUS = 10;
const int BUTTON_X = 110;
const int BUTTON_Y = 100;

const int LED_X = 280;
const int LED_Y = 20;
const int LED_RADIUS = 10;
const int LED_BORDER = 2;

const int NAV_ARROW_SIZE = 25;
const int NAV_ARROW_PADDING = 10;
const int NAV_DOT_RADIUS = 4;

BLEDevice peripheral;
BLECharacteristic nChar, kChar, pChar, suggestChar, llmChar, phChar, humidChar, sunChar, moistureChar, lightChar, llmStatusChar, plantTypeChar, llmSelectionChar;

const int maxPoints = 160;
float nBuffer[maxPoints], kBuffer[maxPoints], pBuffer[maxPoints], phBuffer[maxPoints], humidBuffer[maxPoints], sunBuffer[maxPoints], moistureBuffer[maxPoints], lightBuffer[maxPoints];
int bufferIndex = 0;
bool connected = false;
uint16_t ledColor = COLOR_MUTED;
float lastN = 0, lastK = 0, lastP = 0, lastPh = 0, lastHumid = 0, lastSun = 0, lastMoisture = 0, lastLight = 0;
unsigned long lastHeartbeatTime = 0;
bool homeViewDirty = false;

// Touch gesture state
int touch_x = -1;
int touch_y = -1;

enum View { HOME, PLOT, BITMAP, CONTROL, SETTINGS,STATUS_V };
bool scanning = false;
View currentView = HOME;
View lastView = PLOT; // Force initial draw
String suggestionText = "";
int scrollOffset = 0;
int totalLines = 0;
int selectedPlantType = 0;
const char* plantTypes[] = {"ground", "veg", "shrub", "flowering"};
bool isDropdownOpen = false;
int selectedLlmBackend = 0; // 0 for tinyllm, 1 for chatgpt

const int RADIO_BUTTON_X = 50;
const int RADIO_BUTTON_Y = 120;
const int RADIO_BUTTON_RADIUS = 8;
const int RADIO_BUTTON_SPACING = 120;


enum PlotState { ALL, N, K, P, PH, HUMID, SUN, MOISTURE, LIGHT };
PlotState currentPlotState = ALL;

const int TOGGLE_BUTTON_X = 250;
const int TOGGLE_BUTTON_Y = 220;
const int TOGGLE_BUTTON_WIDTH = 60;
const int TOGGLE_BUTTON_HEIGHT = 20;

// Function forward declarations
void handleBLEData();
void startBleScan();
void drawConnectionStatus();
void drawBitmapView();
void drawControlView();
void drawSettingsView();
void handleTouch();
void drawPlot();
void drawLabels(float n, float p, float k, float ph, float humid, float sun, float moisture, float light);
String wordWrap(String text, unsigned int lineLength);
void fetchLlmResponse();
void setupCharacteristics();
int countLines(String text);
void setLlmBackend(int backend);