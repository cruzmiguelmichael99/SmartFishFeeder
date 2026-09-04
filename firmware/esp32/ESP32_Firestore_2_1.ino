// ============================================================
// ESP32 FIRMWARE — Firestore-based Smart Fish Feeder
// Talks directly to Firestore over HTTPS, no third-party IoT
// platform involved. Also accepts "FEED" over classic Bluetooth
// as an offline fallback when the app can't reach Firestore.
//
// CHANGES IN THIS VERSION:
//   1. firestorePatch() now takes an updateMask so heartbeat
//      writes merge instead of overwriting the whole document.
//      Without this, any heartbeat that skipped a field (e.g.
//      waterTemperature during a failed sensor read) DELETED
//      that field from Firestore instead of leaving it alone.
//   2. sendHeartbeat() now always sends waterSensorOk (true/false)
//      on every heartbeat, separate from waterTemperature itself.
//      waterTemperature is preserved (not cleared) on a failed
//      read, and waterSensorOk tells the app whether that number
//      is live or stale.
// ============================================================

#include <WiFi.h>
#include <WiFiManager.h>
#include <HTTPClient.h>
#include <ArduinoJson.h>
#include <OneWire.h>
#include <DallasTemperature.h>
#include <BluetoothSerial.h>

// ---------- FILL THESE IN ----------
// Project ID: Firebase Console > gear icon > Project settings > General > Project ID
const char* FIREBASE_PROJECT_ID = "YOUR_FIREBASE_PROJECT_ID";

// Web API Key: same Project settings > General page, under "Web API Key"
const char* FIREBASE_API_KEY = "YOUR_WEB_API_KEY";

// Use the SAME email/password you log into the Smart Fish Feeder app
// with. This is how the ESP32 authenticates as a real user, which the
// security rules require — without this, nothing below actually works.
const char* USER_EMAIL = "your_app_login_email@example.com";
const char* USER_PASSWORD = "your_app_login_password";

// This is the pairing ID shown in the app's Settings screen (your Firebase
// Auth UID). Copy it there and paste it here so the ESP32 knows which
// account's data to read/write. Since USER_EMAIL/PASSWORD above sign in
// as that same account, this will always match automatically — this
// constant just saves re-deriving it from the sign-in response.
const char* USER_UID = "PASTE_PAIRING_ID_FROM_APP_SETTINGS";

// A name for this specific feeder. Shown in the app as "Device ID".
const char* DEVICE_ID = "ESP32-FEEDER-01";
// ------------------------------------

// ---------- BLUETOOTH (offline Feed Now fallback) ----------
// Name must exactly match DEVICE_NAME in the app's BluetoothHelper.kt.
// Pair this device once via Android's system Bluetooth settings before
// the app can connect to it.
BluetoothSerial SerialBT;
#define BLUETOOTH_DEVICE_NAME "FishFeeder-BT"

// ---------- SERIAL2 TO MEGA ----------
#define RXD2 16 // ESP32 RX2 <- Mega TX1
#define TXD2 17 // ESP32 TX2 -> Mega RX1

// ---------- HC-SR04 (FEED LEVEL) ----------
#define TRIG_PIN 23
#define ECHO_PIN 22
const float FEED_DETECTED_DISTANCE = 42.0; // cm — same threshold as before

// ---------- DS18B20 (WATER TEMPERATURE) ----------
// Data pin needs a 4.7k pull-up resistor between it and 3.3V.
#define ONE_WIRE_PIN 4
OneWire oneWire(ONE_WIRE_PIN);
DallasTemperature waterTempSensor(&oneWire);

// ---------- SCHEDULE LIMIT ----------
// The app allows unlimited schedules; the Mega can only hold this many at
// once. Raise this (and the matching value on the Mega) if you need more.
#define MAX_SCHEDULES 10

// ---------- AUTH STATE ----------
String idToken = "";
String refreshToken = "";
unsigned long tokenExpiryMillis = 0;

// ---------- TIMING ----------
unsigned long lastHeartbeatMillis = 0;
const unsigned long HEARTBEAT_INTERVAL = 20000; // 20s

unsigned long lastCommandCheckMillis = 0;
const unsigned long COMMAND_CHECK_INTERVAL = 8000; // 8s — Feed Now should feel responsive

unsigned long lastScheduleSyncMillis = 0;
const unsigned long SCHEDULE_SYNC_INTERVAL = 60000; // 60s — schedules don't change often

unsigned long lastFeedLevelCheckMillis = 0;
const unsigned long FEED_LEVEL_CHECK_INTERVAL = 5000; // 5s

// ---------- STATE ----------
bool feedLevelLow = false;
float currentWaterTemp = 0.0;
bool waterTempValid = false;
bool masterScheduleEnabled = true; // "Enable Schedule" toggle from the app's Schedule tab

// ---------- DEBUG ----------
void debug(String msg) { Serial.println(msg); }

// ============================================================
// FIREBASE AUTHENTICATION
// Signs in with email/password via the Identity Toolkit REST API,
// getting an ID token that's included as a Bearer token on every
// Firestore request below — this is what makes request.auth non-null
// so your security rules actually allow access.
// ============================================================
bool signIn() {
  if (WiFi.status() != WL_CONNECTED) return false;

  HTTPClient http;
  String url = "https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=" +
               String(FIREBASE_API_KEY);
  http.begin(url);
  http.addHeader("Content-Type", "application/json");

  String body = "{\"email\":\"" + String(USER_EMAIL) +
                "\",\"password\":\"" + String(USER_PASSWORD) +
                "\",\"returnSecureToken\":true}";
  int code = http.POST(body);

  bool success = false;
  if (code == 200) {
    String response = http.getString();
    DynamicJsonDocument doc(2048);
    DeserializationError err = deserializeJson(doc, response);
    if (!err) {
      idToken = doc["idToken"].as<String>();
      refreshToken = doc["refreshToken"].as<String>();
      long expiresInSeconds = String(doc["expiresIn"].as<const char*>()).toInt();
      // Refresh 1 minute early to avoid a request landing right as it expires.
      tokenExpiryMillis = millis() + (expiresInSeconds * 1000UL) - 60000UL;
      debug("Signed in to Firebase. UID: " + doc["localId"].as<String>());
      success = true;
    } else {
      debug("Sign-in response parse failed: " + String(err.c_str()));
    }
  } else {
    debug("Sign-in failed: HTTP " + String(code) + " — check USER_EMAIL/USER_PASSWORD/FIREBASE_API_KEY");
    debug(http.getString());
  }
  http.end();
  return success;
}

bool refreshIdToken() {
  if (WiFi.status() != WL_CONNECTED || refreshToken.length() == 0) return false;

  HTTPClient http;
  String url = "https://securetoken.googleapis.com/v1/token?key=" + String(FIREBASE_API_KEY);
  http.begin(url);
  http.addHeader("Content-Type", "application/x-www-form-urlencoded");

  String body = "grant_type=refresh_token&refresh_token=" + refreshToken;
  int code = http.POST(body);

  bool success = false;
  if (code == 200) {
    String response = http.getString();
    DynamicJsonDocument doc(2048);
    DeserializationError err = deserializeJson(doc, response);
    if (!err) {
      idToken = doc["id_token"].as<String>();
      refreshToken = doc["refresh_token"].as<String>();
      long expiresInSeconds = String(doc["expires_in"].as<const char*>()).toInt();
      tokenExpiryMillis = millis() + (expiresInSeconds * 1000UL) - 60000UL;
      success = true;
    }
  } else {
    debug("Token refresh failed: HTTP " + String(code) + " — falling back to full sign-in");
  }
  http.end();
  return success;
}

// Call this before any Firestore request. Signs in if we've never signed
// in, or refreshes if the current token is expired/about to expire.
void ensureValidToken() {
  if (idToken.length() == 0) {
    signIn();
  } else if (millis() > tokenExpiryMillis) {
    if (!refreshIdToken()) {
      signIn();
    }
  }
}

// ============================================================
// FIRESTORE REST HELPERS
// ============================================================
String firestoreUrl(String path) {
  return "https://firestore.googleapis.com/v1/projects/" + String(FIREBASE_PROJECT_ID) +
         "/databases/(default)/documents/" + path;
}

String firestoreGet(String path) {
  if (WiFi.status() != WL_CONNECTED) return "";
  ensureValidToken();
  if (idToken.length() == 0) return "";

  HTTPClient http;
  http.begin(firestoreUrl(path));
  http.addHeader("Authorization", "Bearer " + idToken);
  int code = http.GET();
  String response = "";
  if (code == 200) {
    response = http.getString();
  } else {
    debug("Firestore GET failed (" + path + "): HTTP " + String(code));
  }
  http.end();
  return response;
}

// CHANGED: now takes maskFields, e.g.
//   "updateMask.fieldPaths=isOnline&updateMask.fieldPaths=deviceId"
// so this PATCH merges only the listed fields instead of replacing the
// whole document with whatever happens to be in fieldsJson this call.
bool firestorePatch(String path, String fieldsJson, String maskFields) {
  if (WiFi.status() != WL_CONNECTED) return false;
  ensureValidToken();
  if (idToken.length() == 0) return false;

  HTTPClient http;
  http.begin(firestoreUrl(path) + "?" + maskFields);
  http.addHeader("Authorization", "Bearer " + idToken);
  http.addHeader("Content-Type", "application/json");

  String body = "{\"fields\":{" + fieldsJson + "}}";
  int code = http.PATCH(body);

  if (code != 200) {
    debug("Firestore PATCH failed (" + path + "): HTTP " + String(code));
    debug("Response: " + http.getString());
  }

  http.end();
  return code == 200;
}

bool firestoreDelete(String path) {
  if (WiFi.status() != WL_CONNECTED) return false;
  ensureValidToken();
  if (idToken.length() == 0) return false;

  HTTPClient http;
  http.begin(firestoreUrl(path));
  http.addHeader("Authorization", "Bearer " + idToken);
  int code = http.sendRequest("DELETE");
  http.end();
  return code == 200;
}

// ============================================================
// HEARTBEAT
// CHANGED: always sends waterSensorOk (true/false) so the app can tell
// whether waterTemperature is a live reading or a preserved stale value.
// waterTemperature itself is only included (and only masked) when the
// current read succeeded, so a failed read leaves the last good value
// sitting untouched in Firestore instead of deleting it.
// ============================================================
void sendHeartbeat() {
  String fields = "";
  fields += "\"isOnline\":{\"booleanValue\":true},";
  fields += "\"deviceId\":{\"stringValue\":\"" + String(DEVICE_ID) + "\"},";
  fields += "\"lastSeen\":{\"stringValue\":\"" + String(millis() / 1000) + "s uptime\"},";
  fields += "\"feedLevelLow\":{\"booleanValue\":" + String(feedLevelLow ? "true" : "false") + "},";
  fields += "\"waterSensorOk\":{\"booleanValue\":" + String(waterTempValid ? "true" : "false") + "}";

  String mask = "updateMask.fieldPaths=isOnline&updateMask.fieldPaths=deviceId&updateMask.fieldPaths=lastSeen&updateMask.fieldPaths=feedLevelLow&updateMask.fieldPaths=waterSensorOk";

  if (waterTempValid) {
    fields += ",\"waterTemperature\":{\"doubleValue\":" + String(currentWaterTemp, 2) + "}";
    mask += "&updateMask.fieldPaths=waterTemperature";
  }

  String path = "users/" + String(USER_UID) + "/device/status";
  bool ok = firestorePatch(path, fields, mask);
  debug(ok ? "Heartbeat sent." : "Heartbeat FAILED.");
}

// ============================================================
// FEED LEVEL
// ============================================================
void checkFeedLevel() {
  digitalWrite(TRIG_PIN, LOW);
  delayMicroseconds(2);
  digitalWrite(TRIG_PIN, HIGH);
  delayMicroseconds(10);
  digitalWrite(TRIG_PIN, LOW);

  long duration = pulseIn(ECHO_PIN, HIGH, 30000);
  float distanceCM = (duration * 0.0343) / 2.0;

  if (distanceCM > 0) {
    feedLevelLow = distanceCM >= FEED_DETECTED_DISTANCE;
    Serial.print("Feed distance: ");
    Serial.print(distanceCM);
    Serial.println(feedLevelLow ? " cm (LOW)" : " cm (OK)");
  }
}

// ============================================================
// WATER TEMPERATURE
// ============================================================
void checkWaterTemperature() {
  waterTempSensor.requestTemperatures();
  float tempC = waterTempSensor.getTempCByIndex(0);

  if (tempC > -100.0) {
    currentWaterTemp = tempC;
    waterTempValid = true;
    Serial.print("Water temperature: ");
    Serial.print(tempC);
    Serial.println(" C");
  } else {
    waterTempValid = false;
    debug("DS18B20 not responding — check wiring/pull-up resistor.");
  }
}

// ============================================================
// COMMANDS (Firestore-triggered Feed Now)
// ============================================================
void checkAndProcessCommands() {
  String path = "users/" + String(USER_UID) + "/commands";
  String response = firestoreGet(path);
  if (response.length() == 0) return;

  DynamicJsonDocument doc(8192);
  DeserializationError err = deserializeJson(doc, response);
  if (err) {
    debug("Failed to parse commands response: " + String(err.c_str()));
    return;
  }

  if (!doc.containsKey("documents")) return;

  JsonArray documents = doc["documents"].as<JsonArray>();
  for (JsonObject document : documents) {
    String name = document["name"].as<String>();
    String type = document["fields"]["type"]["stringValue"].as<String>();
    String status = document["fields"]["status"]["stringValue"].as<String>();

    if (type == "feed_now" && status == "pending") {
      debug("Feed Now command received via Firestore — dispensing.");
      Serial2.println("FEED");

      int lastSlash = name.lastIndexOf('/');
      String commandId = name.substring(lastSlash + 1);
      firestoreDelete(path + "/" + commandId);
    }
  }
}

// ============================================================
// BLUETOOTH (offline Feed Now fallback)
// The app connects here directly when it has no internet, sending
// "FEED" the exact same way as the Firestore command path above.
// ============================================================
void checkBluetoothCommands() {
  if (!SerialBT.hasClient()) return;

  while (SerialBT.available()) {
    String btIncoming = SerialBT.readStringUntil('\n');
    btIncoming.trim();
    if (btIncoming == "FEED") {
      debug("Feed Now command received via Bluetooth — dispensing.");
      Serial2.println("FEED");
    }
  }
}

// ============================================================
// SCHEDULES
// ============================================================
void parseFeedingTime(String timeStr, int &hour24, int &minute) {
  bool isPM = timeStr.indexOf("PM") >= 0;
  timeStr.replace("AM", "");
  timeStr.replace("PM", "");
  timeStr.trim();

  int colonIndex = timeStr.indexOf(':');
  int hour12 = timeStr.substring(0, colonIndex).toInt();
  minute = timeStr.substring(colonIndex + 1).toInt();

  if (isPM && hour12 != 12) {
    hour24 = hour12 + 12;
  } else if (!isPM && hour12 == 12) {
    hour24 = 0;
  } else {
    hour24 = hour12;
  }
}

void checkMasterScheduleToggle() {
  String path = "users/" + String(USER_UID);
  String response = firestoreGet(path);
  if (response.length() == 0) return;

  DynamicJsonDocument doc(4096);
  DeserializationError err = deserializeJson(doc, response);
  if (err) {
    debug("Failed to parse user doc: " + String(err.c_str()));
    return;
  }

  if (doc["fields"].containsKey("scheduleEnabled")) {
    masterScheduleEnabled = doc["fields"]["scheduleEnabled"]["booleanValue"] | true;
  } else {
    masterScheduleEnabled = true;
  }

  debug(masterScheduleEnabled ? "Master schedule toggle: ON" : "Master schedule toggle: OFF");
}

void syncSchedulesFromFirestore() {
  checkMasterScheduleToggle();

  if (!masterScheduleEnabled) {
    for (int i = 0; i < MAX_SCHEDULES; i++) {
      Serial2.print("SCH=");
      Serial2.print(i);
      Serial2.print(",-1,-1\n");
    }
    debug("Master schedule toggle is OFF — all schedules cleared on Mega.");
    return;
  }

  String path = "users/" + String(USER_UID) + "/schedules";
  String response = firestoreGet(path);
  if (response.length() == 0) return;

  DynamicJsonDocument doc(8192);
  DeserializationError err = deserializeJson(doc, response);
  if (err) {
    debug("Failed to parse schedules response: " + String(err.c_str()));
    return;
  }

  if (!doc.containsKey("documents")) {
    debug("No schedules found.");
    return;
  }

  JsonArray documents = doc["documents"].as<JsonArray>();
  int index = 0;

  for (JsonObject document : documents) {
    if (index >= MAX_SCHEDULES) break;

    bool enabled = document["fields"]["enabled"]["booleanValue"] | false;
    String feedingTime = document["fields"]["feedingTime"]["stringValue"].as<String>();

    if (!enabled || feedingTime.length() == 0) {
      Serial2.print("SCH=");
      Serial2.print(index);
      Serial2.print(",-1,-1\n");
      index++;
      continue;
    }

    int hour24, minute;
    parseFeedingTime(feedingTime, hour24, minute);

    Serial2.print("SCH=");
    Serial2.print(index);
    Serial2.print(",");
    Serial2.print(hour24);
    Serial2.print(",");
    Serial2.print(minute);
    Serial2.print("\n");

    debug("Schedule " + String(index) + " synced: " + String(hour24) + ":" + String(minute) +
          (enabled ? " (enabled)" : " (disabled)"));
    index++;
  }
}

// ================== SETUP ==================
void setup() {
  Serial.begin(115200);
  Serial2.begin(9600, SERIAL_8N1, RXD2, TXD2);

  pinMode(TRIG_PIN, OUTPUT);
  pinMode(ECHO_PIN, INPUT);

  waterTempSensor.begin();

  SerialBT.begin(BLUETOOTH_DEVICE_NAME);
  Serial.println("Bluetooth ready as \"" + String(BLUETOOTH_DEVICE_NAME) + "\" — pair it via phone Bluetooth settings.");

  WiFiManager wm;
  // Uncomment once if you need to forget saved WiFi and re-pair:
  // wm.resetSettings();

  bool connected = wm.autoConnect("FishFeeder-Setup");
  if (!connected) {
    Serial.println("Failed to connect. Restarting...");
    delay(3000);
    ESP.restart();
  }

  Serial.println("Connected to WiFi!");
  Serial.print("IP Address: ");
  Serial.println(WiFi.localIP());

  signIn();

  checkFeedLevel();
  checkWaterTemperature();
  sendHeartbeat();
  syncSchedulesFromFirestore();
}

// ================== LOOP ==================
void loop() {
  unsigned long now = millis();

  if (now - lastFeedLevelCheckMillis >= FEED_LEVEL_CHECK_INTERVAL) {
    lastFeedLevelCheckMillis = now;
    checkFeedLevel();
  }

  if (now - lastHeartbeatMillis >= HEARTBEAT_INTERVAL) {
    lastHeartbeatMillis = now;
    checkWaterTemperature();
    sendHeartbeat();
  }

  if (now - lastCommandCheckMillis >= COMMAND_CHECK_INTERVAL) {
    lastCommandCheckMillis = now;
    checkAndProcessCommands();
  }

  if (now - lastScheduleSyncMillis >= SCHEDULE_SYNC_INTERVAL) {
    lastScheduleSyncMillis = now;
    syncSchedulesFromFirestore();
  }

  checkBluetoothCommands();

  while (Serial2.available()) {
    String incoming = Serial2.readStringUntil('\n');
    incoming.trim();
    if (incoming.length() > 0) {
      Serial.println("Received from Mega: " + incoming);
    }
  }
}
