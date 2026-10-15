package io.github.xiankai.ciconnectiondoctor.guard

import io.github.xiankai.ciconnectiondoctor.asha.Phase

fun phaseLabel(phase: Phase): String = when (phase) {
    is Phase.Healthy -> "Streaming ready"
    is Phase.Watching -> "Watching for ASHA to reconnect"
    is Phase.Paused -> "ASHA paused, waiting for the Cochlear app's link"
    is Phase.Verifying -> "Connecting ASHA"
    Phase.HandedOff -> "Handed off to another device"
}
