package com.bsd.remotecontrol.ui

import android.app.AlertDialog
import android.graphics.BitmapFactory
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

    private val client get() = RemoteClientHolder.client
    private var streamJob: Job? = null
    private var useRoot = false
    private var remoteW = 1080
    private var remoteH = 1920

    // נגיעה
    private var touchStartX = 0f
    private var touchStartY = 0f
    private var touchStartTime = 0L

    // FPS counter
    private var frameCount = 0
    private var lastFpsTime = System.currentTimeMillis()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )
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

        if (client == null) {
            Toast.makeText(this, "שגיאה: אין חיבור פעיל", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        remoteW = client?.remoteScreenWidth ?: 1080
        remoteH = client?.remoteScreenHeight ?: 1920
        tvStatus.text = "📱 ${RemoteClientHolder.remoteDeviceName} | ${remoteW}×${remoteH}"

        setupTouch()
        setupButtons()
        startStream()
    }

    private fun setupTouch() {
        imageView.setOnTouchListener { view, event ->
            // Guard: view חייב להיות בגודל חוקי
            val vw = view.width
            val vh = view.height
            if (vw <= 0 || vh <= 0) return@setOnTouchListener true

            val scaleX = remoteW.toFloat() / vw.toFloat()
            val scaleY = remoteH.toFloat() / vh.toFloat()

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    touchStartX = event.x
                    touchStartY = event.y
                    touchStartTime = System.currentTimeMillis()
                }
                MotionEvent.ACTION_UP -> {
                    val dx = event.x - touchStartX
                    val dy = event.y - touchStartY
                    val dt = System.currentTimeMillis() - touchStartTime
                    val dist = Math.sqrt((dx * dx + dy * dy).toDouble())

                    val startRemX = (touchStartX * scaleX).toInt().coerceIn(0, remoteW - 1)
                    val startRemY = (touchStartY * scaleY).toInt().coerceIn(0, remoteH - 1)

                    if (dist < 25 && dt < 400) {
                        // Tap פשוט
                        safe { client?.tap(startRemX, startRemY, useRoot) }
                    } else if (dist >= 25) {
                        // Swipe
                        val endRemX = (event.x * scaleX).toInt().coerceIn(0, remoteW - 1)
                        val endRemY = (event.y * scaleY).toInt().coerceIn(0, remoteH - 1)
                        safe { client?.swipe(startRemX, startRemY, endRemX, endRemY, useRoot) }
                    }
                }
            }
            true
        }
    }

    // wrapper בטוח לכל coroutine — לא יקרוס גם אם האקטיביטי נסגרה
    private fun safe(block: suspend () -> Unit) {
        if (isDestroyed || isFinishing) return
        try {
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    if (!isDestroyed) block()
                } catch (e: Exception) {
                    if (!isDestroyed) {
                        runOnUiThread {
                            tvStatus.text = "שגיאה: ${e.message?.take(40)}"
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // אל תקרוס
        }
    }

    private fun setupButtons() {
        btnBack.setOnClickListener    { safe { client?.back(useRoot) } }
        btnHome.setOnClickListener    { safe { client?.home(useRoot) } }
        btnRecents.setOnClickListener { safe { client?.recents(useRoot) } }
        btnVolUp.setOnClickListener   { safe { client?.volumeUp(useRoot) } }
        btnVolDown.setOnClickListener { safe { client?.volumeDown(useRoot) } }

        findViewById<Button>(R.id.btnApps).setOnClickListener    { showAppsList() }
        findViewById<Button>(R.id.btnShell).setOnClickListener   { showShellDialog() }
        findViewById<Button>(R.id.btnRefresh).setOnClickListener { restartStream() }

        // Root toggle
        val btnRoot = findViewById<Button?>(R.id.btnRoot)
        btnRoot?.setOnClickListener {
            useRoot = !useRoot
            btnRoot.text = if (useRoot) "Root ✅" else "Root"
            Toast.makeText(this, if (useRoot) "Root מופעל" else "Root כבוי", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startStream() {
        if (isDestroyed) return
        progressBar.visibility = View.VISIBLE
        streamJob?.cancel()
        streamJob = client?.startStream { jpegBytes ->
            if (isDestroyed) return@startStream
            try {
                val bmp = BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size)
                if (bmp != null && !isDestroyed) {
                    // FPS
                    frameCount++
                    val now = System.currentTimeMillis()
                    if (now - lastFpsTime >= 1000) {
                        val fps = frameCount
                        frameCount = 0
                        lastFpsTime = now
                        runOnUiThread { if (!isDestroyed) tvFps.text = "$fps fps" }
                    }
                    runOnUiThread {
                        if (!isDestroyed) {
                            progressBar.visibility = View.GONE
                            imageView.setImageBitmap(bmp)
                        }
                    }
                }
            } catch (e: Exception) {
                // לא לקרוס על frame פגום
            }
        }
    }

    private fun restartStream() {
        streamJob?.cancel()
        startStream()
    }

    private fun showAppsList() {
        if (isDestroyed) return
        progressBar.visibility = View.VISIBLE
        safe {
            val apps = client?.getAppList() ?: emptyList()
            if (!isDestroyed) {
                runOnUiThread {
                    progressBar.visibility = View.GONE
                    if (apps.isEmpty()) {
                        Toast.makeText(this, "לא נמצאו אפליקציות", Toast.LENGTH_SHORT).show()
                        return@runOnUiThread
                    }
                    val names = apps.map { it.name }.toTypedArray()
                    try {
                        AlertDialog.Builder(this)
                            .setTitle("אפליקציות (${apps.size})")
                            .setItems(names) { _, i ->
                                safe { client?.launchApp(apps[i].packageName) }
                            }
                            .setNegativeButton("ביטול", null)
                            .show()
                    } catch (e: Exception) { }
                }
            }
        }
    }

    private fun showShellDialog() {
        if (isDestroyed) return
        try {
            val input = EditText(this).apply {
                hint = "הכנס פקודה..."
                setTextColor(0xFFFFFFFF.toInt())
                setHintTextColor(0xFF888888.toInt())
                setBackgroundColor(0xFF1A2535.toInt())
                setPadding(24, 16, 24, 16)
            }
            AlertDialog.Builder(this)
                .setTitle("פקודת Shell מרחוק")
                .setView(input)
                .setPositiveButton("הפעל") { _, _ ->
                    val cmd = input.text.toString().trim()
                    if (cmd.isBlank()) return@setPositiveButton
                    safe {
                        val result = client?.runShell(cmd, useRoot) ?: "שגיאה"
                        if (!isDestroyed) {
                            runOnUiThread {
                                try {
                                    AlertDialog.Builder(this)
                                        .setTitle("תוצאה")
                                        .setMessage(result.take(2000).ifBlank { "(ריק)" })
                                        .setPositiveButton("סגור", null)
                                        .show()
                                } catch (e: Exception) { }
                            }
                        }
                    }
                }
                .setNegativeButton("ביטול", null)
                .show()
        } catch (e: Exception) { }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        try { menuInflater.inflate(R.menu.remote_menu, menu) } catch (e: Exception) {}
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menuToggleRoot -> {
                useRoot = !useRoot
                item.title = if (useRoot) "Root: פעיל ✅" else "Root: כבוי"
                Toast.makeText(this, if (useRoot) "מצב Root פעיל" else "Root כבוי", Toast.LENGTH_SHORT).show()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onDestroy() {
        streamJob?.cancel()
        super.onDestroy()
    }
}
