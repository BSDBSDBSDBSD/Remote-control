package com.bsd.remotecontrol.ui

import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.os.*
import android.view.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.bsd.remotecontrol.R
import kotlinx.coroutines.*

class RemoteViewActivity : AppCompatActivity() {

    private lateinit var imageView: ImageView
    private lateinit var progressBar: ProgressBar
    private lateinit var tvStatus: TextView
    private lateinit var tvFps: TextView
    private lateinit var btnBack: Button
    private lateinit var btnHome: Button
    private lateinit var btnRecents: Button
    private lateinit var btnVolUp: Button
    private lateinit var btnVolDown: Button
    private lateinit var btnRoot: Button

    private val client get() = RemoteClientHolder.client
    private var streamJob: Job? = null
    private var useRoot = false
    private var remoteW = 1080
    private var remoteH = 1920

    // Current frame + zoom/pan state
    private var frameW = 0
    private var frameH = 0
    private var userScale = 1f
    private var userTransX = 0f
    private var userTransY = 0f
    private val matrix = Matrix()

    private var touchStartX = 0f
    private var touchStartY = 0f
    private var touchStartTime = 0L
    private var multiTouch = false
    private var lastTapTime = 0L

    private var frameCount = 0
    private var lastFpsTime = System.currentTimeMillis()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (SettingsActivity.getKeepScreenOn(this)) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN)
        setContentView(R.layout.activity_remote_view)

        imageView   = findViewById(R.id.ivRemoteScreen)
        progressBar = findViewById(R.id.progressBar)
        tvStatus    = findViewById(R.id.tvStatus)
        tvFps       = findViewById(R.id.tvFps)
        btnBack     = findViewById(R.id.btnBack)
        btnHome     = findViewById(R.id.btnHome)
        btnRecents  = findViewById(R.id.btnRecents)
        btnVolUp    = findViewById(R.id.btnVolUp)
        btnVolDown  = findViewById(R.id.btnVolDown)
        btnRoot     = findViewById(R.id.btnRoot)

        if (client == null) {
            Toast.makeText(this, "שגיאה: אין חיבור פעיל", Toast.LENGTH_LONG).show()
            finish(); return
        }

        useRoot = SettingsActivity.getUseRootDefault(this)
        remoteW = client?.remoteScreenWidth ?: 1080
        remoteH = client?.remoteScreenHeight ?: 1920
        tvStatus.text = "📱 ${RemoteClientHolder.remoteDeviceName}  ${remoteW}×${remoteH}"
        tvFps.visibility = if (SettingsActivity.getShowFps(this)) View.VISIBLE else View.GONE

        imageView.scaleType = ImageView.ScaleType.MATRIX
        updateRootButton()
        setupTouch()
        setupButtons()
        startStream()
    }

    private fun updateRootButton() {
        btnRoot.text = if (useRoot) "Root✅" else "Root"
        btnRoot.backgroundTintList = getColorStateList(
            if (useRoot) android.R.color.holo_red_light else R.color.red_primary
        )
    }

    // ---- Zoom / pan (2 fingers) + tap/swipe (1 finger) ----
    private val scaleDetector by lazy {
        ScaleGestureDetector(this, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(d: ScaleGestureDetector): Boolean {
                userScale = (userScale * d.scaleFactor).coerceIn(1f, 5f)
                applyMatrix()
                return true
            }
        })
    }

    private fun setupTouch() {
        imageView.setOnTouchListener { _, event ->
            scaleDetector.onTouchEvent(event)
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    multiTouch = false
                    touchStartX = event.x; touchStartY = event.y
                    touchStartTime = System.currentTimeMillis()
                }
                MotionEvent.ACTION_POINTER_DOWN -> multiTouch = true
                MotionEvent.ACTION_MOVE -> {
                    if (event.pointerCount >= 2) {
                        // Two-finger drag = pan
                        multiTouch = true
                        userTransX += event.x - touchStartX
                        userTransY += event.y - touchStartY
                        touchStartX = event.x; touchStartY = event.y
                        applyMatrix()
                    }
                }
                MotionEvent.ACTION_UP -> {
                    if (!multiTouch) handleSingleTouchUp(event)
                }
            }
            true
        }
    }

    private fun handleSingleTouchUp(event: MotionEvent) {
        val dx = event.x - touchStartX
        val dy = event.y - touchStartY
        val dt = System.currentTimeMillis() - touchStartTime
        val dist = Math.hypot(dx.toDouble(), dy.toDouble())

        // Double-tap (quick, in place) resets zoom.
        val now = System.currentTimeMillis()
        if (dist < 25 && dt < 250 && now - lastTapTime < 300) {
            resetZoom(); lastTapTime = 0; return
        }
        if (dist < 25 && dt < 400) lastTapTime = now

        val start = viewToRemote(touchStartX, touchStartY) ?: return
        if (dist < 25 && dt < 400) {
            safe { client?.tap(start.first, start.second, useRoot) }
        } else if (dist >= 25) {
            val end = viewToRemote(event.x, event.y) ?: return
            safe { client?.swipe(start.first, start.second, end.first, end.second, useRoot) }
        }
    }

    /** Maps a point in the ImageView to remote-screen pixel coordinates via the display matrix. */
    private fun viewToRemote(vx: Float, vy: Float): Pair<Int, Int>? {
        if (frameW <= 0 || frameH <= 0) return null
        val inv = Matrix()
        if (!matrix.invert(inv)) return null
        val pts = floatArrayOf(vx, vy)
        inv.mapPoints(pts)
        val rx = (pts[0] * remoteW / frameW).toInt().coerceIn(0, remoteW - 1)
        val ry = (pts[1] * remoteH / frameH).toInt().coerceIn(0, remoteH - 1)
        return rx to ry
    }

    private fun applyMatrix() {
        val vw = imageView.width.toFloat()
        val vh = imageView.height.toFloat()
        if (vw <= 0 || vh <= 0 || frameW <= 0 || frameH <= 0) return
        val fit = minOf(vw / frameW, vh / frameH)
        val scale = fit * userScale
        val tx = (vw - frameW * scale) / 2f + userTransX
        val ty = (vh - frameH * scale) / 2f + userTransY
        matrix.reset()
        matrix.postScale(scale, scale)
        matrix.postTranslate(tx, ty)
        imageView.imageMatrix = matrix
    }

    private fun resetZoom() {
        userScale = 1f; userTransX = 0f; userTransY = 0f
        applyMatrix()
        Toast.makeText(this, "התצוגה אופסה", Toast.LENGTH_SHORT).show()
    }

    private fun safe(block: suspend () -> Unit) {
        if (isDestroyed || isFinishing) return
        try {
            lifecycleScope.launch(Dispatchers.IO) {
                try { if (!isDestroyed) block() } catch (e: Exception) {
                    if (!isDestroyed) runOnUiThread { tvStatus.text = "שגיאה: ${e.message?.take(50)}" }
                }
            }
        } catch (_: Exception) { }
    }

    private fun setupButtons() {
        btnBack.setOnClickListener    { safe { client?.back(useRoot) } }
        btnHome.setOnClickListener    { safe { client?.home(useRoot) } }
        btnRecents.setOnClickListener { safe { client?.recents(useRoot) } }
        btnVolUp.setOnClickListener   { safe { client?.volumeUp(useRoot) } }
        btnVolDown.setOnClickListener { safe { client?.volumeDown(useRoot) } }

        btnRoot.setOnClickListener {
            useRoot = !useRoot
            updateRootButton()
            Toast.makeText(this, if (useRoot) "Root מופעל" else "Root כבוי", Toast.LENGTH_SHORT).show()
        }

        findViewById<Button>(R.id.btnApps).setOnClickListener    { showAppsList() }
        findViewById<Button>(R.id.btnShell).setOnClickListener   { showShellDialog() }
        findViewById<Button>(R.id.btnRefresh).setOnClickListener { resetZoom() }
        findViewById<Button>(R.id.btnClipboard).setOnClickListener { showClipboardDialog() }
    }

    /** Polls the server for screen frames one at a time (no concurrent socket reads). */
    private fun startStream() {
        if (isDestroyed) return
        progressBar.visibility = View.VISIBLE
        streamJob?.cancel()
        val interval = SettingsActivity.getFrameInterval(this).toLong().coerceAtLeast(80L)
        streamJob = lifecycleScope.launch(Dispatchers.IO) {
            while (isActive && !isDestroyed) {
                val jpeg = try { client?.screenshot() } catch (e: Exception) { null }
                if (jpeg != null) {
                    val bmp: Bitmap? = try { BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size) } catch (e: Exception) { null }
                    if (bmp != null) {
                        frameCount++
                        val now = System.currentTimeMillis()
                        val showFps = now - lastFpsTime >= 1000
                        val fps = frameCount
                        if (showFps) { frameCount = 0; lastFpsTime = now }
                        withContext(Dispatchers.Main) {
                            if (!isDestroyed) {
                                progressBar.visibility = View.GONE
                                frameW = bmp.width; frameH = bmp.height
                                imageView.setImageBitmap(bmp)
                                applyMatrix()
                                if (showFps) tvFps.text = "$fps fps"
                            }
                        }
                    }
                }
                delay(interval)
            }
        }
    }

    private fun showAppsList() {
        if (isDestroyed) return
        progressBar.visibility = View.VISIBLE
        safe {
            val apps = client?.getAppList() ?: emptyList()
            if (!isDestroyed) runOnUiThread {
                progressBar.visibility = View.GONE
                if (apps.isEmpty()) { Toast.makeText(this, "לא נמצאו אפליקציות", Toast.LENGTH_SHORT).show(); return@runOnUiThread }
                val names = apps.map { "${it.name}\n${it.packageName}" }.toTypedArray()
                try {
                    AlertDialog.Builder(this)
                        .setTitle("אפליקציות (${apps.size})")
                        .setItems(names) { _, i -> safe { client?.launchApp(apps[i].packageName) } }
                        .setNegativeButton("ביטול", null)
                        .show()
                } catch (_: Exception) { }
            }
        }
    }

    private fun showClipboardDialog() {
        if (isDestroyed) return
        try {
            val options = arrayOf(
                "📋  קרא לוח מרחוק (המכשיר הנשלט)",
                "📤  שלח לוח מקומי (שלי) למרחוק",
                "⌨️  הזן טקסט ידנית"
            )
            AlertDialog.Builder(this)
                .setTitle("לוח הגזירים")
                .setItems(options) { _, which ->
                    when (which) {
                        0 -> safe {
                            val text = client?.getClipboard() ?: ""
                            if (!isDestroyed) runOnUiThread {
                                if (text.isBlank()) Toast.makeText(this, "לוח המרחוק ריק", Toast.LENGTH_SHORT).show()
                                else {
                                    val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    cm.setPrimaryClip(ClipData.newPlainText("remote", text))
                                    try {
                                        AlertDialog.Builder(this).setTitle("לוח מרחוק")
                                            .setMessage(text.take(1000))
                                            .setPositiveButton("העתק ✓") { _, _ -> }.show()
                                    } catch (_: Exception) {}
                                }
                            }
                        }
                        1 -> {
                            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val localText = cm.primaryClip?.getItemAt(0)?.text?.toString() ?: ""
                            if (localText.isBlank()) Toast.makeText(this, "לוח מקומי ריק", Toast.LENGTH_SHORT).show()
                            else { safe { client?.setClipboard(localText) }; Toast.makeText(this, "נשלח למכשיר המרוחק ✓", Toast.LENGTH_SHORT).show() }
                        }
                        2 -> showTextInputDialog()
                    }
                }.show()
        } catch (_: Exception) { }
    }

    private fun showTextInputDialog() {
        if (isDestroyed) return
        try {
            val input = EditText(this).apply {
                hint = "הכנס טקסט לשליחה..."
                setTextColor(0xFFFFFFFF.toInt()); setHintTextColor(0xFF666666.toInt())
                setBackgroundColor(0xFF1A2535.toInt()); setPadding(24, 16, 24, 16)
            }
            AlertDialog.Builder(this).setTitle("שלח טקסט למכשיר מרוחק").setView(input)
                .setPositiveButton("שלח") { _, _ ->
                    val text = input.text.toString()
                    if (text.isNotBlank()) safe { client?.setClipboard(text) }
                }
                .setNegativeButton("ביטול", null).show()
        } catch (_: Exception) { }
    }

    private fun showShellDialog() {
        if (isDestroyed) return
        try {
            val input = EditText(this).apply {
                hint = "הכנס פקודה..."
                setTextColor(0xFFFFFFFF.toInt()); setHintTextColor(0xFF666666.toInt())
                setBackgroundColor(0xFF1A2535.toInt()); setPadding(24, 16, 24, 16); setTextIsSelectable(true)
            }
            AlertDialog.Builder(this).setTitle("פקודת Shell מרחוק").setView(input)
                .setPositiveButton("הפעל") { _, _ ->
                    val cmd = input.text.toString().trim()
                    if (cmd.isBlank()) return@setPositiveButton
                    safe {
                        val result = client?.runShell(cmd, useRoot) ?: "שגיאה"
                        if (!isDestroyed) runOnUiThread {
                            try {
                                AlertDialog.Builder(this).setTitle("תוצאה")
                                    .setMessage(result.take(3000).ifBlank { "(ריק)" })
                                    .setPositiveButton("סגור", null)
                                    .setNeutralButton("העתק") { _, _ ->
                                        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        cm.setPrimaryClip(ClipData.newPlainText("shell", result))
                                        Toast.makeText(this, "הועתק ✓", Toast.LENGTH_SHORT).show()
                                    }.show()
                            } catch (_: Exception) { }
                        }
                    }
                }
                .setNegativeButton("ביטול", null).show()
        } catch (_: Exception) { }
    }

    override fun onDestroy() {
        streamJob?.cancel()
        client?.disconnect()
        super.onDestroy()
    }
}
