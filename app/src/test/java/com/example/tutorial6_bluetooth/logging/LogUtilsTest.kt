package com.example.tutorial6_bluetooth.logging

import org.junit.Assert.assertTrue
import org.junit.Test

class LogUtilsTest {

    @Test
    fun generateFilename_defaultPrefix_txt() {
        val filename = LogUtils.generateFilename("", "TXT")
        assertTrue("Filename should start with 'log_'", filename.startsWith("log_"))
        assertTrue("Filename should end with '.txt'", filename.endsWith(".txt"))
        // Check timestamp format roughly (yyyyMMdd_HHmmss is 15 chars)
        // log_ + 15 + .txt = 4 + 15 + 4 = 23 chars
        assertTrue("Filename length incorrect", filename.length == 23)
        //assertTrue("Timestamp format incorrect", filename.matches(Regex("log_\\d{8}_\\d{6}\\.txt")))
    }

    @Test
    fun generateFilename_customPrefix_csv() {
        val filename = LogUtils.generateFilename("test", "CSV")
        assertTrue("Filename should start with 'test_'", filename.startsWith("test_"))
        assertTrue("Filename should end with '.csv'", filename.endsWith(".csv"))
        //assertTrue("Timestamp format incorrect", filename.matches(Regex("test_\\d{8}_\\d{6}\\.csv")))
    }

    @Test
    fun generateFilename_trimPrefix() {
        val filename = LogUtils.generateFilename("  trimmed  ", "TXT")
        assertTrue("Filename should start with 'trimmed_'", filename.startsWith("trimmed_"))
        assertTrue("Filename should end with '.txt'", filename.endsWith(".txt"))
    }

    @Test
    fun generateFilename_caseInsensitiveType() {
        val filenameCsv = LogUtils.generateFilename("test", "csv")
        assertTrue("Should be .csv", filenameCsv.endsWith(".csv"))

        val filenameTxt = LogUtils.generateFilename("test", "TxT")
        assertTrue("Should be .txt", filenameTxt.endsWith(".txt"))
    }

    @Test
    fun generateFilename_unknownType_defaultsToTxt() {
        val filename = LogUtils.generateFilename("test", "PDF")
        assertTrue("Should default to .txt for unknown types", filename.endsWith(".txt"))
    }

    @Test
    fun generateFilename_blankPrefix_defaultsToLog() {
        val filename = LogUtils.generateFilename("   ", "TXT")
        assertTrue("Should default to 'log' prefix", filename.startsWith("log_"))
    }
}
