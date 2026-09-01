package com.bsd.remotecontrol.model

import com.google.gson.Gson

data class RemoteCommand(
    val type: String,      // TOUCH, SWIPE, KEY, APP_LIST, APP_LAUNCH, SHELL, SCREENSHOT_REQUEST
    val x: Int = 0,
    val y: Int = 0,
    val x2: Int = 0,
    val y2: Int = 0,
    val keyCode: Int = 0,
    val packageName: String = "",
    val shellCmd: String = "",
    val useRoot: Boolean = false
) {
    fun toJson(): String = Gson().toJson(this)
    companion object {
        fun fromJson(json: String): RemoteCommand = Gson().fromJson(json, RemoteCommand::class.java)
    }
}

data class RemoteResponse(
    val success: Boolean,
    val error: String = "",
    // לרשימת אפליקציות
    val apps: List<AppInfo> = emptyList(),
    // לתוצאת shell
    val shellOutput: String = "",
    // לScreenshot: frameSize נשלח קודם, אחר כך ה-JPEG bytes
    val frameSize: Int = 0,
    val isRoot: Boolean = false,
    val screenWidth: Int = 0,
    val screenHeight: Int = 0
) {
    fun toJson(): String = Gson().toJson(this)
    companion object {
        fun fromJson(json: String): RemoteResponse = Gson().fromJson(json, RemoteResponse::class.java)
    }
}

data class AppInfo(
    val name: String,
    val packageName: String
) {
    fun toJson(): String = Gson().toJson(this)
}

object CommandTypes {
    const val TOUCH            = "TOUCH"
    const val SWIPE            = "SWIPE"
    const val KEY              = "KEY"
    const val APP_LIST         = "APP_LIST"
    const val APP_LAUNCH       = "APP_LAUNCH"
    const val APP_STOP         = "APP_STOP"
    const val SHELL            = "SHELL"
    const val SCREENSHOT       = "SCREENSHOT"
    const val STREAM_START     = "STREAM_START"
    const val STREAM_STOP      = "STREAM_STOP"
    const val ROOT_STATUS      = "ROOT_STATUS"
    const val SCREEN_INFO      = "SCREEN_INFO"
    const val BACK             = "BACK"
    const val HOME             = "HOME"
    const val RECENTS          = "RECENTS"
    const val VOLUME_UP        = "VOLUME_UP"
    const val VOLUME_DOWN      = "VOLUME_DOWN"
    const val POWER            = "POWER"
}
