package io.github.xiankai.ciconnectiondoctor.guide

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import io.github.xiankai.ciconnectiondoctor.asha.GuideStep

private const val ACTION_DEVICE_DETAIL = "com.android.settings.BLUETOOTH_DEVICE_DETAIL_SETTINGS"
private const val EXTRA_DEVICE_ADDRESS = "device_address"
private const val EXTRA_FRAGMENT_ARGS = ":settings:show_fragment_args"
private const val ACTION_HEARING_DEVICES = "android.settings.HEARING_DEVICES_SETTINGS"

const val RESTART_ROUTINE = "To start Shizuku: join any Wi-Fi, turn on wireless debugging, tap Start in Shizuku, then Refresh in this app."
const val SHOW_STEPS = "Show steps"

data class StepText(val title: String, val text: String, val action: String)

fun stepText(step: GuideStep): StepText = when (step) {
    GuideStep.ConnectStreaming -> StepText(
        "Streaming didn't reconnect",
        "Your processor is linked but streaming isn't reconnecting. Tap Connect on its page.",
        "Open processor page",
    )
    GuideStep.StartShizuku -> StepText(
        "Streaming keeps failing",
        "Android keeps connecting before Nucleus Smart, and only the guard can stop that. Connect in Settings and reopening Nucleus Smart won't. $RESTART_ROUTINE",
        SHOW_STEPS,
    )
    GuideStep.ResumeStreaming -> StepText(
        "Streaming is switched off",
        "The guard paused streaming and couldn't switch it back on. Settings has no switch for it, and Connect won't bring it back. $RESTART_ROUTINE",
        SHOW_STEPS,
    )
}

fun stepTargets(step: GuideStep, address: String, setupGuide: Intent): List<Intent> = when (step) {
    GuideStep.ConnectStreaming -> listOf(
        Intent(ACTION_DEVICE_DETAIL)
            .putExtra(EXTRA_DEVICE_ADDRESS, address)
            .putExtra(EXTRA_FRAGMENT_ARGS, Bundle().apply { putString(EXTRA_DEVICE_ADDRESS, address) }),
        Intent(ACTION_HEARING_DEVICES),
        Intent(Settings.ACTION_BLUETOOTH_SETTINGS),
    )
    GuideStep.StartShizuku, GuideStep.ResumeStreaming -> listOf(setupGuide)
}
