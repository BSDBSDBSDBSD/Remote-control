package com.bsd.remotecontrol.ui

import android.Manifest
import android.content.pm.PackageManager
import android.net.wifi.p2p.WifiP2pDevice
import android.os.*
import android.view.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.bsd.remotecontrol.R
import com.bsd.remotecontrol.bluetooth.RemoteClient
import com.bsd.remotecontrol.wifi.WifiDirectManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Direct Wi-Fi (Wi-Fi Direct) connection to the controlled phone — no router, no internet.
 * The controlled phone runs the server and hosts a Wi-Fi Direct group (becomes the host at
 * 192.168.49.1). Here we discover it, join as a client, then open a TCP socket to the host.
 * A manual "connect by IP" box stays as a fallback for phones already on the same network.
 */
class WifiScanActivity : AppCompatActivity() {

    private lateinit var listView: ListView
    private lateinit var btnScan: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var tvHint: TextView
    private lateinit var tvMyIp: TextView
    private lateinit var etIpAddress: EditText
    private lateinit var btnConnectIp: Button

    private val peers = mutableListOf<WifiP2pDevice>()
    private lateinit var peerAdapter: BaseAdapter
    private lateinit var wifi: WifiDirectManager
    private var connecting = false
    private val PERM_REQ = 300

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_wifi_scan_remote)

        supportActionBar?.title = "WiFi ישיר — שליטה מרחוק"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        listView     = findViewById(R.id.listDevices)
        btnScan      = findViewById(R.id.btnScan)
        progressBar  = findViewById(R.id.progressBar)
        tvHint       = findViewById(R.id.tvHint)
        tvMyIp       = findViewById(R.id.tvMyIp)
        etIpAddress  = findViewById(R.id.etIpAddress)
        btnConnectIp = findViewById(R.id.btnConnectIp)

        tvMyIp.text = "חיבור ישיר בין הטלפונים — בלי ראוטר ובלי אינטרנט"

        peerAdapter = object : BaseAdapter() {
            override fun getCount() = peers.size
            override fun getItem(pos: Int) = peers[pos]
            override fun getItemId(pos: Int) = pos.toLong()
            override fun getView(pos: Int, convertView: View?, parent: ViewGroup): View {
                val v = convertView ?: LayoutInflater.from(this@WifiScanActivity)
                    .inflate(R.layout.item_device, parent, false)
                val d = peers[pos]
                v.findViewById<TextView>(R.id.tvDeviceName).apply {
                    text = d.deviceName?.ifBlank { "מכשיר" } ?: "מכשיר"
                    setTextColor(0xFFFFFFFF.toInt())
                }
                v.findViewById<TextView>(R.id.tvDeviceAddr).apply {
                    text = "${d.deviceAddress} • ${statusText(d.status)}"
                    setTextColor(0xFF9C27B0.toInt())
                }
                return v
            }
        }
        listView.adapter = peerAdapter
        listView.divider = null

        wifi = WifiDirectManager(this)
        wifi.onPeersChanged = { list ->
            runOnUiThread {
                peers.clear(); peers.addAll(list)
                peerAdapter.notifyDataSetChanged()
                if (!connecting) tvHint.text = if (list.isEmpty())
                    "מחפש… במכשיר הנשלט: פתח את האפליקציה ולחץ \"הפעל שרת\" ובחר WiFi ישיר"
                else "${list.size} מכשירים נמצאו — בחר את המכשיר לשליטה:"
            }
        }
        wifi.onConnectionChanged = { connected, info ->
            if (connected && info != null && info.groupFormed) {
                if (!info.isGroupOwner) {
                    val host = info.groupOwnerAddress?.hostAddress
                    if (host != null) connectToServer(host)
                } else {
                    runOnUiThread {
                        connecting = false
                        progressBar.visibility = View.GONE
                        tvHint.text = "המכשיר הזה הפך למארח בטעות. נתק ונסה שוב מהמכשיר הנשלט."
                    }
                }
            }
        }
        wifi.onStateChanged = { enabled ->
            if (!enabled) runOnUiThread { tvHint.text = "ה-WiFi כבוי. הדלק אותו (לא צריך אינטרנט) ולחץ \"חפש מחדש\"." }
        }

        btnScan.setOnClickListener { ensurePermissionThenDiscover() }
        btnConnectIp.setOnClickListener {
            val ip = etIpAddress.text.toString().trim()
            if (ip.isNotEmpty()) connectToServer(ip)
            else Toast.makeText(this, "הכנס כתובת IP", Toast.LENGTH_SHORT).show()
        }
        listView.setOnItemClickListener { _, _, pos, _ ->
            if (pos < peers.size) connectToPeer(peers[pos])
        }
    }

    private fun statusText(s: Int) = when (s) {
        WifiP2pDevice.CONNECTED -> "מחובר"
        WifiP2pDevice.INVITED -> "הוזמן"
        WifiP2pDevice.FAILED -> "נכשל"
        WifiP2pDevice.AVAILABLE -> "זמין"
        WifiP2pDevice.UNAVAILABLE -> "לא זמין"
        else -> ""
    }

    private fun hasWifiPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            ContextCompat.checkSelfPermission(this, Manifest.permission.NEARBY_WIFI_DEVICES) == PackageManager.PERMISSION_GRANTED
        else
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    private fun ensurePermissionThenDiscover() {
        if (hasWifiPermission()) { discover(); return }
        val perm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            Manifest.permission.NEARBY_WIFI_DEVICES else Manifest.permission.ACCESS_FINE_LOCATION
        ActivityCompat.requestPermissions(this, arrayOf(perm), PERM_REQ)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERM_REQ) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) discover()
            else tvHint.text = "צריך הרשאת \"מכשירים בקרבת מקום\" כדי לחפש בחיבור WiFi ישיר."
        }
    }

    private fun discover() {
        progressBar.visibility = View.VISIBLE
        tvHint.text = "מחפש מכשירים בקרבת מקום…"
        wifi.discoverPeers { ok, err ->
            runOnUiThread { if (!ok) { progressBar.visibility = View.GONE; tvHint.text = "החיפוש נכשל: $err" } }
        }
    }

    private fun connectToPeer(device: WifiP2pDevice) {
        connecting = true
        progressBar.visibility = View.VISIBLE
        tvHint.text = "מתחבר אל ${device.deviceName?.ifBlank { device.deviceAddress } ?: device.deviceAddress}… אשר את הבקשה במכשיר השני אם תופיע."
        // groupOwnerIntent = 0: let the controlled phone be the host at 192.168.49.1.
        wifi.connect(device, groupOwnerIntent = 0) { ok, err ->
            if (!ok) runOnUiThread {
                connecting = false
                progressBar.visibility = View.GONE
                tvHint.text = "החיבור נכשל: $err"
            }
        }
    }

    private fun connectToServer(serverIp: String) {
        progressBar.visibility = View.VISIBLE
        tvHint.text = "מתחבר ל-$serverIp…"
        btnConnectIp.isEnabled = false
        lifecycleScope.launch {
            val success = withContext(Dispatchers.IO) {
                try {
                    val socket = Socket()
                    socket.connect(InetSocketAddress(serverIp, WifiDirectManager.SERVER_PORT), 8000)
                    val client = RemoteClient()
                    client.connectWithSocket(socket)
                    RemoteClientHolder.client = client
                    RemoteClientHolder.remoteDeviceName = "WiFi ($serverIp)"
                    true
                } catch (e: Exception) { false }
            }
            if (!isDestroyed) {
                progressBar.visibility = View.GONE
                btnConnectIp.isEnabled = true
                connecting = false
                if (success) {
                    startActivity(android.content.Intent(this@WifiScanActivity, RemoteViewActivity::class.java))
                    finish()
                } else {
                    tvHint.text = "❌ לא ניתן להתחבר לשרת.\nוודא שבמכשיר הנשלט מופעל \"הפעל שרת\" ונבחר WiFi ישיר."
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        wifi.register()
        if (hasWifiPermission()) discover()
        else tvHint.text = "לחץ \"חפש מחדש\" כדי לאשר הרשאה ולחפש מכשירים."
    }

    override fun onPause() {
        super.onPause()
        try { wifi.unregister() } catch (_: Exception) {}
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) finish()
        return super.onOptionsItemSelected(item)
    }
}
