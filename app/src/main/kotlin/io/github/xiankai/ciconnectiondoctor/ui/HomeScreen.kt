package io.github.xiankai.ciconnectiondoctor.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import io.github.xiankai.ciconnectiondoctor.GuardViewModel
import io.github.xiankai.ciconnectiondoctor.R
import io.github.xiankai.ciconnectiondoctor.asha.HomeAction
import io.github.xiankai.ciconnectiondoctor.status.HomeText
import io.github.xiankai.ciconnectiondoctor.status.Tone
import io.github.xiankai.ciconnectiondoctor.status.currentStatus
import io.github.xiankai.ciconnectiondoctor.status.homeText

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(viewModel: GuardViewModel) {
    val showSetup by viewModel.showSetup.collectAsState()
    val showDetails by viewModel.showDetails.collectAsState()
    val guard by viewModel.guard.collectAsState()
    val privileged by viewModel.privileged.collectAsState()
    val target by viewModel.target.collectAsState()
    val publicLink by viewModel.publicLink.collectAsState()
    val guideStep by viewModel.guideStep.collectAsState()
    val pausedByGuard by viewModel.pausedByGuard.collectAsState()

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle, target) {
        val address = target ?: return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) { viewModel.watchLink(address) }
    }

    if (showSetup) {
        SetupScreen(viewModel)
        return
    }
    if (showDetails) {
        DetailsScreen(viewModel)
        return
    }

    val status = currentStatus(guard, privileged, target != null, publicLink, guideStep, pausedByGuard)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.home_title)) },
                actions = { TextButton(onClick = viewModel::openDetails) { Text(stringResource(R.string.home_details)) } },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            StatusPanel(homeText(status), status.action, viewModel::perform)
        }
    }
}

@Composable
private fun StatusPanel(text: HomeText, action: HomeAction?, onAction: (HomeAction) -> Unit) {
    val style = toneStyle(text.tone)
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = style.container, contentColor = style.content),
    ) {
        Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                style.mark?.let { Text(it, style = MaterialTheme.typography.displaySmall) }
                Text(stringResource(text.title), style = MaterialTheme.typography.headlineMedium)
            }
            text.detail?.let { Text(stringResource(it), style = MaterialTheme.typography.bodyLarge) }
            if (action != null && text.action != null) {
                Button(
                    onClick = { onAction(action) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(top = 8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = style.content, contentColor = style.container),
                ) {
                    Text(stringResource(text.action), style = MaterialTheme.typography.titleLarge)
                }
            }
        }
    }
}

private data class ToneStyle(val container: Color, val content: Color, val mark: String?)

@Composable
private fun toneStyle(tone: Tone): ToneStyle = when (tone) {
    Tone.Good -> ToneStyle(Color(0xFFD3F2D9), Color(0xFF0B4A1D), "✓")
    Tone.Busy -> ToneStyle(Color(0xFFDCE8FA), Color(0xFF0B3A6E), "…")
    Tone.NeedsYou -> ToneStyle(Color(0xFFFFE2A8), Color(0xFF5A3A00), "!")
    Tone.Neutral -> MaterialTheme.colorScheme.let { ToneStyle(it.surfaceVariant, it.onSurfaceVariant, null) }
}
