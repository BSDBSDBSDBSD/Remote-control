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

// ----- MainActivity -----
class MainActivity : AppCompatActivity() {

    private lateinit var btnServer: Button
    private lateinit var btnClient: Button
    private lateinit var switchRoot: Switch
    private lateinit var tvStatus: TextView
    private lateinit var tvAccessibility: TextView
    private lateinit var statusDot: View
    private lateinit var tvRootStatus: TextView

    private val PROJ_REQ = 200
    private val PERM_REQ = 100
    private var connectionType = "bluetooth" // bluetooth / wifi

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main_remote)

        btnServer       = findViewById(R.id.btnServer)
        btnClient       = findViewById(R.id.btnClient)
        switchRoot      = findViewById(R.id.switchRoot)
        tvStatus        = findViewById(R.id.tvStatus)
        tvAccessibility = findViewById(R.id.tvAccessibility)
        statusDot       = findViewById(R.id.statusDot)
        tvRootStatus    = findViewById(R.id.tvRootStatus)

        requestPermissions()

        btnServer.setOnClickListener {
            if (ScreenShareService.isRunning) stopServer() else showServerOptions()
        }

        btnClient.setOnClickListener {
            startActivity(Intent(this, DeviceScanActivity::class.java))
        }

        // לחיצה על accessibility - פותחת הגדרות
        tvAccessibility.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            Toast.makeText(this, "מצא 'BT Remote Control' והפעל אותו", Toast.LENGTH_LONG).show()
        }
    }

    private fun showServerOptions() {
        val options = arrayOf("Bluetooth (ללא אינטרנט)", "Wi-Fi Direct (מהיר יותר)")
        AlertDialog.Builder(this, R.style.DarkDialog)
            .setTitle("בחר סוג חיבור")
            .setItems(options) { _, which ->
                connectionType = if (which == 0) "bluetooth" else "wifi"
                if (switchRoot.isChecked) {
                    startServer(null, -1)
                } else {
                    requestScreenCapture()
                }
            }
            .show()
    }

    private fun requestScreenCapture() {
        val mpm = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        startActivityForResult(mpm.createScreenCaptureIntent(), PROJ_REQ)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PROJ_REQ) {
            if (resultCode == Activity.RESULT_OK && data != null) {
                startServer(data, resultCode)
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
        startService(Intent(this, ScreenShareService::class.java).apply {
            action = ScreenShareService.ACTION_STOP
        })
        setServerUI(false)
    }

    private fun setServerUI(running: Boolean) {
        btnServer.text = if (running) "עצור שרת" else "הפעל שרת (מצב נשלט)"
        btnServer.backgroundTintList = getColorStateList(
            if (running) android.R.color.holo_red_light else R.color.red_primary
        )
        tvStatus.text = if (running) "שרת פעיל [$connectionType]" else "שרת כבוי"
        statusDot.setBackgroundResource(if (running) R.drawable.dot_green else R.drawable.dot_red)
    }

    private fun updateAccessibilityStatus() {
        val enabled = RemoteAccessibilityService.isEnabled
        tvAccessibility.text = if (enabled)
            "✅ שירות נגישות פעיל"
        else
            "⚠️ שירות נגישות כבוי — לחץ להפעלה"
        tvAccessibility.setTextColor(
            if (enabled) 0xFF1DB954.toInt() else 0xFFFFC107.toInt()
        )
    }

    private fun requestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val perms = arrayOf(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_ADVERTISE
            )
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
    }

    override fun onResume() {
        super.onResume()
        updateAccessibilityStatus()
        setServerUI(ScreenShareService.isRunning)
    }
}

// ----- DeviceScanActivity -----
class DeviceScanActivity : AppCompatActivity() {

    private lateinit var listView: ListView
    private lateinit var progressBar: ProgressBar
    private lateinit var tvHint: TextView
    private val client = RemoteClient()
    private val devices = mutableListOf<BluetoothDevice>()
    private lateinit var listAdapter: RemoteDeviceAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_device_scan_remote)

        supportActionBar?.title = "בחר מכשיר לשליטה"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        listView    = findViewById(R.id.listDevices)
        progressBar = findViewById(R.id.progressBar)
        tvHint      = findViewById(R.id.tvHint)

        listAdapter = RemoteDeviceAdapter(this, devices)
        listView.adapter = listAdapter
        listView.divider = null

        loadPaired()
        listView.setOnItemClickListener { _, _, pos, _ -> connectTo(devices[pos]) }
    }

    private fun loadPaired() {
        val paired = try {
            BluetoothAdapter.getDefaultAdapter()?.bondedDevices ?: emptySet()
        } catch (e: SecurityException) { emptySet() }

        devices.clear(); devices.addAll(paired)
        listAdapter.notifyDataSetChanged()

        tvHint.text = if (devices.isEmpty())
            "לא נמצאו מכשירים מזווגים.\nזווג מכשיר בהגדרות Bluetooth ואז חזור."
        else
            "${devices.size} מכשירים — בחר מכשיר לשליטה מרחוק:"
    }

    private fun connectTo(device: BluetoothDevice) {
        progressBar.visibility = View.VISIBLE
        listView.isEnabled = false
        val name = try { device.name ?: device.address } catch (e: SecurityException) { device.address }
        tvHint.text = "מתחבר אל $name..."

        lifecycleScope.launch {
            val ok = try { client.connect(device) } catch (e: Exception) { false }
            runOnUiThread {
                progressBar.visibility = View.GONE
                listView.isEnabled = true
                if (ok) {
                    RemoteClientHolder.client = client
                    RemoteClientHolder.remoteDeviceName = name
                    startActivity(Intent(this@DeviceScanActivity, RemoteViewActivity::class.java))
                } else {
                    tvHint.text = "❌ חיבור נכשל — וודא שהשרת פעיל במכשיר השני"
                }
            }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) finish()
        return super.onOptionsItemSelected(item)
    }
}

class RemoteDeviceAdapter(
    private val ctx: Context,
    private val devices: List<BluetoothDevice>
) : BaseAdapter() {
    override fun getCount() = devices.size
    override fun getItem(pos: Int) = devices[pos]
    override fun getItemId(pos: Int) = pos.toLong()
    override fun getView(pos: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: LayoutInflater.from(ctx)
            .inflate(R.layout.item_device, parent, false)
        val device = devices[pos]
        view.findViewById<TextView>(R.id.tvDeviceName).apply {
            text = try { device.name ?: "מכשיר לא ידוע" } catch (e: SecurityException) { "מכשיר" }
            setTextColor(0xFFFFFFFF.toInt())
        }
        view.findViewById<TextView>(R.id.tvDeviceAddr).apply {
            text = device.address
            setTextColor(0xFF888888.toInt())
        }
        return view
    }
}

object RemoteClientHolder {
    var client: RemoteClient? = null
    var remoteDeviceName: String = ""
}
