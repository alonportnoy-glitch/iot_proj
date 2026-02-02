package com.example.tutorial6_bluetooth.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import com.example.tutorial6_bluetooth.constants.Constants
import com.example.tutorial6_bluetooth.databinding.ActivityMainBinding
import com.example.tutorial6_bluetooth.service.SerialService

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val buffer = StringBuilder() // Add this to handle data fragmentation
    // --- JUMPBiT: Python Object to hold your class instance ---
    private var jumpDetector: com.chaquo.python.PyObject? = null

    private var isTracking = false // Track if we are currently "started"

    // Helper to send "START" or "STOP" to the SerialService
    private fun sendBluetoothMessage(msg: String) {
        val intent = Intent(this, SerialService::class.java)
        intent.action = Constants.ACTION_WRITE_DATA
        intent.putExtra(Constants.EXTRA_DATA, msg.toByteArray())
        startService(intent)
    }
    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            if (permissions.all { it.value }) {
                Toast.makeText(this, "Permissions Granted", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Permissions Denied", Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        android.util.Log.d("MainActivity", "onCreate")
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // --- JUMPBEAT: Initialize Python & Load Your Script ---
        initPython()

        checkPermissions()

        binding.btnConnect.setOnClickListener {
            if (hasPermissions()) {
                val intent = Intent(this, DeviceListActivity::class.java)
                startActivity(intent)
            } else {
                checkPermissions()
            }
        }

        binding.btnStart.setOnClickListener {
            if (serialService?.isConnected == true) {
                sendBluetoothMessage("START")
                isTracking = true
                updateButtonState(true) // Update UI
            }
        }

        // Mapping 'Stop' button to the disconnect logic
        binding.btnStop.setOnClickListener {
            if (serialService?.isConnected == true) {
                sendBluetoothMessage("STOP")
                isTracking = false
                updateButtonState(true) // Update UI
            }
        }

        // Use safe calls for buttons that might not be in landscape
        binding.btnStart.isEnabled = false
        binding.btnStop.isEnabled = false
        updateButtonState(false)
    }

    // --- JUMPBEAT: Helper function to start Chaquopy ---
    private fun initPython() {
        if (!Python.isStarted()) {
            Python.start(AndroidPlatform(this))
        }
        val py = Python.getInstance()
        // "step_detector" is the filename of your python script
        val module = py.getModule("jump_")
        // "JumpDetector" is the class name inside that script
        jumpDetector = module.callAttr("JumpDetector")
    }

    // --- JUMPBEAT: Receiver for Incoming Bluetooth Data ---
    // This listens for data coming from the SerialService
    private val serialDataReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            // Use the same action as TerminalActivity for consistency
            if (intent?.action == Constants.ACTION_SERIAL_DATA_RECEIVED) {
                val data = intent.getByteArrayExtra(Constants.EXTRA_DATA)
                if (data != null) {
                    val text = String(data)

                    // Use LogUtils to handle fragmentation (ensures full lines like "t,ax,ay,az...")
                    val lines = com.example.tutorial6_bluetooth.logging.LogUtils.processBuffer(buffer, text)

                    for (line in lines) {
                        processIncomingData(line)
                    }
                }
            }
        }
    }

    // --- JUMPBEAT: Parse Data and Send to Python ---
    @SuppressLint("DefaultLocale")
    private fun processIncomingData(dataString: String) {
        android.util.Log.d("BT_DATA", "Received: $dataString")
        try {
            val parts = dataString.split(",")
            // Ensure we have enough parts (t, ax, ay, az, gx, gy, gz, pulse)
            if (parts.size >= 8) {
                val t = parts[0].toFloat()
                val ax = parts[1].toFloat()
                val ay = parts[2].toFloat()
                val az = parts[3].toFloat()
                val gx = parts[4].toFloat()
                val gy = parts[5].toFloat()
                val gz = parts[6].toFloat()
                val pulse = parts[7].toFloat()

                // Call Python logic
                val results = jumpDetector?.callAttr("process_realtime", t, ax, ay, az, gx, gy, gz, pulse)?.asList()

                if (results != null) {
                    android.util.Log.d("PY_RES", "Results: $results")
                    val totalJumps = results[0].toInt()
                    val bpm = results[2].toFloat()
                    val rpm = results[3].toFloat()
                    val efficiency = results[4].toFloat()

                    runOnUiThread {
                        // Update your CardView/UI elements
                        binding.tvJumpsCount.text = totalJumps.toString()
                        binding.tvBpm.text = String.format("%.0f", pulse)
                        binding.tvRpm.text = String.format("%.1f", rpm)
                        binding.tvEfficiency.text = String.format("%.2f %%", efficiency * 100)

                        binding.tvConnectionStatus.text = "Connected — Tracking"
                        binding.tvConnectionStatus.setTextColor(android.graphics.Color.parseColor("#2E7D32"))
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("JumpBeat", "Error parsing line: $dataString - ${e.message}")
        }
    }
    private var serialService: SerialService? = null
    private var isBound = false

    private val serviceConnection = object : android.content.ServiceConnection {
        override fun onServiceConnected(className: android.content.ComponentName, service: android.os.IBinder) {
            val binder = service as SerialService.SerialBinder
            serialService = binder.getService()
            isBound = true
            updateConnectionState(serialService?.isConnected == true)
        }

        override fun onServiceDisconnected(arg0: android.content.ComponentName) {
            isBound = false
            serialService = null
        }
    }

    override fun onStart() {
        super.onStart()
        val intent = Intent(this, SerialService::class.java)
        bindService(intent, serviceConnection, BIND_AUTO_CREATE)
    }

    override fun onStop() {
        super.onStop()
        if (isBound) {
            unbindService(serviceConnection)
            isBound = false
        }
    }

    private fun updateConnectionState(isConnected: Boolean) {
        // Just a wrapper to match your existing receiver calls
        updateButtonState(isConnected)
    }

    private fun updateButtonState(isConnected: Boolean) {
        if (isConnected) {
            // We are connected to Bluetooth
            binding.btnConnect.isEnabled = false

            // Toggle buttons based on whether we are currently tracking
            binding.btnStart.isEnabled = !isTracking
            binding.btnStop.isEnabled = isTracking

            if (isTracking) {
                binding.tvConnectionStatus.text = "Connected — Jumping!"
                binding.tvConnectionStatus.setTextColor(android.graphics.Color.parseColor("#2E7D32")) // Green
            } else {
                binding.tvConnectionStatus.text = "Connected — Ready"
                binding.tvConnectionStatus.setTextColor(android.graphics.Color.BLACK)
            }
        } else {
            // Disconnected
            isTracking = false
            binding.btnConnect.isEnabled = true
            binding.btnStart.isEnabled = false
            binding.btnStop.isEnabled = false
            binding.tvConnectionStatus.text = "Disconnected — No device"
            binding.tvConnectionStatus.setTextColor(android.graphics.Color.GRAY)
        }
    }

    private val connectionStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Constants.ACTION_SERIAL_STATE_CHANGED) {
                val isConnected = intent.getBooleanExtra(Constants.EXTRA_SERVICE_CONNECTED, false)
                updateConnectionState(isConnected)
            }
        }
    }

    override fun onResume() {
        super.onResume()

        val filter = IntentFilter()
        filter.addAction(Constants.ACTION_SERIAL_STATE_CHANGED)
        filter.addAction(Constants.ACTION_SERIAL_DATA_RECEIVED) // Match TerminalActivity

        ContextCompat.registerReceiver(
            this,
            serialDataReceiver, // Use the updated receiver logic
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        // Register connection state receiver
        ContextCompat.registerReceiver(
            this,
            connectionStateReceiver,
            IntentFilter(Constants.ACTION_SERIAL_STATE_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        // --- JUMPBEAT: Register Data Receiver ---
        // This allows us to hear the raw data coming from the rope
        ContextCompat.registerReceiver(
            this,
            serialDataReceiver,
            IntentFilter(Constants.ACTION_SERIAL_READ), // Make sure this Constant exists!
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        if (isBound && serialService != null) {
            updateConnectionState(serialService?.isConnected == true)
        }
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(connectionStateReceiver)
        // --- JUMPBEAT: Unregister Data Receiver ---
        unregisterReceiver(serialDataReceiver)
    }

    private fun hasPermissions(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                return false
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                return false
            }
        }
        return true
    }

    private fun checkPermissions() {
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
        } else {
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
            permissions.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        if (permissions.isNotEmpty()) {
            requestPermissionLauncher.launch(permissions.toTypedArray())
        }
    }
}