package io.github.xiankai.ciconnectiondoctor.status

import androidx.annotation.StringRes
import io.github.xiankai.ciconnectiondoctor.R
import io.github.xiankai.ciconnectiondoctor.asha.Headline
import io.github.xiankai.ciconnectiondoctor.asha.HomeAction
import io.github.xiankai.ciconnectiondoctor.asha.HomeStatus

enum class Tone { Good, Busy, NeedsYou, Neutral }

data class HomeText(@StringRes val title: Int, @StringRes val detail: Int?, @StringRes val action: Int?, val tone: Tone)

fun homeText(status: HomeStatus): HomeText {
    val (title, detail, tone) = when (status.headline) {
        Headline.PickProcessor -> Triple(R.string.headline_pick_processor, R.string.headline_pick_processor_detail, Tone.NeedsYou)
        Headline.HandedOff -> Triple(R.string.headline_handed_off, R.string.headline_handed_off_detail, Tone.Neutral)
        Headline.BluetoothOff -> Triple(R.string.headline_bluetooth_off, R.string.headline_bluetooth_off_detail, Tone.NeedsYou)
        Headline.NeedsConnectTap -> Triple(R.string.headline_needs_connect_tap, R.string.headline_needs_connect_tap_detail, Tone.NeedsYou)
        Headline.HelperStopped -> Triple(R.string.headline_helper_stopped, R.string.headline_helper_stopped_detail, Tone.NeedsYou)
        Headline.SoundOn -> Triple(R.string.headline_sound_on, soundOnDetail(status), Tone.Good)
        Headline.Reconnecting -> Triple(R.string.headline_reconnecting, R.string.headline_reconnecting_detail, Tone.Busy)
        Headline.WaitingForProcessor -> Triple(R.string.headline_waiting, R.string.headline_waiting_detail, Tone.Neutral)
        Headline.SoundOff -> Triple(R.string.headline_sound_off, R.string.headline_sound_off_detail, Tone.NeedsYou)
        Headline.Checking -> Triple(R.string.headline_checking, null, Tone.Neutral)
    }
    return HomeText(title, detail, status.action?.let(::actionLabel), tone)
}

private fun soundOnDetail(status: HomeStatus): Int =
    if (status.action == HomeAction.StartHelper) R.string.headline_sound_on_helper_down_detail else R.string.headline_sound_on_detail

@StringRes
private fun actionLabel(action: HomeAction): Int = when (action) {
    HomeAction.PickProcessor -> R.string.action_pick_processor
    HomeAction.BringSoundBack -> R.string.action_bring_sound_back
    HomeAction.TurnOnBluetooth -> R.string.action_turn_on_bluetooth
    HomeAction.OpenProcessorPage -> R.string.action_open_processor_page
    HomeAction.StartHelper -> R.string.action_start_helper
    HomeAction.FixNow -> R.string.action_fix_now
}
