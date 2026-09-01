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
import kotlinx.coroutines.*
import java.io.*
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
        // JPEG quality - נמוך יותר = מהיר יותר על Bluetooth
        const val JPEG_QUALITY = 40
        const val FRAME_INTERVAL_MS = 250L  // 4fps - מספיק לשליטה, Bluetooth מוגבל ב-bandwidth
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var serverSocket: BluetoothServerSocket? = null
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var screenWidth = 0
    private var screenHeight = 0
    private var useRoot = false
    private var streaming = false

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
                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, -1)
                val resultData = intent.getParcelableExtra<Intent>(EXTRA_RESULT_DATA)

                startForeground(NOTIF_ID, buildNotification("ממתין לחיבור..."))

                if (resultCode != -1 && resultData != null) {
                    setupMediaProjection(resultCode, resultData)
                }

                startBluetoothServer()
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
        val mpm = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = mpm.getMediaProjection(resultCode, data)
        imageReader = ImageReader.newInstance(screenWidth, screenHeight, PixelFormat.RGBA_8888, 2)
        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "BTRemoteCapture",
            screenWidth, screenHeight,
            resources.displayMetrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface, null, null
        )
        Log.d(TAG, "MediaProjection setup: ${screenWidth}x${screenHeight}")
    }

    private fun startBluetoothServer() {
        scope.launch {
            try {
                val adapter = BluetoothAdapter.getDefaultAdapter()
                serverSocket = adapter.listenUsingRfcommWithServiceRecord("BTRemote", BT_UUID)
                Log.d(TAG, "BT Remote Server listening")

                while (isActive) {
                    val socket = serverSocket?.accept() ?: break
                    Log.d(TAG, "Remote client connected: ${socket.remoteDevice.name}")
                    updateNotification("מחובר ל: ${socket.remoteDevice.name}")
                    launch { handleRemoteClient(socket) }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Server error: ${e.message}")
            }
        }
    }

    private suspend fun handleRemoteClient(socket: BluetoothSocket) {
        val input  = DataInputStream(BufferedInputStream(socket.inputStream))
        val output = DataOutputStream(BufferedOutputStream(socket.outputStream))
        var streamJob: Job? = null

        try {
            while (socket.isConnected) {
                val len = input.readInt()
                val jsonBytes = ByteArray(len)
                input.readFully(jsonBytes)
                val cmd = RemoteCommand.fromJson(String(jsonBytes))

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
                        streamJob = scope.launch { streamFrames(output) }
                        sendOk(output)
                    }
                    CommandTypes.STREAM_STOP -> {
                        streaming = false
                        streamJob?.cancel()
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
            socket.close()
            updateNotification("ממתין לחיבור...")
        }
    }

    private suspend fun streamFrames(output: DataOutputStream) {
        while (streaming) {
            sendSingleFrame(output)
            delay(FRAME_INTERVAL_MS)
        }
    }

    private fun sendSingleFrame(output: DataOutputStream) {
        val jpegBytes = captureScreen() ?: return
        // שולח: [4 bytes size][jpeg bytes]
        output.writeInt(jpegBytes.size)
        output.write(jpegBytes)
        output.flush()
    }

    private fun captureScreen(): ByteArray? {
        // Root path - הכי מהיר ואמין
        if (useRoot && InputManager.isRootAvailable) {
            return InputManager.takeScreenshotRoot()
        }
        // MediaProjection path
        val reader = imageReader ?: return null
        return try {
            val image = reader.acquireLatestImage() ?: return null
            val plane = image.planes[0]
            val bitmap = Bitmap.createBitmap(
                plane.buffer.let { buf ->
                    val pixelStride = plane.pixelStride
                    val rowStride = plane.rowStride
                    val rowPadding = rowStride - pixelStride * screenWidth
                    Bitmap.createBitmap(
                        screenWidth + rowPadding / pixelStride,
                        screenHeight,
                        Bitmap.Config.ARGB_8888
                    ).also { it.copyPixelsFromBuffer(buf) }
                },
                0, 0, screenWidth, screenHeight
            )
            image.close()
            val baos = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, baos)
            bitmap.recycle()
            baos.toByteArray()
        } catch (e: Exception) {
            Log.e(TAG, "captureScreen error: ${e.message}")
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
        serverSocket?.close()
        virtualDisplay?.release()
        mediaProjection?.stop()
        imageReader?.close()
        isRunning = false
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(CHANNEL_ID, "BT Remote Service", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }
    }

    private fun buildNotification(text: String): Notification {
        val pi = PendingIntent.getActivity(this, 0,
            Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("BT Remote - שרת פעיל")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(text: String) {
        getSystemService(NotificationManager::class.java).notify(NOTIF_ID, buildNotification(text))
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stopEverything()
        super.onDestroy()
    }
}
