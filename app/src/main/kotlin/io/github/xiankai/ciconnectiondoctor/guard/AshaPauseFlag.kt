package io.github.xiankai.ciconnectiondoctor.guard

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object AshaPauseFlag {

    private const val KEY_PAUSED = "asha_paused_by_guard"

    private lateinit var prefs: SharedPreferences

    private val _paused = MutableStateFlow(false)
    val paused: StateFlow<Boolean> = _paused.asStateFlow()

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences("guard", Context.MODE_PRIVATE)
        _paused.value = prefs.getBoolean(KEY_PAUSED, false)
    }

    @Synchronized
    fun record(paused: Boolean) { // stack-reviewer-ignore: only this app's helper can allow ASHA again (Settings' Connect skips a FORBIDDEN profile); its shutdown-hook resume is cleared by the guide on any ASHA attempt or by the guard's next privileged snapshot
        if (_paused.value == paused) return
        prefs.edit().putBoolean(KEY_PAUSED, paused).commit()
        _paused.value = paused
    }
}
