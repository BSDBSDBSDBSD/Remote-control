package com.bsd.remotecontrol.ui

import android.net.wifi.p2p.WifiP2pDevice
import android.os.*
import android.view.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.bsd.remotecontrol.R
import com.bsd.remotecontrol.bluetooth.RemoteClient
import com.bsd.remotecontrol.wifi.WifiDirectManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket

class WifiScanActivity : AppCompatActivity() {

    private lateinit var listView: ListView
    private lateinit var btnScan: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var tvHint: TextView
    private lateinit var tvMyIp: TextView

    private val wifiManager by lazy { WifiDirectManager(this) }
    private val peers = mutableListOf<WifiP2pDevice>()
    private lateinit var peerAdapter: RcWifiPeerAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_wifi_scan_remote)

        supportActionBar?.title = "WiFi Direct — בחר מכשיר"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        listView    = findViewById(R.id.listDevices)
        btnScan     = findViewById(R.id.btnScan)
        progressBar = findViewById(R.id.progressBar)
        tvHint      = findViewById(R.id.tvHint)
        tvMyIp      = findViewById(R.id.tvMyIp)

        peerAdapter = RcWifiPeerAdapter(this, peers)
        listView.adapter = peerAdapter
        listView.divider = null

        wifiManager.register()

        wifiManager.onPeersChanged = { newPeers ->
            runOnUiThread {
                progressBar.visibility = View.GONE
                peers.clear()
                peers.addAll(newPeers)
                peerAdapter.notifyDataSetChanged()
                tvHint.text = if (newPeers.isEmpty())
                    "לא נמצאו מכשירים — ודא ש-WiFi מופעל ושהצד השני בחיפוש"
                else
                    "${newPeers.size} מכשירים — בחר:"
            }
        }

        wifiManager.onConnectionChanged = { connected, info ->
            runOnUiThread {
                if (connected && info != null) {
                    val serverIp = if (info.isGroupOwner) "192.168.49.1"
                    else info.groupOwnerAddress?.hostAddress ?: "192.168.49.1"
                    tvHint.text = "✅ מחובר! מתחבר לשרת $serverIp..."
                    connectToWifiServer(serverIp)
                }
            }
        }

        tvMyIp.text = "IP שלי: ..."
        Handler(Looper.getMainLooper()).postDelayed({
            if (!isDestroyed) tvMyIp.text = "IP שלי: ${wifiManager.getLocalIp()}"
        }, 500)

        btnScan.setOnClickListener { startDiscovery() }
        startDiscovery()

        listView.setOnItemClickListener { _, _, pos, _ ->
            if (pos < peers.size) wifiManager.connect(peers[pos]) { ok ->
                if (!ok) runOnUiThread { tvHint.text = "חיבור נכשל — נסה שוב" }
            }
        }
    }

    private fun startDiscovery() {
        progressBar.visibility = View.VISIBLE
        tvHint.text = "מחפש מכשירים..."
        wifiManager.discoverPeers { success, error ->
            runOnUiThread {
                if (!success) {
                    progressBar.visibility = View.GONE
                    tvHint.text = "שגיאה: $error\nוודא ש-WiFi מופעל"
                }
            }
        }
    }

    private fun connectToWifiServer(serverIp: String) {
        progressBar.visibility = View.VISIBLE
        lifecycleScope.launch {
            val success = withContext(Dispatchers.IO) {
                try {
                    val socket = Socket()
                    socket.connect(InetSocketAddress(serverIp, WifiDirectManager.SERVER_PORT), 5000)
                    val client = RemoteClient()
                    client.connectWithSocket(socket)
                    RemoteClientHolder.client = client
                    RemoteClientHolder.remoteDeviceName = "WiFi Direct ($serverIp)"
                    true
                } catch (e: Exception) {
                    false
                }
            }
            if (!isDestroyed) {
                progressBar.visibility = View.GONE
                if (success) {
                    startActivity(android.content.Intent(this@WifiScanActivity, RemoteViewActivity::class.java))
                    finish()
                } else {
                    tvHint.text = "❌ לא ניתן להתחבר לשרת\nוודא שהשרת פועל במצב WiFi במכשיר השני"
                }
            }
        }
    }

    override fun onDestroy() {
        wifiManager.unregister()
        super.onDestroy()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) finish()
        return super.onOptionsItemSelected(item)
    }
}

class RcWifiPeerAdapter(
    private val ctx: android.content.Context,
    private val peers: List<WifiP2pDevice>
) : BaseAdapter() {
    override fun getCount() = peers.size
    override fun getItem(pos: Int) = peers[pos]
    override fun getItemId(pos: Int) = pos.toLong()
    override fun getView(pos: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: LayoutInflater.from(ctx)
            .inflate(R.layout.item_device, parent, false)
        val device = peers[pos]
        view.findViewById<TextView>(R.id.tvDeviceName).apply {
            text = device.deviceName.ifBlank { "WiFi Device" }
            setTextColor(0xFFFFFFFF.toInt())
        }
        view.findViewById<TextView>(R.id.tvDeviceAddr).apply {
            text = "WiFi Direct • ${device.deviceAddress}"
            setTextColor(0xFF9C27B0.toInt())
        }
        return view
    }
}
