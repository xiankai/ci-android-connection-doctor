package io.github.xiankai.ciconnectiondoctor.asha

import org.junit.Assert.assertEquals
import org.junit.Test

class AutostartTest {

    private fun plan(
        trigger: AutostartTrigger,
        processorPicked: Boolean = true,
        startAtBoot: Boolean = true,
        canConnect: Boolean = true,
        streamingPaused: Boolean = false,
    ) = Autostart.plan(trigger, processorPicked, startAtBoot, canConnect, streamingPaused)

    @Test
    fun bootStartsTheGuard() {
        assertEquals(AutostartPlan(showPausedNotice = false, startGuard = true), plan(AutostartTrigger.Boot))
    }

    @Test
    fun appUpdateStartsTheGuard() {
        assertEquals(AutostartPlan(showPausedNotice = false, startGuard = true), plan(AutostartTrigger.AppUpdated))
    }

    @Test
    fun appUpdateLeavesTheGuardOffWhenStartAtBootIsOff() {
        assertEquals(AutostartPlan(showPausedNotice = false, startGuard = false), plan(AutostartTrigger.AppUpdated, startAtBoot = false))
    }

    @Test
    fun noProcessorMeansNoStart() {
        assertEquals(AutostartPlan(showPausedNotice = false, startGuard = false), plan(AutostartTrigger.AppUpdated, processorPicked = false))
    }

    @Test
    fun noBluetoothPermissionMeansNoStart() {
        assertEquals(AutostartPlan(showPausedNotice = false, startGuard = false), plan(AutostartTrigger.Boot, canConnect = false))
    }

    @Test
    fun pausedStreamingAfterBootShowsTheNotice() {
        assertEquals(AutostartPlan(showPausedNotice = true, startGuard = false), plan(AutostartTrigger.Boot, startAtBoot = false, streamingPaused = true))
    }

    @Test
    fun pausedStreamingAfterUpdateShowsNoRebootNotice() {
        assertEquals(AutostartPlan(showPausedNotice = false, startGuard = true), plan(AutostartTrigger.AppUpdated, streamingPaused = true))
    }

    @Test
    fun pausedNoticeNeedsAProcessor() {
        assertEquals(AutostartPlan(showPausedNotice = false, startGuard = false), plan(AutostartTrigger.Boot, processorPicked = false, streamingPaused = true))
    }
}
