package io.github.xiankai.ciconnectiondoctor.guard

import android.Manifest
import android.app.ForegroundServiceStartNotAllowedException
import android.companion.CompanionDeviceService
import android.content.pm.PackageManager

class ProcessorPresenceService : CompanionDeviceService() {

    @Suppress("OVERRIDE_DEPRECATION")
    override fun onDeviceAppeared(address: String) {
        val store = TargetStore(this)
        if (!store.startOnConnect || !address.equals(store.address, ignoreCase = true)) return
        if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) return
        if (Guard.state.value.running) return
        try {
            AshaGuardService.send(this)
        } catch (e: ForegroundServiceStartNotAllowedException) {
            Guard.note("Processor connected, but Android refused to start the guard: ${e.message}")
        }
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun onDeviceDisappeared(address: String) = Unit
}
