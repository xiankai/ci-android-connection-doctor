package io.github.xiankai.ciconnectiondoctor.guard

import android.util.Log
import io.github.xiankai.ciconnectiondoctor.asha.Command
import io.github.xiankai.ciconnectiondoctor.asha.Decision
import io.github.xiankai.ciconnectiondoctor.asha.EventLog
import io.github.xiankai.ciconnectiondoctor.asha.HearingAidControl
import io.github.xiankai.ciconnectiondoctor.asha.LinkSnapshot
import io.github.xiankai.ciconnectiondoctor.asha.Mode
import io.github.xiankai.ciconnectiondoctor.asha.Phase
import io.github.xiankai.ciconnectiondoctor.asha.RecoveryCoordinator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class GuardState(
    val running: Boolean = false,
    val mode: Mode = Mode.Auto,
    val phase: Phase? = null,
    val snapshot: LinkSnapshot? = null,
    val recoveries: Int = 0,
    val log: List<EventLog.Entry> = emptyList(),
    val done: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
object Guard {

    private const val POLL_INTERVAL_MS = 250L
    private const val LOG_TAG = "AshaGuard"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default.limitedParallelism(1))
    private val coordinator = RecoveryCoordinator()
    private val log = EventLog()
    private var loop: Job? = null
    private var lastError: String? = null

    private val _state = MutableStateFlow(GuardState())
    val state: StateFlow<GuardState> = _state.asStateFlow()

    fun start(control: HearingAidControl, address: String) = scope.launch { // stack-reviewer-ignore: single-threaded dispatcher and no suspension between the check and the assignment, so a queued second start sees the running loop
        if (loop?.isActive == true) return@launch
        coordinator.reset()
        record("Guard started for $address")
        loop = scope.launch {
            while (isActive) {
                tick(control, address)
                delay(POLL_INTERVAL_MS)
            }
        }
        publish(running = true, done = false)
    }

    fun finish(reason: String) = scope.launch {
        if (!_state.value.running || _state.value.done) return@launch
        record(reason)
        publish(done = true)
    }

    fun stop(control: HearingAidControl, address: String, onStopped: (leftPaused: Boolean) -> Unit) = scope.launch {
        loop?.cancel()
        loop = null
        val restore = runCatching { control.snapshot(address) }
            .onSuccess { execute(control, address, coordinator.onStop(it)) }
            .onFailure { record("Could not restore ASHA on stop: ${it.message}") }
        val leftPaused = restore.isFailure && coordinator.mode == Mode.Auto && AshaPauseFlag.paused.value
        if (coordinator.mode == Mode.HandedOff) {
            runCatching { control.keepPauseWithoutGuard() }
                .onFailure { record("Could not keep the hand-off pause after stopping: ${it.message}") }
        }
        record("Guard stopped")
        publish(running = false)
        onStopped(leftPaused)
    }

    fun allowAshaBeforeShutdown(control: HearingAidControl, address: String) = scope.launch {
        loop?.cancel() // stack-reviewer-ignore: ACTION_SHUTDOWN is sent only once shutdown is committed and cannot be aborted; stopping the loop first keeps a tick from pausing ASHA again on the way down
        loop = null
        if (!AshaPauseFlag.paused.value) return@launch
        execute(control, address, Decision(listOf(Command.SetAshaAllowed(true)), listOf("Phone shutting down: ASHA allowed again so it restarts with streaming on")))
        publish()
    }

    fun handOff(control: HearingAidControl, address: String) = scope.launch {
        execute(control, address, coordinator.handOff())
        publish()
    }

    fun backToPhone(control: HearingAidControl, address: String) = scope.launch {
        execute(control, address, coordinator.backToPhone(System.currentTimeMillis()))
        publish()
    }

    fun restartCochlearApp(control: HearingAidControl, address: String) = scope.launch {
        execute(control, address, Decision(listOf(Command.RestartCochlearApp), listOf("Restarting the Cochlear app on request")))
        publish()
    }

    fun note(message: String) = scope.launch {
        record(message)
        publish()
    }

    fun clearLog() = scope.launch {
        log.clear()
        publish()
    }

    private fun tick(control: HearingAidControl, address: String) {
        val snapshot = try {
            control.snapshot(address)
        } catch (e: Exception) {
            reportError(e)
            return
        }
        lastError = null
        if (snapshot.adapterOn) AshaPauseFlag.record(!snapshot.ashaAllowed) // stack-reviewer-ignore: with the adapter off the helper reports a placeholder ashaAllowed without reading the policy; a guard resume still clears the flag through the accepted SetAshaAllowed command
        val nowMs = System.currentTimeMillis()
        execute(control, address, coordinator.step(snapshot, nowMs))
        publish(snapshot = snapshot)
        if (coordinator.isDone(nowMs)) finish(doneReason())
    }

    private fun doneReason(): String =
        if (coordinator.phase is Phase.Healthy) "Streaming held; stopping the guard" else "No link for a minute; stopping the guard"


    private fun execute(control: HearingAidControl, address: String, decision: Decision) {
        decision.events.forEach(::record)
        decision.commands.forEach { command ->
            try {
                val accepted = when (command) {
                    is Command.SetAshaAllowed -> control.setAshaAllowed(address, command.allowed)
                    Command.ConnectAsha -> control.connectAsha(address)
                    Command.RestartCochlearApp -> control.restartCochlearApp()
                    Command.StopCochlearApp -> control.stopCochlearApp()
                }
                if (!accepted) record("Command failed: $command")
                if (accepted && command is Command.SetAshaAllowed) AshaPauseFlag.record(!command.allowed)
            } catch (e: Exception) {
                reportError(e)
            }
        }
    }

    private fun reportError(e: Exception) {
        val message = "${e.javaClass.simpleName}: ${e.message}"
        if (message == lastError) return
        lastError = message
        record(message)
        publish()
    }

    private fun record(message: String) {
        log.record(message)
        Log.i(LOG_TAG, message)
    }

    private fun publish(running: Boolean? = null, snapshot: LinkSnapshot? = null, done: Boolean? = null) {
        _state.update {
            it.copy(
                running = running ?: it.running,
                mode = coordinator.mode,
                phase = coordinator.phase,
                snapshot = snapshot ?: it.snapshot,
                recoveries = coordinator.recoveries,
                log = log.snapshot(),
                done = done ?: it.done,
            )
        }
    }
}
