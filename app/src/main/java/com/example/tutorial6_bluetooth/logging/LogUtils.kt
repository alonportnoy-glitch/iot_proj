package com.example.tutorial6_bluetooth.logging

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object LogUtils {
    fun generateFilename(prefix: String, type: String): String {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val cleanPrefix = if (prefix.isBlank()) "log" else prefix.trim()
        //val extension = if (type.equals("CSV", ignoreCase = true)) "csv" else "txt"
        val extension = type.lowercase(Locale.US)
        return "${cleanPrefix}_${timestamp}.$extension"
    }

    /**
     * Appends new data to the buffer and extracts complete lines.
     * @param buffer The StringBuilder buffer to maintain state.
     * @param newData The new raw string data received.
     * @return A list of complete lines extracted from the buffer.
     */
    fun processBuffer(buffer: StringBuilder, newData: String): List<String> {
        val lines = mutableListOf<String>()
        buffer.append(newData)

        while (buffer.contains("\n")) {
            val newlineIndex = buffer.indexOf("\n")
            val line = buffer.substring(0, newlineIndex).trim()
            buffer.delete(0, newlineIndex + 1)
            if (line.isNotEmpty()) {
                lines.add(line)
            }
        }
        return lines
    }
}
