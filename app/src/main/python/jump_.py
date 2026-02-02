import math
from collections import deque

class JumpDetector:
    def __init__(self):
        # --- Constants (Configurable) ---

        # Jump Detection Constants
        # Threshold for jump detection (m/s^2).
        # Standard gravity is ~9.81. Jumps usually spike > 15-20.
        self.ACCEL_JUMP_THRESHOLD = 20.0
        # Minimum time between jumps (in seconds) to prevent double counting
        self.JUMP_COOLDOWN = 0.7

        # Pulse (BPM) Detection Constants
        # Window size for moving average to smooth the raw IR signal
        self.PULSE_SMOOTH_WINDOW = 5
        # Threshold for detecting a pulse peak (raw IR units)
        # This often requires calibration or a dynamic threshold algorithm
        self.IR_PEAK_THRESHOLD = 108000
        # Minimum time between heartbeats (e.g., 0.25s = max 240 BPM)
        self.PULSE_COOLDOWN = 0.25

        # Calculation Windows
        # Time window (seconds) to average RPM/BPM over for stability
        self.RATE_CALC_WINDOW = 10.0

        # --- State Variables ---
        self.jump_count = 0
        self.last_jump_time = -self.JUMP_COOLDOWN
        self.jump_times = deque() # Stores timestamps of jumps for RPM calc

        self.last_beat_time = -self.PULSE_COOLDOWN
        self.beat_times = deque() # Stores timestamps of beats for BPM calc

        # Smoothing Buffer for IR signal
        self.ir_buffer = deque(maxlen=self.PULSE_SMOOTH_WINDOW)

    def process_realtime(self, t, ax, ay, az, gx, gy, gz, pulse):
        """
        Process incoming sensor data.

        Args:
            t (float): Timestamp in seconds.
            ax, ay, az (float): Acceleration in m/s^2.
            gx, gy, gz (float): Angular velocity (rad/s).
            pulse (int/float): Raw IR sensor value.

        Returns:
            list: [jump_count, is_new_jump, bpm, rpm]
        """

        # 1. Jump Detection Logic
        # We use the Euclidean norm (magnitude) to detect jumps regardless of device orientation.
        # Formula: $$|a| = \sqrt{a_x^2 + a_y^2 + a_z^2}$$
        accel_magnitude = math.sqrt(ax**2 + ay**2 + az**2)

        is_new_jump = False

        # Peak detection with cooldown
        if (accel_magnitude > self.ACCEL_JUMP_THRESHOLD and
                (t - self.last_jump_time) > self.JUMP_COOLDOWN):

            self.jump_count += 1
            self.last_jump_time = t
            self.jump_times.append(t)
            is_new_jump = True



        # 2. Pulse (BPM) Detection Logic
        # Smooth the raw IR signal to reduce noise
        self.ir_buffer.append(pulse)
        avg_ir = sum(self.ir_buffer) / len(self.ir_buffer)

        # Basic static thresholding (consider upgrading to dynamic if sensor varies widely)
        if (avg_ir > self.IR_PEAK_THRESHOLD and
                (t - self.last_beat_time) > self.PULSE_COOLDOWN):

            self.last_beat_time = t
            self.beat_times.append(t)



        # 3. Rate Calculations (BPM and RPM)
        #bpm = self._calculate_rate(self.beat_times, t)
        bpm = pulse
        rpm = self._calculate_rate(self.jump_times, t)
        efficiency_score = 0.0
        if bpm > 0:
            efficiency_score = rpm / bpm

        return [self.jump_count, accel_magnitude, bpm, rpm, efficiency_score, 0, 0, 0]

    def _calculate_rate(self, time_deque, current_time):
        """
        Helper to calculate rate (events per minute) based on a deque of timestamps.
        Removes timestamps older than RATE_CALC_WINDOW.
        """
        # Remove old events
        while len(time_deque) > 0 and (current_time - time_deque[0] > self.RATE_CALC_WINDOW):
            time_deque.popleft()

        count = len(time_deque)

        # Avoid division by zero or unstable rates with too few samples
        if count < 2:
            return 0.0

        # Calculate actual time span between first and last event in the window
        # Formula: Rate = (Events - 1) / (Time_Span / 60)
        time_span = time_deque[-1] - time_deque[0]

        if time_span <= 0:
            return 0.0

        rate = (count - 1) / (time_span / 60.0)
        return round(rate, 1)