// ============================================================
// MEGA FIRMWARE — motor control + RTC scheduling
// Mostly unchanged from the original, with two fixes:
// 1. MAX_SCHEDULES raised from 3 to 10, to match the app's
//    unlimited schedule list (capped at 10 by the ESP32 side).
// 2. Schedules with hour == -1 (sent by the ESP32 for a
//    disabled schedule) are now correctly skipped, instead of
//    the old code which fired every schedule unconditionally.
// ============================================================

#include <Wire.h>
#include <RTClib.h>

RTC_DS3231 rtc;

// ---------- BTS7960 MOTOR PINS ----------
#define RPWM 5
#define LPWM 6
#define R_EN 7
#define L_EN 8

// ---------- SCHEDULE ----------
#define MAX_SCHEDULES 10 // was 3 — raised to match the app

struct Schedule {
  int hour = -1;
  int minute = -1;
};

Schedule schedules[MAX_SCHEDULES];

// ---------- SERIAL ----------
String incoming = "";

// ---------- DEBUG ----------
void debug(String msg) { Serial.println(msg); }

// ---------- MOTOR CONTROL ----------
void feedNow() {
  debug("FEED command received — motor running counter-clockwise instantly!");

  digitalWrite(R_EN, HIGH);
  digitalWrite(L_EN, HIGH);

  const int targetPWM = 200;
  const unsigned long runTime = 4000;

  analogWrite(LPWM, 0);
  analogWrite(RPWM, targetPWM);

  unsigned long start = millis();
  while (millis() - start < runTime) {
    delay(10);
  }

  analogWrite(LPWM, 0);
  analogWrite(RPWM, 0);
  digitalWrite(R_EN, LOW);
  digitalWrite(L_EN, LOW);

  debug("Motor stopped.");
}

// ---------- SETUP ----------
void setup() {
  Serial.begin(9600);
  Serial1.begin(9600); // ESP32 connection

  pinMode(RPWM, OUTPUT);
  pinMode(LPWM, OUTPUT);
  pinMode(R_EN, OUTPUT);
  pinMode(L_EN, OUTPUT);

  Wire.begin();
  rtc.begin();

  if (rtc.lostPower()) {
    debug("RTC lost power, setting system time...");
    rtc.adjust(DateTime(F(__DATE__), F(__TIME__)));
  }

  debug("Mega started");
}

// ---------- CHECK SCHEDULES ----------
void checkSchedules() {
  DateTime now = rtc.now();

  for (int i = 0; i < MAX_SCHEDULES; i++) {
    // hour == -1 means this slot is empty or the schedule is disabled
    // (the ESP32 sends -1 for disabled schedules) — skip it.
    if (schedules[i].hour < 0) continue;

    if (schedules[i].hour == now.hour() &&
        schedules[i].minute == now.minute() &&
        now.second() == 0) {
      feedNow();
    }
  }
}

// ---------- LOOP ----------
void loop() {
  while (Serial1.available()) {
    char c = Serial1.read();
    Serial.print(c);

    if (c == '\n' || c == '\r') {
      if (incoming.length() > 0) {
        incoming.trim();
        Serial.println();
        Serial.println("Received: " + incoming);

        // ---- SCHEDULE ----
        if (incoming.startsWith("SCH=")) {
          int index = incoming.substring(4, incoming.indexOf(',')).toInt();

          if (index >= 0 && index < MAX_SCHEDULES) {
            int comma1 = incoming.indexOf(',');
            int comma2 = incoming.indexOf(',', comma1 + 1);

            if (comma1 > 0 && comma2 > comma1) {
              schedules[index].hour = incoming.substring(comma1 + 1, comma2).toInt();
              schedules[index].minute = incoming.substring(comma2 + 1).toInt();
              Serial.println("Schedule updated!");
            } else {
              Serial.println("Invalid schedule format!");
            }
          } else {
            Serial.println("Schedule index out of range!");
          }
        }

        // ---- FEED ----
        else if (incoming == "FEED") {
          Serial.println("FEED command received!");
          feedNow();
        }

        incoming = "";
      }
    } else {
      incoming += c;
      if (incoming.length() > 50) {
        incoming = "";
        Serial.println("\nBuffer cleared (overflow)");
      }
    }
  }

  checkSchedules();
}
