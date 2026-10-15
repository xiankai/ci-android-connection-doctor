package io.github.xiankai.ciconnectiondoctor

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import io.github.xiankai.ciconnectiondoctor.ui.GuardScreen
import io.github.xiankai.ciconnectiondoctor.ui.theme.ConnectionDoctorTheme

class MainActivity : ComponentActivity() {

    private val viewModel: GuardViewModel by viewModels()

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { viewModel.refresh() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestRuntimePermissions()
        if (savedInstanceState == null && (showsSetup(intent) || !viewModel.device.value.shizukuInstalled)) viewModel.openSetup()
        setContent {
            ConnectionDoctorTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    GuardScreen(viewModel = viewModel)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (showsSetup(intent)) viewModel.openSetup()
    }

    override fun onResume() {
        super.onResume()
        viewModel.refresh()
    }

    private fun requestRuntimePermissions() {
        val needed = listOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.POST_NOTIFICATIONS)
            .filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (needed.isNotEmpty()) permissionLauncher.launch(needed.toTypedArray())
    }

    companion object {
        private const val EXTRA_SHOW_SETUP = "show_setup"

        fun intent(context: Context, showSetup: Boolean): Intent =
            Intent(context, MainActivity::class.java).putExtra(EXTRA_SHOW_SETUP, showSetup)

        private fun showsSetup(intent: Intent?): Boolean = intent?.getBooleanExtra(EXTRA_SHOW_SETUP, false) == true
    }
}
