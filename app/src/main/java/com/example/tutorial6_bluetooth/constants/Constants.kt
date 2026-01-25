package com.example.tutorial6_bluetooth.constants

import java.util.UUID

object Constants {
    // SPP UUID
    val SERIAL_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    // Intent Actions
    const val ACTION_SERIAL_CONNECT = "com.example.tutorial6_bluetooth.ACTION_SERIAL_CONNECT"
    const val ACTION_SERIAL_DISCONNECT = "com.example.tutorial6_bluetooth.ACTION_SERIAL_DISCONNECT"
    const val ACTION_SERIAL_DATA_RECEIVED = "com.example.tutorial6_bluetooth.ACTION_SERIAL_DATA_RECEIVED"
    const val ACTION_WRITE_DATA = "com.example.tutorial6_bluetooth.ACTION_WRITE_DATA"
    const val ACTION_SERIAL_STATE_CHANGED = "com.example.tutorial6_bluetooth.ACTION_SERIAL_STATE_CHANGED"
    
    const val ACTION_START_LOGGING = "com.example.tutorial6_bluetooth.ACTION_START_LOGGING"
    const val ACTION_STOP_LOGGING = "com.example.tutorial6_bluetooth.ACTION_STOP_LOGGING"
    const val ACTION_SERIAL_READ = "com.example.tutorial6_bluetooth.SERIAL_READ"
    const val EXTRA_SERIAL_DATA = "com.example.tutorial6_bluetooth.SERIAL_DATA"

    // Extras
    const val EXTRA_DEVICE_ADDRESS = "device_address"
    const val EXTRA_DATA = "data"
    const val EXTRA_SERVICE_CONNECTED = "service_connected"
    const val EXTRA_LOG_FILENAME = "log_filename"
    const val EXTRA_LOG_FILENAME_PREFIX = "log_filename_prefix"
    const val EXTRA_LOG_TYPE = "log_type"
    const val EXTRA_ERROR = "error"

    // Notification
    const val NOTIFICATION_CHANNEL_ID = "serial_channel"
    const val NOTIFICATION_ID = 1

    // Configuration
    const val RECORD_ON_CONNECTION = false
}
