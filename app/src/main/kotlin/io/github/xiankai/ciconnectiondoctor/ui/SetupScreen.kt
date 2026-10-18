package io.github.xiankai.ciconnectiondoctor.ui

import android.app.Activity
import android.content.IntentSender
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
        "Install the helper app (Shizuku)",
        "This app needs it to bring phone sound back. Install it, then come back here.",
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
        "Start the helper app (Shizuku)",
        "After a restart: open Shizuku and tap Start under \"Start via Wireless debugging\".\n\n" +
            "The first time only: tap Pairing in Shizuku, then Pair device with pairing code on the Wireless debugging page. " +
            "Type the six digits into Shizuku's notification (Enter pairing code), then tap Start.",
        "Open Shizuku",
    )
    SetupStep.GrantPermission -> SetupText(
        "Allow this app in the helper app",
        "Tap Allow all the time. If no dialog appears, open Shizuku's app list and allow this app there.",
        "Grant permission",
    )
    SetupStep.PickProcessor -> SetupText(
        "Pick your sound processor",
        "Choose it from the list under Sound processor.",
        "Open the list",
    )
    SetupStep.AutoStart -> SetupText(
        "Let this app start by itself",
        "Then it brings phone sound back whenever your processor connects or the phone restarts. Android asks once to link this app to your processor: tap Allow.",
        "Turn on",
    )
}

private fun setupState(
    device: DeviceSetup,
    privileged: PrivilegedBridge.Status,
    processorPicked: Boolean,
    autoStartOn: Boolean,
) = SetupState(
    shizukuInstalled = device.shizukuInstalled,
    developerOptionsOn = device.developerOptionsOn,
    wifiConnected = device.wifiConnected,
    wirelessDebuggingOn = device.wirelessDebuggingOn,
    shizukuRunning = privileged != PrivilegedBridge.Status.ShizukuNotRunning,
    permissionGranted = privileged == PrivilegedBridge.Status.Connecting || privileged == PrivilegedBridge.Status.Ready,
    processorPicked = processorPicked,
    autoStartOn = autoStartOn,
    )

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupScreen(viewModel: GuardViewModel) {
    val device by viewModel.device.collectAsState()
    val privileged by viewModel.privileged.collectAsState()
    val target by viewModel.target.collectAsState()
    val autoStartOn by viewModel.startOnConnect.collectAsState()
    val state = setupState(device, privileged, target != null, autoStartOn)
    val items = SetupGuide.items(state)
    var showAll by rememberSaveable { mutableStateOf(false) }

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
        val focused = items.firstOrNull { it.status == StepStatus.Current }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (focused == null) {
                item { AllSetCard(viewModel) }
            } else {
                item { FocusedStep(focused.step, SetupGuide.stepsLeft(state), viewModel) }
            }
            item {
                TextButton(onClick = { showAll = !showAll }) { Text(if (showAll) "Hide all steps" else "Show all steps") }
            }
            if (showAll) items(items, key = { it.step }) { item -> StepCard(item, viewModel) }
        }
    }
}

@Composable
private fun FocusedStep(step: SetupStep, stepsLeft: Int, viewModel: GuardViewModel) {
    val text = setupText(step)
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                if (stepsLeft <= 1) "Last step" else "$stepsLeft steps left",
                style = MaterialTheme.typography.labelLarge,
            )
            Text(text.title, style = MaterialTheme.typography.headlineSmall)
            Text(text.text, style = MaterialTheme.typography.bodyLarge)
            StepActions(step, text, viewModel, big = true)
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
            Text("All set", style = MaterialTheme.typography.titleMedium)
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
private fun StepActions(step: SetupStep, text: SetupText, viewModel: GuardViewModel, big: Boolean = false) {
    val association = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
        viewModel.onAssociationResult(it.resultCode == Activity.RESULT_OK)
    }
    val launchAssociation = { sender: IntentSender -> association.launch(IntentSenderRequest.Builder(sender).build()) }
    if (big) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            text.action?.let { label ->
                Button(
                    onClick = { act(step, viewModel, launchAssociation) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
                ) { Text(label, style = MaterialTheme.typography.titleLarge) }
            }
            if (step == SetupStep.StartShizuku) {
                OutlinedButton(onClick = viewModel::reconnectShizuku, modifier = Modifier.fillMaxWidth()) { Text("I've started it") }
            }
        }
        return
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        text.action?.let { label -> Button(onClick = { act(step, viewModel, launchAssociation) }) { Text(label) } }
        if (step == SetupStep.StartShizuku) OutlinedButton(onClick = viewModel::reconnectShizuku) { Text("Refresh") }
    }
}

private fun act(step: SetupStep, viewModel: GuardViewModel, launchAssociation: (IntentSender) -> Unit) {
    when (step) {
        SetupStep.InstallShizuku -> viewModel.openShizukuDownload()
        SetupStep.DeveloperOptions -> viewModel.openSettings(Settings.ACTION_DEVICE_INFO_SETTINGS)
        SetupStep.JoinWifi -> viewModel.openSettings(Settings.ACTION_WIFI_SETTINGS)
        SetupStep.WirelessDebugging -> viewModel.openSettings(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
        SetupStep.StartShizuku -> viewModel.openShizuku()
        SetupStep.GrantPermission -> viewModel.requestShizukuPermission()
        SetupStep.PickProcessor -> viewModel.closeSetupToDetails()
        SetupStep.AutoStart -> viewModel.setAutoFix(true, launchAssociation)
    }
}

private fun statusMark(status: StepStatus): String = when (status) {
    StepStatus.Done -> "✓"
    StepStatus.Current -> "→"
    StepStatus.Todo -> "○"
    StepStatus.Unknown -> "?"
}
