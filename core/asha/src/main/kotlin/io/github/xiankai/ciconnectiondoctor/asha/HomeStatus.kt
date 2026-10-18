package io.github.xiankai.ciconnectiondoctor.asha

enum class Headline {
    PickProcessor,
    HandedOff,
    BluetoothOff,
    NeedsConnectTap,
    HelperStopped,
    SoundOn,
    Reconnecting,
    WaitingForProcessor,
    SoundOff,
    Checking,
}

enum class HomeAction { PickProcessor, BringSoundBack, TurnOnBluetooth, OpenProcessorPage, StartHelper, FixNow }

data class HomeStatus(val headline: Headline, val action: HomeAction? = null)

data class HomeInputs(
    val processorPicked: Boolean,
    val helperReady: Boolean,
    val guardRunning: Boolean = false,
    val mode: Mode = Mode.Auto,
    val phase: Phase? = null,
    val guardSnapshot: LinkSnapshot? = null,
    val publicLink: PublicLinkState? = null,
    val guideStep: GuideStep? = null,
    val pausedByGuard: Boolean = false,
)

object Home {

    fun status(inputs: HomeInputs): HomeStatus = with(inputs) {
        val startHelperIfNeeded = HomeAction.StartHelper.takeUnless { helperReady }
        when {
            !processorPicked -> HomeStatus(Headline.PickProcessor, HomeAction.PickProcessor)
            mode == Mode.HandedOff -> HomeStatus(Headline.HandedOff, startHelperIfNeeded ?: HomeAction.BringSoundBack)
            bluetoothOff -> HomeStatus(Headline.BluetoothOff, HomeAction.TurnOnBluetooth)
            guideStep == GuideStep.ConnectStreaming -> HomeStatus(Headline.NeedsConnectTap, HomeAction.OpenProcessorPage)
            guideStep != null -> HomeStatus(Headline.HelperStopped, HomeAction.StartHelper)
            soundOn -> HomeStatus(Headline.SoundOn, startHelperIfNeeded)
            !helperReady && (guardRunning || pausedByGuard) -> HomeStatus(Headline.HelperStopped, HomeAction.StartHelper)
            linkUp == false -> HomeStatus(Headline.WaitingForProcessor, startHelperIfNeeded)
            guardHasReading -> HomeStatus(Headline.Reconnecting)
            linkUp == true -> HomeStatus(Headline.SoundOff, startHelperIfNeeded ?: HomeAction.FixNow)
            else -> HomeStatus(Headline.Checking)
        }
    }

    private val HomeInputs.guardHasReading: Boolean
        get() = guardRunning && phase != null && guardSnapshot != null

    private val HomeInputs.bluetoothOff: Boolean
        get() = publicLink?.adapterOn == false || guardHasReading && guardSnapshot?.adapterOn == false

    private val HomeInputs.soundOn: Boolean
        get() = if (guardHasReading) phase is Phase.Healthy else publicLink?.asha == AshaState.Connected

    private val HomeInputs.linkUp: Boolean?
        get() = if (guardHasReading) guardSnapshot?.linkUp else publicLink?.linkUp
}
