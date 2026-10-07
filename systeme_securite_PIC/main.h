// ========== main.h — FANOVANA VOAHAZO ==========

#ifndef __MAIN__
#define __MAIN__

#include <16F877A.h>
#device ADC=10
#fuses HS,NOWDT,NOPUT,NOLVP
#use delay(crystal=20MHz)

#use rs232(baud=9600, xmit=PIN_C6, rcv=PIN_C7, errors)

#include <string.h>
#include <stdio.h>

#define A0      0
#define LOW     0
#define HIGH    1
#define OUTPUT  0
#define IINPUT  1

// ---------- Ring Buffer UART Rx ----------
#define RX_BUFFER_SIZE  64
char rxBuffer[RX_BUFFER_SIZE];
int8 rxHead = 0;
int8 rxTail = 0;

// ---------- Compteur millis() ----------
int32 arduinoMillisCounter;

#INT_TIMER2
void arduinoTimer2ISR() {
   arduinoMillisCounter += 4;
}

#INT_RDA
void rdaISR() {
   rxBuffer[rxHead] = getc();
   rxHead = (rxHead + 1) % RX_BUFFER_SIZE;
}

int32 millis() {
   int32 value;
   disable_interrupts(GLOBAL);
   value = arduinoMillisCounter;
   enable_interrupts(GLOBAL);
   return value;
}

// ---------- Fonctions Arduino-style ----------
void pinMode(int8 pin, int1 mode) {
   if((pin == PIN_A1) || (pin == PIN_A2)) {
      if(mode == IINPUT) set_tris_a(get_tris_a() | 0x06);
   }
   else if(pin == PIN_E0) {
      if(mode == OUTPUT) output_drive(PIN_E0);
      else output_float(PIN_E0);
   }
   else if(pin == PIN_E1) {
      if(mode == OUTPUT) output_drive(PIN_E1);
      else output_float(PIN_E1);
   }
   else if(pin == PIN_C2) {
      if(mode == OUTPUT) output_drive(PIN_C2);
      else output_float(PIN_C2);
   }
}

#define digitalWrite(pin, value) output_bit((pin), (value))
#define digitalRead(pin)         input(pin)
#define delay                         delay_ms
#define delayMicroseconds             delay_us
#define SerialPrint                 printf

int16 analogRead(int8 channel) {
   set_adc_channel(channel);
   delay_us(20);
   return read_adc();
}

void analogWrite(int8 pin, int8 value) {
   int16 duty;
   if(pin == PIN_C2) {
      duty = ((int32)value * 1023) / 255;
      set_pwm1_duty(duty);
   }
}

void arduinoInit() {
   setup_adc_ports(AN0);
   setup_adc(ADC_CLOCK_INTERNAL);
   set_tris_a(0xFF);

   set_tris_e(0x00);
   set_tris_c(0x80);
   setup_ccp1(CCP_PWM);
   setup_timer_2(T2_DIV_BY_16, 249, 5);
   set_pwm1_duty(0);

   enable_interrupts(INT_TIMER2);
   enable_interrupts(INT_RDA);     // <-- VAovAO: UART Rx interrupt
   enable_interrupts(GLOBAL);
}

void setup();
void loop();

void main() {
   arduinoInit();
   setup();
   while(TRUE) {
      loop();
   }
}

#endif
