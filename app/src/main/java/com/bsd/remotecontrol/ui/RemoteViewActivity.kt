package com.bsd.remotecontrol.ui

import android.app.AlertDialog
import android.graphics.BitmapFactory
import android.os.Bundle
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
    private var streamJob: kotlinx.coroutines.Job? = null
    private var useRoot = false
    private var remoteW = 1080
    private var remoteH = 1920

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Fullscreen landscape
        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
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
        tvStatus.text = "📱 ${RemoteClientHolder.remoteDeviceName} | ${remoteW}x${remoteH}"

        setupTouchOnScreen()
        setupButtons()
        startScreenStream()
    }

    private fun setupTouchOnScreen() {
        imageView.setOnTouchListener { view, event ->
            // המרת קואורדינטות מ-ImageView לרזולוציית המכשיר המרוחק
            val scaleX = remoteW.toFloat() / view.width
            val scaleY = remoteH.toFloat() / view.height
            val remX = (event.x * scaleX).toInt()
            val remY = (event.y * scaleY).toInt()

            when (event.action) {
                MotionEvent.ACTION_DOWN -> { /* שמור נקודת התחלה */ }
                MotionEvent.ACTION_UP -> {
                    lifecycleScope.launch {
                        client?.tap(remX, remY, useRoot)
                    }
                }
                MotionEvent.ACTION_MOVE -> { /* אפשר להוסיף swipe tracking */ }
            }
            true
        }
    }

    private fun setupButtons() {
        btnBack.setOnClickListener { lifecycleScope.launch { client?.back(useRoot) } }
        btnHome.setOnClickListener { lifecycleScope.launch { client?.home(useRoot) } }
        btnRecents.setOnClickListener { lifecycleScope.launch { client?.recents(useRoot) } }
        btnVolUp.setOnClickListener { lifecycleScope.launch { client?.volumeUp(useRoot) } }
        btnVolDown.setOnClickListener { lifecycleScope.launch { client?.volumeDown(useRoot) } }

        btnApps.setOnClickListener { showAppsList() }
        btnShell.setOnClickListener { showShellDialog() }
        btnRefresh.setOnClickListener {
            streamJob?.cancel()
            startScreenStream()
        }
    }

    private fun startScreenStream() {
        progressBar.visibility = View.VISIBLE
        streamJob = client?.startStream { jpegBytes ->
            val bmp = BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size)
            runOnUiThread {
                progressBar.visibility = View.GONE
                imageView.setImageBitmap(bmp)
            }
        }
    }

    private fun showAppsList() {
        progressBar.visibility = View.VISIBLE
        lifecycleScope.launch {
            val apps = client?.getAppList() ?: emptyList()
            runOnUiThread {
                progressBar.visibility = View.GONE
                val names = apps.map { it.name }.toTypedArray()
                AlertDialog.Builder(this@RemoteViewActivity)
                    .setTitle("אפליקציות מותקנות (${apps.size})")
                    .setItems(names) { _, index ->
                        lifecycleScope.launch {
                            client?.launchApp(apps[index].packageName)
                        }
                    }
                    .setNeutralButton("ביטול", null)
                    .show()
            }
        }
    }

    private fun showShellDialog() {
        if (!useRoot) {
            Toast.makeText(this, "Shell זמין רק במצב Root", Toast.LENGTH_SHORT).show()
            return
        }
        val input = EditText(this).apply { hint = "הכנס פקודה..." }
        AlertDialog.Builder(this)
            .setTitle("Shell במכשיר המרוחק")
            .setView(input)
            .setPositiveButton("הפעל") { _, _ ->
                val cmd = input.text.toString().trim()
                if (cmd.isBlank()) return@setPositiveButton
                lifecycleScope.launch {
                    val result = client?.runShell(cmd, useRoot) ?: "שגיאה"
                    runOnUiThread {
                        AlertDialog.Builder(this@RemoteViewActivity)
                            .setTitle("תוצאה")
                            .setMessage(result.take(2000))
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
                item.title = if (useRoot) "Root: ON" else "Root: OFF"
                Toast.makeText(this, if (useRoot) "Root פעיל" else "Root כבוי", Toast.LENGTH_SHORT).show()
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
