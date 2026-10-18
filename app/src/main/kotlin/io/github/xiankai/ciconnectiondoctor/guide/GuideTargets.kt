package io.github.xiankai.ciconnectiondoctor.guide

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import io.github.xiankai.ciconnectiondoctor.asha.GuideStep

private const val ACTION_DEVICE_DETAIL = "com.android.settings.BLUETOOTH_DEVICE_DETAIL_SETTINGS"
private const val EXTRA_DEVICE_ADDRESS = "device_address"
private const val EXTRA_FRAGMENT_ARGS = ":settings:show_fragment_args"
private const val ACTION_HEARING_DEVICES = "android.settings.HEARING_DEVICES_SETTINGS"

const val SHOW_STEPS = "Show me how"

data class StepText(val title: String, val text: String, val action: String)

fun stepText(step: GuideStep): StepText = when (step) {
    GuideStep.ConnectStreaming -> StepText(
        "Phone sound needs one tap",
        "Open your processor's page and tap Connect.",
        "Open processor page",
    )
    GuideStep.StartShizuku -> StepText(
        "Phone sound keeps dropping",
        "Start the helper app so this app can bring it back.",
        SHOW_STEPS,
    )
    GuideStep.ResumeStreaming -> StepText(
        "Phone sound is off",
        "Start the helper app to switch it back on.",
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
