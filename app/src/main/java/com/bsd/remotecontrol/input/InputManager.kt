package com.bsd.remotecontrol.input

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import com.bsd.remotecontrol.model.AppInfo
import com.topjohnwu.superuser.Shell

object InputManager {

    var isRootAvailable: Boolean = false
        private set

    fun init() {
        Shell.setDefaultBuilder(Shell.Builder.create().setTimeout(10))
        isRootAvailable = try { Shell.getShell().isRoot } catch (e: Exception) { false }
    }

    // -------- TAP --------
    fun tap(x: Int, y: Int, useRoot: Boolean) {
        if (useRoot && isRootAvailable) {
            Shell.cmd("input tap $x $y").exec()
        } else {
            RemoteAccessibilityService.instance?.performTap(x, y)
        }
    }

    // -------- SWIPE --------
    fun swipe(x1: Int, y1: Int, x2: Int, y2: Int, useRoot: Boolean) {
        if (useRoot && isRootAvailable) {
            Shell.cmd("input swipe $x1 $y1 $x2 $y2 300").exec()
        } else {
            RemoteAccessibilityService.instance?.performSwipe(x1, y1, x2, y2)
        }
    }

    // -------- KEY --------
    fun key(keyCode: Int, useRoot: Boolean) {
        if (useRoot && isRootAvailable) {
            Shell.cmd("input keyevent $keyCode").exec()
        } else {
            RemoteAccessibilityService.instance?.injectKeyEvent(keyCode)
        }
    }

    // -------- BACK / HOME / RECENTS --------
    fun back(useRoot: Boolean) {
        if (useRoot && isRootAvailable) Shell.cmd("input keyevent 4").exec()
        else RemoteAccessibilityService.instance?.performBack()
    }

    fun home(useRoot: Boolean) {
        if (useRoot && isRootAvailable) Shell.cmd("input keyevent 3").exec()
        else RemoteAccessibilityService.instance?.performHome()
    }

    fun recents(useRoot: Boolean) {
        if (useRoot && isRootAvailable) Shell.cmd("input keyevent 187").exec()
        else RemoteAccessibilityService.instance?.performRecents()
    }

    // -------- VOLUME --------
    fun volumeUp(useRoot: Boolean) {
        if (useRoot && isRootAvailable) Shell.cmd("input keyevent 24").exec()
        else key(24, false)
    }

    fun volumeDown(useRoot: Boolean) {
        if (useRoot && isRootAvailable) Shell.cmd("input keyevent 25").exec()
        else key(25, false)
    }

    // -------- LAUNCH APP --------
    fun launchApp(packageName: String, useRoot: Boolean): Boolean {
        return if (useRoot && isRootAvailable) {
            Shell.cmd("monkey -p $packageName -c android.intent.category.LAUNCHER 1").exec().isSuccess
        } else {
            try {
                val pm = null // נמסר ב-ScreenShareService
                Shell.cmd("am start -n $packageName/.MainActivity").exec().isSuccess
            } catch (e: Exception) { false }
        }
    }

    fun launchAppWithContext(context: Context, packageName: String): Boolean {
        return try {
            val intent = context.packageManager.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                true
            } else false
        } catch (e: Exception) { false }
    }

    // -------- STOP APP --------
    fun stopApp(packageName: String, useRoot: Boolean): Boolean {
        return if (useRoot && isRootAvailable) {
            Shell.cmd("am force-stop $packageName").exec().isSuccess
        } else {
            false // צריך root
        }
    }

    // -------- SHELL COMMAND --------
    fun runShell(cmd: String, useRoot: Boolean): String {
        return if (useRoot && isRootAvailable) {
            val result = Shell.cmd(cmd).exec()
            (result.out + result.err).joinToString("\n")
        } else {
            try {
                val proc = Runtime.getRuntime().exec(cmd)
                proc.inputStream.bufferedReader().readText()
            } catch (e: Exception) {
                "Error: ${e.message}"
            }
        }
    }

    // -------- APP LIST --------
    fun getInstalledApps(context: Context): List<AppInfo> {
        val pm = context.packageManager
        val flags = PackageManager.GET_META_DATA
        return pm.getInstalledApplications(flags)
            .filter { (it.flags and ApplicationInfo.FLAG_SYSTEM) == 0 }  // רק אפליקציות משתמש
            .map { info ->
                AppInfo(
                    name = pm.getApplicationLabel(info).toString(),
                    packageName = info.packageName
                )
            }
            .sortedBy { it.name.lowercase() }
    }

    // -------- SCREENSHOT (root) --------
    fun takeScreenshotRoot(): ByteArray? {
        return try {
            val result = Shell.cmd("screencap -p /sdcard/.btremote_tmp.png && echo OK").exec()
            if (!result.isSuccess) return null
            val proc = Runtime.getRuntime().exec(arrayOf("su", "-c", "cat /sdcard/.btremote_tmp.png"))
            val bytes = proc.inputStream.readBytes()
            Shell.cmd("rm /sdcard/.btremote_tmp.png").exec()
            bytes
        } catch (e: Exception) { null }
    }
}
