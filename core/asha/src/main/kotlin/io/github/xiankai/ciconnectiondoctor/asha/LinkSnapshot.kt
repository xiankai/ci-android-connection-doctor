package io.github.xiankai.ciconnectiondoctor.asha

enum class AshaState { Disconnected, Connecting, Connected, Disconnecting }

data class LinkSnapshot(
    val linkUp: Boolean,
    val asha: AshaState,
    val ashaAllowed: Boolean,
    val adapterOn: Boolean = true,
)

sealed interface Command {
    data class SetAshaAllowed(val allowed: Boolean) : Command
    data object ConnectAsha : Command
    data object RestartCochlearApp : Command
    data object StopCochlearApp : Command
}

data class Decision(
    val commands: List<Command> = emptyList(),
    val events: List<String> = emptyList(),
) {
    companion object {
        val NONE = Decision()
    }
}

interface HearingAidControl {
    fun snapshot(address: String): LinkSnapshot
    fun setAshaAllowed(address: String, allowed: Boolean): Boolean
    fun connectAsha(address: String): Boolean
    fun restartCochlearApp(): Boolean
    fun stopCochlearApp(): Boolean
    fun keepPauseWithoutGuard()
}
