package com.example.tutorial6_bluetooth.ui

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.tutorial6_bluetooth.R
import com.example.tutorial6_bluetooth.constants.Constants
import com.example.tutorial6_bluetooth.databinding.ActivityTerminalBinding
import com.example.tutorial6_bluetooth.logging.LogUtils
import com.example.tutorial6_bluetooth.service.SerialService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.*

class TerminalActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTerminalBinding
    private val messages = ArrayList<Message>()
    private lateinit var adapter: MessageAdapter
    private val buffer = StringBuilder()

    // --- NEW: Variables for Data Recording ---
    private val recordedData = ArrayList<String>() // Stores the CSV lines
    private var startTime: Long = 0                // To calculate relative time
    private var isRecording = false                // Flag to track state
    private var pythonStepDetector: com.chaquo.python.PyObject? = null

    data class Message(val text: String, val isIncoming: Boolean)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTerminalBinding.inflate(layoutInflater)
        setContentView(binding.root)
        //init
        if (!com.chaquo.python.Python.isStarted()) {
            com.chaquo.python.android.AndroidPlatform(this).let { com.chaquo.python.Python.start(it) }
        }
        val py = com.chaquo.python.Python.getInstance()
        val module = py.getModule("step_detector") // the python file that does the step calculations
        pythonStepDetector = module.callAttr("StepDetector")

        setupRecyclerView()
        setupSlowMode()

        // Initial UI State
        updateRecordingUI(false)

        binding.btnReturn.setOnClickListener {
            finish()
        }

        // --- 1. START LISTENER ---
        binding.btnStart.setOnClickListener {
            val prefix = binding.etLogPrefix.text.toString()
            val type = if (binding.rbCsv.isChecked) "CSV" else "TXT"
            val activityType = binding.spinnerActivityType.selectedItem.toString()

            // Reset Data for new session
            recordedData.clear()
            startTime = System.currentTimeMillis()
            isRecording = true

            // Send Intent to Service (if your service handles raw logging too, otherwise we handle it here)
            /*val intent = Intent(this, SerialService::class.java)
            intent.action = Constants.ACTION_START_LOGGING
            intent.putExtra(Constants.EXTRA_LOG_FILENAME_PREFIX, prefix)
            intent.putExtra(Constants.EXTRA_LOG_TYPE, type)
            intent.putExtra("EXTRA_ACTIVITY_TYPE", activityType)
            startService(intent)

             */
            sendMessage("START")

            updateRecordingUI(true)
        }

        // --- 2. STOP LISTENER ---
        binding.btnStop.setOnClickListener {
            // Stop logic
            isRecording = false

            val intent = Intent(this, SerialService::class.java)
            intent.action = Constants.ACTION_STOP_LOGGING
            startService(intent)

            sendMessage("STOP")

            updateRecordingUI(false)

            // Lab Requirement: Ask for "Actual Steps" immediately after stop
            showStepCountDialog()
        }

        binding.btnSend.setOnClickListener {
            val text = binding.etInput.text.toString()
            if (text.isNotEmpty()) {
                sendMessage(text)
                binding.etInput.text.clear()
            }
        }

    }

    // --- 3. STEP COUNT DIALOG ---
    private fun showStepCountDialog() {
        val inputSteps = EditText(this)
        inputSteps.inputType = android.text.InputType.TYPE_CLASS_NUMBER
        inputSteps.hint = "e.g. 150"

        AlertDialog.Builder(this)
            .setTitle("Experiment Finished")
            .setMessage("Enter the ACTUAL number of steps taken:")
            .setView(inputSteps)
            .setCancelable(false)
            .setPositiveButton("Save File") { _, _ ->
                val steps = inputSteps.text.toString()
                if (steps.isNotEmpty()) {
                    saveToCsv(steps)
                } else {
                    Toast.makeText(this, "Steps empty. File NOT saved.", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Discard Data") { dialog, _ ->
                dialog.dismiss()
                Toast.makeText(this, "Data discarded", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    // --- 4. CSV SAVING LOGIC (With Header) ---
    /*private fun saveToCsv(actualSteps: String) {
        val prefix = binding.etLogPrefix.text.toString().trim()
        val fileExtension = if (binding.rbCsv.isChecked) "csv" else "txt"
        val filename = if (prefix.isEmpty()) "log.$fileExtension" else "$prefix.$fileExtension"
        //val filename = if (prefix.isEmpty()) "log.csv" else "${prefix}.csv"
        val activityType = binding.spinnerActivityType.selectedItem.toString()
        val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        val timestamp = dateFormat.format(Date())

        // Build content exactly as per Lab Image
        val csvContent = StringBuilder()

        // Row 1: Name
        csvContent.append("NAME,$filename,,,,,\n")
        // Row 2: Time
        csvContent.append("EXPERIMENT TIME,$timestamp,,,,,\n")
        // Row 3: Activity
        csvContent.append("ACTIVITY TYPE,$activityType,,,,,\n")
        // Row 4: Steps
        csvContent.append("COUNT OF ACTUAL STEPS,$actualSteps,,,,,\n")
        // Row 5: Spacer
        csvContent.append(",,,,,,\n")
        // Row 6: Headers
        csvContent.append("Time [sec],ACC X,ACC Y,ACC Z,GYRO X,GYRO Y,GYRO Z\n")

        // Row 7+: Data
        for (line in recordedData) {
            csvContent.append(line).append("\n")
        }

        try {
            // Save to Internal Storage
            val out = openFileOutput(filename, Context.MODE_PRIVATE)
            val writer = OutputStreamWriter(out)
            writer.write(csvContent.toString())
            writer.close()
            Toast.makeText(this, "Saved $filename", Toast.LENGTH_LONG).show()
            recordedData.clear()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Error saving: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }*/
    private fun saveToCsv(actualSteps: String) {
        val prefix = binding.etLogPrefix.text.toString().trim()
        // Force the extension based on the radio button choice
        val extension = if (binding.rbCsv.isChecked) "csv" else "txt"
        val timestamp = SimpleDateFormat("ddMMyyyy_HH:mm", Locale.getDefault()).format(Date())

        val filename = "${prefix}_${timestamp}.$extension"
        val activityType = binding.spinnerActivityType.selectedItem.toString()

        val headerTimestamp = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date())

        val content = StringBuilder()
        // 1. Write the Header
        // Row 1: Name
        content.append("NAME,$filename,,,,,\n")
        // Row 2: Time
        content.append("EXPERIMENT TIME,$headerTimestamp,,,,,\n")
        // Row 3: Activity
        content.append("ACTIVITY TYPE,$activityType,,,,,\n")
        // Row 4: Steps
        content.append("COUNT OF ACTUAL STEPS,$actualSteps,,,,,\n")
        // Row 5: Spacer
        content.append(",,,,,,\n")
        // Row 6: Headers
        content.append("Time [sec],ACC X,ACC Y,ACC Z,GYRO X,GYRO Y,GYRO Z\n")

        // 2. Write the Data
        for (line in recordedData) {
            content.append(line).append("\n")
        }

        try {
            // Use getExternalFilesDir so you can see the file on your PC
            val file = java.io.File(getExternalFilesDir(null), filename)
            file.writeText(content.toString())

            Toast.makeText(this, "Saved: ${file.absolutePath}", Toast.LENGTH_LONG).show()
            recordedData.clear() // Reset for next run
        } catch (e: Exception) {
            android.util.Log.e("TerminalActivity", "Error saving file", e)
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateRecordingUI(recording: Boolean, filename: String? = null) {
        if (recording) {
            binding.btnStart.isEnabled = false
            binding.btnStop.isEnabled = true
            binding.btnStop.backgroundTintList =
                ContextCompat.getColorStateList(this, android.R.color.holo_red_dark)

            // Lock inputs
            binding.etLogPrefix.isEnabled = false
            binding.spinnerActivityType.isEnabled = false
            binding.rbCsv.isEnabled = false
            binding.rbTxt.isEnabled = false

            binding.tvFilename.text = "Recording..."
        } else {
            binding.btnStart.isEnabled = true
            binding.btnStop.isEnabled = false
            binding.btnStart.backgroundTintList =
                ContextCompat.getColorStateList(this, android.R.color.holo_green_dark)

            // Unlock inputs
            binding.etLogPrefix.isEnabled = true
            binding.spinnerActivityType.isEnabled = true
            binding.rbCsv.isEnabled = true
            binding.rbTxt.isEnabled = true

            binding.tvFilename.text = "Not Recording"
        }
    }

    // --- SERVICE & DATA HANDLING ---

    private var serialService: SerialService? = null
    private var isBound = false

    private val serviceConnection = object : android.content.ServiceConnection {
        override fun onServiceConnected(
            className: android.content.ComponentName,
            service: android.os.IBinder
        ) {
            val binder = service as SerialService.SerialBinder
            serialService = binder.getService()
            isBound = true

            if (serialService?.isConnected == true) {
                binding.btnStart.isEnabled = !isRecording
                binding.btnStop.isEnabled = isRecording
                binding.etInput.isEnabled = true
                binding.btnSend.isEnabled = true
            } else {
                binding.btnStart.isEnabled = false
                binding.btnStop.isEnabled = false
                binding.etInput.isEnabled = false
                binding.btnSend.isEnabled = false
            }
        }

        override fun onServiceDisconnected(arg0: android.content.ComponentName) {
            isBound = false
            serialService = null
        }
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
        val filter = IntentFilter()
        filter.addAction(Constants.ACTION_SERIAL_DATA_RECEIVED)
        filter.addAction(Constants.ACTION_SERIAL_STATE_CHANGED)
        ContextCompat.registerReceiver(this, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(receiver)
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Constants.ACTION_SERIAL_DATA_RECEIVED -> {
                    val data = intent.getByteArrayExtra(Constants.EXTRA_DATA)
                    if (data != null) {
                        val text = String(data)
                        // Use LogUtils to handle fragmentation (ensures we get full lines)
                        val lines = LogUtils.processBuffer(buffer, text)
                        //val lines = com.example.tutorial6_bluetooth.logging.LogUtils.processBuffer(buffer, text)

                        for (line in lines) {
                            // 1. Display on Screen
                            addMessage(line, true)

                            // 2. Record Data (if recording is active)
                            if (isRecording) {
                                /*val timeSec = (System.currentTimeMillis() - startTime) / 1000.0
                                // Format: "0.001,-1.41,-1.49,..."
                                val csvLine = String.format(Locale.US, "%.3f,%s", timeSec, line)
                                recordedData.add(csvLine)
                                 */
                                val timeSec = (System.currentTimeMillis() - startTime) / 1000.0
                                recordedData.add(String.format(Locale.US, "%.3f,%s", timeSec, line))
                            }
                            try {
                                //  getting ( ACC_X,ACC_Y,ACC_Z,...)
                                val parts = line.split(",")
                                if (parts.size >= 3) {
                                    val x = parts[1].toFloat()
                                    val y = parts[2].toFloat()
                                    val z = parts[3].toFloat()

                                    val currentTime = System.currentTimeMillis()

                                    val magnitude = kotlin.math.sqrt(x*x + y*y + z*z)

                                    // 2. Log it to Logcat (View this in the "Logcat" tab at the bottom of Android Studio)
                                    // Tag: "StepDebug", Message: "Mag: 12.34"
                                    android.util.Log.d("StepDebug", "Magnitude: $magnitude")

                                    // 3. (Optional) Toast - ONLY use for testing single values, NOT continuous data
                                    // Toast.makeText(context, "Mag: $magnitude", Toast.LENGTH_SHORT).show()


                                    val result = pythonStepDetector?.callAttr("process_realtime", x, y, z, currentTime)

                                    if (result != null) {
                                        val resultList = result.asList()
                                        val steps = resultList[0].toInt()
                                        val isNewStep = resultList[1].toBoolean()

                                        binding.tvStepCount.text = "Steps: $steps"
                                    }
                                }
                            } catch (e: Exception) {
                                android.util.Log.e("StepDetector", "Error parsing data: ${e.message}")
                            }
                        }
                    }
                }

                Constants.ACTION_SERIAL_STATE_CHANGED -> {
                    val isConnected =
                        intent.getBooleanExtra(Constants.EXTRA_SERVICE_CONNECTED, false)

                    if (isConnected) {
                        binding.etInput.isEnabled = true
                        binding.btnSend.isEnabled = true
                        // Only enable Start if not currently recording
                        binding.btnStart.isEnabled = !isRecording
                        binding.btnStop.isEnabled = isRecording
                    } else {
                        binding.etInput.isEnabled = false
                        binding.btnSend.isEnabled = false
                        binding.btnStart.isEnabled = false
                        binding.btnStop.isEnabled = false
                    }
                }
            }
        }
    }

    // --- UI HELPERS (RecyclerView & Adapter) ---

    private fun setupRecyclerView() {
        adapter = MessageAdapter(messages)
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter
    }

    // Slow mode logic (unchanged)
    private var isSlowMode = false
    private val pendingMessages = ArrayList<Message>()
    private val updateHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val updateRunnable = object : Runnable {
        override fun run() {
            if (pendingMessages.isNotEmpty()) {
                val count = pendingMessages.size
                val start = messages.size
                messages.addAll(pendingMessages)
                pendingMessages.clear()
                adapter.notifyItemRangeInserted(start, count)
                binding.recyclerView.scrollToPosition(messages.size - 1)
            }
            if (isSlowMode) {
                updateHandler.postDelayed(this, 1000)
            }
        }
    }

    private fun setupSlowMode() {
        binding.swSlowMode.setOnCheckedChangeListener { _, isChecked ->
            isSlowMode = isChecked
            if (isChecked) {
                updateHandler.post(updateRunnable)
            } else {
                updateHandler.removeCallbacks(updateRunnable)
                updateRunnable.run()
            }
        }
    }

    private fun addMessage(text: String, isIncoming: Boolean) {
        if (isSlowMode) {
            pendingMessages.add(Message(text, isIncoming))
        } else {
            lifecycleScope.launch(Dispatchers.Main) {
                messages.add(Message(text, isIncoming))
                adapter.notifyItemInserted(messages.size - 1)
                binding.recyclerView.scrollToPosition(messages.size - 1)
            }
        }
    }

    private fun sendMessage(text: String) {
        lifecycleScope.launch(Dispatchers.Main) {
            messages.add(Message(text, false))
            adapter.notifyItemInserted(messages.size - 1)
            binding.recyclerView.scrollToPosition(messages.size - 1)
        }
        val intent = Intent(this, SerialService::class.java)
        intent.action = Constants.ACTION_WRITE_DATA
        intent.putExtra(Constants.EXTRA_DATA, text.toByteArray())
        startService(intent)
    }

    inner class MessageAdapter(private val messages: List<Message>) :
        RecyclerView.Adapter<MessageAdapter.ViewHolder>() {

        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val textView: TextView = itemView.findViewById(android.R.id.text1)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_terminal_message, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val message = messages[position]
            holder.textView.text = message.text
            holder.textView.setTextColor(if (message.isIncoming) Color.GREEN else Color.BLUE)
        }

        override fun getItemCount(): Int = messages.size
    }
}
