package io.github.xiankai.ciconnectiondoctor.asha

enum class AutostartTrigger { Boot, AppUpdated }

data class AutostartPlan(val showPausedNotice: Boolean, val startGuard: Boolean)

object Autostart {
    fun plan(
        trigger: AutostartTrigger,
        processorPicked: Boolean,
        startAtBoot: Boolean,
        canConnect: Boolean,
        streamingPaused: Boolean,
    ): AutostartPlan = AutostartPlan(
        showPausedNotice = trigger == AutostartTrigger.Boot && streamingPaused && processorPicked,
        startGuard = startAtBoot && processorPicked && canConnect,
    )
}
