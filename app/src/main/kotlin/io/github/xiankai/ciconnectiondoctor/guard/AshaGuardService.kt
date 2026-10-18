package io.github.xiankai.ciconnectiondoctor.guard

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import io.github.xiankai.ciconnectiondoctor.MainActivity
import io.github.xiankai.ciconnectiondoctor.R
import io.github.xiankai.ciconnectiondoctor.asha.GuideStep
import io.github.xiankai.ciconnectiondoctor.guide.GuideStepActivity
import io.github.xiankai.ciconnectiondoctor.guide.ManualGuideRunner
import io.github.xiankai.ciconnectiondoctor.guide.PublicLinkReader
import io.github.xiankai.ciconnectiondoctor.guide.stepText
import io.github.xiankai.ciconnectiondoctor.privileged.PrivilegedBridge
import io.github.xiankai.ciconnectiondoctor.status.currentStatus
import io.github.xiankai.ciconnectiondoctor.status.homeText
import io.github.xiankai.ciconnectiondoctor.asha.Mode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class AshaGuardService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var shownStep: GuideStep? = null

    private val shutdownReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val address = TargetStore(context).address ?: return
            val pending = goAsync()
            Guard.allowAshaBeforeShutdown(PrivilegedBridge, address).invokeOnCompletion { pending.finish() }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createChannels(this)
        PausedStreamingNotice.cancel(this)
        ContextCompat.registerReceiver(
            this,
            shutdownReceiver,
            IntentFilter(Intent.ACTION_SHUTDOWN),
            ContextCompat.RECEIVER_EXPORTED, // stack-reviewer-ignore: ACTION_SHUTDOWN is a protected broadcast only the system can send, and it is delivered to runtime-registered receivers only
        )
        startForeground(NOTIFICATION_ID, buildNotification(Guard.state.value, PrivilegedBridge.status.value, ManualGuideRunner.step.value), ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        scope.launch {
            combine(Guard.state, PrivilegedBridge.status, ManualGuideRunner.step, AshaPauseFlag.paused) { state, status, step, _ -> Triple(state, status, step) }
                .distinctUntilChangedBy { (state, status, step) -> listOf(notificationStatus(state, status), state.mode, status, step) }
                .collect { (state, status, step) ->
                    getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification(state, status, step))
                }
        }
        scope.launch {
            combine(Guard.state.map { it.running }, PrivilegedBridge.status) { running, status ->
                running && status in GUIDE_STATUSES
            }
                .distinctUntilChanged()
                .collect(::setGuideActive)
        }
        scope.launch {
            Guard.state.map { it.running && it.done }
                .distinctUntilChanged()
                .filter { it }
                .collect { TargetStore(this@AshaGuardService).address?.let(::stopGuard) ?: stopSelf() }
        }
    }

    private fun stopGuard(address: String) = Guard.stop(PrivilegedBridge, address) { leftPaused ->
        if (leftPaused) PausedStreamingNotice.afterStop(this)
        stopSelf()
    }

    private fun setGuideActive(active: Boolean) {
        val address = TargetStore(this).address
        if (active && address != null) {
            ManualGuideRunner.start(PublicLinkReader(this, address))
        } else {
            ManualGuideRunner.stop()
        }
    }

    private fun guideNotification(step: GuideStep, alert: Boolean): Notification {
        val text = stepText(step)
        val open = PendingIntent.getActivity(
            this,
            step.ordinal,
            GuideStepActivity.intent(this, step),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, GUIDE_CHANNEL_ID)
            .setContentTitle(text.title)
            .setContentText(text.text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text.text))
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setOngoing(true)
            .setOnlyAlertOnce(!alert)
            .setContentIntent(open)
            .addAction(NotificationCompat.Action(0, text.action, open))
            .addAction(NotificationCompat.Action(0, "Not now", actionIntent(ACTION_DISMISS_GUIDE)))
            .build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val address = TargetStore(this).address
        if (address == null) {
            stopSelf() // stack-reviewer-ignore: Start is disabled in the UI until a processor is selected; a sticky restart without one stops quietly by design
            return START_NOT_STICKY
        }
        when (intent?.action) {
            ACTION_HAND_OFF -> {
                startGuard(address)
                Guard.handOff(PrivilegedBridge, address)
            }
            ACTION_BACK_TO_PHONE -> {
                startGuard(address)
                Guard.backToPhone(PrivilegedBridge, address)
            }
            ACTION_STOP -> stopGuard(address)
            ACTION_DISMISS_GUIDE -> ManualGuideRunner.dismiss()
            else -> startGuard(address)
        }
        return START_STICKY
    }

    private fun startGuard(address: String) {
        PrivilegedBridge.refresh()
        Guard.start(PrivilegedBridge, address)
    }

    override fun onDestroy() {
        unregisterReceiver(shutdownReceiver)
        scope.cancel()
        ManualGuideRunner.stop()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun notificationStatus(state: GuardState, privileged: PrivilegedBridge.Status) =
        currentStatus(state, privileged, processorPicked = true, guideStep = ManualGuideRunner.step.value, pausedByGuard = AshaPauseFlag.paused.value)

    private fun buildNotification(state: GuardState, privileged: PrivilegedBridge.Status, step: GuideStep?): Notification {
        val newStep = step != shownStep
        shownStep = step
        return step?.let { guideNotification(it, alert = newStep) } ?: statusNotification(state, privileged)
    }

    private fun statusNotification(state: GuardState, privileged: PrivilegedBridge.Status): Notification {
        val text = homeText(notificationStatus(state, privileged))
        val modeAction = if (state.mode == Mode.HandedOff) {
            NotificationCompat.Action(0, getString(R.string.action_bring_sound_back), actionIntent(ACTION_BACK_TO_PHONE))
        } else {
            NotificationCompat.Action(0, getString(R.string.hand_off), actionIntent(ACTION_HAND_OFF))
        }
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val helperReady = privileged == PrivilegedBridge.Status.Ready
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(text.title))
            .setContentText(text.detail?.let(::getString))
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openApp)
            .apply { if (helperReady) addAction(modeAction) }
            .build()
    }

    private fun actionIntent(action: String): PendingIntent = PendingIntent.getService(
        this,
        action.hashCode(),
        Intent(this, AshaGuardService::class.java).setAction(action),
        PendingIntent.FLAG_IMMUTABLE,
    )

    companion object {
        private const val CHANNEL_ID = "asha_guard"
        private const val NOTIFICATION_ID = 1
        private val GUIDE_STATUSES = setOf(PrivilegedBridge.Status.ShizukuNotRunning, PrivilegedBridge.Status.PermissionRequired)
        const val GUIDE_CHANNEL_ID = "recovery_steps"
        private const val ACTION_DISMISS_GUIDE = "io.github.xiankai.ciconnectiondoctor.DISMISS_GUIDE"
        const val ACTION_HAND_OFF = "io.github.xiankai.ciconnectiondoctor.HAND_OFF"
        const val ACTION_BACK_TO_PHONE = "io.github.xiankai.ciconnectiondoctor.BACK_TO_PHONE"
        const val ACTION_STOP = "io.github.xiankai.ciconnectiondoctor.STOP"

        fun createChannels(context: Context) {
            val guard = NotificationChannel(CHANNEL_ID, context.getString(R.string.channel_fixing), NotificationManager.IMPORTANCE_LOW)
            val guide = NotificationChannel(GUIDE_CHANNEL_ID, context.getString(R.string.channel_needs_you), NotificationManager.IMPORTANCE_HIGH)
            context.getSystemService(NotificationManager::class.java).createNotificationChannels(listOf(guard, guide))
        }

        fun send(context: Context, action: String? = null) {
            val intent = Intent(context, AshaGuardService::class.java).setAction(action)
            context.startForegroundService(intent)
        }
    }
}
