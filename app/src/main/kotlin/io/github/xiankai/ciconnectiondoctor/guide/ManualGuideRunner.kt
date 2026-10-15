package io.github.xiankai.ciconnectiondoctor.guide

import io.github.xiankai.ciconnectiondoctor.asha.AshaState
import io.github.xiankai.ciconnectiondoctor.asha.GuideStep
import io.github.xiankai.ciconnectiondoctor.asha.GuideUpdate
import io.github.xiankai.ciconnectiondoctor.asha.ManualGuide
import io.github.xiankai.ciconnectiondoctor.asha.PublicLinkState
import io.github.xiankai.ciconnectiondoctor.guard.AshaPauseFlag
import io.github.xiankai.ciconnectiondoctor.guard.Guard
import io.github.xiankai.ciconnectiondoctor.privileged.PrivilegedBridge
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
object ManualGuideRunner {

    private const val POLL_INTERVAL_MS = 1_000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default.limitedParallelism(1))
    private val guide = ManualGuide()
    private var loop: Job? = null
    private var reader: PublicLinkReader? = null

    private val _step = MutableStateFlow<GuideStep?>(null)
    val step: StateFlow<GuideStep?> = _step.asStateFlow()

    fun start(newReader: PublicLinkReader) = scope.launch {
        if (loop?.isActive == true) {
            newReader.close()
            return@launch
        }
        reader = newReader
        Guard.note("Shizuku isn't ready; the guard will advise if streaming drops")
        loop = scope.launch {
            while (isActive) {
                PrivilegedBridge.refreshWhileUnavailable()
                runCatching { newReader.read() }
                    .onSuccess { state -> state?.let(::observe) }
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    fun stop() = scope.launch {
        loop?.cancel()
        loop = null
        reader?.close()
        reader = null
        guide.reset()
        _step.value = null
    }

    fun dismiss() = scope.launch {
        apply(guide.dismiss())
    }

    private fun observe(state: PublicLinkState) {
        if (state.asha != AshaState.Disconnected) AshaPauseFlag.record(false)
        apply(guide.observe(state.copy(pausedByGuard = AshaPauseFlag.paused.value), System.currentTimeMillis()))
    }

    private fun apply(update: GuideUpdate) {
        update.events.forEach(Guard::note)
        _step.value = update.step
        if (update.done) Guard.finish("Nothing left to advise on; stopping the guard")
    }
}
