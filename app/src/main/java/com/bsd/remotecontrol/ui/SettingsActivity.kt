package com.bsd.remotecontrol.ui

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.bsd.remotecontrol.R

class SettingsActivity : AppCompatActivity() {

    companion object {
        const val PREFS = "rc_settings"
        const val KEY_JPEG_QUALITY    = "jpeg_quality"
        const val KEY_FRAME_INTERVAL  = "frame_interval"
        const val KEY_KEEP_SCREEN_ON  = "keep_screen_on"
        const val KEY_AUTO_RECONNECT  = "auto_reconnect"
        const val KEY_SHOW_FPS        = "show_fps"
        const val KEY_USE_ROOT_DEFAULT = "use_root_default"

        fun prefs(ctx: Context): SharedPreferences =
            ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        fun getJpegQuality(ctx: Context)   = prefs(ctx).getInt(KEY_JPEG_QUALITY, 40)
        fun getFrameInterval(ctx: Context) = prefs(ctx).getInt(KEY_FRAME_INTERVAL, 250)
        fun getKeepScreenOn(ctx: Context)  = prefs(ctx).getBoolean(KEY_KEEP_SCREEN_ON, true)
        fun getAutoReconnect(ctx: Context) = prefs(ctx).getBoolean(KEY_AUTO_RECONNECT, false)
        fun getShowFps(ctx: Context)       = prefs(ctx).getBoolean(KEY_SHOW_FPS, true)
        fun getUseRootDefault(ctx: Context)= prefs(ctx).getBoolean(KEY_USE_ROOT_DEFAULT, false)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings_rc)

        supportActionBar?.title = "הגדרות"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val prefs = prefs(this)

        // JPEG quality
        val seekQuality   = findViewById<SeekBar>(R.id.seekJpegQuality)
        val tvQualityVal  = findViewById<TextView>(R.id.tvJpegQualityVal)
        seekQuality.progress = prefs.getInt(KEY_JPEG_QUALITY, 40)
        tvQualityVal.text = "${seekQuality.progress}%"
        seekQuality.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                val q = progress.coerceAtLeast(10)
                tvQualityVal.text = "$q%"
                prefs.edit().putInt(KEY_JPEG_QUALITY, q).apply()
            }
            override fun onStartTrackingTouch(sb: SeekBar) {}
            override fun onStopTrackingTouch(sb: SeekBar) {}
        })

        // Frame interval (FPS)
        val spinnerFps = findViewById<Spinner>(R.id.spinnerFps)
        val fpsOptions = arrayOf("2 fps (500ms)", "4 fps (250ms)", "8 fps (125ms)", "15 fps (67ms)")
        val fpsValues  = intArrayOf(500, 250, 125, 67)
        spinnerFps.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, fpsOptions)
        val curInterval = prefs.getInt(KEY_FRAME_INTERVAL, 250)
        spinnerFps.setSelection(fpsValues.indexOfFirst { it == curInterval }.takeIf { it >= 0 } ?: 1)
        spinnerFps.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, pos: Int, id: Long) {
                prefs.edit().putInt(KEY_FRAME_INTERVAL, fpsValues[pos]).apply()
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        // Keep screen on
        val switchScreen = findViewById<Switch>(R.id.switchKeepScreenOn)
        switchScreen.isChecked = prefs.getBoolean(KEY_KEEP_SCREEN_ON, true)
        switchScreen.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean(KEY_KEEP_SCREEN_ON, checked).apply()
        }

        // Auto reconnect
        val switchReconnect = findViewById<Switch>(R.id.switchAutoReconnect)
        switchReconnect.isChecked = prefs.getBoolean(KEY_AUTO_RECONNECT, false)
        switchReconnect.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean(KEY_AUTO_RECONNECT, checked).apply()
        }

        // Show FPS
        val switchFps = findViewById<Switch>(R.id.switchShowFps)
        switchFps.isChecked = prefs.getBoolean(KEY_SHOW_FPS, true)
        switchFps.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean(KEY_SHOW_FPS, checked).apply()
        }

        // Root by default
        val switchRootDefault = findViewById<Switch>(R.id.switchRootDefault)
        switchRootDefault.isChecked = prefs.getBoolean(KEY_USE_ROOT_DEFAULT, false)
        switchRootDefault.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean(KEY_USE_ROOT_DEFAULT, checked).apply()
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) { finish(); return true }
        return super.onOptionsItemSelected(item)
    }
}
