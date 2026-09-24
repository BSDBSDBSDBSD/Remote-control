package com.bsd.remotecontrol.wifi

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.p2p.*
import android.util.Log

class WifiDirectManager(private val context: Context) {

    companion object {
        const val TAG = "WifiDirect"
        const val SERVER_PORT = 8989
        const val GO_IP = "192.168.49.1"
    }

    private val manager: WifiP2pManager by lazy {
        context.getSystemService(Context.WIFI_P2P_SERVICE) as WifiP2pManager
    }
    private val channel: WifiP2pManager.Channel by lazy {
        manager.initialize(context, context.mainLooper, null)
    }

    var peers = listOf<WifiP2pDevice>()
    var connectionInfo: WifiP2pInfo? = null
    var isConnected = false

    var onPeersChanged: ((List<WifiP2pDevice>) -> Unit)? = null
    var onConnectionChanged: ((Boolean, WifiP2pInfo?) -> Unit)? = null
    var onStateChanged: ((Boolean) -> Unit)? = null

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            when (intent.action) {
                WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> {
                    try {
                        manager.requestPeers(channel) { peerList ->
                            peers = peerList.deviceList.toList()
                            onPeersChanged?.invoke(peers)
                        }
                    } catch (e: SecurityException) {
                        Log.w(TAG, "Peers: ${e.message}")
                    }
                }
                WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> {
                    val networkInfo = intent.getParcelableExtra<android.net.NetworkInfo>(
                        WifiP2pManager.EXTRA_NETWORK_INFO
                    )
                    if (networkInfo?.isConnected == true) {
                        manager.requestConnectionInfo(channel) { info ->
                            connectionInfo = info
                            isConnected = true
                            onConnectionChanged?.invoke(true, info)
                        }
                    } else {
                        isConnected = false
                        onConnectionChanged?.invoke(false, null)
                    }
                }
                WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION -> {
                    val state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1)
                    onStateChanged?.invoke(state == WifiP2pManager.WIFI_P2P_STATE_ENABLED)
                }
            }
        }
    }

    fun register() {
        val filter = IntentFilter().apply {
            addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)
        }
        try { context.registerReceiver(receiver, filter) } catch (e: Exception) {}
    }

    fun unregister() {
        try { context.unregisterReceiver(receiver) } catch (e: Exception) {}
    }

    fun discoverPeers(onResult: (Boolean, String) -> Unit) {
        try {
            manager.discoverPeers(channel, object : WifiP2pManager.ActionListener {
                override fun onSuccess() = onResult(true, "")
                override fun onFailure(reason: Int) = onResult(false, "קוד שגיאה $reason")
            })
        } catch (e: SecurityException) {
            onResult(false, "חסרה הרשאה")
        }
    }

    fun connect(device: WifiP2pDevice, onResult: (Boolean) -> Unit) {
        val config = WifiP2pConfig().apply {
            deviceAddress = device.deviceAddress
            wps.setup = android.net.wifi.WpsInfo.PBC
        }
        try {
            manager.connect(channel, config, object : WifiP2pManager.ActionListener {
                override fun onSuccess() = onResult(true)
                override fun onFailure(reason: Int) = onResult(false)
            })
        } catch (e: SecurityException) {
            onResult(false)
        }
    }

    fun disconnect() {
        try {
            manager.removeGroup(channel, object : WifiP2pManager.ActionListener {
                override fun onSuccess() { isConnected = false }
                override fun onFailure(reason: Int) {}
            })
        } catch (e: Exception) {}
    }

    fun getLocalIp(): String {
        return try {
            java.net.NetworkInterface.getNetworkInterfaces().toList()
                .flatMap { it.inetAddresses.toList() }
                .filterIsInstance<java.net.Inet4Address>()
                .firstOrNull { it.hostAddress?.startsWith("192.168.49") == true }
                ?.hostAddress ?: "לא ידוע"
        } catch (e: Exception) { "שגיאה" }
    }
}
