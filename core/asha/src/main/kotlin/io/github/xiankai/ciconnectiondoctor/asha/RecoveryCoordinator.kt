package io.github.xiankai.ciconnectiondoctor.asha

data class RecoveryTiming(
    val ashaDropGraceMs: Long = 2_000,
    val dormantLinkStableMs: Long = 8_000,
    val pausedLinkStableMs: Long = 5_000,
    val verifyMs: Long = 5_000,
    val healthyConfirmMs: Long = 10_000,
    val pauseRetryMs: Long = 1_000,
    val noLinkWarningMs: Long = 20_000,
    val nudgeDelayMs: Long = 1_000,
    val nudgeMinIntervalMs: Long = 30_000,
    val noLinkGiveUpMs: Long = 60_000,
)

enum class Mode { Auto, HandedOff }

sealed interface Phase {
    data class Healthy(val sinceMs: Long, val confirmed: Boolean = false) : Phase
    data class Watching(val sinceMs: Long) : Phase
    data class Paused(val sinceMs: Long, val lastPauseRequestMs: Long, val warnedNoLink: Boolean = false) : Phase
    data class Verifying(val sinceMs: Long) : Phase
    data object HandedOff : Phase
}

class RecoveryCoordinator(private val timing: RecoveryTiming = RecoveryTiming()) {

    var mode: Mode = Mode.Auto
        private set

    var phase: Phase? = null
        private set

    var recoveries: Int = 0
        private set

    private var linkUpSinceMs: Long? = null
    private var lastLinkDropMs: Long? = null
    private var adapterWasOff = false
    private var nudgeArmed = false
    private var lastNudgeMs: Long? = null
    private var recoveryPending = false
    private var noLinkSinceMs: Long? = null

    fun reset() {
        phase = Phase.HandedOff.takeIf { mode == Mode.HandedOff }
        linkUpSinceMs = null
        lastLinkDropMs = null
        adapterWasOff = false
        nudgeArmed = false
        lastNudgeMs = null
        recoveryPending = false
        noLinkSinceMs = null
    }

    fun isDone(nowMs: Long): Boolean {
        if (mode != Mode.Auto) return false
        val current = phase
        if (current is Phase.Healthy && current.confirmed) return true
        val since = noLinkSinceMs ?: return false
        return nowMs - since >= timing.noLinkGiveUpMs
    }

    fun handOff(): Decision {
        mode = Mode.HandedOff
        phase = Phase.HandedOff
        return Decision(
            listOf(Command.SetAshaAllowed(false), Command.StopCochlearApp),
            listOf("Handed off: ASHA paused and the Cochlear app stopped so another device can take the processor"),
        )
    }

    fun backToPhone(nowMs: Long): Decision {
        mode = Mode.Auto
        nudgeArmed = true
        return pause(nowMs, "Back to phone: restarting the Cochlear app's link before resuming ASHA")
    }

    fun onStop(snapshot: LinkSnapshot): Decision {
        if (mode == Mode.HandedOff || snapshot.ashaAllowed) return Decision.NONE
        return Decision(listOf(Command.SetAshaAllowed(true)), listOf("Stopping: ASHA allowed again"))
    }

    fun step(snapshot: LinkSnapshot, nowMs: Long): Decision {
        noLinkSinceMs = if (snapshot.adapterOn && !snapshot.linkUp) noLinkSinceMs ?: nowMs else null
        if (!snapshot.adapterOn) {
            trackLink(false, nowMs)
            adapterWasOff = true
            return Decision.NONE
        }
        if (adapterWasOff || phase == null) {
            adapterWasOff = false
            nudgeArmed = true
        }
        trackLink(snapshot.linkUp, nowMs)
        if (mode == Mode.HandedOff) {
            return if (snapshot.ashaAllowed) Decision(listOf(Command.SetAshaAllowed(false))) else Decision.NONE
        }
        return when (val current = phase) {
            null -> start(snapshot, nowMs)
            is Phase.Healthy -> healthy(current, snapshot, nowMs)
            is Phase.Watching -> watching(current, snapshot, nowMs)
            is Phase.Paused -> paused(current, snapshot, nowMs)
            is Phase.Verifying -> verifying(current, snapshot, nowMs)
            Phase.HandedOff -> Decision.NONE
        }
    }

    private fun trackLink(linkUp: Boolean, nowMs: Long) {
        val wasUp = linkUpSinceMs != null
        if (linkUp && !wasUp) linkUpSinceMs = nowMs
        if (!linkUp && wasUp) {
            linkUpSinceMs = null
            lastLinkDropMs = nowMs
        }
    }

    private fun linkDroppedSince(sinceMs: Long): Boolean = (lastLinkDropMs ?: Long.MIN_VALUE) >= sinceMs

    private fun linkStableMs(nowMs: Long, notBeforeMs: Long = Long.MIN_VALUE): Long {
        val upSince = linkUpSinceMs ?: return 0
        return nowMs - maxOf(upSince, notBeforeMs)
    }

    private fun start(snapshot: LinkSnapshot, nowMs: Long): Decision {
        if (!snapshot.ashaAllowed) { // stack-reviewer-ignore: start only runs while phase is null, on the tick's fresh device snapshot, and always leaves phase set, so it fires once per guard run
            val next = start(snapshot.copy(ashaAllowed = true), nowMs)
            return Decision(
                listOf(Command.SetAshaAllowed(true)) + next.commands,
                listOf("ASHA was left paused by an earlier run; allowing it again") + next.events,
            )
        }
        if (snapshot.asha == AshaState.Connected) return becomeHealthy(nowMs, "ASHA already connected")
        return startWatching(snapshot, nowMs, "ASHA not connected; watching the link")
    }

    private fun healthy(current: Phase.Healthy, snapshot: LinkSnapshot, nowMs: Long): Decision {
        if (snapshot.asha == AshaState.Connected) {
            if (current.confirmed || nowMs - current.sinceMs < timing.healthyConfirmMs) return Decision.NONE
            phase = current.copy(confirmed = true)
            nudgeArmed = false
            if (!recoveryPending) return Decision.NONE
            recoveryPending = false
            recoveries++
            return Decision(events = listOf("ASHA held for ${timing.healthyConfirmMs / 1000} s; recovery confirmed"))
        }
        if (!current.confirmed) {
            nudgeArmed = true
            recoveryPending = false
        }
        return startWatching(snapshot, nowMs, "ASHA dropped; watching the link")
    }

    private fun startWatching(snapshot: LinkSnapshot, nowMs: Long, event: String): Decision {
        val watching = Phase.Watching(nowMs)
        phase = watching
        val next = watching(watching, snapshot, nowMs)
        return Decision(next.commands, listOf(event) + next.events)
    }

    private fun watching(current: Phase.Watching, snapshot: LinkSnapshot, nowMs: Long): Decision {
        if (snapshot.asha == AshaState.Connected) return becomeHealthy(nowMs, "ASHA reconnected on its own")
        if (!snapshot.ashaAllowed) {
            phase = Phase.Paused(nowMs, nowMs)
            return Decision(events = listOf("ASHA is paused; waiting for a stable link"))
        }
        if (linkDroppedSince(current.sinceMs)) {
            return pause(nowMs, "Link dropped while ASHA was reconnecting; pausing ASHA so the Cochlear app can reconnect")
        }
        if (nowMs - current.sinceMs < timing.ashaDropGraceMs) return Decision.NONE
        if (!snapshot.linkUp) return pause(nowMs, "No link; pausing ASHA until the Cochlear app reconnects")
        if (linkStableMs(nowMs) < timing.dormantLinkStableMs) return Decision.NONE
        phase = Phase.Verifying(nowMs)
        return Decision(
            listOf(Command.ConnectAsha),
            listOf("Link stable without ASHA; connecting ASHA"),
        )
    }

    private fun paused(current: Phase.Paused, snapshot: LinkSnapshot, nowMs: Long): Decision {
        if (snapshot.ashaAllowed) {
            if (nowMs - current.lastPauseRequestMs < timing.pauseRetryMs) return Decision.NONE
            phase = current.copy(lastPauseRequestMs = nowMs)
            return Decision(listOf(Command.SetAshaAllowed(false)))
        }
        if (nudgeDue(current, nowMs)) {
            nudgeArmed = false
            lastNudgeMs = nowMs
            phase = current.copy(sinceMs = nowMs, warnedNoLink = false)
            return Decision(
                listOf(Command.RestartCochlearApp),
                listOf("Restarting the Cochlear app so it sets up a fresh link"),
            )
        }
        if (snapshot.linkUp && linkStableMs(nowMs, notBeforeMs = current.sinceMs) >= timing.pausedLinkStableMs) {
            phase = Phase.Verifying(nowMs)
            return Decision(
                listOf(Command.SetAshaAllowed(true), Command.ConnectAsha),
                listOf("Link stable with ASHA paused; resuming and connecting ASHA"),
            )
        }
        if (!snapshot.linkUp && !current.warnedNoLink && nowMs - current.sinceMs >= timing.noLinkWarningMs) {
            phase = current.copy(warnedNoLink = true)
            return Decision(events = listOf("Still no link ${timing.noLinkWarningMs / 1000} s after pausing ASHA; is the Cochlear app running?"))
        }
        return Decision.NONE
    }

    private fun nudgeDue(current: Phase.Paused, nowMs: Long): Boolean {
        if (!nudgeArmed || nowMs - current.sinceMs < timing.nudgeDelayMs) return false
        val last = lastNudgeMs ?: return true
        return nowMs - last >= timing.nudgeMinIntervalMs
    }

    private fun verifying(current: Phase.Verifying, snapshot: LinkSnapshot, nowMs: Long): Decision {
        if (snapshot.asha == AshaState.Connected) {
            recoveryPending = true
            return becomeHealthy(nowMs, "ASHA connected ${nowMs - current.sinceMs} ms after the connect request")
        }
        if (linkDroppedSince(current.sinceMs)) return failVerify(nowMs, "Link dropped while connecting ASHA; pausing again")
        if (nowMs - current.sinceMs >= timing.verifyMs) {
            return failVerify(nowMs, "ASHA did not connect within ${timing.verifyMs / 1000} s; pausing again")
        }
        return Decision.NONE
    }

    private fun failVerify(nowMs: Long, event: String): Decision {
        nudgeArmed = true
        return pause(nowMs, event)
    }

    private fun becomeHealthy(nowMs: Long, event: String): Decision {
        phase = Phase.Healthy(nowMs)
        return Decision(events = listOf(event))
    }

    private fun pause(nowMs: Long, event: String): Decision {
        phase = Phase.Paused(nowMs, nowMs)
        return Decision(listOf(Command.SetAshaAllowed(false)), listOf(event))
    }
}
