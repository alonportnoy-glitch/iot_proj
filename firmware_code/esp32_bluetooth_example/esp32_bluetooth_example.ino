#include "BluetoothSerial.h"

// Check if Bluetooth is available
#if !defined(CONFIG_BT_ENABLED) || !defined(CONFIG_BLUEDROID_ENABLED)
#error Bluetooth is not enabled! Please run `make menuconfig` to and enable it
#endif

// Check Serial Port Profile
#if !defined(CONFIG_BT_SPP_ENABLED)
#error Serial Port Profile for Bluetooth is not available or not enabled. It is only available for the ESP32 chip.
#endif

BluetoothSerial SerialBT;

String device_name = "ESP32-BT-Slave22";

int t;
float tmp36;
int sampleNum = 100;
int flagBTConnected = false;

void setup()
{
  Serial.begin(115200);
  SerialBT.begin(device_name);  //Bluetooth device name
  Serial.printf("The device with name \"%s\" is started.\nNow you can pair it with Bluetooth!\n", device_name.c_str());
}


bool isSending = true;

void loop()
{
  if (SerialBT.available()) {
    delay(20); // Wait for the full message to arrive
    String incoming = "";
    while(SerialBT.available()) {
      incoming += (char)SerialBT.read();
    }
    incoming.trim();
    Serial.println("Received: " + incoming); // Debug to Serial
    
    if (incoming == "STOP") {
      isSending = false;
      SerialBT.println("Stopped");
    } else if (incoming == "START") {
      isSending = true;
      SerialBT.println("Started");
    }
  }

  if (SerialBT.hasClient()) {
    if (flagBTConnected == false) {
      Serial.println("Bluetooth client connected!");
      flagBTConnected = true;
    }

    if (isSending) {
      t = t+1;
      
      // Generate a random number between 0 and 100 to simulate temperature
      // random() returns a long between 0 and max - 1
      tmp36 = random(0, 10000) / 100.0; 
      
      Serial.println(tmp36);
    

      SerialBT.println((String)" " + tmp36 + ", " + t);

      int delayTime = random(50, 300); // Random delay between 200 and 800 ms
      delay(delayTime);
    } else {
      delay(100); // Small delay when not sending
    }
  } else {
    flagBTConnected = false;
    Serial.println("No Bluetooth client connected. Waiting...");
    delay(1000); // Wait 1 second between connection checks
  }
}
