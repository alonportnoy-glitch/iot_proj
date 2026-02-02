#include "BluetoothSerial.h"
#include "heartRate.h"
#include <Wire.h>
#include <MAX30105.h> 
#include <Adafruit_LSM303_U.h>
#include <Adafruit_Sensor.h>
#include <Adafruit_L3GD20_U.h>

// Sensors (same as before)
Adafruit_LSM303_Accel_Unified accel = Adafruit_LSM303_Accel_Unified(54321);
Adafruit_L3GD20_Unified gyro = Adafruit_L3GD20_Unified(20);
MAX30105 particleSensor; 

BluetoothSerial SerialBT;
String device_name = "ESP32-BT-Slave22";

// ----- Sampling & Detection parameters -----
const unsigned long SAMPLE_INTERVAL_MS = 10; // 100 Hz
const float EMA_ALPHA = 0.1f;   // DC estimate smoothing (lower => slower)
const float AMP_ALPHA = 0.05f;   // amplitude (rectified) smoothing for adaptive threshold
const float THRESH_MULT = 2.0f;  // threshold = AMP_EST * THRESH_MULT
const unsigned long MIN_RR_MS = 300; // minimum ms between beats (refractory) ~ 200 BPM max -> 300ms ~ 200 BPM
const unsigned long RATE_WINDOW_MS = 3000; // 3 second window to average BPM

// ----- State -----
unsigned long lastSampleMillis = 0;
bool isSending = false;
float time_started = 0;

bool flagBTConnected = false;

// Beat detection state
float ir_dc = 0.0f;       // DC estimate (EMA)
float amp_est = 0.0f;     // rectified amplitude estimate (EMA)
bool prev_rising = false;
float prev_ac = 0.0f;
unsigned long lastBeatMs = 0;

float currentBPM = 0.0f; // Global variable to store latest BPM
unsigned long lastTxMillis = 0; // Timer for Bluetooth transmission
const unsigned long TX_INTERVAL_MS = 20; // 50Hz Transmission rate (Saves bandwidth)

// Queue for beat timestamps (milliseconds)
#define MAX_BEATS 32
unsigned long beatTimes[MAX_BEATS];
int beatCount = 0;

// Utility: push beat timestamp and maintain sliding window
void pushBeat(unsigned long nowMs) {
  // append
  if (beatCount < MAX_BEATS) {
    beatTimes[beatCount++] = nowMs;
  } else {
    // shift left (rare)
    for (int i = 1; i < MAX_BEATS; ++i) beatTimes[i-1] = beatTimes[i];
    beatTimes[MAX_BEATS-1] = nowMs;
  }
  // remove old outside RATE_WINDOW_MS
  unsigned long cutoff = nowMs - RATE_WINDOW_MS;
  int start = 0;
  while (start < beatCount && beatTimes[start] < cutoff) start++;
  if (start > 0) {
    int newCount = beatCount - start;
    for (int i = 0; i < newCount; ++i) beatTimes[i] = beatTimes[start + i];
    beatCount = newCount;
  }
}

// compute BPM from the beatTimes (same formula as your python: (n-1)/(span/60))
float computeBPM() {
  if (beatCount < 2) return 0.0f;
  unsigned long t0 = beatTimes[0];
  unsigned long tlast = beatTimes[beatCount - 1];
  unsigned long spanMs = tlast - t0;
  if (spanMs == 0) return 0.0f;
  float minutes = spanMs / 60000.0f;
  float rate = (beatCount - 1) / minutes;
  return roundf(rate * 10.0f) / 10.0f; // one decimal like earlier
}

// Display sensor details (kept from your original)
void displaySensorDetails(void)
{
  sensor_t sensor;
  accel.getSensor(&sensor);
  Serial.println("------------------------------------");
  Serial.print  ("Sensor:       "); Serial.println(sensor.name);
  Serial.print  ("Driver Ver:   "); Serial.println(sensor.version);
  Serial.print  ("Unique ID:    "); Serial.println(sensor.sensor_id);
  Serial.print  ("Max Value:    "); Serial.print(sensor.max_value); Serial.println(" m/s^2");
  Serial.print  ("Min Value:    "); Serial.print(sensor.min_value); Serial.println(" m/s^2");
  Serial.print  ("Resolution:   "); Serial.print(sensor.resolution); Serial.println(" m/s^2");
  Serial.println("------------------------------------");
  Serial.println("");
  delay(500);
}

void setup()
{
  Serial.begin(115200);
  SerialBT.begin(device_name);  //Bluetooth device name
  Serial.printf("The device with name \"%s\" is started.\nNow you can pair it with Bluetooth!\n", device_name.c_str());

  if(!accel.begin())
  {
    Serial.println("Ooops, no LSM303 detected ... Check your wiring!");
    while(1);
  }

  gyro.enableAutoRange(true);
  if(!gyro.begin())
  {
    Serial.println("Ooops, no L3GD20 detected ... Check your wiring!");
    while(1);
  }

  if (!particleSensor.begin(Wire)) 
  {
    Serial.println("MAX30102 was not found. Please check wiring/power. ");
    while (1);
  }
  
  Wire.setClock(400000); 
  particleSensor.setup(); 
  particleSensor.setPulseAmplitudeRed(0x0A); // small LED amplitude to save power, tune if needed
  particleSensor.setPulseAmplitudeGreen(0); // Turn off Green LED

  displaySensorDetails();

  // initialize timing and estimators
  lastSampleMillis = millis();
  ir_dc = 0.0f;
  amp_est = 0.0f;
  prev_ac = 0.0f;
  prev_rising = false;
  lastBeatMs = 0;
  beatCount = 0;
  time_started = 0;
}

// Non-blocking command reader (reads commands from BT serial without blocking sampling)
void handleBTCommandsNonBlocking() {
  if (SerialBT.available()) {
    // read all available data into a single string
    String incoming = "";
    while (SerialBT.available()) {
      char c = (char)SerialBT.read();
      incoming += c;
    }
    incoming.trim();
    Serial.println("Received: " + incoming);
    if (incoming == "STOP") {
      isSending = false;
    } else if (incoming == "START") {
      isSending = true;
      time_started = millis();
      // reset beat counters if desired
      // beatCount = 0;
    }
  }
}


void loop()
{
  // 1. Always poll commands (Keep this fast)
  handleBTCommandsNonBlocking();

  // 2. Manage Bluetooth Connection State
  if (SerialBT.hasClient()) {
    if (!flagBTConnected) {
      Serial.println("Bluetooth client connected!");
      flagBTConnected = true;
    }
  } else {
    if (flagBTConnected) {
      Serial.println("Bluetooth client disconnected.");
    }
    flagBTConnected = false;
  }

  // ---------------------------------------------------------
  // 3. PULSE SENSOR PROCESSING (High Priority - Drain Buffer)
  // ---------------------------------------------------------
  
  // This pulls data from the sensor hardware into the library's software buffer
  particleSensor.check(); 

  // Process ALL available samples. 
  // If BT blocked for 50ms, there might be 5 samples waiting. We process all of them now.
  while (particleSensor.available()) {
    
    // Get the oldest sample from the FIFO
    long irValue = particleSensor.getFIFOIR(); 
    
    // --- Signal Processing Math (Same as before) ---
    
    // Initialize DC if needed
    if (ir_dc == 0.0f) ir_dc = (float)irValue;
    
    // EMA for DC baseline
    ir_dc = (1.0f - EMA_ALPHA) * ir_dc + EMA_ALPHA * (float)irValue;
    
    // AC component
    float ac = (float)irValue - ir_dc;
    
    // Rectified
    float rect = ac > 0.0f ? ac : 0.0f;
    
    // Amplitude estimate
    amp_est = (1.0f - AMP_ALPHA) * amp_est + AMP_ALPHA * rect;
    
    // Threshold
    float threshold = amp_est * THRESH_MULT;
    
    // Peak Detection
    bool nowRising = (ac > prev_ac);
    unsigned long now = millis(); // Approx timestamp for this sample
    
    if (prev_rising && !nowRising) {
      // Local maximum detected
      unsigned long sinceLastBeat = now - lastBeatMs;
      if (prev_ac > threshold && sinceLastBeat > MIN_RR_MS) {
        // Register beat
        lastBeatMs = now;
        pushBeat(now);
        // Optional Debug (Warning: High frequency printing can cause lag too)
        // Serial.printf("Beat! BPM: %.1f\n", computeBPM());
      }
    }
    
    prev_rising = nowRising;
    prev_ac = ac;

    // Update the global BPM variable so the BT loop can see it
    currentBPM = computeBPM();

    // Advance to the next sample in the FIFO
    particleSensor.nextSample(); 
  }

  // ---------------------------------------------------------
  // 4. BLUETOOTH TRANSMISSION (Lower Priority - Timed)
  // ---------------------------------------------------------
  unsigned long now = millis();
  
  // We send at 50Hz (TX_INTERVAL_MS = 20) to save bandwidth
  if (now - lastTxMillis >= TX_INTERVAL_MS) {
    lastTxMillis += TX_INTERVAL_MS; 
    
    // Read IMU (IMU data is real-time, so we read it "now")
    sensors_event_t event_acc;
    sensors_event_t event_gyro;
    accel.getEvent(&event_acc);
    gyro.getEvent(&event_gyro);

    // Send via Bluetooth if connected and started
    if (SerialBT.hasClient() && isSending) {
        float timestamp_s = (now - (unsigned long)time_started) / 1000.0f;
        
        // Construct String
        // Note: Using currentBPM which was calculated in the loop above
        String out = String(timestamp_s, 3) + ", " +
                     String(event_acc.acceleration.x, 3) + ", " + 
                     String(event_acc.acceleration.y, 3) + ", " + 
                     String(event_acc.acceleration.z, 3) + ", " +
                     String(event_gyro.gyro.x, 3) + ", " + 
                     String(event_gyro.gyro.y, 3) + ", " + 
                     String(event_gyro.gyro.z, 3) + ", " +
                     String(currentBPM, 1);
                     
        SerialBT.println(out);
    }
  }
  
  // Small yield
  delay(1);
}
