package io.github.xiankai.ciconnectiondoctor

import android.content.Context
import android.content.Intent
import android.os.Process

object ProcessRestart {
    fun restart(context: Context, showSetup: Boolean) {
        context.startActivity(
            MainActivity.intent(context, showSetup)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
        )
        Process.killProcess(Process.myPid())
    }
}
