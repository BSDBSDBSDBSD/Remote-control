package com.bsd.remotecontrol.bluetooth

import android.bluetooth.*
import android.util.Log
import com.bsd.remotecontrol.model.AppInfo
import com.bsd.remotecontrol.model.RemoteCommand
import com.bsd.remotecontrol.model.RemoteResponse
import com.bsd.remotecontrol.screen.ScreenShareService
import kotlinx.coroutines.*
import java.io.*

class RemoteClient {

    companion object { const val TAG = "RemoteClient" }

    private var socket: BluetoothSocket? = null
    private var input: DataInputStream? = null
    private var output: DataOutputStream? = null
    var isConnected = false
        private set
    var remoteScreenWidth = 1080
    var remoteScreenHeight = 1920

    suspend fun connect(device: BluetoothDevice): Boolean = withContext(Dispatchers.IO) {
        try {
            disconnect()
            val s = device.createRfcommSocketToServiceRecord(ScreenShareService.BT_UUID)
            BluetoothAdapter.getDefaultAdapter().cancelDiscovery()
            s.connect()
            socket = s
            input  = DataInputStream(BufferedInputStream(s.inputStream))
            output = DataOutputStream(BufferedOutputStream(s.outputStream))
            isConnected = true

            // קבלת מידע מסך
            val info = sendCommand(RemoteCommand("SCREEN_INFO"))
            if (info != null) {
                remoteScreenWidth  = info.screenWidth
                remoteScreenHeight = info.screenHeight
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Connect failed: ${e.message}")
            false
        }
    }

    fun disconnect() {
        try { input?.close(); output?.close(); socket?.close() } catch (_: Exception) {}
        isConnected = false
    }

    // -------- CONTROL --------
    suspend fun tap(x: Int, y: Int, useRoot: Boolean = false) = withContext(Dispatchers.IO) {
        sendCommand(RemoteCommand("TOUCH", x = x, y = y, useRoot = useRoot))
    }

    suspend fun swipe(x1: Int, y1: Int, x2: Int, y2: Int, useRoot: Boolean = false) = withContext(Dispatchers.IO) {
        sendCommand(RemoteCommand("SWIPE", x = x1, y = y1, x2 = x2, y2 = y2, useRoot = useRoot))
    }

    suspend fun back(useRoot: Boolean = false)    = withContext(Dispatchers.IO) { sendCommand(RemoteCommand("BACK", useRoot = useRoot)) }
    suspend fun home(useRoot: Boolean = false)    = withContext(Dispatchers.IO) { sendCommand(RemoteCommand("HOME", useRoot = useRoot)) }
    suspend fun recents(useRoot: Boolean = false) = withContext(Dispatchers.IO) { sendCommand(RemoteCommand("RECENTS", useRoot = useRoot)) }
    suspend fun volumeUp(useRoot: Boolean = false)   = withContext(Dispatchers.IO) { sendCommand(RemoteCommand("VOLUME_UP", useRoot = useRoot)) }
    suspend fun volumeDown(useRoot: Boolean = false) = withContext(Dispatchers.IO) { sendCommand(RemoteCommand("VOLUME_DOWN", useRoot = useRoot)) }

    suspend fun getAppList(): List<AppInfo> = withContext(Dispatchers.IO) {
        sendCommand(RemoteCommand("APP_LIST"))?.apps ?: emptyList()
    }

    suspend fun launchApp(packageName: String): Boolean = withContext(Dispatchers.IO) {
        sendCommand(RemoteCommand("APP_LAUNCH", packageName = packageName))?.success ?: false
    }

    suspend fun stopApp(packageName: String, useRoot: Boolean = false): Boolean = withContext(Dispatchers.IO) {
        sendCommand(RemoteCommand("APP_STOP", packageName = packageName, useRoot = useRoot))?.success ?: false
    }

    suspend fun runShell(cmd: String, useRoot: Boolean = false): String = withContext(Dispatchers.IO) {
        sendCommand(RemoteCommand("SHELL", shellCmd = cmd, useRoot = useRoot))?.shellOutput ?: ""
    }

    // -------- SCREENSHOT --------
    suspend fun getScreenshot(): ByteArray? = withContext(Dispatchers.IO) {
        try {
            sendRaw(RemoteCommand("SCREENSHOT").toJson())
            receiveFrame()
        } catch (e: Exception) {
            Log.e(TAG, "screenshot error: ${e.message}")
            null
        }
    }

    // -------- STREAM --------
    fun startStream(onFrame: (ByteArray) -> Unit): Job {
        return CoroutineScope(Dispatchers.IO).launch {
            try {
                sendRaw(RemoteCommand("STREAM_START").toJson())
                receiveJson() // ACK

                while (isActive && isConnected) {
                    val frame = receiveFrame() ?: break
                    onFrame(frame)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Stream error: ${e.message}")
            }
        }
    }

    suspend fun stopStream() = withContext(Dispatchers.IO) {
        sendCommand(RemoteCommand("STREAM_STOP"))
    }

    // -------- INTERNAL --------
    private fun sendCommand(cmd: RemoteCommand): RemoteResponse? {
        sendRaw(cmd.toJson())
        return receiveJson()
    }

    private fun sendRaw(json: String) {
        val out = output ?: throw IOException("Not connected")
        val bytes = json.toByteArray()
        out.writeInt(bytes.size)
        out.write(bytes)
        out.flush()
    }

    private fun receiveJson(): RemoteResponse? {
        return try {
            val inp = input ?: return null
            val len = inp.readInt()
            val bytes = ByteArray(len)
            inp.readFully(bytes)
            RemoteResponse.fromJson(String(bytes))
        } catch (e: Exception) {
            Log.e(TAG, "receiveJson: ${e.message}")
            null
        }
    }

    private fun receiveFrame(): ByteArray? {
        return try {
            val inp = input ?: return null
            val size = inp.readInt()
            if (size <= 0) return null
            val bytes = ByteArray(size)
            inp.readFully(bytes)
            bytes
        } catch (e: Exception) {
            Log.e(TAG, "receiveFrame: ${e.message}")
            null
        }
    }
}
