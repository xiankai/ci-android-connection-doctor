package io.github.xiankai.ciconnectiondoctor.asha

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SetupGuideTest {

    private val freshPhone = SetupState(
        shizukuInstalled = false,
        developerOptionsOn = false,
        wifiConnected = false,
        wirelessDebuggingOn = false,
        shizukuRunning = false,
        permissionGranted = false,
        processorPicked = false,
        autoStartOn = false,
    )

    private val afterRestart = freshPhone.copy(
        shizukuInstalled = true,
        developerOptionsOn = true,
        permissionGranted = true,
        processorPicked = true,
        autoStartOn = true,
    )

    private fun current(state: SetupState): SetupStep? =
        SetupGuide.items(state).singleOrNull { it.status == StepStatus.Current }?.step

    private fun statusOf(state: SetupState, step: SetupStep): StepStatus =
        SetupGuide.items(state).first { it.step == step }.status

    @Test
    fun freshPhoneStartsWithInstallingShizuku() {
        assertEquals(SetupStep.InstallShizuku, current(freshPhone))
        assertEquals(StepStatus.Todo, statusOf(freshPhone, SetupStep.AutoStart))
    }

    @Test
    fun walksThroughEachStepInOrder() {
        var state = freshPhone
        val seen = mutableListOf<SetupStep>()
        val finish = listOf<(SetupState) -> SetupState>(
            { it.copy(shizukuInstalled = true) },
            { it.copy(developerOptionsOn = true) },
            { it.copy(wifiConnected = true) },
            { it.copy(wirelessDebuggingOn = true) },
            { it.copy(shizukuRunning = true) },
            { it.copy(permissionGranted = true) },
            { it.copy(processorPicked = true) },
            { it.copy(autoStartOn = true) },
        )
        finish.forEach { step ->
            seen += current(state)!!
            state = step(state)
        }
        assertEquals(SetupStep.entries, seen)
        assertTrue(SetupGuide.complete(state))
    }

    @Test
    fun afterRestartLandsOnTheRestartRoutine() {
        assertEquals(SetupStep.JoinWifi, current(afterRestart))
        assertEquals(SetupStep.WirelessDebugging, current(afterRestart.copy(wifiConnected = true)))
        assertEquals(SetupStep.StartShizuku, current(afterRestart.copy(wifiConnected = true, wirelessDebuggingOn = true)))
    }

    @Test
    fun wifiAndWirelessDebuggingCanBeLeftOnceShizukuRuns() {
        val running = afterRestart.copy(shizukuRunning = true)
        assertTrue(SetupGuide.complete(running))
        assertEquals(StepStatus.Done, statusOf(running, SetupStep.WirelessDebugging))
    }

    @Test
    fun anUndetectableStepNeverBlocksTheGuide() {
        val state = afterRestart.copy(wifiConnected = null, wirelessDebuggingOn = null)
        assertEquals(SetupStep.StartShizuku, current(state))
        assertEquals(StepStatus.Unknown, statusOf(state, SetupStep.JoinWifi))
    }

    @Test
    fun permissionCountsOnlyWhileShizukuRuns() {
        assertEquals(StepStatus.Todo, statusOf(afterRestart, SetupStep.GrantPermission))
        assertFalse(SetupGuide.complete(afterRestart))
    }

    @Test
    fun stepsLeftCountsOnlyStepsStillToDo() {
        assertEquals(SetupStep.entries.size, SetupGuide.stepsLeft(freshPhone))
        assertEquals(4, SetupGuide.stepsLeft(afterRestart))
        assertEquals(0, SetupGuide.stepsLeft(afterRestart.copy(shizukuRunning = true)))
    }

    @Test
    fun anUncheckableStepIsNotCountedAsLeft() {
        val state = afterRestart.copy(wifiConnected = null, wirelessDebuggingOn = null)
        assertEquals(2, SetupGuide.stepsLeft(state))
    }
}
