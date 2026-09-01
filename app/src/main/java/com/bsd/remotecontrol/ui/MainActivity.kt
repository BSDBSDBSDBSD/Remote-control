package com.bsd.remotecontrol.ui

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.*
import android.provider.Settings
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.bsd.remotecontrol.R
import com.bsd.remotecontrol.bluetooth.RemoteClient
import com.bsd.remotecontrol.input.RemoteAccessibilityService
import com.bsd.remotecontrol.screen.ScreenShareService
import kotlinx.coroutines.launch

// ----- MainActivity -----
class MainActivity : AppCompatActivity() {

    private lateinit var btnServer: Button
    private lateinit var btnClient: Button
    private lateinit var switchRoot: Switch
    private lateinit var tvStatus: TextView
    private lateinit var tvAccessibility: TextView

    private val PROJ_REQ = 200
    private val PERM_REQ = 100

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main_remote)

        btnServer       = findViewById(R.id.btnServer)
        btnClient       = findViewById(R.id.btnClient)
        switchRoot      = findViewById(R.id.switchRoot)
        tvStatus        = findViewById(R.id.tvStatus)
        tvAccessibility = findViewById(R.id.tvAccessibility)

        requestPermissions()

        btnServer.setOnClickListener {
            if (ScreenShareService.isRunning) {
                stopServer()
            } else {
                startServerFlow()
            }
        }

        btnClient.setOnClickListener {
            startActivity(Intent(this, DeviceScanActivity::class.java))
        }
    }

    private fun startServerFlow() {
        if (!switchRoot.isChecked) {
            // בלי root - צריך MediaProjection לצילום מסך
            val mpm = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            startActivityForResult(mpm.createScreenCaptureIntent(), PROJ_REQ)
        } else {
            // עם root - לא צריך MediaProjection
            startServer(null, -1)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PROJ_REQ && resultCode == Activity.RESULT_OK && data != null) {
            startServer(data, resultCode)
        }
    }

    private fun startServer(projData: Intent?, resultCode: Int) {
        val intent = Intent(this, ScreenShareService::class.java).apply {
            action = ScreenShareService.ACTION_START
            putExtra("use_root", switchRoot.isChecked)
            if (projData != null) {
                putExtra(ScreenShareService.EXTRA_RESULT_CODE, resultCode)
                putExtra(ScreenShareService.EXTRA_RESULT_DATA, projData)
            }
        }
        startForegroundService(intent)
        btnServer.text = "עצור שרת"
        tvStatus.text = "שרת פעיל - ממתין לחיבורים..."
    }

    private fun stopServer() {
        startService(Intent(this, ScreenShareService::class.java).apply {
            action = ScreenShareService.ACTION_STOP
        })
        btnServer.text = "הפעל שרת"
        tvStatus.text = "שרת כבוי"
    }

    override fun onResume() {
        super.onResume()
        val accessOn = RemoteAccessibilityService.isEnabled
        tvAccessibility.text = if (accessOn) "✅ Accessibility פעיל" else "⚠️ Accessibility כבוי - לחץ להפעלה"
        tvAccessibility.setOnClickListener {
            if (!accessOn) startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        btnServer.text = if (ScreenShareService.isRunning) "עצור שרת" else "הפעל שרת"
    }

    private fun requestPermissions() {
        val perms = arrayOf(
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_ADVERTISE,
            Manifest.permission.POST_NOTIFICATIONS
        )
        val denied = perms.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (denied.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, denied.toTypedArray(), PERM_REQ)
        }
    }
}

// ----- DeviceScanActivity -----
class DeviceScanActivity : AppCompatActivity() {

    private lateinit var listView: ListView
    private lateinit var progressBar: ProgressBar
    private lateinit var tvHint: TextView
    private val client = RemoteClient()
    private val devices = mutableListOf<BluetoothDevice>()
    private lateinit var listAdapter: ArrayAdapter<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_device_scan_remote)

        listView    = findViewById(R.id.listDevices)
        progressBar = findViewById(R.id.progressBar)
        tvHint      = findViewById(R.id.tvHint)

        listAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, mutableListOf())
        listView.adapter = listAdapter

        loadPaired()
        listView.setOnItemClickListener { _, _, pos, _ -> connectTo(devices[pos]) }
    }

    private fun loadPaired() {
        val paired = BluetoothAdapter.getDefaultAdapter()?.bondedDevices ?: emptySet()
        devices.clear(); devices.addAll(paired)
        listAdapter.clear()
        if (devices.isEmpty()) {
            tvHint.text = "אין מכשירים מזווגים"
        } else {
            tvHint.text = "בחר מכשיר לשליטה מרחוק:"
            devices.forEach { listAdapter.add("${it.name ?: "Unknown"}\n${it.address}") }
        }
    }

    private fun connectTo(device: BluetoothDevice) {
        progressBar.visibility = android.view.View.VISIBLE
        tvHint.text = "מתחבר..."
        listView.isEnabled = false

        lifecycleScope.launch {
            val ok = client.connect(device)
            runOnUiThread {
                progressBar.visibility = android.view.View.GONE
                listView.isEnabled = true
                if (ok) {
                    RemoteClientHolder.client = client
                    RemoteClientHolder.remoteDeviceName = device.name ?: device.address
                    startActivity(Intent(this@DeviceScanActivity, RemoteViewActivity::class.java))
                } else {
                    tvHint.text = "חיבור נכשל - ודא שהשרת פעיל במכשיר השני"
                    Toast.makeText(this@DeviceScanActivity, "חיבור נכשל", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}

object RemoteClientHolder {
    var client: RemoteClient? = null
    var remoteDeviceName: String = ""
}
