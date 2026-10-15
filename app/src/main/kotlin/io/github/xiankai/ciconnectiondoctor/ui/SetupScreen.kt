package io.github.xiankai.ciconnectiondoctor.ui

import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import io.github.xiankai.ciconnectiondoctor.GuardViewModel
import io.github.xiankai.ciconnectiondoctor.asha.SetupGuide
import io.github.xiankai.ciconnectiondoctor.asha.SetupItem
import io.github.xiankai.ciconnectiondoctor.asha.SetupState
import io.github.xiankai.ciconnectiondoctor.asha.SetupStep
import io.github.xiankai.ciconnectiondoctor.asha.StepStatus
import io.github.xiankai.ciconnectiondoctor.guide.DeviceSetup
import io.github.xiankai.ciconnectiondoctor.privileged.PrivilegedBridge
import kotlinx.coroutines.delay

private const val DEVICE_POLL_MS = 1_000L

private data class SetupText(val title: String, val text: String, val action: String? = null)

private fun setupText(step: SetupStep): SetupText = when (step) {
    SetupStep.InstallShizuku -> SetupText(
        "Install Shizuku",
        "Shizuku lends this app the rights it needs to pause and resume streaming. Install it, then come back here.",
        "Get Shizuku",
    )
    SetupStep.DeveloperOptions -> SetupText(
        "Turn on Developer options",
        "In About phone, tap Build number seven times. Android may ask for your PIN.",
        "Open About phone",
    )
    SetupStep.JoinWifi -> SetupText(
        "Join any Wi-Fi",
        "Any network works, even one without internet, like a café's. The phone's own hotspot doesn't count. You can leave it again once Shizuku runs.",
        "Open Wi-Fi",
    )
    SetupStep.WirelessDebugging -> SetupText(
        "Turn on wireless debugging",
        "In Developer options, tap the words Wireless debugging and turn on Use wireless debugging. Tap OK, then Allow for this network.",
        "Open Developer options",
    )
    SetupStep.StartShizuku -> SetupText(
        "Start Shizuku",
        "After a restart: open Shizuku and tap Start under \"Start via Wireless debugging\".\n\n" +
            "The first time only: tap Pairing in Shizuku, then Pair device with pairing code on the Wireless debugging page. " +
            "Type the six digits into Shizuku's notification (Enter pairing code), then tap Start.",
        "Open Shizuku",
    )
    SetupStep.GrantPermission -> SetupText(
        "Allow this app in Shizuku",
        "Tap Allow all the time. If no dialog appears, open Shizuku's app list and allow this app there.",
        "Grant permission",
    )
    SetupStep.PickProcessor -> SetupText(
        "Pick your sound processor",
        "Choose it under Sound processor on the main screen.",
        "Go to main screen",
    )
    SetupStep.StartGuard -> SetupText(
        "Start the guard",
        "A notification stays up while it runs.",
        "Start guard",
    )
}

private fun setupState(
    device: DeviceSetup,
    privileged: PrivilegedBridge.Status,
    processorPicked: Boolean,
    guardRunning: Boolean,
) = SetupState(
    shizukuInstalled = device.shizukuInstalled,
    developerOptionsOn = device.developerOptionsOn,
    wifiConnected = device.wifiConnected,
    wirelessDebuggingOn = device.wirelessDebuggingOn,
    shizukuRunning = privileged != PrivilegedBridge.Status.ShizukuNotRunning,
    permissionGranted = privileged == PrivilegedBridge.Status.Connecting || privileged == PrivilegedBridge.Status.Ready,
    processorPicked = processorPicked,
    guardRunning = guardRunning,
    )

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupScreen(viewModel: GuardViewModel) {
    val device by viewModel.device.collectAsState()
    val privileged by viewModel.privileged.collectAsState()
    val target by viewModel.target.collectAsState()
    val guard by viewModel.guard.collectAsState()
    val state = setupState(device, privileged, target != null, guard.running)
    val items = SetupGuide.items(state)

    BackHandler(onBack = viewModel::closeSetup)
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                delay(DEVICE_POLL_MS)
                viewModel.refreshDevice()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Set up") },
                actions = { TextButton(onClick = viewModel::closeSetup) { Text("Close") } },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (SetupGuide.complete(state)) item { AllSetCard(viewModel) }
            items(items, key = { it.step }) { item -> StepCard(item, viewModel) }
        }
    }
}

@Composable
private fun AllSetCard(viewModel: GuardViewModel) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("All set: the guard is running", style = MaterialTheme.typography.titleMedium)
            Text(
                "You can turn wireless debugging off and leave the Wi-Fi. Shizuku keeps running until the phone restarts. " +
                    "Don't turn off USB debugging or Developer options: that stops Shizuku.",
            )
            Button(onClick = viewModel::closeSetup) { Text("Done") }
        }
    }
}

@Composable
private fun StepCard(item: SetupItem, viewModel: GuardViewModel) {
    val text = setupText(item.step)
    val current = item.status == StepStatus.Current
    val actionable = current || item.status == StepStatus.Unknown
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = if (current) {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
        } else {
            CardDefaults.cardColors()
        },
    ) {
        Row(modifier = Modifier.padding(16.dp)) {
            Text(statusMark(item.status), style = MaterialTheme.typography.titleMedium, modifier = Modifier.width(32.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text.title, style = MaterialTheme.typography.titleMedium)
                if (actionable) Text(text.text)
                if (item.status == StepStatus.Unknown) {
                    Text(
                        "This app can't check this step. Skip it if it's done.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (actionable) StepActions(item.step, text, viewModel)
            }
        }
    }
}

@Composable
private fun StepActions(step: SetupStep, text: SetupText, viewModel: GuardViewModel) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        text.action?.let { label -> Button(onClick = { act(step, viewModel) }) { Text(label) } }
        if (step == SetupStep.StartShizuku) OutlinedButton(onClick = viewModel::reconnectShizuku) { Text("Refresh") }
    }
}

private fun act(step: SetupStep, viewModel: GuardViewModel) {
    when (step) {
        SetupStep.InstallShizuku -> viewModel.openShizukuDownload()
        SetupStep.DeveloperOptions -> viewModel.openSettings(Settings.ACTION_DEVICE_INFO_SETTINGS)
        SetupStep.JoinWifi -> viewModel.openSettings(Settings.ACTION_WIFI_SETTINGS)
        SetupStep.WirelessDebugging -> viewModel.openSettings(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
        SetupStep.StartShizuku -> viewModel.openShizuku()
        SetupStep.GrantPermission -> viewModel.requestShizukuPermission()
        SetupStep.PickProcessor -> viewModel.closeSetup()
        SetupStep.StartGuard -> viewModel.startGuard()
    }
}

private fun statusMark(status: StepStatus): String = when (status) {
    StepStatus.Done -> "✓"
    StepStatus.Current -> "→"
    StepStatus.Todo -> "○"
    StepStatus.Unknown -> "?"
}
