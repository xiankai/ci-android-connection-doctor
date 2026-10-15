package io.github.xiankai.ciconnectiondoctor.guard

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return // stack-reviewer-ignore: the app is not directBootAware, so LOCKED_BOOT_COMPLETED is never delivered to it
        val store = TargetStore(context)
        if (AshaPauseFlag.paused.value && store.address != null) PausedStreamingNotice.afterReboot(context)
        if (!store.startAtBoot || store.address == null || !canRunConnectedDeviceService(context)) return
        AshaGuardService.send(context) // stack-reviewer-ignore: BOOT_COMPLETED is an exempt context for starting a connectedDevice foreground service; the service runs without Shizuku and only logs until the privileged helper is ready
    }

    private fun canRunConnectedDeviceService(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
}
