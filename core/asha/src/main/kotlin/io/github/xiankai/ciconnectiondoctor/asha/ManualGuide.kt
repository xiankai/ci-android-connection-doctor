package io.github.xiankai.ciconnectiondoctor.asha

data class PublicLinkState(
    val linkUp: Boolean,
    val asha: AshaState,
    val adapterOn: Boolean = true,
    val lastLinkDropMs: Long? = null,
    val pausedByGuard: Boolean = false,
)

enum class GuideStep { ConnectStreaming, StartShizuku, ResumeStreaming }

data class GuideUpdate(val step: GuideStep?, val events: List<String> = emptyList(), val done: Boolean = false)

data class GuideTiming(
    val dropGraceMs: Long = 10_000,
    val linkRecentMs: Long = 8_000,
    val linkStableMs: Long = 5_000,
    val retryRecentMs: Long = 8_000,
    val loopOverMs: Long = 30_000,
    val streamingHeldMs: Long = 10_000,
    val linkGoneMs: Long = 60_000,
)

class ManualGuide(private val timing: GuideTiming = GuideTiming()) {

    var step: GuideStep? = null
        private set

    private var dismissed = false
    private var ashaDownSinceMs: Long? = null
    private var ashaUpSinceMs: Long? = null
    private var lastRetryMs: Long? = null
    private var linkUpSinceMs: Long? = null
    private var linkLastSeenMs: Long? = null
    private var linkSeenOrStartedMs: Long? = null

    fun observe(state: PublicLinkState, nowMs: Long): GuideUpdate {
        if (linkSeenOrStartedMs == null || state.adapterOn && state.linkUp) linkSeenOrStartedMs = nowMs
        val update = advise(state, nowMs)
        return if (linkGone(nowMs)) update.copy(done = true) else update
    }

    private fun advise(state: PublicLinkState, nowMs: Long): GuideUpdate {
        if (!state.adapterOn) {
            val hadStep = step != null
            clearAdvice()
            return GuideUpdate(null, listOfNotNull("Bluetooth is off; recovery advice cleared".takeIf { hadStep }))
        }
        trackLink(state.linkUp, nowMs)
        if (state.asha == AshaState.Connected) return streamingUp(nowMs)
        ashaUpSinceMs = null
        if (state.asha == AshaState.Connecting || state.asha == AshaState.Disconnecting) lastRetryMs = nowMs
        state.lastLinkDropMs?.let { drop -> lastRetryMs = maxOf(lastRetryMs ?: drop, drop) }
        if (ashaDownSinceMs == null) ashaDownSinceMs = nowMs
        return show(adviceFor(state, nowMs))
    }

    private fun linkGone(nowMs: Long): Boolean = nowMs - (linkSeenOrStartedMs ?: nowMs) >= timing.linkGoneMs

    fun dismiss(): GuideUpdate {
        if (step == null) return GuideUpdate(null)
        step = null
        dismissed = true
        return GuideUpdate(null, listOf("Recovery advice dismissed until streaming comes back"))
    }

    fun reset() {
        clearAdvice()
        linkSeenOrStartedMs = null
    }

    private fun clearAdvice() {
        step = null
        dismissed = false
        ashaDownSinceMs = null
        ashaUpSinceMs = null
        lastRetryMs = null
        linkUpSinceMs = null
        linkLastSeenMs = null
    }

    private fun adviceFor(state: PublicLinkState, nowMs: Long): GuideStep? {
        if (dismissed) return null
        if (state.pausedByGuard) return GuideStep.ResumeStreaming
        val downFor = nowMs - (ashaDownSinceMs ?: nowMs)
        if (downFor < timing.dropGraceMs) return step
        if (!seenWithin(linkLastSeenMs, timing.linkRecentMs, nowMs)) return null
        if (seenWithin(lastRetryMs, retryWindowMs(), nowMs)) return GuideStep.StartShizuku
        if (linkStable(nowMs)) return GuideStep.ConnectStreaming
        return step
    }

    private fun retryWindowMs(): Long = if (step == GuideStep.StartShizuku) timing.loopOverMs else timing.retryRecentMs

    private fun show(next: GuideStep?): GuideUpdate {
        if (next == step) return GuideUpdate(step)
        step = next
        val event = when (next) {
            GuideStep.ConnectStreaming -> "Processor linked but streaming isn't reconnecting; suggesting Connect"
            GuideStep.StartShizuku -> "Streaming keeps failing; suggesting starting Shizuku"
            GuideStep.ResumeStreaming -> "Streaming was left paused; suggesting starting Shizuku to resume it"
            null -> "Processor out of reach; recovery advice cleared"
        }
        return GuideUpdate(next, listOf(event))
    }

    private fun seenWithin(atMs: Long?, windowMs: Long, nowMs: Long): Boolean = atMs?.let { nowMs - it <= windowMs } ?: false

    private fun trackLink(linkUp: Boolean, nowMs: Long) {
        if (!linkUp) {
            linkUpSinceMs = null
            return
        }
        if (linkUpSinceMs == null) linkUpSinceMs = nowMs
        linkLastSeenMs = nowMs
    }

    private fun linkStable(nowMs: Long): Boolean {
        val upSince = linkUpSinceMs ?: return false
        return nowMs - upSince >= timing.linkStableMs
    }

    private fun streamingUp(nowMs: Long): GuideUpdate {
        val upSince = ashaUpSinceMs ?: nowMs.also { ashaUpSinceMs = it }
        if (nowMs - upSince < timing.streamingHeldMs) return GuideUpdate(step)
        val hadStep = step != null
        step = null
        dismissed = false
        ashaDownSinceMs = null
        lastRetryMs = null
        return GuideUpdate(null, listOfNotNull("Streaming is back".takeIf { hadStep }), done = true)
    }
}
