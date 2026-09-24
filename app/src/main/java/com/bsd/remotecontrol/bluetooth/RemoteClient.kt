package com.bsd.remotecontrol.bluetooth

import android.bluetooth.*
import android.util.Log
import com.bsd.remotecontrol.model.AppInfo
import com.bsd.remotecontrol.model.RemoteCommand
import com.bsd.remotecontrol.model.RemoteResponse
import com.bsd.remotecontrol.screen.ScreenShareService
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.*
import java.net.Socket as TcpSocket

/**
 * Talks to the server over a single socket (Bluetooth RFCOMM or Wi-Fi TCP).
 *
 * IMPORTANT: the socket carries a strict request->response protocol. Every exchange
 * (a command and its reply, or a screenshot request and its frame) must happen one at
 * a time — otherwise two readers on the same stream desync and one of them reads a
 * bogus length from the middle of the other's payload, which previously caused a
 * multi-GB allocation and an OutOfMemoryError crash. The [io] mutex serialises all
 * exchanges, and the screen is refreshed by polling [screenshot] instead of a
 * concurrent push-stream.
 */
class RemoteClient {

    companion object {
        const val TAG = "RemoteClient"
        private const val MAX_MSG = 32 * 1024 * 1024 // hard cap so a desync can never OOM
    }

    private var btSocket: BluetoothSocket? = null
    private var tcpSocket: TcpSocket? = null
    private var input: DataInputStream? = null
    private var output: DataOutputStream? = null
    private val io = Mutex()

    var isConnected = false
        private set
    var remoteScreenWidth = 1080
    var remoteScreenHeight = 1920
    /** Reported by the controlled device: whether it can actually receive control. */
    var serverIsRoot = false
        private set
    var serverAccessibilityOn = false
        private set
    val canControl: Boolean get() = serverIsRoot || serverAccessibilityOn

    // ---- connection ----
    suspend fun connectWithSocket(socket: TcpSocket): Boolean = withContext(Dispatchers.IO) {
        try {
            disconnect()
            tcpSocket = socket
            input  = DataInputStream(BufferedInputStream(socket.getInputStream()))
            output = DataOutputStream(BufferedOutputStream(socket.getOutputStream()))
            isConnected = true
            fetchScreenInfo()
            true
        } catch (e: Exception) {
            Log.e(TAG, "TCP connect failed: ${e.message}"); false
        }
    }

    suspend fun connect(device: BluetoothDevice): Boolean = withContext(Dispatchers.IO) {
        try {
            disconnect()
            val s = device.createRfcommSocketToServiceRecord(ScreenShareService.BT_UUID)
            BluetoothAdapter.getDefaultAdapter()?.cancelDiscovery()
            s.connect()
            btSocket = s
            input  = DataInputStream(BufferedInputStream(s.inputStream))
            output = DataOutputStream(BufferedOutputStream(s.outputStream))
            isConnected = true
            fetchScreenInfo()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Connect failed: ${e.message}"); false
        }
    }

    private suspend fun fetchScreenInfo() {
        val info = command(RemoteCommand("SCREEN_INFO"))
        if (info != null) {
            remoteScreenWidth  = info.screenWidth.takeIf { it > 0 } ?: remoteScreenWidth
            remoteScreenHeight = info.screenHeight.takeIf { it > 0 } ?: remoteScreenHeight
            serverIsRoot = info.isRoot
            serverAccessibilityOn = info.accessibilityOn
        }
    }

    fun disconnect() {
        isConnected = false
        try { input?.close() } catch (_: Exception) {}
        try { output?.close() } catch (_: Exception) {}
        try { btSocket?.close() } catch (_: Exception) {}
        try { tcpSocket?.close() } catch (_: Exception) {}
        btSocket = null; tcpSocket = null
    }

    // ---- control (each call is one serialised exchange) ----
    suspend fun tap(x: Int, y: Int, useRoot: Boolean = false) =
        command(RemoteCommand("TOUCH", x = x, y = y, useRoot = useRoot))

    suspend fun swipe(x1: Int, y1: Int, x2: Int, y2: Int, useRoot: Boolean = false) =
        command(RemoteCommand("SWIPE", x = x1, y = y1, x2 = x2, y2 = y2, useRoot = useRoot))

    suspend fun back(useRoot: Boolean = false)    = command(RemoteCommand("BACK", useRoot = useRoot))
    suspend fun home(useRoot: Boolean = false)    = command(RemoteCommand("HOME", useRoot = useRoot))
    suspend fun recents(useRoot: Boolean = false) = command(RemoteCommand("RECENTS", useRoot = useRoot))
    suspend fun volumeUp(useRoot: Boolean = false)   = command(RemoteCommand("VOLUME_UP", useRoot = useRoot))
    suspend fun volumeDown(useRoot: Boolean = false) = command(RemoteCommand("VOLUME_DOWN", useRoot = useRoot))

    suspend fun getAppList(): List<AppInfo> = command(RemoteCommand("APP_LIST"))?.apps ?: emptyList()
    suspend fun launchApp(packageName: String): Boolean = command(RemoteCommand("APP_LAUNCH", packageName = packageName))?.success ?: false
    suspend fun stopApp(packageName: String, useRoot: Boolean = false): Boolean =
        command(RemoteCommand("APP_STOP", packageName = packageName, useRoot = useRoot))?.success ?: false
    suspend fun runShell(cmd: String, useRoot: Boolean = false): String =
        command(RemoteCommand("SHELL", shellCmd = cmd, useRoot = useRoot))?.shellOutput ?: ""
    suspend fun getClipboard(): String = command(RemoteCommand("CLIPBOARD_GET"))?.shellOutput ?: ""
    suspend fun setClipboard(text: String) = command(RemoteCommand("CLIPBOARD_SET", shellCmd = text))

    /** Requests and returns a single JPEG screen frame. Poll this to refresh the view. */
    suspend fun screenshot(): ByteArray? = withContext(Dispatchers.IO) {
        io.withLock {
            try {
                sendRaw(RemoteCommand("SCREENSHOT").toJson())
                receiveFrame()
            } catch (e: Exception) {
                Log.e(TAG, "screenshot: ${e.message}")
                null
            }
        }
    }

    // ---- internals ----
    private suspend fun command(cmd: RemoteCommand): RemoteResponse? = withContext(Dispatchers.IO) {
        io.withLock {
            try {
                sendRaw(cmd.toJson())
                receiveJson()
            } catch (e: Exception) {
                Log.e(TAG, "command ${cmd.type}: ${e.message}")
                null
            }
        }
    }

    private fun sendRaw(json: String) {
        val out = output ?: throw IOException("Not connected")
        val bytes = json.toByteArray()
        out.writeInt(bytes.size)
        out.write(bytes)
        out.flush()
    }

    private fun receiveJson(): RemoteResponse? {
        val inp = input ?: return null
        val len = inp.readInt()
        if (len <= 0 || len > MAX_MSG) throw IOException("bad response length $len")
        val bytes = ByteArray(len)
        inp.readFully(bytes)
        return RemoteResponse.fromJson(String(bytes))
    }

    private fun receiveFrame(): ByteArray? {
        val inp = input ?: return null
        val size = inp.readInt()
        if (size <= 0 || size > MAX_MSG) throw IOException("bad frame length $size")
        val bytes = ByteArray(size)
        inp.readFully(bytes)
        return bytes
    }
}
