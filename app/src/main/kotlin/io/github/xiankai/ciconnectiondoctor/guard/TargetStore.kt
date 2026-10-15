package io.github.xiankai.ciconnectiondoctor.guard

import android.content.Context

class TargetStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences("guard", Context.MODE_PRIVATE)

    var address: String?
        get() = prefs.getString(KEY_ADDRESS, null)
        set(value) = prefs.edit().putString(KEY_ADDRESS, value).apply()

    var startAtBoot: Boolean
        get() = prefs.getBoolean(KEY_START_AT_BOOT, false)
        set(value) = prefs.edit().putBoolean(KEY_START_AT_BOOT, value).apply()

    var startOnConnect: Boolean
        get() = prefs.getBoolean(KEY_START_ON_CONNECT, false)
        set(value) = prefs.edit().putBoolean(KEY_START_ON_CONNECT, value).apply()

    private companion object {
        const val KEY_ADDRESS = "target_address"
        const val KEY_START_AT_BOOT = "start_at_boot"
        const val KEY_START_ON_CONNECT = "start_on_connect"
    }
}
