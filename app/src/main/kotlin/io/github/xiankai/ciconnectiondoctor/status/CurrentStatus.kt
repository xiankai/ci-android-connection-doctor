package io.github.xiankai.ciconnectiondoctor.status

import io.github.xiankai.ciconnectiondoctor.asha.GuideStep
import io.github.xiankai.ciconnectiondoctor.asha.Home
import io.github.xiankai.ciconnectiondoctor.asha.HomeInputs
import io.github.xiankai.ciconnectiondoctor.asha.HomeStatus
import io.github.xiankai.ciconnectiondoctor.asha.PublicLinkState
import io.github.xiankai.ciconnectiondoctor.guard.GuardState
import io.github.xiankai.ciconnectiondoctor.privileged.PrivilegedBridge

fun currentStatus(
    guard: GuardState,
    privileged: PrivilegedBridge.Status,
    processorPicked: Boolean,
    publicLink: PublicLinkState? = null,
    guideStep: GuideStep? = null,
    pausedByGuard: Boolean = false,
): HomeStatus = Home.status(
    HomeInputs(
        processorPicked = processorPicked,
        helperReady = privileged == PrivilegedBridge.Status.Ready || privileged == PrivilegedBridge.Status.Connecting,
        guardRunning = guard.running,
        mode = guard.mode,
        phase = guard.phase,
        guardSnapshot = guard.snapshot,
        publicLink = publicLink,
        guideStep = guideStep,
        pausedByGuard = pausedByGuard,
    ),
)
