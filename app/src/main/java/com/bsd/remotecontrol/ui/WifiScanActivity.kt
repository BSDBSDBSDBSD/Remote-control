package com.bsd.remotecontrol.ui

import android.content.Context
import android.net.wifi.WifiManager
import android.os.*
import android.view.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.bsd.remotecontrol.R
import com.bsd.remotecontrol.bluetooth.RemoteClient
import com.bsd.remotecontrol.wifi.WifiDirectManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteOrder

class WifiScanActivity : AppCompatActivity() {

    private lateinit var listView: ListView
    private lateinit var btnScan: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var tvHint: TextView
    private lateinit var tvMyIp: TextView
    private lateinit var etIpAddress: EditText
    private lateinit var btnConnectIp: Button

    private val foundServers = mutableListOf<String>()
    private lateinit var serverAdapter: ArrayAdapter<String>
    private var isScanning = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_wifi_scan_remote)

        supportActionBar?.title = "WiFi — שליטה מרחוק"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        listView     = findViewById(R.id.listDevices)
        btnScan      = findViewById(R.id.btnScan)
        progressBar  = findViewById(R.id.progressBar)
        tvHint       = findViewById(R.id.tvHint)
        tvMyIp       = findViewById(R.id.tvMyIp)
        etIpAddress  = findViewById(R.id.etIpAddress)
        btnConnectIp = findViewById(R.id.btnConnectIp)

        serverAdapter = object : ArrayAdapter<String>(this, 0, foundServers) {
            override fun getView(pos: Int, convertView: View?, parent: ViewGroup): View {
                val v = convertView ?: LayoutInflater.from(context)
                    .inflate(R.layout.item_device, parent, false)
                val ip = getItem(pos) ?: ""
                v.findViewById<TextView>(R.id.tvDeviceName).apply {
                    text = "שרת שליטה"
                    setTextColor(0xFFFFFFFF.toInt())
                }
                v.findViewById<TextView>(R.id.tvDeviceAddr).apply {
                    text = "$ip:${WifiDirectManager.SERVER_PORT}"
                    setTextColor(0xFF9C27B0.toInt())
                }
                return v
            }
        }
        listView.adapter = serverAdapter
        listView.divider = null

        val myIp = getLocalIp()
        tvMyIp.text = "IP שלי: $myIp"

        btnScan.setOnClickListener { startLanScan() }
        btnConnectIp.setOnClickListener {
            val ip = etIpAddress.text.toString().trim()
            if (ip.isNotEmpty()) {
                connectToServer(ip)
            } else {
                Toast.makeText(this, "הכנס כתובת IP", Toast.LENGTH_SHORT).show()
            }
        }

        listView.setOnItemClickListener { _, _, pos, _ ->
            if (pos < foundServers.size) connectToServer(foundServers[pos])
        }

        startLanScan()
    }

    private fun getLocalIp(): String {
        return try {
            val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val ip = wm.connectionInfo.ipAddress
            if (ip == 0) return "לא מחובר"
            val ipBytes = if (ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN)
                byteArrayOf(
                    (ip and 0xFF).toByte(),
                    (ip shr 8 and 0xFF).toByte(),
                    (ip shr 16 and 0xFF).toByte(),
                    (ip shr 24 and 0xFF).toByte()
                )
            else
                byteArrayOf(
                    (ip shr 24 and 0xFF).toByte(),
                    (ip shr 16 and 0xFF).toByte(),
                    (ip shr 8 and 0xFF).toByte(),
                    (ip and 0xFF).toByte()
                )
            "${ipBytes[0].toInt() and 0xFF}.${ipBytes[1].toInt() and 0xFF}.${ipBytes[2].toInt() and 0xFF}.${ipBytes[3].toInt() and 0xFF}"
        } catch (e: Exception) { "?" }
    }

    private fun getSubnetPrefix(): String {
        val ip = getLocalIp()
        if (ip == "?" || ip == "לא מחובר") return "192.168.1"
        val parts = ip.split(".")
        return if (parts.size == 4) "${parts[0]}.${parts[1]}.${parts[2]}" else "192.168.1"
    }

    private fun startLanScan() {
        if (isScanning) return
        isScanning = true
        foundServers.clear()
        serverAdapter.notifyDataSetChanged()
        progressBar.visibility = View.VISIBLE
        btnScan.isEnabled = false
        tvHint.text = "סורק רשת מקומית..."

        val subnet = getSubnetPrefix()
        val port = WifiDirectManager.SERVER_PORT

        lifecycleScope.launch {
            val jobs = (1..254).map { i ->
                async(Dispatchers.IO) {
                    val ip = "$subnet.$i"
                    try {
                        val socket = Socket()
                        socket.connect(InetSocketAddress(ip, port), 150)
                        socket.close()
                        ip
                    } catch (e: Exception) { null }
                }
            }
            val results = jobs.awaitAll().filterNotNull()
            withContext(Dispatchers.Main) {
                isScanning = false
                progressBar.visibility = View.GONE
                btnScan.isEnabled = true
                foundServers.clear()
                foundServers.addAll(results)
                serverAdapter.notifyDataSetChanged()
                tvHint.text = when {
                    results.isEmpty() -> "לא נמצאו שרתים — הפעל שרת WiFi במכשיר הנשלט"
                    else -> "${results.size} שרת/ים נמצאו — בחר:"
                }
            }
        }
    }

    private fun connectToServer(serverIp: String) {
        progressBar.visibility = View.VISIBLE
        tvHint.text = "מתחבר ל-$serverIp..."
        btnConnectIp.isEnabled = false

        lifecycleScope.launch {
            val success = withContext(Dispatchers.IO) {
                try {
                    val socket = Socket()
                    socket.connect(InetSocketAddress(serverIp, WifiDirectManager.SERVER_PORT), 5000)
                    val client = RemoteClient()
                    client.connectWithSocket(socket)
                    RemoteClientHolder.client = client
                    RemoteClientHolder.remoteDeviceName = "WiFi ($serverIp)"
                    true
                } catch (e: Exception) {
                    false
                }
            }
            if (!isDestroyed) {
                progressBar.visibility = View.GONE
                btnConnectIp.isEnabled = true
                if (success) {
                    startActivity(android.content.Intent(this@WifiScanActivity, RemoteViewActivity::class.java))
                    finish()
                } else {
                    tvHint.text = "❌ לא ניתן להתחבר\nוודא שהשרת פועל ושניכם על אותה WiFi"
                }
            }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) finish()
        return super.onOptionsItemSelected(item)
    }
}
