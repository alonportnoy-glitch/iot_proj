package com.example.tutorial6_bluetooth.ui

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.graphics.Color
import android.os.Bundle
import android.os.IBinder
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.tutorial6_bluetooth.R
import com.example.tutorial6_bluetooth.constants.Constants
import com.example.tutorial6_bluetooth.service.SerialService
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class FileExplorerActivity : AppCompatActivity() {

    private lateinit var rvFiles: RecyclerView
    private lateinit var btnView: Button
    private lateinit var btnDelete: Button
    private lateinit var btnReturn: Button
    
    private val logic = FileExplorerLogic()
    private val files = ArrayList<File>()
    private lateinit var adapter: FileAdapter
    
    private var serialService: SerialService? = null
    private var isBound = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_file_explorer)

        rvFiles = findViewById(R.id.rvFiles)
        btnView = findViewById(R.id.btnView)
        btnDelete = findViewById(R.id.btnDelete)
        btnReturn = findViewById(R.id.btnReturn)

        setupRecyclerView()
        loadFiles()

        btnReturn.setOnClickListener {
            finish()
        }

        btnDelete.setOnClickListener {
            val file = logic.getSelectedFile()
            if (file != null) {
                if (logic.canDelete(file)) {
                    val nextSelection = logic.getNextSelectionAfterDelete(file)
                    if (file.delete()) {
                        Toast.makeText(this, "File deleted", Toast.LENGTH_SHORT).show()
                        
                        // Update selection
                        if (nextSelection != null) {
                            logic.selectFile(nextSelection)
                        } else {
                            logic.clearSelection()
                        }
                        
                        loadFiles()
                        updateButtons()
                    } else {
                        Toast.makeText(this, "Failed to delete file", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(this, "Cannot delete recording file", Toast.LENGTH_SHORT).show()
                }
            }
        }

        btnView.setOnClickListener {
            val file = logic.getSelectedFile()
            if (file != null) {
                showFileContent(file)
            }
        }
    }

    private fun setupRecyclerView() {
        adapter = FileAdapter(files) { file ->
            if (logic.selectFile(file)) {
                adapter.notifyDataSetChanged()
                updateButtons()
            } else {
                Toast.makeText(this, "Cannot select file currently recording", Toast.LENGTH_SHORT).show()
            }
        }
        rvFiles.layoutManager = LinearLayoutManager(this)
        rvFiles.adapter = adapter
    }

    private fun loadFiles() {
        files.clear()
        val dir = getExternalFilesDir(null)
        if (dir != null && dir.exists()) {
            val fileList = dir.listFiles()
            if (fileList != null) {
                // Filter out directories if any, and maybe sort
                files.addAll(fileList.filter { it.isFile }.sortedByDescending { it.lastModified() })
            }
        }
        logic.setFiles(files) // Update logic with new list
        adapter.notifyDataSetChanged()
    }

    private fun updateButtons() {
        val selected = logic.getSelectedFile() != null
        btnView.isEnabled = selected
        btnDelete.isEnabled = selected
    }

    private fun showFileContent(file: File) {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_file_viewer)
        dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)

        val rvContent = dialog.findViewById<RecyclerView>(R.id.rvFileContent)
        val btnClose = dialog.findViewById<Button>(R.id.btnClose)
        val tvTitle = dialog.findViewById<TextView>(R.id.tvDialogTitle)

        tvTitle.text = file.name
        
        // Read file content
        val lines = try {
            file.readLines()
        } catch (e: Exception) {
            listOf("Error reading file: ${e.message}")
        }

        rvContent.layoutManager = LinearLayoutManager(this)
        rvContent.adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
                val view = LayoutInflater.from(parent.context).inflate(R.layout.item_log_line, parent, false)
                return object : RecyclerView.ViewHolder(view) {}
            }

            override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
                val tv = holder.itemView.findViewById<TextView>(android.R.id.text1)
                tv.text = lines[position]
            }

            override fun getItemCount() = lines.size
        }

        btnClose.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    // Service Binding
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            val binder = service as SerialService.SerialBinder
            serialService = binder.getService()
            isBound = true
            updateRecordingStatus()
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            isBound = false
            serialService = null
        }
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Constants.ACTION_SERIAL_STATE_CHANGED) {
                updateRecordingStatus()
            }
        }
    }

    private fun updateRecordingStatus() {
        val filename = serialService?.getLogFilename()
        logic.setRecordingFile(filename)
        adapter.notifyDataSetChanged()
    }

    override fun onStart() {
        super.onStart()
        val intent = Intent(this, SerialService::class.java)
        bindService(intent, serviceConnection, BIND_AUTO_CREATE)
    }

    override fun onStop() {
        super.onStop()
        if (isBound) {
            unbindService(serviceConnection)
            isBound = false
        }
    }

    override fun onResume() {
        super.onResume()
        ContextCompat.registerReceiver(
            this,
            receiver,
            IntentFilter(Constants.ACTION_SERIAL_STATE_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        if (isBound) {
            updateRecordingStatus()
        }
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(receiver)
    }

    // Adapter
    inner class FileAdapter(
        private val files: List<File>,
        private val onFileClick: (File) -> Unit
    ) : RecyclerView.Adapter<FileAdapter.ViewHolder>() {

        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val tvFilename: TextView = itemView.findViewById(R.id.tvFilename)
            val tvFileInfo: TextView = itemView.findViewById(R.id.tvFileInfo)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_file, parent, false)
            return ViewHolder(view)
        }

        @SuppressLint("SetTextI18n")
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val file = files[position]
            holder.tvFilename.text = file.name
            
            val size = file.length() / 1024
            val date = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(file.lastModified()))
            holder.tvFileInfo.text = "${size}KB • $date"

            val isRecording = logic.isRecording(file)
            val isSelected = logic.getSelectedFile() == file

            if (isRecording) {
                holder.itemView.setBackgroundColor(Color.parseColor("#F5F5F5")) // Lighter Gray
                holder.itemView.isEnabled = false
                holder.tvFilename.setTextColor(Color.GRAY)
            } else if (isSelected) {
                holder.itemView.setBackgroundColor(Color.parseColor("#E3F2FD")) // Light Blue
                holder.itemView.isEnabled = true
                holder.tvFilename.setTextColor(Color.BLACK)
            } else {
                holder.itemView.setBackgroundColor(Color.TRANSPARENT)
                holder.itemView.isEnabled = true
                holder.tvFilename.setTextColor(Color.BLACK)
            }

            holder.itemView.setOnClickListener {
                onFileClick(file)
            }
        }

        override fun getItemCount() = files.size
    }
}
