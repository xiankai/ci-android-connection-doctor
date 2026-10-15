package io.github.xiankai.ciconnectiondoctor.guard

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import androidx.core.app.NotificationCompat
import io.github.xiankai.ciconnectiondoctor.asha.GuideStep
import io.github.xiankai.ciconnectiondoctor.guide.GuideStepActivity
import io.github.xiankai.ciconnectiondoctor.guide.RESTART_ROUTINE
import io.github.xiankai.ciconnectiondoctor.guide.SHOW_STEPS

object PausedStreamingNotice {

    private const val NOTIFICATION_ID = 3

    fun afterReboot(context: Context) = show(
        context,
        "Streaming is switched off",
        "The phone restarted while the guard had streaming paused. Settings has no switch for it, and Connect won't bring it back. $RESTART_ROUTINE Then start the guard.",
    )

    fun afterStop(context: Context) = show(
        context,
        "Streaming may still be off",
        "Shizuku isn't running, so the guard stopped without switching streaming back on. If streaming doesn't come back, the guard needs Shizuku to switch it on. $RESTART_ROUTINE Then start the guard.",
    )

    fun cancel(context: Context) = context.getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)

    private fun show(context: Context, title: String, text: String) {
        AshaGuardService.createChannels(context)
        val open = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID,
            GuideStepActivity.intent(context, GuideStep.ResumeStreaming),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, AshaGuardService.GUIDE_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setContentIntent(open)
            .setAutoCancel(true)
            .addAction(NotificationCompat.Action(0, SHOW_STEPS, open))
            .build()
        context.getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification)
    }
}
