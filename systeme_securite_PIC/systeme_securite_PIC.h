#ifndef __SYSTEME_SECURITE_PIC__
#define __SYSTEME_SECURITE_PIC__

#include <18F4550.h>
#device ADC=10
#device PASS_STRINGS=IN_RAM 
#use delay(crystal=20MHz)
#use rs232(baud=9600, xmit=PIN_C6, rcv=PIN_C7, ERRORS)

#include <string.h>
#include <stdio.h>



#define int8_t                        int8
#define int16_t                       int16
#define int32_t                       int32
#define uint8_t                       unsigned int8_t
#define uint16_t                      unsigned int16_t
#define uint32_t                      unsigned int32_t
#define bool                          int1
 
#define SerialPrint                    printf


#define LOW     0
#define HIGH    1
#define OUTPUT  0
#define IINPUT  1

#define RX_BUFFER_SIZE  64
char rxBuffer[RX_BUFFER_SIZE];
int8 rxHead = 0;
int8 rxTail = 0;

int32 MillisCounter = 0;
int8 toiletteDuty = 0;
int8 toiletteCounter = 0;

#define BUZZER_PIN            PIN_C0   
#define BUZZER_HALF_PERIOD_MS 150      // 150ms ON + 150ms OFF = cycle 300ms
int1 buzzerActive = 0;   

#INT_TIMER2
void Timer2ISR() {
   static int8 count = 0;
   static int16 buzzerCount = 0;  
   count++;
   if(count >= 5) { // 5 * 200us = 1ms
      MillisCounter++;
      count = 0;

      if (buzzerActive) {
         buzzerCount++;
         if (buzzerCount >= BUZZER_HALF_PERIOD_MS) {
            buzzerCount = 0;
            output_toggle(BUZZER_PIN);
         }
      } else {
         buzzerCount = 0;
         output_low(BUZZER_PIN);
      }
   }
   toiletteCounter++;
   if ((toiletteCounter & 0x3F) < (toiletteDuty >> 2)) output_high(PIN_E1);
   else output_low(PIN_E1);
}

#INT_RDA
void rdaISR() {
   int8 nextHead = (rxHead + 1) % RX_BUFFER_SIZE;
   if (nextHead != rxTail) {
      rxBuffer[rxHead] = getc();
      rxHead = nextHead;
   } else {
      getc(); 
   }
}

int32 millis() {
   int32 val;
   disable_interrupts(GLOBAL);
   val = MillisCounter;
   enable_interrupts(GLOBAL);
   return val;
}

// ---------- pinMode generic (PORT A/C/D/E) ----------
void pinMode(int16 pin, int state) {
   int16 bit;
   if (pin >= PIN_A0 && pin <= PIN_A5) {
      bit = get_tris_a();
      if (state == 1) bit |= (1 << (pin - PIN_A0));
      else             bit &= ~(1 << (pin - PIN_A0));
      set_tris_a(bit);
   }
   if (pin >= PIN_C0 && pin <= PIN_C7) {
      bit = get_tris_c();
      if (state == 1) bit |= (1 << (pin - PIN_C0));
      else             bit &= ~(1 << (pin - PIN_C0));
      set_tris_c(bit);
   }
   if (pin >= PIN_D0 && pin <= PIN_D5) {
      bit = get_tris_d();
      if (state == 1) bit |= (1 << (pin - PIN_D0));
      else             bit &= ~(1 << (pin - PIN_D0));
      set_tris_d(bit);
   }
   if (pin >= PIN_E0 && pin <= PIN_E2) {
      bit = get_tris_e();
      if (state == 1) bit |= (1 << (pin - PIN_E0));
      else             bit &= ~(1 << (pin - PIN_E0));
      set_tris_e(bit);
   }
}

#define digitalWrite(pin, value) output_bit((pin), (value))
#define digitalRead(pin)         input(pin)
#define delay                  delay_ms

int8 pinToADCChannel(int16 pin) {
   switch (pin) {
      case PIN_A0: return 0;
      case PIN_A1: return 1;
      case PIN_A2: return 2;
      case PIN_A3: return 3;
      case PIN_A5: return 4;
      case PIN_E0: return 5;
      case PIN_E1: return 6;
      case PIN_E2: return 7;
      case PIN_B2: return 8;
      case PIN_B3: return 9;
      case PIN_B1: return 10;
      case PIN_B4: return 11;
      case PIN_B0: return 12;
      default:     return 0;
   }
}

float analogRead(int16 pin) {
   set_adc_channel(pinToADCChannel(pin));
   delay_us(20);
   return read_adc();
}

void analogWrite(int8 pin, int8 value) {
   if (pin == PIN_C2) {
      int16 duty = ((int32)value * 1023) / 255;
      set_pwm1_duty(duty);
   } else if (pin == PIN_E1) {
      toiletteDuty = value;
   } else {
      if (value > 127) output_high(pin); else output_low(pin);
   }
}

#define SCL PIN_D0
#define SDA PIN_D1

#define DS3231_ADDR_WRITE   0xD0   // (0x68 << 1)
#define DS3231_ADDR_READ    0xD1   // (0x68 << 1) | 1
#define DS3231_REG_SECONDS  0x00
#define DS3231_REG_HOURS    0x02
#define DS3231_REG_STATUS   0x0F
#define DS3231_REG_TEMP_MSB 0x11

typedef struct {
   int8 second;      
   int8 minute;     
   int8 hour;        
   int8 dayOfWeek;  
   int8 date;        
   int8 month;       
   int8 year;        
} DateTime;

void rtc_i2c_start() {
   digitalWrite(SDA, HIGH);
   digitalWrite(SCL, HIGH);
   delay_us(5);
   digitalWrite(SDA, LOW);
   delay_us(5);
   digitalWrite(SCL, LOW);
}

void rtc_i2c_stop() {
   digitalWrite(SDA, LOW);
   digitalWrite(SCL, HIGH);
   delay_us(5);
   digitalWrite(SDA, HIGH);
   delay_us(5);
}

void rtc_i2c_write(int8 data) {
   int8 i;
   for (i = 0; i < 8; i++) {
      digitalWrite(SDA, (data & 0x80) ? HIGH : LOW);
      digitalWrite(SCL, HIGH);
      delay_us(5);
      digitalWrite(SCL, LOW);
      data <<= 1;
   }
   input(SDA);
   digitalWrite(SCL, HIGH);
   delay_us(5);
   digitalWrite(SCL, LOW);
}

int8 rtc_i2c_read(int1 ack) {
   int8 value = 0;
   int8 i;
   input(SDA);
   for (i = 0; i < 8; i++) {
      value <<= 1;
      digitalWrite(SCL, HIGH);
      delay_us(5);
      if (input(SDA)) value |= 1;
      digitalWrite(SCL, LOW);
      delay_us(5);
   }
   digitalWrite(SDA, ack ? LOW : HIGH);   
   digitalWrite(SCL, HIGH);
   delay_us(5);
   digitalWrite(SCL, LOW);
   input(SDA);
   return value;
}

int8 dec2bcd(int8 val) { return (int8)(((val / 10) << 4) | (val % 10)); }
int8 bcd2dec(int8 val) { return (int8)(((val >> 4) * 10) + (val & 0x0F)); }

int8 rtc_readRegister(int8 reg) {
   int8 value;
   rtc_i2c_start();
   rtc_i2c_write(DS3231_ADDR_WRITE);
   rtc_i2c_write(reg);
   rtc_i2c_start();                    
   rtc_i2c_write(DS3231_ADDR_READ);
   value = rtc_i2c_read(0);          
   rtc_i2c_stop();
   return value;
}

void rtc_writeRegister(int8 reg, int8 value) {
   rtc_i2c_start();
   rtc_i2c_write(DS3231_ADDR_WRITE);
   rtc_i2c_write(reg);
   rtc_i2c_write(value);
   rtc_i2c_stop();
}

void rtc_init() {
   int8 h = rtc_readRegister(DS3231_REG_HOURS);
   rtc_writeRegister(DS3231_REG_HOURS, h & 0x3F);   // manery mode 24h
}

void rtc_clearLostPowerFlag() {
   int8 status = rtc_readRegister(DS3231_REG_STATUS);
   rtc_writeRegister(DS3231_REG_STATUS, status & ~0x80);
}

void rtc_setDateTime(DateTime* dt) {
   rtc_i2c_start();
   rtc_i2c_write(DS3231_ADDR_WRITE);
   rtc_i2c_write(DS3231_REG_SECONDS);
   rtc_i2c_write(dec2bcd(dt->second));
   rtc_i2c_write(dec2bcd(dt->minute));
   rtc_i2c_write(dec2bcd(dt->hour) & 0x3F);   // bit6=0 -> mode 24h 
   rtc_i2c_write(dec2bcd(dt->dayOfWeek));
   rtc_i2c_write(dec2bcd(dt->date));
   rtc_i2c_write(dec2bcd(dt->month));
   rtc_i2c_write(dec2bcd(dt->year));
   rtc_i2c_stop();
   rtc_clearLostPowerFlag();
}

void rtc_getDateTime(DateTime* dt) {
   int8 raw[7];
   int8 i;

   rtc_i2c_start();
   rtc_i2c_write(DS3231_ADDR_WRITE);
   rtc_i2c_write(DS3231_REG_SECONDS);
   rtc_i2c_start();                     
   rtc_i2c_write(DS3231_ADDR_READ);
   for (i = 0; i < 7; i++) {
      raw[i] = rtc_i2c_read(i < 6);    
   }
   rtc_i2c_stop();

   dt->second    = bcd2dec(raw[0] & 0x7F);
   dt->minute    = bcd2dec(raw[1] & 0x7F);
   dt->hour      = bcd2dec(raw[2] & 0x3F);
   dt->dayOfWeek = bcd2dec(raw[3] & 0x07);
   dt->date      = bcd2dec(raw[4] & 0x3F);
   dt->month     = bcd2dec(raw[5] & 0x1F);
   dt->year      = bcd2dec(raw[6]);
}

int1 rtc_lostPower() {
   return (rtc_readRegister(DS3231_REG_STATUS) & 0x80) != 0;
}

float rtc_getTemperature() {
   signed int8 msb;
   int8 lsb;
   msb = rtc_readRegister(DS3231_REG_TEMP_MSB);
   lsb = rtc_readRegister(0x12);
   return (float)msb + ((lsb >> 6) * 0.25);
}

void Init() {
   setup_adc_ports(AN0);
   setup_adc(ADC_CLOCK_INTERNAL);
   setup_ccp1(CCP_PWM);
   setup_timer_2(T2_DIV_BY_4, 249, 1); 
   enable_interrupts(INT_TIMER2);
   enable_interrupts(INT_RDA);
   enable_interrupts(GLOBAL);
   rtc_init();   
}

void setup();
void loop();

void main() {
   Init();
   setup();
   while(TRUE) { loop(); }
}
#endif
