package com.example.tutorial6_bluetooth.logging

import android.content.Context

interface DataLogger {
    fun start(context: Context, prefix: String = "log", type: String = "TXT"): String
    fun log(data: String)
    fun stop()
}
