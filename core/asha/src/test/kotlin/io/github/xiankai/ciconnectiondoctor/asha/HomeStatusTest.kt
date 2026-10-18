package io.github.xiankai.ciconnectiondoctor.asha

import org.junit.Assert.assertEquals
import org.junit.Test

class HomeStatusTest {

    private val ready = HomeInputs(processorPicked = true, helperReady = true)
    private val helperDown = ready.copy(helperReady = false)

    private val streaming = PublicLinkState(linkUp = true, asha = AshaState.Connected)
    private val linkedNoSound = PublicLinkState(linkUp = true, asha = AshaState.Disconnected)
    private val processorAway = PublicLinkState(linkUp = false, asha = AshaState.Disconnected)
    private val bluetoothOff = PublicLinkState(linkUp = false, asha = AshaState.Disconnected, adapterOn = false)

    private fun guarding(phase: Phase, linkUp: Boolean, asha: AshaState = AshaState.Disconnected) = ready.copy(
        guardRunning = true,
        phase = phase,
        guardSnapshot = LinkSnapshot(linkUp = linkUp, asha = asha, ashaAllowed = true),
    )

    private fun assertStatus(expected: HomeStatus, inputs: HomeInputs) = assertEquals(expected, Home.status(inputs))

    @Test
    fun noProcessorAsksToPickOne() {
        assertStatus(HomeStatus(Headline.PickProcessor, HomeAction.PickProcessor), HomeInputs(processorPicked = false, helperReady = true))
    }

    @Test
    fun streamingAtRestIsSoundOnWithNothingToDo() {
        assertStatus(HomeStatus(Headline.SoundOn), ready.copy(publicLink = streaming))
    }

    @Test
    fun helperDownWhileSoundWorksKeepsSoundOnAsHeadline() {
        assertStatus(HomeStatus(Headline.SoundOn, HomeAction.StartHelper), helperDown.copy(publicLink = streaming))
    }

    @Test
    fun processorAwayIsWaitingWithNothingToDo() {
        assertStatus(HomeStatus(Headline.WaitingForProcessor), ready.copy(publicLink = processorAway))
    }

    @Test
    fun processorAwayWithHelperDownOffersToStartIt() {
        assertStatus(HomeStatus(Headline.WaitingForProcessor, HomeAction.StartHelper), helperDown.copy(publicLink = processorAway))
    }

    @Test
    fun linkedWithoutSoundAtRestOffersToFixNow() {
        assertStatus(HomeStatus(Headline.SoundOff, HomeAction.FixNow), ready.copy(publicLink = linkedNoSound))
    }

    @Test
    fun guardRecoveringWithLinkUpIsReconnecting() {
        assertStatus(HomeStatus(Headline.Reconnecting), guarding(Phase.Paused(0, 0), linkUp = true))
    }

    @Test
    fun guardRecoveringWithLinkDownIsWaitingNotReconnecting() {
        assertStatus(HomeStatus(Headline.WaitingForProcessor), guarding(Phase.Watching(0), linkUp = false))
    }

    @Test
    fun guardHealthyIsSoundOn() {
        assertStatus(HomeStatus(Headline.SoundOn), guarding(Phase.Healthy(0), linkUp = true, asha = AshaState.Connected))
    }

    @Test
    fun guardReadingWinsOverPublicLink() {
        assertStatus(HomeStatus(Headline.Reconnecting), guarding(Phase.Verifying(0), linkUp = true).copy(publicLink = streaming))
    }

    @Test
    fun guardRunningWithoutHelperAndNoSoundAsksToStartHelper() {
        assertStatus(
            HomeStatus(Headline.HelperStopped, HomeAction.StartHelper),
            helperDown.copy(guardRunning = true, publicLink = linkedNoSound),
        )
    }

    @Test
    fun soundLeftPausedWithHelperDownAsksToStartHelper() {
        assertStatus(
            HomeStatus(Headline.HelperStopped, HomeAction.StartHelper),
            helperDown.copy(pausedByGuard = true, publicLink = linkedNoSound),
        )
    }

    @Test
    fun guideStepsMapToTheirOneAction() {
        assertStatus(
            HomeStatus(Headline.NeedsConnectTap, HomeAction.OpenProcessorPage),
            helperDown.copy(guideStep = GuideStep.ConnectStreaming, publicLink = linkedNoSound),
        )
        assertStatus(
            HomeStatus(Headline.HelperStopped, HomeAction.StartHelper),
            helperDown.copy(guideStep = GuideStep.StartShizuku, publicLink = linkedNoSound),
        )
        assertStatus(
            HomeStatus(Headline.HelperStopped, HomeAction.StartHelper),
            helperDown.copy(guideStep = GuideStep.ResumeStreaming, publicLink = linkedNoSound),
        )
    }

    @Test
    fun handedOffOffersToBringSoundBack() {
        assertStatus(HomeStatus(Headline.HandedOff, HomeAction.BringSoundBack), ready.copy(mode = Mode.HandedOff, publicLink = processorAway))
    }

    @Test
    fun handedOffWithHelperDownAsksToStartHelperFirst() {
        assertStatus(HomeStatus(Headline.HandedOff, HomeAction.StartHelper), helperDown.copy(mode = Mode.HandedOff))
    }

    @Test
    fun bluetoothOffAsksToTurnItOn() {
        assertStatus(HomeStatus(Headline.BluetoothOff, HomeAction.TurnOnBluetooth), ready.copy(publicLink = bluetoothOff))
    }

    @Test
    fun staleGuardSnapshotAfterStopIsIgnored() {
        val stopped = guarding(Phase.Healthy(0), linkUp = true, asha = AshaState.Connected).copy(guardRunning = false)
        assertStatus(HomeStatus(Headline.WaitingForProcessor), stopped.copy(publicLink = processorAway))
    }

    @Test
    fun nothingKnownYetIsChecking() {
        assertStatus(HomeStatus(Headline.Checking), ready)
    }
}
