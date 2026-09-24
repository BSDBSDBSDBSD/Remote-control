package com.bsd.remotecontrol.screen

import android.app.*
import android.bluetooth.*
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.*
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.bsd.remotecontrol.input.InputManager
import com.bsd.remotecontrol.model.CommandTypes
import com.bsd.remotecontrol.model.RemoteCommand
import com.bsd.remotecontrol.model.RemoteResponse
import com.bsd.remotecontrol.ui.MainActivity
import com.bsd.remotecontrol.wifi.WifiDirectManager
import kotlinx.coroutines.*
import java.io.*
import java.net.ServerSocket
import java.util.UUID

class ScreenShareService : Service() {

    companion object {
        const val TAG = "ScreenShareSvc"
        val BT_UUID: UUID = UUID.fromString("fa87c0d0-afac-11de-8a39-0800200c9b77")
        const val CHANNEL_ID = "bt_remote_channel"
        const val NOTIF_ID = 2001
        const val ACTION_START = "START"
        const val ACTION_STOP  = "STOP"
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_RESULT_DATA = "result_data"
        var isRunning = false
        const val JPEG_QUALITY = 40
        const val FRAME_INTERVAL_MS = 250L  // 4fps
        const val WIFI_TCP_PORT = WifiDirectManager.SERVER_PORT
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var btServerSocket: BluetoothServerSocket? = null
    private var tcpServerSocket: ServerSocket? = null
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var screenWidth = 0
    private var screenHeight = 0
    private var useRoot = false
    private var streaming = false
    private var connectionType = "bluetooth"
    private var wifiDirect: com.bsd.remotecontrol.wifi.WifiDirectManager? = null

    override fun onCreate() {
        super.onCreate()
        InputManager.init()
        createNotificationChannel()
        getScreenDimensions()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                useRoot = intent.getBooleanExtra("use_root", false)
                connectionType = intent.getStringExtra("connection_type") ?: "bluetooth"
                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, -1)
                val resultData = intent.getParcelableExtra<Intent>(EXTRA_RESULT_DATA)
                val hasProjection = resultCode != -1 && resultData != null

                // Start foreground with the RIGHT type: mediaProjection only when we actually
                // have screen-capture consent (otherwise Android 14 rejects that type and the
                // app would crash); connectedDevice for the root path (no screen capture).
                val notif = buildNotification("ממתין לחיבור...")
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        val type = if (hasProjection)
                            android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
                        else
                            android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
                        startForeground(NOTIF_ID, notif, type)
                    } else {
                        startForeground(NOTIF_ID, notif)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "startForeground failed", e)
                    try { startForeground(NOTIF_ID, notif) } catch (e2: Exception) {
                        Log.e(TAG, "plain startForeground failed too", e2)
                        stopSelf(); isRunning = false; return START_NOT_STICKY
                    }
                }

                if (hasProjection) {
                    setupMediaProjection(resultCode, resultData!!)
                }

                // Always listen on BOTH transports; the controlling phone picks one.
                startBluetoothServer()
                startWifiTcpServer()
                // Host a direct Wi-Fi group so the other phone can find and reach this one
                // with no router and no internet.
                startWifiDirectHost()
                isRunning = true
            }
            ACTION_STOP -> {
                stopEverything()
                stopSelf()
                isRunning = false
            }
        }
        return START_STICKY
    }

    private fun getScreenDimensions() {
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        wm.defaultDisplay.getRealMetrics(metrics)
        screenWidth = metrics.widthPixels
        screenHeight = metrics.heightPixels
    }

    private fun setupMediaProjection(resultCode: Int, data: Intent) {
        try {
            val mpm = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            mediaProjection = mpm.getMediaProjection(resultCode, data)
            // Android 14+ requires a registered callback before createVirtualDisplay.
            mediaProjection?.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() {
                    try { virtualDisplay?.release() } catch (_: Exception) {}
                }
            }, Handler(Looper.getMainLooper()))
            imageReader = ImageReader.newInstance(screenWidth, screenHeight, PixelFormat.RGBA_8888, 2)
            virtualDisplay = mediaProjection?.createVirtualDisplay(
                "BTRemoteCapture",
                screenWidth, screenHeight,
                resources.displayMetrics.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader?.surface, null, null
            )
            Log.d(TAG, "MediaProjection setup: ${screenWidth}x${screenHeight}")
        } catch (e: Exception) {
            Log.e(TAG, "MediaProjection setup failed: ${e.message}")
        }
    }

    // ---- BT Server ----
    private fun startBluetoothServer() {
        scope.launch {
            try {
                val adapter = BluetoothAdapter.getDefaultAdapter() ?: return@launch
                btServerSocket = adapter.listenUsingRfcommWithServiceRecord("BTRemote", BT_UUID)
                Log.d(TAG, "BT Server listening")
                while (isActive) {
                    val socket = try { btServerSocket?.accept() } catch (e: Exception) { break } ?: break
                    val name = try { socket.remoteDevice.name ?: socket.remoteDevice.address } catch (e: Exception) { "Unknown" }
                    Log.d(TAG, "BT client connected: $name")
                    updateNotification("מחובר (BT): $name")
                    launch { handleClient(socket.inputStream, socket.outputStream, onDone = { socket.close() }) }
                }
            } catch (e: Exception) {
                Log.e(TAG, "BT Server error: ${e.message}")
            }
        }
    }

    // ---- WiFi Direct TCP Server ----
    private fun startWifiTcpServer() {
        scope.launch {
            try {
                tcpServerSocket = ServerSocket(WIFI_TCP_PORT)
                Log.d(TAG, "TCP Server listening on port $WIFI_TCP_PORT")
                while (isActive) {
                    val socket = try { tcpServerSocket?.accept() } catch (e: Exception) { break } ?: break
                    val addr = socket.inetAddress.hostAddress ?: "unknown"
                    Log.d(TAG, "WiFi client connected: $addr")
                    updateNotification("מחובר (WiFi): $addr")
                    launch { handleClient(socket.getInputStream(), socket.getOutputStream(), onDone = { socket.close() }) }
                }
            } catch (e: Exception) {
                Log.e(TAG, "TCP Server error: ${e.message}")
            }
        }
    }

    // ---- Wi-Fi Direct host (direct connection, no router) ----
    private fun startWifiDirectHost() {
        try {
            val wd = com.bsd.remotecontrol.wifi.WifiDirectManager(this)
            wifiDirect = wd
            wd.register()
            wd.createGroup { ok, err ->
                if (ok) updateNotification("שרת WiFi ישיר פעיל — התחבר מהמכשיר השני")
                else updateNotification("WiFi ישיר נכשל: $err")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Wi-Fi Direct host failed: ${e.message}")
        }
    }

    // ---- Unified client handler ----
    private suspend fun handleClient(
        rawIn: InputStream,
        rawOut: OutputStream,
        onDone: () -> Unit
    ) {
        val input  = DataInputStream(BufferedInputStream(rawIn))
        val output = DataOutputStream(BufferedOutputStream(rawOut))
        var streamJob: Job? = null

        try {
            while (true) {
                val len = input.readInt()
                if (len <= 0 || len > 4 * 1024 * 1024) break  // sanity check
                val jsonBytes = ByteArray(len)
                input.readFully(jsonBytes)
                val cmd = try { RemoteCommand.fromJson(String(jsonBytes)) } catch (e: Exception) { continue }

                when (cmd.type) {
                    CommandTypes.TOUCH   -> { InputManager.tap(cmd.x, cmd.y, cmd.useRoot && useRoot); sendOk(output) }
                    CommandTypes.SWIPE   -> { InputManager.swipe(cmd.x, cmd.y, cmd.x2, cmd.y2, cmd.useRoot && useRoot); sendOk(output) }
                    CommandTypes.BACK    -> { InputManager.back(cmd.useRoot && useRoot); sendOk(output) }
                    CommandTypes.HOME    -> { InputManager.home(cmd.useRoot && useRoot); sendOk(output) }
                    CommandTypes.RECENTS -> { InputManager.recents(cmd.useRoot && useRoot); sendOk(output) }
                    CommandTypes.VOLUME_UP   -> { InputManager.volumeUp(cmd.useRoot && useRoot); sendOk(output) }
                    CommandTypes.VOLUME_DOWN -> { InputManager.volumeDown(cmd.useRoot && useRoot); sendOk(output) }
                    CommandTypes.KEY     -> { InputManager.key(cmd.keyCode, cmd.useRoot && useRoot); sendOk(output) }

                    CommandTypes.APP_LIST -> {
                        val apps = InputManager.getInstalledApps(this@ScreenShareService)
                        sendJson(output, RemoteResponse(success = true, apps = apps).toJson())
                    }
                    CommandTypes.APP_LAUNCH -> {
                        val ok = InputManager.launchAppWithContext(this@ScreenShareService, cmd.packageName)
                        sendJson(output, RemoteResponse(success = ok).toJson())
                    }
                    CommandTypes.APP_STOP -> {
                        val ok = InputManager.stopApp(cmd.packageName, cmd.useRoot && useRoot)
                        sendJson(output, RemoteResponse(success = ok).toJson())
                    }
                    CommandTypes.SHELL -> {
                        val out = InputManager.runShell(cmd.shellCmd, cmd.useRoot && useRoot)
                        sendJson(output, RemoteResponse(success = true, shellOutput = out).toJson())
                    }
                    CommandTypes.SCREEN_INFO -> {
                        sendJson(output, RemoteResponse(
                            success = true,
                            screenWidth = screenWidth,
                            screenHeight = screenHeight,
                            isRoot = InputManager.isRootAvailable
                        ).toJson())
                    }
                    CommandTypes.ROOT_STATUS -> {
                        sendJson(output, RemoteResponse(success = true, isRoot = InputManager.isRootAvailable).toJson())
                    }
                    CommandTypes.SCREENSHOT -> {
                        sendSingleFrame(output)
                    }
                    CommandTypes.STREAM_START -> {
                        streaming = true
                        streamJob?.cancel()
                        streamJob = scope.launch { streamFrames(output) }
                        sendOk(output)
                    }
                    CommandTypes.STREAM_STOP -> {
                        streaming = false
                        streamJob?.cancel()
                        sendOk(output)
                    }
                    CommandTypes.CLIPBOARD_GET -> {
                        val text = InputManager.getClipboard(this@ScreenShareService)
                        sendJson(output, RemoteResponse(success = true, shellOutput = text).toJson())
                    }
                    CommandTypes.CLIPBOARD_SET -> {
                        InputManager.setClipboard(this@ScreenShareService, cmd.shellCmd)
                        sendOk(output)
                    }
                }
            }
        } catch (e: EOFException) {
            Log.d(TAG, "Client disconnected")
        } catch (e: Exception) {
            Log.e(TAG, "Client error: ${e.message}")
        } finally {
            streaming = false
            streamJob?.cancel()
            try { onDone() } catch (_: Exception) {}
            updateNotification("ממתין לחיבור...")
        }
    }

    private suspend fun streamFrames(output: DataOutputStream) {
        while (streaming) {
            try { sendSingleFrame(output) } catch (e: Exception) { break }
            delay(FRAME_INTERVAL_MS)
        }
    }

    private fun sendSingleFrame(output: DataOutputStream) {
        val jpegBytes = captureScreen() ?: return
        output.writeInt(jpegBytes.size)
        output.write(jpegBytes)
        output.flush()
    }

    private fun captureScreen(): ByteArray? {
        if (useRoot && InputManager.isRootAvailable) {
            return captureScreenRoot()
        }
        val reader = imageReader ?: return null
        return try {
            val image = reader.acquireLatestImage() ?: return null
            val plane = image.planes[0]
            val pixelStride = plane.pixelStride
            val rowStride = plane.rowStride
            val rowPadding = rowStride - pixelStride * screenWidth
            val bitmapFull = Bitmap.createBitmap(
                screenWidth + rowPadding / pixelStride,
                screenHeight,
                Bitmap.Config.ARGB_8888
            )
            bitmapFull.copyPixelsFromBuffer(plane.buffer)
            image.close()
            val bitmap = if (rowPadding == 0) bitmapFull
                         else Bitmap.createBitmap(bitmapFull, 0, 0, screenWidth, screenHeight)
            val baos = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, baos)
            if (bitmap !== bitmapFull) bitmapFull.recycle()
            bitmap.recycle()
            baos.toByteArray()
        } catch (e: Exception) {
            Log.e(TAG, "captureScreen error: ${e.message}")
            null
        }
    }

    // Root screenshot: use base64 pipe — no double-su
    private fun captureScreenRoot(): ByteArray? {
        return try {
            val tmpPath = "/data/local/tmp/.btremote_cap.png"
            val result = com.topjohnwu.superuser.Shell.cmd(
                "screencap -p $tmpPath && base64 $tmpPath && rm -f $tmpPath"
            ).exec()
            if (!result.isSuccess || result.out.isEmpty()) return null
            val b64 = result.out.joinToString("")
            val pngBytes = android.util.Base64.decode(b64, android.util.Base64.DEFAULT)
            // Convert PNG → JPEG for smaller size
            val bmp = android.graphics.BitmapFactory.decodeByteArray(pngBytes, 0, pngBytes.size) ?: return pngBytes
            val baos = ByteArrayOutputStream()
            bmp.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, baos)
            bmp.recycle()
            baos.toByteArray()
        } catch (e: Exception) {
            Log.e(TAG, "captureScreenRoot error: ${e.message}")
            null
        }
    }

    private fun sendOk(output: DataOutputStream) {
        sendJson(output, RemoteResponse(success = true).toJson())
    }

    private fun sendJson(output: DataOutputStream, json: String) {
        val bytes = json.toByteArray()
        output.writeInt(bytes.size)
        output.write(bytes)
        output.flush()
    }

    private fun stopEverything() {
        streaming = false
        scope.cancel()
        try { wifiDirect?.disconnect() } catch (_: Exception) {}
        try { wifiDirect?.unregister() } catch (_: Exception) {}
        wifiDirect = null
        try { btServerSocket?.close() } catch (_: Exception) {}
        try { tcpServerSocket?.close() } catch (_: Exception) {}
        try { virtualDisplay?.release() } catch (_: Exception) {}
        try { mediaProjection?.stop() } catch (_: Exception) {}
        try { imageReader?.close() } catch (_: Exception) {}
        isRunning = false
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(CHANNEL_ID, "BT Remote Service", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }
    }

    private fun buildNotification(text: String): Notification {
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("BT Remote — שרת פעיל")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(text: String) {
        try {
            getSystemService(NotificationManager::class.java).notify(NOTIF_ID, buildNotification(text))
        } catch (_: Exception) {}
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stopEverything()
        super.onDestroy()
    }
}
