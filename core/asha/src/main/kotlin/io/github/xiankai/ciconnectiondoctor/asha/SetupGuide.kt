package io.github.xiankai.ciconnectiondoctor.asha

enum class SetupStep {
    InstallShizuku,
    DeveloperOptions,
    JoinWifi,
    WirelessDebugging,
    StartShizuku,
    GrantPermission,
    PickProcessor,
    StartGuard,
}

enum class StepStatus { Done, Current, Todo, Unknown }

data class SetupItem(val step: SetupStep, val status: StepStatus)

data class SetupState(
    val shizukuInstalled: Boolean,
    val developerOptionsOn: Boolean?,
    val wifiConnected: Boolean?,
    val wirelessDebuggingOn: Boolean?,
    val shizukuRunning: Boolean,
    val permissionGranted: Boolean,
    val processorPicked: Boolean,
    val guardRunning: Boolean,
)

object SetupGuide {

    fun items(state: SetupState): List<SetupItem> {
        val checks = SetupStep.entries.map { it to isDone(it, state) }
        val current = checks.firstOrNull { (_, done) -> done == false }?.first
        return checks.map { (step, done) -> SetupItem(step, statusOf(step, done, current)) }
    }

    fun complete(state: SetupState): Boolean = items(state).none { it.status == StepStatus.Current }

    private fun statusOf(step: SetupStep, done: Boolean?, current: SetupStep?): StepStatus = when {
        done == true -> StepStatus.Done
        step == current -> StepStatus.Current
        done == null -> StepStatus.Unknown
        else -> StepStatus.Todo
    }

    private fun isDone(step: SetupStep, state: SetupState): Boolean? = when (step) {
        SetupStep.InstallShizuku -> state.shizukuInstalled || state.shizukuRunning
        SetupStep.DeveloperOptions -> state.developerOptionsOn.orDoneOnceRunning(state)
        SetupStep.JoinWifi -> state.wifiConnected.orDoneOnceRunning(state)
        SetupStep.WirelessDebugging -> state.wirelessDebuggingOn.orDoneOnceRunning(state)
        SetupStep.StartShizuku -> state.shizukuRunning
        SetupStep.GrantPermission -> state.shizukuRunning && state.permissionGranted
        SetupStep.PickProcessor -> state.processorPicked
        SetupStep.StartGuard -> state.guardRunning
    }

    private fun Boolean?.orDoneOnceRunning(state: SetupState): Boolean? = if (state.shizukuRunning) true else this
}
