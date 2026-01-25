package com.example.tutorial6_bluetooth.service

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.launch

class SerialConnectionManager(private val scope: CoroutineScope) {

    private var currentSocket: ISerialSocket? = null
    private var connectionJob: Job? = null

    fun connect(socket: ISerialSocket) {
        // 1. Disconnect existing socket to unblock any pending operations
        currentSocket?.disconnect()
        
        // 2. Cancel previous job
        // Note: We use a specific job for connection to avoid cancelling other service tasks if any
        connectionJob?.cancel()
        
        // 3. Update current socket
        currentSocket = socket
        
        // 4. Start new connection
        connectionJob = scope.launch {
            currentSocket?.connectAndRun()
        }
    }

    fun disconnect() {
        currentSocket?.disconnect()
        connectionJob?.cancel()
        currentSocket = null
        connectionJob = null
    }

    fun write(data: ByteArray) {
        scope.launch {
            currentSocket?.write(data)
        }
    }
}
