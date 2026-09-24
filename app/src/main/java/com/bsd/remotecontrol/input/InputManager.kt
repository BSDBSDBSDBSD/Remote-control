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
        else RemoteAccessibilityService.instance?.injectKeyEvent(24)
    }

    fun volumeDown(useRoot: Boolean) {
        if (useRoot && isRootAvailable) Shell.cmd("input keyevent 25").exec()
        else RemoteAccessibilityService.instance?.injectKeyEvent(25)
    }

    // -------- LAUNCH APP --------
    // Use launchAppWithContext (requires Context) — this root-only variant is kept as fallback
    fun launchApp(packageName: String): Boolean {
        return if (isRootAvailable) {
            Shell.cmd("monkey -p $packageName -c android.intent.category.LAUNCHER 1").exec().isSuccess
        } else false
    }

    fun launchAppWithContext(context: Context, packageName: String): Boolean {
        return try {
            val intent = context.packageManager.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                true
            } else {
                // Fallback to root am start
                if (isRootAvailable) {
                    Shell.cmd("monkey -p $packageName -c android.intent.category.LAUNCHER 1").exec().isSuccess
                } else false
            }
        } catch (e: Exception) { false }
    }

    // -------- STOP APP --------
    fun stopApp(packageName: String, useRoot: Boolean): Boolean {
        return if (useRoot && isRootAvailable) {
            Shell.cmd("am force-stop $packageName").exec().isSuccess
        } else false
    }

    // -------- SHELL COMMAND --------
    fun runShell(cmd: String, useRoot: Boolean): String {
        return if (useRoot && isRootAvailable) {
            val result = Shell.cmd(cmd).exec()
            (result.out + result.err).joinToString("\n")
        } else {
            try {
                val proc = Runtime.getRuntime().exec(cmd.split(" ").toTypedArray())
                proc.inputStream.bufferedReader().readText().ifBlank {
                    proc.errorStream.bufferedReader().readText()
                }
            } catch (e: Exception) {
                "Error: ${e.message}"
            }
        }
    }

    // -------- APP LIST --------
    fun getInstalledApps(context: Context): List<AppInfo> {
        return try {
            val pm = context.packageManager
            pm.getInstalledApplications(PackageManager.GET_META_DATA)
                .filter { (it.flags and ApplicationInfo.FLAG_SYSTEM) == 0 }
                .map { info ->
                    AppInfo(
                        name = pm.getApplicationLabel(info).toString(),
                        packageName = info.packageName
                    )
                }
                .sortedBy { it.name.lowercase() }
        } catch (e: Exception) { emptyList() }
    }

    // -------- SCREENSHOT (root) — fixed: use libsu only --------
    fun takeScreenshotRoot(): ByteArray? {
        if (!isRootAvailable) return null
        return try {
            val tmpPath = "/data/local/tmp/.btremote_cap.png"
            val result = Shell.cmd("screencap -p $tmpPath").exec()
            if (!result.isSuccess) return null
            val catResult = Shell.cmd("cat $tmpPath").exec()
            Shell.cmd("rm -f $tmpPath").exec()
            if (catResult.isSuccess) {
                // cat output via libsu gives lines — reconstruct bytes via direct file read
                readFileRoot(tmpPath)
            } else null
        } catch (e: Exception) { null }
    }

    private fun readFileRoot(path: String): ByteArray? {
        return try {
            // Use base64 encode/decode to safely transfer binary via shell
            val result = Shell.cmd("base64 $path 2>/dev/null || base64 -i $path").exec()
            if (!result.isSuccess || result.out.isEmpty()) return null
            val b64 = result.out.joinToString("")
            android.util.Base64.decode(b64, android.util.Base64.DEFAULT)
        } catch (e: Exception) { null }
    }

    // -------- CLIPBOARD --------
    fun getClipboard(context: Context): String {
        return try {
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            cm.primaryClip?.getItemAt(0)?.text?.toString() ?: ""
        } catch (e: Exception) { "" }
    }

    fun setClipboard(context: Context, text: String) {
        try {
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            cm.setPrimaryClip(android.content.ClipData.newPlainText("remote", text))
        } catch (e: Exception) { }
    }
}
