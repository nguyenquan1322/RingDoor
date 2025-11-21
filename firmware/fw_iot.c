// ======================================================
// ===============      INCLUDE          =================
// ======================================================
#include <WiFi.h>
#include <HTTPClient.h>
#include <SPI.h>
#include <MFRC522.h>
#include <Wire.h>
#include <Adafruit_GFX.h>
#include <Adafruit_SSD1306.h>
#include <ArduinoJson.h>



// ======================================================
// ==================   DEFINE CONFIG   =================
// ======================================================

// ----------- WIFI -----------
#define WIFI_SSID       "CHIEN_tq_1_5G"
#define WIFI_PASSWORD   "Chiendeptrai1"

// ----------- FIREBASE -----------
#define FIREBASE_HOST   "ringdoor-90d68-default-rtdb.firebaseio.com"
#define DEVICE_ID       "esp32-frontdoor-01"

// ----------- RC522 (SPI) -----------
#define RC522_SS_PIN    5       // SDA/SS/CS
#define RC522_RST_PIN   27

// ----------- I/O PINS -----------
#define BTN_PIN         12
#define LED_PIN         2       // Cửa (LED)
#define BUZZER_PIN      4

// ----------- OLED -----------
#define SCREEN_WIDTH    128
#define SCREEN_HEIGHT   64



// ======================================================
// ================== GLOBAL VARIABLES ==================
// ======================================================

MFRC522 mfrc522(RC522_SS_PIN, RC522_RST_PIN);
Adafruit_SSD1306 display(SCREEN_WIDTH, SCREEN_HEIGHT, &Wire, -1);

// ---- Button interrupt ----
volatile bool buttonInterruptFlag = false;
unsigned long lastButtonHandled = 0;
const unsigned long BUTTON_DEBOUNCE_MS = 200;

// ---- Buzzer ----
bool buzzerOn = false;
unsigned long buzzerOffTime = 0;

// ---- Door (LED as relay) ----
bool doorOpen = false;
unsigned long doorCloseTime = 0;
const unsigned long DOOR_OPEN_TIME_MS = 5000; // 5 giây

// ---- RFID ----
bool registerMode = false;
String registerOwner = "";

// ---- Firebase polling ----
unsigned long lastCmdCheck = 0;
const unsigned long CMD_CHECK_INTERVAL_MS = 1000;



// ======================================================
// ==================  HELPER FUNCTIONS =================
// ======================================================

// Build Firebase URL
String firebaseUrl(const String &path) {
  return "https://" + String(FIREBASE_HOST) + path + ".json";
}

// HTTP GET
String httpGET(const String &url) {
  if (WiFi.status() != WL_CONNECTED) {
    Serial.println("[HTTP GET] WiFi not connected");
    return "";
  }

  HTTPClient http;
  http.begin(url);

  int code = http.GET();
  String payload = "";

  if (code == HTTP_CODE_OK) {
    payload = http.getString();
  } else {
    Serial.printf("[HTTP GET FAILED] code=%d url=%s\n", code, url.c_str());
  }

  http.end();
  payload.trim();
  if (payload.startsWith("\"")) payload.remove(0, 1);
  if (payload.endsWith("\"")) payload.remove(payload.length() - 1);
  return payload;
}

// HTTP PUT
int httpPUT(const String &url, const String &body) {
  if (WiFi.status() != WL_CONNECTED) {
    Serial.println("[HTTP PUT] WiFi not connected");
    return -1;
  }

  HTTPClient http;
  http.begin(url);
  http.addHeader("Content-Type", "application/json");

  int code = http.PUT(body);
  http.end();

  if (code != HTTP_CODE_OK && code != HTTP_CODE_NO_CONTENT) {
    Serial.printf("[HTTP PUT FAILED] code=%d url=%s\n", code, url.c_str());
  }

  return code;
}

// HTTP POST
int httpPOST(const String &url, const String &body) {
  if (WiFi.status() != WL_CONNECTED) {
    Serial.println("[HTTP POST] WiFi not connected");
    return -1;
  }

  HTTPClient http;
  http.begin(url);
  http.addHeader("Content-Type", "application/json");

  int code = http.POST(body);
  http.end();

  if (code != HTTP_CODE_OK && code != HTTP_CODE_CREATED) {
    Serial.printf("[HTTP POST FAILED] code=%d url=%s\n", code, url.c_str());
  }

  return code;
}

// Time string (giả lập)
String getTimeString() {
  return String(millis() / 1000);
}

// Cập nhật trạng thái cửa: "open" / "close"
void updateDoorState(const String &state) {
  if(state=="ringOff" || state=="ringOn")
  {
    String path = "/Devices/" + String(DEVICE_ID) + "/statusRing";
    httpPUT(firebaseUrl(path), "\"" + state + "\"");
    Serial.println("[STATUS] Ring=" + state);
  }
  else
  {
    String path = "/Devices/" + String(DEVICE_ID) + "/status";
    httpPUT(firebaseUrl(path), "\"" + state + "\"");
    Serial.println("[STATUS] door=" + state);
  }
  
}


// ======================================================
// ==================  OLED & BUZZER  ===================
// ======================================================

void startBeep(unsigned long durationMs) {
  digitalWrite(BUZZER_PIN, HIGH);
  buzzerOn = true;
  buzzerOffTime = millis() + durationMs;
}

void buzzerTask() {
  if (buzzerOn && millis() >= buzzerOffTime) {
    digitalWrite(BUZZER_PIN, LOW);
    buzzerOn = false;
    updateDoorState("ringOff");
  }
}

void oledMessage(const String &l1, const String &l2) {
  display.clearDisplay();
  display.setTextSize(1);
  display.setTextColor(SSD1306_WHITE);
  display.setCursor(0, 0);
  display.println(l1);
  display.setCursor(0, 16);
  display.println(l2);
  display.display();
}



// ======================================================
// ===================  DOOR CONTROL  ===================
// ======================================================

void openDoor() {
  digitalWrite(LED_PIN, HIGH);
  doorOpen = true;
  doorCloseTime = millis() + DOOR_OPEN_TIME_MS;

  oledMessage("Door OPEN", "Welcome!");
  updateDoorState("Open");
  Serial.println("[DOOR] OPEN");
}

void doorTask() {
  if (doorOpen && millis() >= doorCloseTime) {
    digitalWrite(LED_PIN, LOW);
    doorOpen = false;
    oledMessage("Door CLOSED", "");
    updateDoorState("Close");
    Serial.println("[DOOR] CLOSED (auto)");
  }
}



// ======================================================
// ====================  BUTTON ISR  ====================
// ======================================================

void IRAM_ATTR buttonISR() {
  buttonInterruptFlag = true;
}

// void handleButtonPress() {
//   Serial.println("[BTN] Pressed");
//   // Ấn nút thì còi kêu ngắn
//   startBeep(100);

//   // Đẩy event lên Firebase (để server biết có người bấm chuông)
//   DynamicJsonDocument doc(256);
//   doc["timestamp"] = getTimeString();
//   doc["event"]     = "button_press";
//   doc["device"]    = DEVICE_ID;

//   String body;
//   serializeJson(doc, body);
//   String path = "/DoorEvents/" + String(DEVICE_ID);
//   httpPOST(firebaseUrl(path), body);

//   oledMessage("Button pressed", "Waiting...");
// }

void handleButtonPress() {
  Serial.println("[BTN] Pressed");

  // Ấn nút thì còi kêu
  startBeep(200);

  // Cập nhật trạng thái "ring" lên Firebase
  updateDoorState("ringOn");

  // Đẩy event DoorEvents lên server
  // DynamicJsonDocument doc(256);
  // doc["timestamp"] = getTimeString();
  // doc["event"]     = "button_press";
  // doc["device"]    = DEVICE_ID;

  // String body;
  // serializeJson(doc, body);
  // String path = "/DoorEvents/" + String(DEVICE_ID);
  // httpPOST(firebaseUrl(path), body);

  // oledMessage("Button pressed", "Waiting...");
}


// ======================================================
// ======================  RFID  ========================
// ======================================================

String getUidString() {
  String s = "";
  for (byte i = 0; i < mfrc522.uid.size; i++) {
    if (mfrc522.uid.uidByte[i] < 0x10) s += "0";
    s += String(mfrc522.uid.uidByte[i], HEX);
  }
  s.toUpperCase();
  return s;
}

// kiểm tra thẻ trong allowedCards (hiện tại: hardcode để test)
bool isCardAllowed(const String &uid) {
  // test local
  if (uid == "05CCF105") return true;
  return false;

  // Khi dùng Firebase thì đổi thành:
  /*
  String path = "/Devices/" + String(DEVICE_ID) + "/allowedCards/" + uid;
  String url  = firebaseUrl(path);
  Serial.println("[CHECK CARD] GET " + url);
  String data = httpGET(url);
  Serial.println("[CHECK CARD] payload = '" + data + "'");
  return (data != "" && data != "null");
  */
}

// log event quẹt thẻ (để server xem log, không phải quyết định mở)
// void sendCardEvent(const String &uid, bool matched) {
//   DynamicJsonDocument doc(512);
//   doc["timestamp"] = getTimeString();
//   doc["event"]     = "rfid_card";
//   doc["device"]    = DEVICE_ID;
//   doc["uid"]       = uid;
//   doc["matched"]   = matched;

//   String body; serializeJson(doc, body);
//   String path = "/DoorEvents/" + String(DEVICE_ID);
//   httpPOST(firebaseUrl(path), body);
// }

// Đăng ký thẻ mới khi nhận lệnh từ server
// void registerNewCardOnFirebase(const String &uid, const String &owner) {
//   DynamicJsonDocument doc(256);
//   doc["owner"]     = owner;
//   doc["createdAt"] = getTimeString();

//   String body; serializeJson(doc, body);
//   String path = "/Devices/" + String(DEVICE_ID) + "/allowedCards/" + uid;
//   httpPUT(firebaseUrl(path), body);
// }

void rfidTask() {
  if (!mfrc522.PICC_IsNewCardPresent()) return;
  if (!mfrc522.PICC_ReadCardSerial()) return;

  String uid = getUidString();
  Serial.println("[RFID] UID=" + uid);
  // if (registerMode) {
  //   // ===== CHẾ ĐỘ ĐĂNG KÝ THẺ =====
  //   registerNewCardOnFirebase(uid, registerOwner);
  //   oledMessage("Card Registered", uid);
  //   registerMode = false;
  //   registerOwner = "";
  // } 
  // else {
    // ===== CHẾ ĐỘ KIỂM TRA THẺ =====
    bool ok = isCardAllowed(uid);

    // luôn log event quẹt thẻ
    // sendCardEvent(uid, ok);

    if (ok) {
      // THẺ HỢP LỆ → MỞ CỬA LOCAL
      openDoor();
    } else {
      // THẺ SAI → CHỈ BÁO LỖI
      oledMessage("Card INVALID","");
      // startBeep(200);              // nếu không muốn kêu thì comment dòng này
      doorOpen = false;
      digitalWrite(LED_PIN, LOW);
    }
  // }

  mfrc522.PICC_HaltA();
  mfrc522.PCD_StopCrypto1();
}



// ======================================================
// ===================  FIREBASE CMDS ===================
// ======================================================

void handleFirebaseCommands() {
  String cmdPath = "/Commands/" + String(DEVICE_ID);
  String payload = httpGET(firebaseUrl(cmdPath));

  if (payload == "" || payload == "null") return;

  DynamicJsonDocument doc(2048);
  if (deserializeJson(doc, payload)) return;

  String type  = doc["type"]  | "";
  String value = doc["value"] | "";

  Serial.println("[CMD] type=" + type + " value=" + value);

  if (type == "open_door") {
    // OPTION 2: MỞ CỬA DO SERVER REQUEST
    openDoor();
  } 
  // else if (type == "register_card") {
  //   // SERVER YÊU CẦU ĐĂNG KÝ THẺ MỚI
  //   registerMode  = true;
  //   registerOwner = value;
  //   oledMessage("Register card", "Tap new card");
  // }

  // Xoá command sau khi xử lý
  httpPUT(firebaseUrl(cmdPath), "null");
}



// ======================================================
// ======================= SETUP =========================
// ======================================================

void connectWiFi() {
  WiFi.begin(WIFI_SSID, WIFI_PASSWORD);
  Serial.print("Connecting WiFi");

  while (WiFi.status() != WL_CONNECTED) {
    delay(500);
    Serial.print(".");
  }

  Serial.println("\nWiFi connected!");
  Serial.print("IP: ");
  Serial.println(WiFi.localIP());
}

void setup() {
  Serial.begin(115200);

  pinMode(LED_PIN, OUTPUT);
  pinMode(BUZZER_PIN, OUTPUT);
  pinMode(BTN_PIN, INPUT_PULLUP);

  digitalWrite(LED_PIN, LOW);
  digitalWrite(BUZZER_PIN, LOW);

  attachInterrupt(digitalPinToInterrupt(BTN_PIN), buttonISR, FALLING);

  // RFID init
  SPI.begin();
  mfrc522.PCD_Init();
  Serial.println("RC522 init done");

  // OLED init
  if (!display.begin(SSD1306_SWITCHCAPVCC, 0x3C)) {
    Serial.println("OLED FAILED!");
    while (1);
  }

  oledMessage("Smart Door", "Connecting WiFi...");
  connectWiFi();

  oledMessage("Smart Door", "Ready");
}



// ======================================================
// ======================== LOOP =========================
// ======================================================

void loop() {
  unsigned long now = millis();

  // ---- Button debounce ----
  if (buttonInterruptFlag) {
    buttonInterruptFlag = false;

    if (now - lastButtonHandled > BUTTON_DEBOUNCE_MS) {
      lastButtonHandled = now;
      handleButtonPress();
    }
  }

  // ---- Tasks ----
  buzzerTask();
  doorTask();
  rfidTask();

  // ---- Firebase polling ----
  if (now - lastCmdCheck > CMD_CHECK_INTERVAL_MS) {
    lastCmdCheck = now;
    handleFirebaseCommands();
  }
}
