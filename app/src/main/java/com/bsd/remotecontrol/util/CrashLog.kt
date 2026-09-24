package com.bsd.remotecontrol.util

import android.content.Context
import android.os.Build
import android.util.Log
import java.io.File

/**
 * Saves the stack trace of an uncaught exception to a file so the next launch can show
 * what went wrong (and the user can copy it), instead of the app just closing silently.
 */
object CrashLog {
    private const val FILE = "last_crash.txt"

    fun install(ctx: Context) {
        val app = ctx.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            try {
                val text = buildString {
                    append("Android ").append(Build.VERSION.RELEASE)
                    append(" (API ").append(Build.VERSION.SDK_INT).append(") · ")
                    append(Build.MANUFACTURER).append(' ').append(Build.MODEL)
                    append("\nThread: ").append(t.name).append("\n\n")
                    append(Log.getStackTraceString(e))
                }
                File(app.filesDir, FILE).writeText(text)
            } catch (_: Throwable) {}
            previous?.uncaughtException(t, e)
        }
    }

    fun read(ctx: Context): String? =
        File(ctx.filesDir, FILE).takeIf { it.exists() }?.let { runCatching { it.readText() }.getOrNull() }

    fun clear(ctx: Context) { runCatching { File(ctx.filesDir, FILE).delete() } }
}
