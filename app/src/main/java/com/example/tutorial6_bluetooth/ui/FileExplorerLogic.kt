package com.example.tutorial6_bluetooth.ui

import java.io.File

class FileExplorerLogic {

    private var recordingFilename: String? = null
    private var selectedFile: File? = null
    private var fileList: List<File> = emptyList()

    fun setFiles(files: List<File>) {
        fileList = files
    }

    fun setRecordingFile(filename: String?) {
        recordingFilename = filename
    }

    fun isRecording(file: File): Boolean {
        return file.name == recordingFilename
    }

    fun selectFile(file: File): Boolean {
        if (isRecording(file)) {
            return false
        }
        selectedFile = file
        return true
    }

    fun getSelectedFile(): File? {
        return selectedFile
    }

    fun clearSelection() {
        selectedFile = null
    }

    fun canDelete(file: File): Boolean {
        return !isRecording(file)
    }

    /**
     * Determines the next file to select after the current selection is deleted.
     * It tries to find the closest non-recording file.
     * Priority: Next file -> Previous file -> null
     */
    fun getNextSelectionAfterDelete(deletedFile: File): File? {
        val index = fileList.indexOf(deletedFile)
        if (index == -1) return null

        // Try to find next available
        for (i in index + 1 until fileList.size) {
            val file = fileList[i]
            if (!isRecording(file)) {
                return file
            }
        }

        // Try to find previous available
        for (i in index - 1 downTo 0) {
            val file = fileList[i]
            if (!isRecording(file)) {
                return file
            }
        }

        return null
    }
}
