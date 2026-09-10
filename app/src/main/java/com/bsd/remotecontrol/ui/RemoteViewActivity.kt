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
    private lateinit var btnBack: ImageButton
    private lateinit var btnHome: ImageButton
    private lateinit var btnRecents: ImageButton
    private lateinit var btnVolUp: ImageButton
    private lateinit var btnVolDown: ImageButton
    private lateinit var btnApps: ImageButton
    private lateinit var btnShell: ImageButton
    private lateinit var btnRefresh: ImageButton

    private val client get() = RemoteClientHolder.client
    private var streamJob: Job? = null
    private var useRoot = false
    private var remoteW = 1080
    private var remoteH = 1920

    // מעקב נגיעות לswipe
    private var touchStartX = 0f
    private var touchStartY = 0f
    private var touchStartTime = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )
        setContentView(R.layout.activity_remote_view)

        imageView   = findViewById(R.id.ivRemoteScreen)
        progressBar = findViewById(R.id.progressBar)
        tvStatus    = findViewById(R.id.tvStatus)
        btnBack     = findViewById(R.id.btnBack)
        btnHome     = findViewById(R.id.btnHome)
        btnRecents  = findViewById(R.id.btnRecents)
        btnVolUp    = findViewById(R.id.btnVolUp)
        btnVolDown  = findViewById(R.id.btnVolDown)
        btnApps     = findViewById(R.id.btnApps)
        btnShell    = findViewById(R.id.btnShell)
        btnRefresh  = findViewById(R.id.btnRefresh)

        remoteW = client?.remoteScreenWidth ?: 1080
        remoteH = client?.remoteScreenHeight ?: 1920
        tvStatus.text = "📱 ${RemoteClientHolder.remoteDeviceName} | ${remoteW}×${remoteH}"

        setupTouch()
        setupButtons()
        startStream()
    }

    private fun setupTouch() {
        imageView.setOnTouchListener { view, event ->
            // guard: view must have size
            if (view.width <= 0 || view.height <= 0) return@setOnTouchListener true

            val scaleX = remoteW.toFloat() / view.width.toFloat()
            val scaleY = remoteH.toFloat() / view.height.toFloat()
            val remX = (event.x * scaleX).toInt().coerceIn(0, remoteW - 1)
            val remY = (event.y * scaleY).toInt().coerceIn(0, remoteH - 1)

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

                    if (dist < 20 && dt < 500) {
                        // Tap פשוט
                        val startRemX = ((touchStartX * scaleX)).toInt().coerceIn(0, remoteW - 1)
                        val startRemY = ((touchStartY * scaleY)).toInt().coerceIn(0, remoteH - 1)
                        safeLaunch { client?.tap(startRemX, startRemY, useRoot) }
                    } else if (dist >= 20) {
                        // Swipe
                        val startRemX = ((touchStartX * scaleX)).toInt().coerceIn(0, remoteW - 1)
                        val startRemY = ((touchStartY * scaleY)).toInt().coerceIn(0, remoteH - 1)
                        safeLaunch { client?.swipe(startRemX, startRemY, remX, remY, useRoot) }
                    }
                }
            }
            true
        }
    }

    private fun safeLaunch(block: suspend () -> Unit) {
        try {
            lifecycleScope.launch(Dispatchers.IO) {
                try { block() } catch (e: Exception) {
                    runOnUiThread {
                        tvStatus.text = "שגיאה: ${e.message?.take(50)}"
                    }
                }
            }
        } catch (e: Exception) {
            // אל תקרוס
        }
    }

    private fun setupButtons() {
        btnBack.setOnClickListener    { safeLaunch { client?.back(useRoot) } }
        btnHome.setOnClickListener    { safeLaunch { client?.home(useRoot) } }
        btnRecents.setOnClickListener { safeLaunch { client?.recents(useRoot) } }
        btnVolUp.setOnClickListener   { safeLaunch { client?.volumeUp(useRoot) } }
        btnVolDown.setOnClickListener { safeLaunch { client?.volumeDown(useRoot) } }
        btnApps.setOnClickListener    { showAppsList() }
        btnShell.setOnClickListener   { showShellDialog() }
        btnRefresh.setOnClickListener { restartStream() }
    }

    private fun startStream() {
        progressBar.visibility = View.VISIBLE
        streamJob?.cancel()
        streamJob = client?.startStream { jpegBytes ->
            try {
                val bmp = BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size)
                if (bmp != null) {
                    runOnUiThread {
                        progressBar.visibility = View.GONE
                        imageView.setImageBitmap(bmp)
                    }
                }
            } catch (e: Exception) {
                // אל תקרוס על frame פגום
            }
        }
    }

    private fun restartStream() {
        streamJob?.cancel()
        startStream()
    }

    private fun showAppsList() {
        progressBar.visibility = View.VISIBLE
        safeLaunch {
            val apps = client?.getAppList() ?: emptyList()
            runOnUiThread {
                progressBar.visibility = View.GONE
                if (apps.isEmpty()) {
                    Toast.makeText(this, "לא נמצאו אפליקציות", Toast.LENGTH_SHORT).show()
                    return@runOnUiThread
                }
                val names = apps.map { it.name }.toTypedArray()
                AlertDialog.Builder(this, R.style.DarkDialog)
                    .setTitle("אפליקציות מותקנות (${apps.size})")
                    .setItems(names) { _, i ->
                        safeLaunch { client?.launchApp(apps[i].packageName) }
                    }
                    .setNegativeButton("ביטול", null)
                    .show()
            }
        }
    }

    private fun showShellDialog() {
        val input = EditText(this).apply {
            hint = "הכנס פקודה..."
            setTextColor(0xFFFFFFFF.toInt())
            setHintTextColor(0xFF888888.toInt())
            setPadding(24, 16, 24, 16)
        }
        AlertDialog.Builder(this, R.style.DarkDialog)
            .setTitle("פקודת Shell במכשיר המרוחק")
            .setView(input)
            .setPositiveButton("הפעל") { _, _ ->
                val cmd = input.text.toString().trim()
                if (cmd.isBlank()) return@setPositiveButton
                safeLaunch {
                    val result = client?.runShell(cmd, useRoot) ?: "שגיאה"
                    runOnUiThread {
                        AlertDialog.Builder(this, R.style.DarkDialog)
                            .setTitle("תוצאה")
                            .setMessage(result.take(2000).ifBlank { "(ריק)" })
                            .setPositiveButton("סגור", null)
                            .show()
                    }
                }
            }
            .setNegativeButton("ביטול", null)
            .show()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.remote_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menuToggleRoot -> {
                useRoot = !useRoot
                item.title = if (useRoot) "Root: פעיל ✅" else "Root: כבוי"
                Toast.makeText(this, if (useRoot) "מצב Root פעיל" else "מצב Root כבוי", Toast.LENGTH_SHORT).show()
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
