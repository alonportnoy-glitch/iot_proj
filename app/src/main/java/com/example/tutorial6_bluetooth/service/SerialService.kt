package com.example.tutorial6_bluetooth.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.tutorial6_bluetooth.constants.Constants
import com.example.tutorial6_bluetooth.logging.DataLogger
import com.example.tutorial6_bluetooth.logging.TextFileLogger
import com.example.tutorial6_bluetooth.ui.MainActivity
import kotlinx.coroutines.*

class SerialService : Service(), SerialListener {

    inner class SerialBinder : Binder() {
        fun getService(): SerialService = this@SerialService
    }

    private val binder = SerialBinder()
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val connectionManager = SerialConnectionManager(serviceScope)
    private var connected = false
    private val logger: DataLogger = TextFileLogger()
    private var currentLogFilename: String? = null

    // Public properties for clients
    val isConnected: Boolean
        get() = connected

    fun getLogFilename(): String? {
        return currentLogFilename
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onBind(intent: Intent): IBinder {
        return binder
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        android.util.Log.d("SerialService", "onStartCommand: action=${intent?.action}")
        when (intent?.action) {
            Constants.ACTION_SERIAL_CONNECT -> {
                val deviceAddress = intent.getStringExtra(Constants.EXTRA_DEVICE_ADDRESS)
                if (deviceAddress != null) {
                    connect(deviceAddress)
                }
            }

            Constants.ACTION_SERIAL_DISCONNECT -> {
                disconnect()
            }

            Constants.ACTION_WRITE_DATA -> {
                val data = intent.getByteArrayExtra(Constants.EXTRA_DATA)
                if (data != null) {
                    write(data)
                }
            }
            /*Constants.ACTION_START_LOGGING -> {
                val prefix = intent.getStringExtra(Constants.EXTRA_LOG_FILENAME_PREFIX) ?: "log"
                val type = intent.getStringExtra(Constants.EXTRA_LOG_TYPE) ?: "TXT"
                startLogging(prefix, type)
            }
            Constants.ACTION_STOP_LOGGING -> {
                stopLogging()
            }*/
        }
        return START_STICKY
    }

    private fun write(data: ByteArray) {
        if (!connected) return
        // This sends data to the bluetooth device, not directly to a file.
        connectionManager.write(data)
    }

    @SuppressLint("MissingPermission")
    private fun connect(deviceAddress: String) {
        android.util.Log.d("SerialService", "connect: $deviceAddress")

        // If already connected, disconnect first
        if (connected) {
            android.util.Log.d(
                "SerialService",
                "Disconnecting existing connection before new connection"
            )
            disconnect()
        }

        val manager = getSystemService(android.bluetooth.BluetoothManager::class.java)
        val adapter = manager.adapter
        val device = adapter.getRemoteDevice(deviceAddress)

        android.util.Log.d("SerialService", "Starting foreground service notification")
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(
                Constants.NOTIFICATION_ID,
                createNotification("Connecting to ${device.name ?: "Device"}..."),
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            )
        } else {
            startForeground(
                Constants.NOTIFICATION_ID,
                createNotification("Connecting to ${device.name ?: "Device"}...")
            )
        }

        // Start logging if configured
        //noinspection ConstantConditionIf
        @Suppress("KotlinConstantConditions")
        if (Constants.RECORD_ON_CONNECTION) {
            startLogging()
        }

        val socket = SerialSocket(device, this)
        connectionManager.connect(socket)
    }

    private fun startLogging(prefix: String = "log", type: String = "TXT") {
        android.util.Log.d("SerialService", "startLogging: prefix=$prefix type=$type")
        stopLogging() // Stop existing if any
        currentLogFilename = logger.start(this, prefix, type)
        broadcastConnectionState(connected) // Broadcast new filename
    }

    public fun startLoggingWithHeader(prefix: String, type: String, header: String) {
        startLogging(prefix, type)
        logger.log(header + "\n")
    }

    private fun stopLogging() {
        android.util.Log.d("SerialService", "stopLogging")
        logger.stop()
        currentLogFilename = null
        broadcastConnectionState(connected) // Broadcast update
    }

    private fun disconnect() {
        android.util.Log.d("SerialService", "disconnect")
        connected = false
        broadcastConnectionState(false)
        connectionManager.disconnect()
        stopLogging()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        android.util.Log.d("SerialService", "onDestroy")
        disconnect()
        serviceScope.cancel()
    }

    // SerialListener Implementation

    override fun onSerialConnect() {
        android.util.Log.d("SerialService", "onSerialConnect")
        connected = true
        updateNotification("Connected")
        broadcastConnectionState(true)
    }

    override fun onSerialConnectError(e: Exception) {
        android.util.Log.e("SerialService", "onSerialConnectError: Connection failed", e)
        connected = false
        updateNotification("Connection Failed: ${e.message}")
        // Ensure we broadcast the failure state so UI knows
        broadcastConnectionState(false, true)
        disconnect()
    }

    override fun onSerialRead(data: ByteArray) {
        // android.util.Log.d("SerialService", "onSerialRead: ${data.size} bytes") // Verbose
        val text = String(data)
        //logger.log(text)

        val intent = Intent(Constants.ACTION_SERIAL_DATA_RECEIVED)
        intent.putExtra(Constants.EXTRA_DATA, data)
        intent.setPackage(packageName)
        sendBroadcast(intent)
    }

    override fun onSerialIoError(e: Exception) {
        android.util.Log.e("SerialService", "onSerialIoError: Connection lost", e)
        connected = false
        updateNotification("Connection Lost: ${e.message}")
        broadcastConnectionState(false, true)
        disconnect()
    }

    // Helpers

    private fun broadcastConnectionState(isConnected: Boolean, error: Boolean = false) {
        val intent = Intent(Constants.ACTION_SERIAL_STATE_CHANGED)
        intent.putExtra(Constants.EXTRA_SERVICE_CONNECTED, isConnected)
        intent.putExtra(Constants.EXTRA_ERROR, error)
        if (currentLogFilename != null) {
            intent.putExtra(Constants.EXTRA_LOG_FILENAME, currentLogFilename)
        }
        intent.setPackage(packageName)
        sendBroadcast(intent)
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            Constants.NOTIFICATION_CHANNEL_ID,
            "Serial Service",
            NotificationManager.IMPORTANCE_LOW
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun createNotification(text: String): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, Constants.NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Bluetooth Serial")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentIntent(pendingIntent)
            .build()
    }

    private fun updateNotification(text: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(Constants.NOTIFICATION_ID, createNotification(text))
    }
}
