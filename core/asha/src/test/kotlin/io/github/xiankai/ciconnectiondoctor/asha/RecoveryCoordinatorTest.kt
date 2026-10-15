package io.github.xiankai.ciconnectiondoctor.asha

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecoveryCoordinatorTest {

    private class SimulatedDevice(
        var linkUp: Boolean,
        var asha: AshaState = AshaState.Disconnected,
        var adapterOn: Boolean = true,
    ) {
        var allowed = true

        fun snapshot() = LinkSnapshot(linkUp, asha, allowed, adapterOn)

        fun apply(commands: List<Command>) = commands.forEach {
            if (it is Command.SetAshaAllowed) allowed = it.allowed
        }
    }

    private fun RecoveryCoordinator.simulate(device: SimulatedDevice, fromMs: Long, toMs: Long, stepMs: Long = 250): List<Command> {
        val commands = mutableListOf<Command>()
        var now = fromMs
        while (now <= toMs) {
            val issued = step(device.snapshot(), now).commands
            device.apply(issued)
            commands += issued
            now += stepMs
        }
        return commands
    }

    private fun healthyConnected() = SimulatedDevice(linkUp = true, asha = AshaState.Connected)

    private fun RecoveryCoordinator.confirmedHealthy(device: SimulatedDevice): Long {
        simulate(device, 0, 11_000)
        assertEquals(Phase.Healthy(0, confirmed = true), phase)
        return 11_250
    }

    @Test
    fun connectedAtStartIsHealthyAndIssuesNothing() {
        val coordinator = RecoveryCoordinator()
        assertEquals(emptyList<Command>(), coordinator.simulate(healthyConnected(), 0, 30_000))
        assertTrue(coordinator.phase is Phase.Healthy)
    }

    @Test
    fun crashLoopLinkDropPausesAshaImmediately() {
        val coordinator = RecoveryCoordinator()
        val device = healthyConnected()
        val t = coordinator.confirmedHealthy(device)
        with(device) { linkUp = false; asha = AshaState.Disconnected }
        assertEquals(listOf(Command.SetAshaAllowed(false)), coordinator.simulate(device, t, t))
        assertTrue(coordinator.phase is Phase.Paused)
    }

    @Test
    fun confirmedHealthyDropWaitsForTheCochlearAppWithoutRestartingIt() {
        val coordinator = RecoveryCoordinator()
        val device = healthyConnected()
        val t = coordinator.confirmedHealthy(device)
        with(device) { linkUp = false; asha = AshaState.Disconnected }
        assertEquals(listOf(Command.SetAshaAllowed(false)), coordinator.simulate(device, t, t + 2_000))

        device.linkUp = true
        assertEquals(emptyList<Command>(), coordinator.simulate(device, t + 2_250, t + 7_000))
        val resume = coordinator.simulate(device, t + 7_250, t + 7_250)
        assertEquals(listOf(Command.SetAshaAllowed(true), Command.ConnectAsha), resume)
    }

    @Test
    fun recoveryIsCountedOnlyAfterAshaHoldsForTheConfirmWindow() {
        val coordinator = RecoveryCoordinator()
        val device = healthyConnected()
        val t = coordinator.confirmedHealthy(device)
        with(device) { linkUp = false; asha = AshaState.Disconnected }
        coordinator.simulate(device, t, t + 2_000)
        device.linkUp = true
        coordinator.simulate(device, t + 2_250, t + 7_250)
        assertTrue(coordinator.phase is Phase.Verifying)

        device.asha = AshaState.Connected
        coordinator.simulate(device, t + 7_500, t + 12_000)
        assertEquals(0, coordinator.recoveries)
        coordinator.simulate(device, t + 12_250, t + 18_000)
        assertEquals(1, coordinator.recoveries)
    }

    @Test
    fun quickAshaDropAfterResumeRestartsTheCochlearApp() {
        val coordinator = RecoveryCoordinator()
        val device = healthyConnected()
        val t = coordinator.confirmedHealthy(device)
        with(device) { linkUp = false; asha = AshaState.Disconnected }
        coordinator.simulate(device, t, t + 2_000)
        device.linkUp = true
        coordinator.simulate(device, t + 2_250, t + 7_250)
        device.asha = AshaState.Connected
        coordinator.simulate(device, t + 7_500, t + 8_000)

        with(device) { linkUp = false; asha = AshaState.Disconnected }
        val afterCrash = coordinator.simulate(device, t + 8_250, t + 10_000)
        assertEquals(listOf(Command.SetAshaAllowed(false), Command.RestartCochlearApp), afterCrash)
        assertEquals(0, coordinator.recoveries)
    }

    @Test
    fun pausedMeasuresStabilityFromAfterTheRestart() {
        val coordinator = RecoveryCoordinator()
        val device = SimulatedDevice(linkUp = true)
        coordinator.simulate(device, 0, 250)
        device.linkUp = false
        val commands = coordinator.simulate(device, 500, 500).toMutableList()
        device.linkUp = true
        commands += coordinator.simulate(device, 750, 6_250)
        assertEquals(listOf(Command.SetAshaAllowed(false), Command.RestartCochlearApp), commands)
        assertTrue(coordinator.phase is Phase.Paused)

        val resume = coordinator.simulate(device, 6_500, 6_500)
        assertEquals(listOf(Command.SetAshaAllowed(true), Command.ConnectAsha), resume)
    }

    @Test
    fun dormantStableLinkGetsDirectAshaConnect() {
        val coordinator = RecoveryCoordinator()
        val device = SimulatedDevice(linkUp = true)
        assertEquals(emptyList<Command>(), coordinator.simulate(device, 0, 7_750))
        assertEquals(listOf(Command.ConnectAsha), coordinator.simulate(device, 8_000, 8_000))
        assertTrue(coordinator.phase is Phase.Verifying)
    }

    @Test
    fun verifyTimeoutPausesAgainAndRestartsTheCochlearApp() {
        val coordinator = RecoveryCoordinator()
        val device = SimulatedDevice(linkUp = true)
        coordinator.simulate(device, 0, 8_000)
        assertEquals(emptyList<Command>(), coordinator.simulate(device, 8_250, 12_750))
        val commands = coordinator.simulate(device, 13_000, 14_000)
        assertEquals(listOf(Command.SetAshaAllowed(false), Command.RestartCochlearApp), commands)
    }

    @Test
    fun cochlearAppRestartsAreRateLimited() {
        val coordinator = RecoveryCoordinator()
        val device = SimulatedDevice(linkUp = true)
        val commands = coordinator.simulate(device, 0, 60_000)
        val restarts = commands.count { it == Command.RestartCochlearApp }
        assertTrue("restarts=$restarts", restarts in 1..2)
    }

    @Test
    fun pauseIsRetriedWhileAshaStillAllowed() {
        val coordinator = RecoveryCoordinator()
        val device = healthyConnected()
        val t = coordinator.confirmedHealthy(device)
        val stuck = LinkSnapshot(linkUp = false, asha = AshaState.Disconnected, ashaAllowed = true)
        val commands = (t..t + 2_000 step 250).flatMap { coordinator.step(stuck, it).commands }
        assertEquals(List(3) { Command.SetAshaAllowed(false) }, commands)
    }

    @Test
    fun noLinkWarningIsLoggedOnce() {
        val coordinator = RecoveryCoordinator()
        val device = healthyConnected()
        val t = coordinator.confirmedHealthy(device)
        with(device) { linkUp = false; asha = AshaState.Disconnected }
        var events = 0
        var now = t
        while (now <= t + 60_000) {
            val decision = coordinator.step(device.snapshot(), now)
            device.apply(decision.commands)
            events += decision.events.count { it.contains("is the Cochlear app running") }
            now += 250
        }
        assertEquals(1, events)
    }

    @Test
    fun adapterOffIsIgnoredThenTheCochlearAppIsRestartedOnce() {
        val coordinator = RecoveryCoordinator()
        val device = healthyConnected()
        val t = coordinator.confirmedHealthy(device)
        with(device) { linkUp = false; asha = AshaState.Disconnected; adapterOn = false }
        assertEquals(emptyList<Command>(), coordinator.simulate(device, t, t + 2_000))
        assertTrue(coordinator.phase is Phase.Healthy)

        device.adapterOn = true
        val afterOn = coordinator.simulate(device, t + 2_250, t + 30_000)
        assertEquals(listOf(Command.SetAshaAllowed(false), Command.RestartCochlearApp), afterOn)
    }

    @Test
    fun ghostLinkAfterAdapterRestartStillRestartsTheCochlearApp() {
        val coordinator = RecoveryCoordinator()
        val device = healthyConnected()
        val t = coordinator.confirmedHealthy(device)
        with(device) { linkUp = false; asha = AshaState.Disconnected; adapterOn = false }
        coordinator.simulate(device, t, t + 1_000)
        with(device) { adapterOn = true; linkUp = true }
        coordinator.simulate(device, t + 1_250, t + 3_000)
        device.linkUp = false
        val afterGhost = coordinator.simulate(device, t + 3_250, t + 5_000)
        assertEquals(listOf(Command.SetAshaAllowed(false), Command.RestartCochlearApp), afterGhost)
    }

    @Test
    fun guardStartWithoutLinkRestartsTheCochlearApp() {
        val coordinator = RecoveryCoordinator()
        val commands = coordinator.simulate(SimulatedDevice(linkUp = false), 0, 10_000)
        assertEquals(listOf(Command.SetAshaAllowed(false), Command.RestartCochlearApp), commands)
    }

    @Test
    fun guardStartAllowsAshaLeftPausedByAnEarlierRun() {
        val coordinator = RecoveryCoordinator()
        val device = SimulatedDevice(linkUp = true).apply { allowed = false }
        assertEquals(listOf(Command.SetAshaAllowed(true)), coordinator.simulate(device, 0, 0))
        assertTrue(device.allowed)
        assertTrue(coordinator.phase is Phase.Watching)
        assertEquals(listOf(Command.ConnectAsha), coordinator.simulate(device, 250, 8_000))
    }

    @Test
    fun guardStartAllowsAshaThenPausesItAgainWithoutALink() {
        val coordinator = RecoveryCoordinator()
        val device = SimulatedDevice(linkUp = false).apply { allowed = false }
        val commands = coordinator.simulate(device, 0, 10_000)
        assertEquals(
            listOf(Command.SetAshaAllowed(true), Command.SetAshaAllowed(false), Command.RestartCochlearApp),
            commands,
        )
    }

    @Test
    fun handOffKeepsAshaPausedAndNeverResumes() {
        val coordinator = RecoveryCoordinator()
        val device = healthyConnected()
        coordinator.simulate(device, 0, 0)
        val handOff = coordinator.handOff()
        device.apply(handOff.commands)
        assertEquals(listOf(Command.SetAshaAllowed(false), Command.StopCochlearApp), handOff.commands)
        device.asha = AshaState.Disconnected
        assertEquals(emptyList<Command>(), coordinator.simulate(device, 250, 60_000))
        assertEquals(Phase.HandedOff, coordinator.phase)
    }

    @Test
    fun handOffReassertsPauseIfSomethingReallowsAsha() {
        val coordinator = RecoveryCoordinator()
        coordinator.handOff()
        val reallowed = LinkSnapshot(linkUp = true, asha = AshaState.Disconnected, ashaAllowed = true)
        assertEquals(listOf(Command.SetAshaAllowed(false)), coordinator.step(reallowed, 1_000).commands)
    }

    @Test
    fun backToPhoneRestartsTheCochlearAppThenResumes() {
        val coordinator = RecoveryCoordinator()
        val device = SimulatedDevice(linkUp = true)
        device.apply(coordinator.handOff().commands)
        coordinator.simulate(device, 0, 0)
        device.apply(coordinator.backToPhone(1_000).commands)
        assertEquals(Mode.Auto, coordinator.mode)
        assertEquals(listOf(Command.RestartCochlearApp), coordinator.simulate(device, 1_250, 6_750))
        val resume = coordinator.simulate(device, 7_000, 7_000)
        assertEquals(listOf(Command.SetAshaAllowed(true), Command.ConnectAsha), resume)
    }

    @Test
    fun stopRestoresAshaOnlyInAutoMode() {
        val paused = LinkSnapshot(linkUp = false, asha = AshaState.Disconnected, ashaAllowed = false)
        val connected = LinkSnapshot(linkUp = true, asha = AshaState.Connected, ashaAllowed = true)
        val auto = RecoveryCoordinator()
        assertEquals(listOf(Command.SetAshaAllowed(true)), auto.onStop(paused).commands)
        assertEquals(emptyList<Command>(), auto.onStop(connected).commands)

        val handedOff = RecoveryCoordinator().apply { handOff() }
        assertEquals(emptyList<Command>(), handedOff.onStop(paused).commands)
    }

    @Test
    fun doneOnceStreamingHoldsForTheConfirmWindow() {
        val coordinator = RecoveryCoordinator()
        val device = healthyConnected()
        coordinator.simulate(device, 0, 9_750)
        assertFalse(coordinator.isDone(9_750))
        coordinator.simulate(device, 10_000, 10_000)
        assertTrue(coordinator.isDone(10_000))
    }

    @Test
    fun doneAfterNoLinkForTheGiveUpWindow() {
        val coordinator = RecoveryCoordinator()
        val device = healthyConnected()
        val t = coordinator.confirmedHealthy(device)
        with(device) { linkUp = false; asha = AshaState.Disconnected }
        coordinator.simulate(device, t, t + 59_750)
        assertFalse(coordinator.isDone(t + 59_750))
        coordinator.simulate(device, t + 60_000, t + 60_000)
        assertTrue(coordinator.isDone(t + 60_000))
    }

    @Test
    fun noLinkFromTheStartCountsFromTheFirstStep() {
        val coordinator = RecoveryCoordinator()
        val device = SimulatedDevice(linkUp = false)
        coordinator.simulate(device, 5_000, 64_750)
        assertFalse(coordinator.isDone(64_750))
        assertTrue(coordinator.isDone(65_000))
    }

    @Test
    fun aLinkThatComesBackRestartsTheGiveUpWindow() {
        val coordinator = RecoveryCoordinator()
        val device = SimulatedDevice(linkUp = false)
        coordinator.simulate(device, 0, 50_000)
        device.linkUp = true
        coordinator.simulate(device, 50_250, 52_000)
        device.linkUp = false
        coordinator.simulate(device, 52_250, 100_000)
        assertFalse(coordinator.isDone(100_000))
    }

    @Test
    fun neverDoneWhileHandedOff() {
        val coordinator = RecoveryCoordinator()
        val device = healthyConnected()
        device.apply(coordinator.handOff().commands)
        with(device) { linkUp = false; asha = AshaState.Disconnected }
        coordinator.simulate(device, 0, 120_000)
        assertFalse(coordinator.isDone(120_000))
    }

    @Test
    fun resetStartsTheNextRunFresh() {
        val coordinator = RecoveryCoordinator()
        val device = healthyConnected()
        coordinator.confirmedHealthy(device)
        coordinator.reset()
        with(device) { linkUp = false; asha = AshaState.Disconnected; allowed = false }
        val commands = coordinator.simulate(device, 20_000, 30_000)
        assertEquals(Command.SetAshaAllowed(true), commands.first())
        assertTrue(Command.RestartCochlearApp in commands)
        assertFalse(coordinator.isDone(30_000))
    }

    @Test
    fun resetKeepsAHandOff() {
        val coordinator = RecoveryCoordinator()
        coordinator.handOff()
        coordinator.reset()
        assertEquals(Mode.HandedOff, coordinator.mode)
        assertEquals(Phase.HandedOff, coordinator.phase)
    }

    @Test
    fun bluetoothOffNeverGivesUpAndTheClockRestartsWhenItComesBack() {
        val coordinator = RecoveryCoordinator()
        val device = healthyConnected()
        val t = coordinator.confirmedHealthy(device)
        with(device) { linkUp = false; asha = AshaState.Disconnected }
        coordinator.simulate(device, t, t + 10_000)
        device.adapterOn = false
        coordinator.simulate(device, t + 10_250, t + 310_000)
        assertFalse(coordinator.isDone(t + 310_000))
        device.adapterOn = true
        coordinator.simulate(device, t + 310_250, t + 370_000)
        assertFalse(coordinator.isDone(t + 370_000))
        coordinator.simulate(device, t + 370_250, t + 370_250)
        assertTrue(coordinator.isDone(t + 370_250))
    }
}
