package com.example.tutorial6_bluetooth.service

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SerialConnectionManagerTest {

    private val testScope = TestScope()
    private val manager = SerialConnectionManager(testScope)

    class MockSerialSocket(val name: String) : ISerialSocket {
        var isConnected = false
        var isDisconnected = false
        var writtenData: ByteArray? = null

        override suspend fun connectAndRun() {
            isConnected = true
            // Simulate running connection
            kotlinx.coroutines.delay(1000) 
        }

        override fun disconnect() {
            isDisconnected = true
            isConnected = false
        }

        override suspend fun write(data: ByteArray) {
            writtenData = data
        }
    }

    @Test
    fun testConnect_DisconnectsPrevious() = testScope.runTest {
        val socket1 = MockSerialSocket("Socket1")
        val socket2 = MockSerialSocket("Socket2")

        manager.connect(socket1)
        advanceUntilIdle()
        
        assertTrue(socket1.isConnected)
        assertFalse(socket1.isDisconnected)

        // Connect second socket
        manager.connect(socket2)
        advanceUntilIdle()

        // Verify socket1 is disconnected
        assertTrue(socket1.isDisconnected)
        
        // Verify socket2 is connected
        assertTrue(socket2.isConnected)
    }

    @Test
    fun testDisconnect() = testScope.runTest {
        val socket = MockSerialSocket("Socket")
        manager.connect(socket)
        advanceUntilIdle()

        assertTrue(socket.isConnected)

        manager.disconnect()
        assertTrue(socket.isDisconnected)
    }
}
