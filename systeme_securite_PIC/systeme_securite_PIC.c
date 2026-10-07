#include "systeme_securite_PIC.h"

int1 bluetooth_available() { 
   return (rxHead != rxTail); 
}

char bluetooth_read() {
   char c = rxBuffer[rxTail];
   rxTail = (rxTail + 1) % RX_BUFFER_SIZE;
   return c;
}

void bluetooth_println(char* msg) { 
   SerialPrint("%s\n", msg); 
}

#define LED_SALON     PIN_E0
#define LED_TOILETTE  PIN_E1
#define LED_COUR      PIN_C2  
#define BUZZER        BUZZER_PIN  
#define PIR_TOILETTE  PIN_A1
#define PIR_COUR      PIN_A3
#define LDR_PIN       PIN_A0        

#define TEMPS_COUR      120000UL  // 2 minitra
#define TEMPS_TOILETTE  300000UL  // 5 minitra
#define SEUIL_NUIT      500


bool salonState = false;
bool manualCour = false;
bool manualToilette = false;
bool motionCour = false;
bool motionToilette = false;
unsigned long timerCour = false;
unsigned long timerToilette = false;


char lastCourMsg[20] = "";
char lastToiletteMsg[24] = "";
bool lastSalonState = false;
char cmdBuf[32];
int8_t cmdLen = 0;

#define DEBOUNCE_COUNT  5  
                          
int8_t pirCourStable = 0;
int8_t pirCourCounter = 0;
int8_t pirToiletteStable = 0;
int8_t pirToiletteCounter = 0;

int8_t debouncePIR(int8_t pin, int8_t* stableState, int8_t* counter) {
   int8_t raw = digitalRead(pin);
   if (raw == *stableState) {
      *counter = 0;
   } else {
      (*counter)++;
      if (*counter >= DEBOUNCE_COUNT) {
         *stableState = raw;
         *counter = 0;
      }
   }
   return *stableState;
}

// Prototypes
void readBluetooth();
void handleCommand(char* cmd);
void syncApp();

void setup() {
  pinMode(LED_SALON, OUTPUT);
  pinMode(LED_TOILETTE, OUTPUT);
  pinMode(LED_COUR, OUTPUT);
  pinMode(BUZZER, OUTPUT);
  pinMode(PIR_TOILETTE, IINPUT);
  pinMode(PIR_COUR, IINPUT);
  pinMode(LDR_PIN, IINPUT);  


  delay(1000);
  bluetooth_println("SYSTEME_PRET");
}

void loop() {
  readBluetooth();

  int16_t ldrVal = analogRead(LDR_PIN);
  int8_t nuit = (ldrVal < SEUIL_NUIT);


  int8_t pirToiletteRaw = (debouncePIR(PIR_TOILETTE, &pirToiletteStable, &pirToiletteCounter) == HIGH);

  if (nuit && pirToiletteRaw) {
    timerToilette = millis();
    motionToilette = true;
  } else if (millis() - timerToilette >= TEMPS_TOILETTE) {
    motionToilette = false;
  }

  if (motionToilette || manualToilette) {
    analogWrite(LED_TOILETTE, 255);  
  } else {
    analogWrite(LED_TOILETTE, nuit ? 128 : 0);  
  }

  
  int8_t pirCourRaw = (debouncePIR(PIR_COUR, &pirCourStable, &pirCourCounter) == HIGH);

  if (nuit && pirCourRaw) {
    timerCour = millis();
    motionCour = true;
    manualCour = false;  
  } else if (millis() - timerCour >= TEMPS_COUR) {
    motionCour = false;
  }

  if (motionCour) {
    analogWrite(LED_COUR, 255); 
    buzzerActive = 1;  
  } else {
    buzzerActive = 0;   
    if (manualCour) {
      analogWrite(LED_COUR, 255);  
    } else {
      analogWrite(LED_COUR, nuit ? 128 : 0);  
    }
  }

  digitalWrite(LED_SALON, salonState ? HIGH : LOW);

  syncApp();
  delay(30);
}

void syncApp() {
   char newCourMsg[20];
   char newToiletteMsg[24];

   if (motionCour) strcpy(newCourMsg, "COUR_ROUGE");
   else if (manualCour) strcpy(newCourMsg, "COUR_JAUNE");
   else strcpy(newCourMsg, "COUR_OFF");

   if (strcmp(newCourMsg, lastCourMsg) != 0) {
      if (motionCour) {
         DateTime dt;
         char msg[48];
         rtc_getDateTime(&dt);
         sprintf(msg, "COUR_ROUGE %02u/%02u/20%02u %02u:%02u:%02u",
                 dt.date, dt.month, dt.year, dt.hour, dt.minute, dt.second);
         bluetooth_println(msg);
      } else {
         bluetooth_println(newCourMsg);
      }
      strcpy(lastCourMsg, newCourMsg);
   }

   if (motionToilette) strcpy(newToiletteMsg, "TOILETTE_DETECTION");
   else if (manualToilette) strcpy(newToiletteMsg, "TOILETTE_JAUNE");
   else strcpy(newToiletteMsg, "TOILETTE_OFF");

   if (strcmp(newToiletteMsg, lastToiletteMsg) != 0) {
      bluetooth_println(newToiletteMsg);
      strcpy(lastToiletteMsg, newToiletteMsg);
   }

   if (salonState != lastSalonState) {
      if (salonState) bluetooth_println("SALON_ON");
      else bluetooth_println("SALON_OFF");
      lastSalonState = salonState;
   }
}

void readBluetooth() {
  while (bluetooth_available()) {
    char c = bluetooth_read();
    if (c == '\n' || c == '\r') {
      if (cmdLen > 0) {
        cmdBuf[cmdLen] = '\0';
        handleCommand(cmdBuf);
        cmdLen = 0;
      }
    } else if (cmdLen < 31) {
      cmdBuf[cmdLen++] = c;
    }
  }
}

void handleCommand( char* cmd) {
  if (strcmp(cmd, "SALON_ON") == 0) { salonState = true; digitalWrite(LED_SALON, 1); }
  else if (strcmp(cmd, "SALON_OFF") == 0) { salonState = false; digitalWrite(LED_SALON, 0); }
  else if (strcmp(cmd, "COUR_ON") == 0) manualCour = true;
  else if (strcmp(cmd, "COUR_OFF") == 0) manualCour = false;
  else if (strcmp(cmd, "TOILETTE_ON") == 0) manualToilette = true;
  else if (strcmp(cmd, "TOILETTE_OFF") == 0) {
    manualToilette = false;
    motionToilette = false;
    timerToilette = 0; 
  } else if (strcmp(cmd, "LDR") == 0) {
    char buf[16];
    int32_t ldrRead = (int32_t)analogRead(LDR_PIN);
    sprintf(buf, "LDR=%lu", ldrRead);  
    bluetooth_println(buf);
  } else if (strcmp(cmd, "PING") == 0) bluetooth_println("PONG");
  else if (strcmp(cmd, "ETAT") == 0) {
    lastCourMsg[0] = '\0';
    lastToiletteMsg[0] = '\0';
    lastSalonState = !salonState;
  }
  else if (strncmp(cmd, "SETDATE=", 8) == 0) {
    if (strlen(cmd) != 21) {   
      bluetooth_println("DATE_ERR_FORMAT");
    } else {
      char* p = cmd + 8;
      int8_t i;
      bool valide = true;
      for (i = 0; i < 13; i++) {
        if (p[i] < '0' || p[i] > '9') { valide = false; break; }
      }
      if (!valide) {
        bluetooth_println("DATE_ERR_FORMAT");
      } else {
        DateTime dtSet;
        dtSet.year      = (p[0]-'0')*10 + (p[1]-'0');
        dtSet.month     = (p[2]-'0')*10 + (p[3]-'0');
        dtSet.date      = (p[4]-'0')*10 + (p[5]-'0');
        dtSet.hour      = (p[6]-'0')*10 + (p[7]-'0');
        dtSet.minute    = (p[8]-'0')*10 + (p[9]-'0');
        dtSet.second    = (p[10]-'0')*10 + (p[11]-'0');
        dtSet.dayOfWeek = (p[12]-'0');

        if (dtSet.month < 1 || dtSet.month > 12 ||
            dtSet.date  < 1 || dtSet.date  > 31 ||
            dtSet.hour  > 23 || dtSet.minute > 59 || dtSet.second > 59 ||
            dtSet.dayOfWeek < 1 || dtSet.dayOfWeek > 7) {
          bluetooth_println("DATE_ERR_RANGE");
        } else {
          rtc_setDateTime(&dtSet);
          bluetooth_println("DATE_OK");
        }
      }
    }
  }
}
