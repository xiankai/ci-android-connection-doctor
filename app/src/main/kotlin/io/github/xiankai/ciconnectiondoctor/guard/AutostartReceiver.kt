package io.github.xiankai.ciconnectiondoctor.guard

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import io.github.xiankai.ciconnectiondoctor.asha.Autostart
import io.github.xiankai.ciconnectiondoctor.asha.AutostartTrigger

class AutostartReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val trigger = when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED -> AutostartTrigger.Boot // stack-reviewer-ignore: the app is not directBootAware, so LOCKED_BOOT_COMPLETED is never delivered to it
            Intent.ACTION_MY_PACKAGE_REPLACED -> AutostartTrigger.AppUpdated
            else -> return
        }
        val store = TargetStore(context)
        val plan = Autostart.plan(
            trigger = trigger,
            processorPicked = store.address != null,
            startAtBoot = store.startAtBoot,
            canConnect = canRunConnectedDeviceService(context),
            streamingPaused = AshaPauseFlag.paused.value,
        )
        if (plan.showPausedNotice) PausedStreamingNotice.afterReboot(context)
        if (plan.startGuard) AshaGuardService.send(context) // stack-reviewer-ignore: BOOT_COMPLETED and MY_PACKAGE_REPLACED are exempt contexts for starting a connectedDevice foreground service; the service runs without Shizuku and only logs until the privileged helper is ready
    }

    private fun canRunConnectedDeviceService(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
}
