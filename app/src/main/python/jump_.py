import numpy as np

class JumpDetector:
    def __init__(self):
        """
        Initialize the Jump Detector with calibrated parameters for JumpBeat.
        """
        # --- CALIBRATED PARAMETERS ---
        # Jumps create higher G-force peaks than walking.
        # We increase the threshold to avoid counting small hops or arm movements.
        self.JUMP_THRESHOLD = 13.5

        # Minimum time between jumps (Debouncing).
        # 300ms = Max 200 Jumps Per Minute, preventing double counts.
        self.MIN_DELAY_MS = 300

        # --- STATE VARIABLES ---
        self.last_jump_time = 0
        self.jump_count = 0
        self.is_above_threshold = False

        # We track start time from the ESP32 to calculate RPM
        self.start_t = None

        # --- SMOOTHING (IMU) ---
        self.window_size = 4
        self.mag_window = []

        # --- PULSE SENSOR PROCESSING ---
        # Moving Average Window for Heart Rate to filter noise
        self.hr_window_size = 10
        self.hr_window = []
        self.smoothed_bpm = 0.0

    def process_realtime(self, t, ax, ay, az, gx, gy, gz, raw_pulse):
        """
        Process sensor data: IMU for Jumps + Pulse Sensor for Heart Rate.

        Args:
            t (float): Timestamp from ESP32 (ms).
            ax, ay, az (float): Accelerometer values.
            gx, gy, gz (float): Gyroscope values (reserved for future use).
            raw_pulse (float): Raw value from the Pulse Sensor.

        Returns:
            list: [total_jumps, is_new_jump, current_bpm, current_rpm, efficiency_score, fatigue_alert]
        """
        # 1. Handle Timestamp (Normalize to start at 0)
        current_time = float(t)
        if self.start_t is None:
            self.start_t = current_time

        # --- PART 1: JUMP DETECTION (IMU) ---
        # 1. Calculate Magnitude using Accelerometer
        magnitude = np.sqrt(ax**2 + ay**2 + az**2)

        # 2. Smooth the IMU signal (Moving Average)
        self.mag_window.append(magnitude)
        if len(self.mag_window) > self.window_size:
            self.mag_window.pop(0)
        smoothed_mag = np.mean(self.mag_window)

        # [cite_start]3. Peak Detection Logic [cite: 14]
        is_new_jump = False

        if smoothed_mag > self.JUMP_THRESHOLD:
            # Rising Edge Detection
            if not self.is_above_threshold:
                self.is_above_threshold = True
                # Debounce Check using ESP32 time
                if (current_time - self.last_jump_time) > self.MIN_DELAY_MS:
                    self.jump_count += 1
                    self.last_jump_time = current_time
                    is_new_jump = True
        else:
            # Signal dropped below threshold
            self.is_above_threshold = False

        # --- PART 2: HEART RATE PROCESSING (Pulse) ---
        # [cite_start]1. Signal Processing: Moving Average to filter hand vibration noise [cite: 12, 13]
        if raw_pulse > 0: # Only process valid readings
            self.hr_window.append(raw_pulse)
            if len(self.hr_window) > self.hr_window_size:
                self.hr_window.pop(0)

            # Calculate the average of the window
            self.smoothed_bpm = sum(self.hr_window) / len(self.hr_window)

        # --- PART 3: PERFORMANCE METRICS ---
        # [cite_start]1. Calculate RPM (Jumps Per Minute) [cite: 15]
        # Calculate elapsed minutes based on ESP32 timestamp (assumed ms)
        elapsed_minutes = (current_time - self.start_t) / 1000.0 / 60.0

        rpm = 0.0
        if elapsed_minutes > 0.1: # Avoid division by zero at start
            rpm = self.jump_count / elapsed_minutes

        # 2. Effort-to-Heart Rate Correlation (Efficiency)
        # Ratio of RPM to BPM.
        # High Efficiency = High Jumps with Low Heart Rate.
        efficiency_score = 0.0
        if self.smoothed_bpm > 0:
            efficiency_score = rpm / self.smoothed_bpm

        # [cite_start]3. Fatigue Detection [cite: 16]
        # Detect if Heart Rate is high (e.g. > 160) but Performance (RPM) is dropping (e.g. < 100)
        fatigue_alert = False
        if self.smoothed_bpm > 160 and rpm < 100 and elapsed_minutes > 1:
            fatigue_alert = True

        # Return simplified list for easy Java/Kotlin parsing
        # Index: 0=Jumps, 1=NewJump?, 2=BPM, 3=RPM, 4=Efficiency, 5=Fatigue
        return [
            self.jump_count,
            is_new_jump,
            float(round(self.smoothed_bpm, 1)),
            float(round(rpm, 1)),
            float(round(efficiency_score, 2)),
            fatigue_alert
        ]

    def reset(self):
        """
        Resets the session.
        """
        self.jump_count = 0
        self.last_jump_time = 0
        self.mag_window = []
        self.hr_window = []
        self.smoothed_bpm = 0.0
        self.start_t = None
        self.is_above_threshold = False