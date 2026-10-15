package io.github.xiankai.ciconnectiondoctor.asha

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ManualGuideTest {

    private val stalled = PublicLinkState(linkUp = true, asha = AshaState.Disconnected)
    private val retrying = PublicLinkState(linkUp = true, asha = AshaState.Connecting)
    private val noLink = PublicLinkState(linkUp = false, asha = AshaState.Disconnected)
    private val streaming = PublicLinkState(linkUp = true, asha = AshaState.Connected)
    private val leftPaused = stalled.copy(pausedByGuard = true)

    private fun ManualGuide.observeFor(state: PublicLinkState, fromMs: Long, toMs: Long, stepMs: Long = 1_000): GuideStep? {
        var now = fromMs
        var step: GuideStep? = null
        while (now <= toMs) {
            step = observe(state, now).step
            now += stepMs
        }
        return step
    }

    private fun ManualGuide.crashLoop(fromMs: Long, toMs: Long): GuideStep? {
        var now = fromMs
        var step: GuideStep? = null
        while (now <= toMs) {
            val lastDrop = now - now % 6_000
            step = observe(stalled.copy(lastLinkDropMs = lastDrop), now).step
            now += 1_000
        }
        return step
    }

    @Test
    fun streamingNeverAdvises() {
        assertNull(ManualGuide().observeFor(streaming, 0, 60_000))
    }

    @Test
    fun stalledLinkSuggestsConnectAfterTheGracePeriod() {
        val guide = ManualGuide()
        assertNull(guide.observeFor(stalled, 0, 9_000))
        assertEquals(GuideStep.ConnectStreaming, guide.observe(stalled, 10_000).step)
    }

    @Test
    fun crashLoopSuggestsShizuku() {
        assertEquals(GuideStep.StartShizuku, ManualGuide().crashLoop(1_000, 20_000))
    }

    @Test
    fun ashaRetryingAlsoSuggestsShizuku() {
        assertEquals(GuideStep.StartShizuku, ManualGuide().observeFor(retrying, 0, 10_000))
    }

    @Test
    fun crashLoopBlipsDoNotClearTheAdvice() {
        val guide = ManualGuide()
        guide.crashLoop(1_000, 20_000)
        assertEquals(GuideStep.StartShizuku, guide.crashLoop(21_000, 120_000))
    }

    @Test
    fun crashLoopEndingInAStallSwitchesToConnect() {
        val guide = ManualGuide()
        guide.crashLoop(1_000, 20_000)
        assertEquals(GuideStep.StartShizuku, guide.observeFor(stalled, 21_000, 48_000))
        assertEquals(GuideStep.ConnectStreaming, guide.observe(stalled, 49_000).step)
    }

    @Test
    fun aLongGapInTheCrashLoopKeepsSuggestingShizuku() {
        val guide = ManualGuide()
        guide.crashLoop(1_000, 20_000)
        assertEquals(GuideStep.StartShizuku, guide.observeFor(stalled, 21_000, 32_000))
        assertEquals(GuideStep.StartShizuku, guide.crashLoop(33_000, 60_000))
    }

    @Test
    fun processorOutOfRangeAdvisesNothing() {
        assertNull(ManualGuide().observeFor(noLink, 0, 60_000))
    }

    @Test
    fun linkGoneTooLongClearsTheAdvice() {
        val guide = ManualGuide()
        guide.observeFor(stalled, 0, 10_000)
        assertEquals(GuideStep.ConnectStreaming, guide.observeFor(noLink, 11_000, 18_000))
        val cleared = guide.observe(noLink, 19_000)
        assertNull(cleared.step)
        assertEquals(listOf("Processor out of reach; recovery advice cleared"), cleared.events)
    }

    @Test
    fun streamingHeldClearsTheAdvice() {
        val guide = ManualGuide()
        guide.observeFor(stalled, 0, 10_000)
        assertEquals(GuideStep.ConnectStreaming, guide.observeFor(streaming, 11_000, 20_000))
        val done = guide.observe(streaming, 21_000)
        assertNull(done.step)
        assertEquals(listOf("Streaming is back"), done.events)
    }

    @Test
    fun dismissHoldsUntilStreamingComesBack() {
        val guide = ManualGuide()
        guide.observeFor(stalled, 0, 10_000)
        guide.dismiss()
        assertNull(guide.observeFor(stalled, 11_000, 60_000))
        guide.observeFor(streaming, 61_000, 71_000)
        assertEquals(GuideStep.ConnectStreaming, guide.observeFor(stalled, 72_000, 82_000))
    }

    @Test
    fun bluetoothOffClearsTheAdvice() {
        val guide = ManualGuide()
        guide.observeFor(stalled, 0, 10_000)
        assertNull(guide.observe(PublicLinkState(linkUp = false, asha = AshaState.Disconnected, adapterOn = false), 11_000).step)
        assertNull(guide.observe(stalled, 12_000).step)
    }

    @Test
    fun streamingLeftPausedSuggestsResumingAtOnce() {
        val update = ManualGuide().observe(leftPaused, 0)
        assertEquals(GuideStep.ResumeStreaming, update.step)
        assertEquals(listOf("Streaming was left paused; suggesting starting Shizuku to resume it"), update.events)
    }

    @Test
    fun streamingLeftPausedWinsOverConnect() {
        assertEquals(GuideStep.ResumeStreaming, ManualGuide().observeFor(leftPaused, 0, 30_000))
    }

    @Test
    fun streamingLeftPausedIsAdvisedOutOfRangeToo() {
        assertEquals(GuideStep.ResumeStreaming, ManualGuide().observeFor(noLink.copy(pausedByGuard = true), 0, 30_000))
    }

    @Test
    fun streamingLeftPausedClearsOnceStreamingHolds() {
        val guide = ManualGuide()
        guide.observe(leftPaused, 0)
        guide.observeFor(streaming, 1_000, 10_000)
        assertNull(guide.observe(streaming, 11_000).step)
    }

    @Test
    fun streamingLeftPausedCanBeDismissed() {
        val guide = ManualGuide()
        guide.observe(leftPaused, 0)
        guide.dismiss()
        assertNull(guide.observeFor(leftPaused, 1_000, 30_000))
    }

    @Test
    fun doneOnceStreamingHolds() {
        val guide = ManualGuide()
        guide.observeFor(streaming, 0, 9_000)
        assertFalse(guide.observe(streaming, 9_500).done)
        assertTrue(guide.observe(streaming, 10_000).done)
    }

    @Test
    fun doneAfterTheLinkIsGoneForAMinute() {
        val guide = ManualGuide()
        guide.observeFor(stalled, 0, 5_000)
        guide.observeFor(noLink, 6_000, 64_000)
        assertFalse(guide.observe(noLink, 64_500).done)
        assertTrue(guide.observe(noLink, 65_000).done)
    }

    @Test
    fun bluetoothOffCountsAsTheLinkBeingGone() {
        val guide = ManualGuide()
        val off = noLink.copy(adapterOn = false)
        guide.observeFor(off, 0, 59_000)
        assertTrue(guide.observe(off, 60_000).done)
    }

    @Test
    fun aStalledLinkIsNeverDone() {
        val guide = ManualGuide()
        guide.observeFor(stalled, 0, 120_000)
        assertFalse(guide.observe(stalled, 121_000).done)
    }
}
