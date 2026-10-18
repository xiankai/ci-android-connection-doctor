package io.github.xiankai.ciconnectiondoctor.ui

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.xiankai.ciconnectiondoctor.BondedDevice
import io.github.xiankai.ciconnectiondoctor.GuardViewModel
import io.github.xiankai.ciconnectiondoctor.R
import io.github.xiankai.ciconnectiondoctor.guard.GuardState
import io.github.xiankai.ciconnectiondoctor.guard.phaseLabel
import io.github.xiankai.ciconnectiondoctor.guide.SHOW_STEPS
import io.github.xiankai.ciconnectiondoctor.privileged.PrivilegedBridge
import io.github.xiankai.ciconnectiondoctor.asha.Mode
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailsScreen(viewModel: GuardViewModel) {
    val guard by viewModel.guard.collectAsState()
    val privileged by viewModel.privileged.collectAsState()
    val target by viewModel.target.collectAsState()
    val devices by viewModel.bondedDevices.collectAsState()
    val startAtBoot by viewModel.startAtBoot.collectAsState()
    val startOnConnect by viewModel.startOnConnect.collectAsState()

    BackHandler(onBack = viewModel::closeDetails)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.home_details)) },
                actions = { TextButton(onClick = viewModel::closeDetails) { Text("Close") } },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { ShizukuCard(privileged, viewModel) }
            item { TargetCard(devices, target, viewModel::selectTarget) }
            item { StatusCard(guard, privileged) }
            item { Controls(guard, privileged, target, startAtBoot, startOnConnect, viewModel) }
            item {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Event log", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    TextButton(onClick = viewModel::clearLog) { Text("Clear") }
                }
            }
            items(guard.log.asReversed(), key = { "${it.timestampMillis}-${it.message.hashCode()}" }) { entry ->
                LogRow(entry.timestampMillis, entry.message)
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun ShizukuCard(status: PrivilegedBridge.Status, viewModel: GuardViewModel) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Helper app (Shizuku)", style = MaterialTheme.typography.titleMedium)
            Text(
                when (status) {
                    PrivilegedBridge.Status.ShizukuNotRunning -> "Not running. Until it runs, the guard can only advise."
                    PrivilegedBridge.Status.PermissionRequired -> "Running, permission needed."
                    PrivilegedBridge.Status.Connecting -> "Starting the privileged helper…"
                    PrivilegedBridge.Status.Ready -> "Ready."
                },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (status == PrivilegedBridge.Status.PermissionRequired) {
                    Button(onClick = viewModel::requestShizukuPermission) { Text("Grant permission") }
                }
                OutlinedButton(onClick = viewModel::reconnectShizuku) { Text("Refresh") }
                if (status != PrivilegedBridge.Status.Ready) {
                    TextButton(onClick = viewModel::openSetup) { Text(SHOW_STEPS) }
                }
            }
        }
    }
}

@Composable
private fun TargetCard(devices: List<BondedDevice>, target: String?, onSelect: (String) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Sound processor", style = MaterialTheme.typography.titleMedium)
            if (devices.isEmpty()) Text("No bonded devices visible (Bluetooth permission needed).")
            devices.forEach { device ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(selected = device.address == target, onClick = { onSelect(device.address) }),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = device.address == target, onClick = { onSelect(device.address) })
                    Column {
                        Text(device.name)
                        Text(device.address, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusCard(guard: GuardState, privileged: PrivilegedBridge.Status) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(statusTitle(guard, privileged), style = MaterialTheme.typography.titleMedium)
            guard.snapshot?.let {
                Text("Link: ${if (it.linkUp) "up" else "down"}")
                Text("ASHA: ${it.asha} (${if (it.ashaAllowed) "allowed" else "paused"})")
            }
            Text("Mode: ${if (guard.mode == Mode.HandedOff) "handed off" else "auto"}")
            Text("Recoveries: ${guard.recoveries}")
        }
    }
}

private fun statusTitle(guard: GuardState, privileged: PrivilegedBridge.Status): String = when {
    !guard.running -> "Guard stopped"
    guard.phase != null -> phaseLabel(guard.phase)
    privileged != PrivilegedBridge.Status.Ready -> "Advice only (Shizuku not running)"
    else -> "Starting"
}

@Composable
private fun Controls(
    guard: GuardState,
    privileged: PrivilegedBridge.Status,
    target: String?,
    startAtBoot: Boolean,
    startOnConnect: Boolean,
    viewModel: GuardViewModel,
) {
    val ready = privileged == PrivilegedBridge.Status.Ready && target != null
    val association = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
        viewModel.onAssociationResult(it.resultCode == Activity.RESULT_OK)
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (guard.running) {
                OutlinedButton(onClick = viewModel::stopGuard, modifier = Modifier.weight(1f)) { Text("Stop guard") }
            } else {
                Button(onClick = viewModel::startGuard, enabled = target != null, modifier = Modifier.weight(1f)) { Text("Start guard") }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (guard.mode == Mode.HandedOff) {
                Button(onClick = viewModel::backToPhone, enabled = ready, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.action_bring_sound_back)) }
            } else {
                OutlinedButton(onClick = viewModel::handOff, enabled = ready, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.hand_off)) }
            }
        }
        OutlinedButton(onClick = viewModel::restartCochlearApp, enabled = ready, modifier = Modifier.fillMaxWidth()) {
            Text("Reopen Cochlear app")
        }
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Start when the processor connects")
                Text(
                    "The guard stops by itself once streaming holds.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = startOnConnect,
                onCheckedChange = { enabled ->
                    viewModel.setStartOnConnect(enabled) { sender -> association.launch(IntentSenderRequest.Builder(sender).build()) }
                },
                enabled = target != null,
            )
        }
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Start the guard at boot")
                Text(
                    "Without Shizuku it can only advise.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = startAtBoot, onCheckedChange = viewModel::setStartAtBoot, enabled = target != null)
        }
    }
}

@Composable
private fun LogRow(timestampMillis: Long, message: String) {
    val time = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date(timestampMillis))
    Column(modifier = Modifier.padding(vertical = 6.dp)) {
        Text(time, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(message, style = MaterialTheme.typography.bodyMedium)
    }
}
