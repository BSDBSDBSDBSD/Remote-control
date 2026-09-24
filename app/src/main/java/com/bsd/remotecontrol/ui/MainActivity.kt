package com.bsd.remotecontrol.ui

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.*
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.*
import android.provider.Settings
import android.view.*
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

class MainActivity : AppCompatActivity() {

    private lateinit var btnServer: Button
    private lateinit var btnClient: Button
    private lateinit var btnClientWifi: Button
    private lateinit var btnSettings: ImageButton
    private lateinit var btnAccessibilityOpen: Button
    private lateinit var switchRoot: Switch
    private lateinit var tvStatus: TextView
    private lateinit var tvAccessibility: TextView
    private lateinit var statusDot: View
    private lateinit var tvRootStatus: TextView
    private lateinit var layoutAccessibility: android.widget.LinearLayout
    private lateinit var dividerAccessibility: View

    private val PROJ_REQ = 200
    private val PERM_REQ = 100
    private var connectionType = "bluetooth"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        com.bsd.remotecontrol.util.CrashLog.install(this)
        setContentView(R.layout.activity_main_remote)

        com.bsd.remotecontrol.util.CrashLog.read(this)?.let { showLastCrash(it) }

        btnServer            = findViewById(R.id.btnServer)
        btnClient            = findViewById(R.id.btnClient)
        btnClientWifi        = findViewById(R.id.btnClientWifi)
        btnSettings          = findViewById(R.id.btnSettings)
        btnAccessibilityOpen = findViewById(R.id.btnAccessibilityOpen)
        switchRoot           = findViewById(R.id.switchRoot)
        tvStatus             = findViewById(R.id.tvStatus)
        tvAccessibility      = findViewById(R.id.tvAccessibility)
        statusDot            = findViewById(R.id.statusDot)
        tvRootStatus         = findViewById(R.id.tvRootStatus)
        layoutAccessibility  = findViewById(R.id.layoutAccessibility)
        dividerAccessibility = findViewById(R.id.dividerAccessibility)

        requestPermissions()

        btnServer.setOnClickListener {
            try {
                if (ScreenShareService.isRunning) stopServer() else startServerFlow()
            } catch (e: Exception) {
                Toast.makeText(this, "שגיאה: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }

        btnClient.setOnClickListener {
            startActivity(Intent(this, DeviceScanActivity::class.java))
        }

        btnClientWifi.setOnClickListener {
            startActivity(Intent(this, WifiScanActivity::class.java))
        }

        btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        findViewById<Button>(R.id.btnEnableBt).setOnClickListener { enableBluetoothDiscoverable() }
        findViewById<Button>(R.id.btnEnableWifi).setOnClickListener { openWifiEnable() }

        // When root is toggled, hide/show accessibility section
        switchRoot.setOnCheckedChangeListener { _, _ ->
            updateAccessibilityStatus()
        }

        btnAccessibilityOpen.setOnClickListener {
            try {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                Toast.makeText(this, "מצא \"שליטה מרחוק\" ברשימה והפעל אותו", Toast.LENGTH_LONG).show()
            } catch (e: Exception) { }
        }

        tvAccessibility.setOnClickListener {
            btnAccessibilityOpen.performClick()
        }
    }

    private fun showLastCrash(text: String) {
        com.bsd.remotecontrol.util.CrashLog.clear(this)
        try {
            AlertDialog.Builder(this)
                .setTitle("האפליקציה נסגרה בגלל שגיאה")
                .setMessage(text.take(4000))
                .setPositiveButton("העתק") { _, _ ->
                    val cm = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    cm.setPrimaryClip(android.content.ClipData.newPlainText("crash", text))
                    Toast.makeText(this, "הועתק", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("סגור", null)
                .show()
        } catch (_: Exception) {}
    }

    /**
     * Starts the server. It always listens on BOTH Bluetooth and WiFi at once (and hosts a
     * direct Wi-Fi group), so the user does NOT choose the transport here — the transport is
     * chosen on the controlling phone when picking how to connect. With root, no screen
     * capture is needed; otherwise we ask for screen-capture consent first.
     */
    /** Turns Bluetooth on (if off) and asks to make the device discoverable for 5 minutes. */
    private fun enableBluetoothDiscoverable() {
        try {
            val adapter = BluetoothAdapter.getDefaultAdapter()
            if (adapter == null) {
                Toast.makeText(this, "אין Bluetooth במכשיר", Toast.LENGTH_SHORT).show(); return
            }
            val i = Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE)
                .putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300)
            startActivity(i) // this dialog also turns Bluetooth on if it is off
        } catch (e: Exception) {
            Toast.makeText(this, "צריך לאשר הרשאות Bluetooth", Toast.LENGTH_SHORT).show()
        }
    }

    /** Opens the WiFi enable panel (Android does not allow toggling WiFi silently). */
    private fun openWifiEnable() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startActivity(Intent(Settings.Panel.ACTION_WIFI))
            } else {
                @Suppress("DEPRECATION")
                startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
            }
        } catch (e: Exception) {
            try { startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS)) } catch (_: Exception) {}
        }
    }

    private fun startServerFlow() {
        connectionType = "both"
        if (switchRoot.isChecked) startServer(null, -1) else requestScreenCapture()
    }

    private fun requestScreenCapture() {
        try {
            val mpm = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            @Suppress("DEPRECATION")
            startActivityForResult(mpm.createScreenCaptureIntent(), PROJ_REQ)
        } catch (e: Exception) {
            Toast.makeText(this, "שגיאה בבקשת צילום מסך", Toast.LENGTH_SHORT).show()
        }
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PROJ_REQ) {
            if (resultCode == Activity.RESULT_OK && data != null) {
                try { startServer(data, resultCode) } catch (e: Exception) {
                    Toast.makeText(this, "שגיאה בהפעלת השרת: ${e.message}", Toast.LENGTH_LONG).show()
                }
            } else {
                Toast.makeText(this, "נדרש אישור צילום מסך", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun startServer(projData: Intent?, resultCode: Int) {
        val intent = Intent(this, ScreenShareService::class.java).apply {
            action = ScreenShareService.ACTION_START
            putExtra("use_root", switchRoot.isChecked)
            putExtra("connection_type", connectionType)
            if (projData != null) {
                putExtra(ScreenShareService.EXTRA_RESULT_CODE, resultCode)
                putExtra(ScreenShareService.EXTRA_RESULT_DATA, projData)
            }
        }
        try {
            startForegroundService(intent)
            setServerUI(true)
        } catch (e: Exception) {
            Toast.makeText(this, "שגיאה: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun stopServer() {
        try {
            startService(Intent(this, ScreenShareService::class.java).apply {
                action = ScreenShareService.ACTION_STOP
            })
        } catch (e: Exception) {}
        setServerUI(false)
    }

    private fun setServerUI(running: Boolean) {
        btnServer.text = if (running) "■  עצור שרת" else "▶  הפעל שרת  (מכשיר נשלט)"
        btnServer.backgroundTintList = getColorStateList(
            if (running) android.R.color.holo_green_dark else R.color.red_primary
        )
        tvStatus.text = if (running) "⬤ שרת פעיל · Bluetooth ו-WiFi" else "שרת כבוי"
        statusDot.setBackgroundResource(if (running) R.drawable.dot_green else R.drawable.dot_red)
    }

    private fun updateAccessibilityStatus() {
        // With root enabled, input injection goes through shell — no Accessibility needed
        if (switchRoot.isChecked) {
            layoutAccessibility.visibility = View.GONE
            dividerAccessibility.visibility = View.GONE
            return
        }
        layoutAccessibility.visibility = View.VISIBLE
        dividerAccessibility.visibility = View.VISIBLE
        val enabled = RemoteAccessibilityService.isEnabled
        tvAccessibility.text = if (enabled) "✅ שירות נגישות פעיל" else "⚠️ שירות נגישות כבוי"
        tvAccessibility.setTextColor(if (enabled) 0xFF22C55E.toInt() else 0xFFFFC107.toInt())
        btnAccessibilityOpen.visibility = if (enabled) View.GONE else View.VISIBLE
    }

    private fun requestPermissions() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val perms = mutableListOf(
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_ADVERTISE
                )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    perms.add(Manifest.permission.NEARBY_WIFI_DEVICES)
                }
                val denied = perms.filter {
                    ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
                }
                if (denied.isNotEmpty()) {
                    ActivityCompat.requestPermissions(this, denied.toTypedArray(), PERM_REQ)
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(
                        this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), PERM_REQ)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    override fun onResume() {
        super.onResume()
        updateAccessibilityStatus()
        setServerUI(ScreenShareService.isRunning)
    }
}

// ----- DeviceScanActivity (BT) -----
class DeviceScanActivity : AppCompatActivity() {

    private lateinit var listView: ListView
    private lateinit var btnScan: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var tvHint: TextView
    private val client = RemoteClient()
    private val pairedDevices = mutableListOf<BluetoothDevice>()
    private val scannedDevices = mutableListOf<BluetoothDevice>()
    private val allDevices = mutableListOf<BluetoothDevice>()
    private lateinit var listAdapter: RemoteDeviceAdapter
    private var isScanning = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            when (intent.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    @Suppress("DEPRECATION")
                    val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                    device?.let {
                        if (allDevices.none { d -> d.address == it.address }) {
                            scannedDevices.add(it)
                            allDevices.add(it)
                            listAdapter.notifyDataSetChanged()
                        }
                    }
                }
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    isScanning = false
                    progressBar.visibility = View.GONE
                    btnScan.text = "חפש מכשירים חדשים"
                    btnScan.isEnabled = true
                    tvHint.text = if (allDevices.isEmpty())
                        "לא נמצאו מכשירים. זווג מכשיר ב-Bluetooth ונסה שוב."
                    else
                        "${pairedDevices.size} מזווגים | ${scannedDevices.size} חדשים"
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_device_scan_remote)

        supportActionBar?.title = "בחר מכשיר לשליטה"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        listView    = findViewById(R.id.listDevices)
        btnScan     = findViewById(R.id.btnScan)
        progressBar = findViewById(R.id.progressBar)
        tvHint      = findViewById(R.id.tvHint)

        listAdapter = RemoteDeviceAdapter(this, allDevices, pairedDevices)
        listView.adapter = listAdapter
        listView.divider = null

        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        }
        try { registerReceiver(receiver, filter) } catch (e: Exception) {}

        loadPaired()
        listView.setOnItemClickListener { _, _, pos, _ ->
            if (pos < allDevices.size) connectTo(allDevices[pos])
        }
        btnScan.setOnClickListener { startDiscovery() }
    }

    private fun loadPaired() {
        val paired = try {
            BluetoothAdapter.getDefaultAdapter()?.bondedDevices ?: emptySet()
        } catch (e: SecurityException) { emptySet() }

        pairedDevices.clear(); pairedDevices.addAll(paired)
        allDevices.clear(); allDevices.addAll(paired)
        listAdapter.notifyDataSetChanged()
        tvHint.text = if (paired.isEmpty())
            "לא נמצאו מזווגים. לחץ 'חפש' לסריקה."
        else "${paired.size} מכשירים מזווגים — בחר:"
    }

    private fun startDiscovery() {
        if (isScanning) return
        val bt = BluetoothAdapter.getDefaultAdapter() ?: return
        try {
            if (bt.isDiscovering) bt.cancelDiscovery()
            bt.startDiscovery()
            isScanning = true
            progressBar.visibility = View.VISIBLE
            btnScan.text = "מחפש..."
            btnScan.isEnabled = false
            tvHint.text = "מחפש מכשירים חדשים..."
        } catch (e: SecurityException) {
            Toast.makeText(this, "נדרשת הרשאת Bluetooth", Toast.LENGTH_SHORT).show()
        }
    }

    private fun connectTo(device: BluetoothDevice) {
        try { BluetoothAdapter.getDefaultAdapter()?.cancelDiscovery() } catch (_: Exception) {}
        progressBar.visibility = View.VISIBLE
        listView.isEnabled = false
        val name = try { device.name ?: device.address } catch (e: SecurityException) { device.address }
        tvHint.text = "מתחבר אל $name..."

        lifecycleScope.launch {
            val ok = try { client.connect(device) } catch (e: Exception) { false }
            if (!isDestroyed) {
                runOnUiThread {
                    progressBar.visibility = View.GONE
                    listView.isEnabled = true
                    if (ok) {
                        RemoteClientHolder.client = client
                        RemoteClientHolder.remoteDeviceName = name
                        startActivity(Intent(this@DeviceScanActivity, RemoteViewActivity::class.java))
                    } else {
                        tvHint.text = "❌ חיבור נכשל — וודא שהשרת פעיל"
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        try { unregisterReceiver(receiver) } catch (_: Exception) {}
        try { BluetoothAdapter.getDefaultAdapter()?.cancelDiscovery() } catch (_: Exception) {}
        super.onDestroy()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) finish()
        return super.onOptionsItemSelected(item)
    }
}

class RemoteDeviceAdapter(
    private val ctx: Context,
    private val devices: List<BluetoothDevice>,
    private val pairedDevices: List<BluetoothDevice>
) : BaseAdapter() {
    override fun getCount() = devices.size
    override fun getItem(pos: Int) = devices[pos]
    override fun getItemId(pos: Int) = pos.toLong()
    override fun getView(pos: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: LayoutInflater.from(ctx)
            .inflate(R.layout.item_device, parent, false)
        val device = devices[pos]
        val isPaired = pairedDevices.any { it.address == device.address }
        view.findViewById<TextView>(R.id.tvDeviceName).apply {
            text = try { device.name ?: "מכשיר" } catch (e: SecurityException) { "מכשיר" }
            setTextColor(0xFFFFFFFF.toInt())
        }
        view.findViewById<TextView>(R.id.tvDeviceAddr).apply {
            text = "${device.address} • ${if (isPaired) "מזווג ✓" else "חדש"}"
            setTextColor(if (isPaired) 0xFF22C55E.toInt() else 0xFF888888.toInt())
        }
        return view
    }
}

object RemoteClientHolder {
    var client: RemoteClient? = null
    var remoteDeviceName: String = ""
}
