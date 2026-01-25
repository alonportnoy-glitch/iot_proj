package com.example.tutorial6_bluetooth.logging

import android.content.Context
import java.io.File
import java.io.FileOutputStream

class TextFileLogger : DataLogger {

    private var logFileOutputStream: FileOutputStream? = null
    private val lineBuffer = StringBuilder()

    override fun start(context: Context, prefix: String, type: String): String {
        val fileName = LogUtils.generateFilename(prefix, type)
        val file = File(context.getExternalFilesDir(null), fileName)
        try {
            logFileOutputStream = FileOutputStream(file, true)
        } catch (e: Exception) {
            e.printStackTrace()
            return ""
        }
        return fileName
    }

    override fun log(data: String) {
        try {
            // Use LogUtils to handle fragmentation (e.g., "12", "3.45", "\n")
            val lines = LogUtils.processBuffer(lineBuffer, data)
            for (line in lines) {
                logFileOutputStream?.write((line + "\n").toByteArray())
            }
            logFileOutputStream?.flush()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun stop() {
        try {
            logFileOutputStream?.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        logFileOutputStream = null
        lineBuffer.clear() // Clear buffer for next session
    }
}