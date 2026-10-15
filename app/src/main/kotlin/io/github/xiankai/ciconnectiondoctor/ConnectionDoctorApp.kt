package io.github.xiankai.ciconnectiondoctor

import android.app.Application
import io.github.xiankai.ciconnectiondoctor.guard.AshaPauseFlag
import io.github.xiankai.ciconnectiondoctor.privileged.PrivilegedBridge

class ConnectionDoctorApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AshaPauseFlag.init(this)
        PrivilegedBridge.init()
    }
}
