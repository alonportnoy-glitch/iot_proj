package com.example.tutorial6_bluetooth.service

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import com.example.tutorial6_bluetooth.constants.Constants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.IOException
import kotlinx.coroutines.CancellationException

interface ISerialSocket {
    suspend fun connectAndRun()
    fun disconnect()
    suspend fun write(data: ByteArray)
}

class SerialSocket(
    private val device: BluetoothDevice,
    private val listener: SerialListener
) : ISerialSocket {
    private var socket: BluetoothSocket? = null
    private val disconnectLock = Any()
    private var isDisconnected = false

    @SuppressLint("MissingPermission")
    override suspend fun connectAndRun() {
        withContext(Dispatchers.IO) {
            android.util.Log.d("SerialSocket", "connectAndRun: Starting")
            try {
                var currentSocket: BluetoothSocket?
                synchronized(disconnectLock) {
                    if (isDisconnected) {
                        android.util.Log.d("SerialSocket", "connectAndRun: Already disconnected, aborting")
                        throw CancellationException("Disconnected before connect")
                    }
                    currentSocket = device.createRfcommSocketToServiceRecord(Constants.SERIAL_UUID)
                    socket = currentSocket
                }
                
                // Connect is blocking, so we do it outside the lock but check for null/closed if disconnected
                currentSocket?.connect()
                
                // Double check after connect if we were disconnected in the meantime
                synchronized(disconnectLock) {
                    if (isDisconnected) {
                        try {
                            currentSocket?.close()
                        } catch (ignored: Exception) {}
                        throw CancellationException("Disconnected during connect")
                    }
                }
                
                listener.onSerialConnect()
                android.util.Log.d("SerialSocket", "connectAndRun: Connected")
            } catch (e: CancellationException) {
                android.util.Log.d("SerialSocket", "connectAndRun: Cancelled during connect")
                throw e
            } catch (e: Exception) {
                var disconnected = false
                synchronized(disconnectLock) {
                    disconnected = isDisconnected
                }
                if (disconnected) {
                    android.util.Log.d("SerialSocket", "connectAndRun: Ignored error due to disconnect")
                } else {
                    android.util.Log.e("SerialSocket", "connectAndRun: Connection failed", e)
                    listener.onSerialConnectError(e)
                }
                try {
                    socket?.close()
                } catch (ignored: Exception) {
                }
                socket = null
                return@withContext
            }

            val inputStream = socket?.inputStream
            val buffer = ByteArray(1024)

            try {
                while (isActive) {
                    val bytesRead = inputStream?.read(buffer) ?: -1
                    if (bytesRead > 0) {
                        val data = buffer.copyOf(bytesRead)
                        listener.onSerialRead(data)
                    } else {
                        android.util.Log.d("SerialSocket", "connectAndRun: End of stream")
                        throw IOException("End of stream")
                    }
                }
            } catch (e: Exception) {
                if (isActive && !isDisconnected) { 
                    android.util.Log.e("SerialSocket", "connectAndRun: Read loop error", e)
                    listener.onSerialIoError(e)
                } else {
                    android.util.Log.d("SerialSocket", "connectAndRun: Cancelled/Disconnected")
                }
            } finally {
                try {
                    socket?.close()
                } catch (ignored: Exception) {
                }
                socket = null
            }
        }
    }

    override suspend fun write(data: ByteArray) {
        withContext(Dispatchers.IO) {
            try {
                socket?.outputStream?.write(data)
                android.util.Log.d("SerialSocket", "write: Wrote ${data.size} bytes")
            } catch (e: Exception) {
                android.util.Log.e("SerialSocket", "write: Error", e)
                listener.onSerialIoError(e)
            }
        }
    }

    override fun disconnect() {
        android.util.Log.d("SerialSocket", "disconnect")
        synchronized(disconnectLock) {
            isDisconnected = true
            try {
                socket?.close()
            } catch (ignored: Exception) {
            }
            socket = null
        }
    }
}
