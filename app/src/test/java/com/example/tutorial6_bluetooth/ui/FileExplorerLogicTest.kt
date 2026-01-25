package com.example.tutorial6_bluetooth.ui

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File

class FileExplorerLogicTest {

    private lateinit var logic: FileExplorerLogic
    private val normalFile = File("path/to/normal.txt")
    private val recordingFile = File("path/to/recording.txt")

    @Before
    fun setUp() {
        logic = FileExplorerLogic()
        logic.setRecordingFile(recordingFile.name)
    }

    @Test
    fun testSelectNormalFile() {
        val result = logic.selectFile(normalFile)
        assertTrue("Should be able to select normal file", result)
        assertEquals("Selected file should be normalFile", normalFile, logic.getSelectedFile())
    }

    @Test
    fun testSelectRecordingFile() {
        val result = logic.selectFile(recordingFile)
        assertFalse("Should NOT be able to select recording file", result)
        assertNull("Selected file should be null", logic.getSelectedFile())
    }

    @Test
    fun testDeleteNormalFile() {
        assertTrue("Should be able to delete normal file", logic.canDelete(normalFile))
    }

    @Test
    fun testDeleteRecordingFile() {
        assertFalse("Should NOT be able to delete recording file", logic.canDelete(recordingFile))
    }
    
    @Test
    fun testRecordingStatus() {
        assertTrue(logic.isRecording(recordingFile))
        assertFalse(logic.isRecording(normalFile))
    }

    @Test
    fun testNextSelection_MiddleFile() {
        val f1 = File("f1")
        val f2 = File("f2")
        val f3 = File("f3")
        logic.setFiles(listOf(f1, f2, f3))
        
        assertEquals(f3, logic.getNextSelectionAfterDelete(f2))
    }

    @Test
    fun testNextSelection_LastFile() {
        val f1 = File("f1")
        val f2 = File("f2")
        val f3 = File("f3")
        logic.setFiles(listOf(f1, f2, f3))
        
        assertEquals(f2, logic.getNextSelectionAfterDelete(f3))
    }

    @Test
    fun testNextSelection_SkipRecording() {
        val f1 = File("f1")
        val f2 = File("f2")
        val rec = recordingFile // f3 is recording
        val f4 = File("f4")
        
        logic.setFiles(listOf(f1, f2, rec, f4))
        logic.setRecordingFile(rec.name)
        
        // Delete f2, next is rec (skip), next is f4
        assertEquals(f4, logic.getNextSelectionAfterDelete(f2))
    }

    @Test
    fun testNextSelection_OnlyRecordingLeft() {
        val f1 = File("f1")
        val rec = recordingFile
        
        logic.setFiles(listOf(f1, rec))
        logic.setRecordingFile(rec.name)
        
        // Delete f1, only rec left (skip) -> null
        assertNull(logic.getNextSelectionAfterDelete(f1))
    }
}
