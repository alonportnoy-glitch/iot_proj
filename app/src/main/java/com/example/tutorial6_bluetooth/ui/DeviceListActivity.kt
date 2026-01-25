package com.example.tutorial6_bluetooth.ui

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.example.tutorial6_bluetooth.R
import com.example.tutorial6_bluetooth.constants.Constants
import com.example.tutorial6_bluetooth.databinding.ActivityDeviceListBinding
import com.example.tutorial6_bluetooth.service.SerialService

class DeviceListActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDeviceListBinding
    private var bluetoothAdapter: BluetoothAdapter? = null
    private val deviceList = ArrayList<BluetoothDevice>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDeviceListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val manager = getSystemService(android.bluetooth.BluetoothManager::class.java)
        bluetoothAdapter = manager.adapter

        binding.btnReturn.setOnClickListener {
            finish()
        }

        if (isEmulator()) {
            binding.tvEmulatorMessage.visibility = View.VISIBLE
            // Don't finish if emulator, just show message and empty list (or whatever is available)
        } else if (bluetoothAdapter == null) {
            finish()
            return
        }

        loadPairedDevices()

        val adapter = object : ArrayAdapter<BluetoothDevice>(this, R.layout.device_list_item, deviceList) {
            @SuppressLint("MissingPermission")
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = convertView ?: layoutInflater.inflate(R.layout.device_list_item, parent, false)
                val device = getItem(position)
                val name = view.findViewById<TextView>(R.id.device_name)
                val address = view.findViewById<TextView>(R.id.device_address)

                name.text = device?.name ?: "Unknown"
                address.text = device?.address
                return view
            }
        }

        binding.listDevices.adapter = adapter
        binding.listDevices.setOnItemClickListener { _, _, position, _ ->
            val device = deviceList[position]
            connectToDevice(device)
        }
    }

    @SuppressLint("MissingPermission")
    private fun loadPairedDevices() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return
        }

        val pairedDevices = bluetoothAdapter?.bondedDevices
        if (pairedDevices != null) {
            deviceList.addAll(pairedDevices)
        }
    }

    private fun connectToDevice(device: BluetoothDevice) {
        val intent = Intent(this, SerialService::class.java)
        intent.action = Constants.ACTION_SERIAL_CONNECT
        intent.putExtra(Constants.EXTRA_DEVICE_ADDRESS, device.address)
        startService(intent)
        // Don't finish here, wait for connection
    }

    private val receiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context?, intent: Intent?) {
            if (intent?.action == Constants.ACTION_SERIAL_STATE_CHANGED) {
                val isConnected = intent.getBooleanExtra(Constants.EXTRA_SERVICE_CONNECTED, false)
                if (isConnected) {
                    val terminalIntent = Intent(this@DeviceListActivity, TerminalActivity::class.java)
                    startActivity(terminalIntent)
                    finish()
                } else {
                    // Only show toast if it was an error
                    val isError = intent.getBooleanExtra(Constants.EXTRA_ERROR, false)
                    if (isError) {
                        android.widget.Toast.makeText(this@DeviceListActivity, "Connection failed", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        androidx.core.content.ContextCompat.registerReceiver(
            this,
            receiver,
            android.content.IntentFilter(Constants.ACTION_SERIAL_STATE_CHANGED),
            androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(receiver)
    }

    private fun isEmulator(): Boolean {
        return (Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic"))
                || Build.FINGERPRINT.startsWith("generic")
                || Build.FINGERPRINT.startsWith("unknown")
                || Build.HARDWARE.contains("goldfish")
                || Build.HARDWARE.contains("ranchu")
                || Build.MODEL.contains("google_sdk")
                || Build.MODEL.contains("Emulator")
                || Build.MODEL.contains("Android SDK built for x86")
                || Build.MANUFACTURER.contains("Genymotion")
                || Build.PRODUCT.contains("sdk_google")
                || Build.PRODUCT.contains("google_sdk")
                || Build.PRODUCT.contains("sdk")
                || Build.PRODUCT.contains("sdk_x86")
                || Build.PRODUCT.contains("vbox86p")
                || Build.PRODUCT.contains("emulator")
                || Build.PRODUCT.contains("simulator")
    }
}
