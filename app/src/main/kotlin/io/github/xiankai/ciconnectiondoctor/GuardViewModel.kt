package io.github.xiankai.ciconnectiondoctor

import android.Manifest
import android.annotation.SuppressLint
import android.app.Application
import android.bluetooth.BluetoothManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageManager
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import io.github.xiankai.ciconnectiondoctor.asha.GuideStep
import io.github.xiankai.ciconnectiondoctor.guard.AshaGuardService
import io.github.xiankai.ciconnectiondoctor.guard.Guard
import io.github.xiankai.ciconnectiondoctor.guard.GuardState
import io.github.xiankai.ciconnectiondoctor.guard.ProcessorPresence
import io.github.xiankai.ciconnectiondoctor.guard.TargetStore
import io.github.xiankai.ciconnectiondoctor.guide.DeviceSetup
import io.github.xiankai.ciconnectiondoctor.guide.GuideStepActivity
import io.github.xiankai.ciconnectiondoctor.guide.ManualGuideRunner
import io.github.xiankai.ciconnectiondoctor.guide.SHIZUKU_DOWNLOAD_URL
import io.github.xiankai.ciconnectiondoctor.guide.SetupProbe
import io.github.xiankai.ciconnectiondoctor.privileged.PrivilegedBridge
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class BondedDevice(val name: String, val address: String)

class GuardViewModel(application: Application) : AndroidViewModel(application) {

    private val targetStore = TargetStore(application)

    val guard: StateFlow<GuardState> = Guard.state
    val privileged: StateFlow<PrivilegedBridge.Status> = PrivilegedBridge.status
    val guideStep: StateFlow<GuideStep?> = ManualGuideRunner.step

    private val _target = MutableStateFlow(targetStore.address)
    val target: StateFlow<String?> = _target.asStateFlow()

    private val _startAtBoot = MutableStateFlow(targetStore.startAtBoot)
    val startAtBoot: StateFlow<Boolean> = _startAtBoot.asStateFlow()

    private val _startOnConnect = MutableStateFlow(targetStore.startOnConnect)
    val startOnConnect: StateFlow<Boolean> = _startOnConnect.asStateFlow()

    private val _bondedDevices = MutableStateFlow<List<BondedDevice>>(emptyList())
    val bondedDevices: StateFlow<List<BondedDevice>> = _bondedDevices.asStateFlow()

    private val _device = MutableStateFlow(SetupProbe.read(application))
    val device: StateFlow<DeviceSetup> = _device.asStateFlow()

    private val _showSetup = MutableStateFlow(false)
    val showSetup: StateFlow<Boolean> = _showSetup.asStateFlow()

    init {
        refresh()
    }

    fun refreshDevice() {
        _device.value = SetupProbe.read(getApplication())
    }

    fun openSetup() {
        refreshDevice()
        _showSetup.value = true
    }

    fun closeSetup() {
        _showSetup.value = false
    }

    fun openShizuku() = launchExternal(SetupProbe.shizukuLaunchIntent(getApplication()))

    fun openSettings(action: String) = launchExternal(Intent(action))

    fun openShizukuDownload() = launchExternal(Intent(Intent.ACTION_VIEW, Uri.parse(SHIZUKU_DOWNLOAD_URL)))

    private fun launchExternal(intent: Intent?) {
        if (intent == null) return
        runCatching { getApplication<Application>().startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            .onFailure { if (it !is ActivityNotFoundException && it !is SecurityException) throw it }
    }

    @SuppressLint("MissingPermission")
    fun refresh() {
        PrivilegedBridge.refresh()
        refreshDevice()
        val app = getApplication<Application>()
        if (app.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) return
        val adapter = app.getSystemService(BluetoothManager::class.java)?.adapter ?: return
        val devices = adapter.bondedDevices.orEmpty()
            .map { BondedDevice(it.name ?: it.address, it.address) }
            .sortedBy { it.name }
        _bondedDevices.value = devices
        if (_target.value == null) {
            devices.firstOrNull { it.name.contains("Hearing", ignoreCase = true) }?.let { selectTarget(it.address) }
        }
    }

    fun selectTarget(address: String) {
        val previous = _target.value
        if (previous != null && previous != address && _startOnConnect.value) {
            ProcessorPresence.stopObserving(getApplication(), previous)
            saveStartOnConnect(false)
        }
        targetStore.address = address
        _target.value = address
    }

    fun setStartOnConnect(enabled: Boolean, launchAssociation: (IntentSender) -> Unit) {
        val address = _target.value ?: return
        val app = getApplication<Application>()
        when {
            !enabled -> {
                ProcessorPresence.stopObserving(app, address)
                saveStartOnConnect(false)
            }
            ProcessorPresence.isAssociated(app, address) -> startObserving(address)
            else -> ProcessorPresence.associate(app, address, launchAssociation) { error ->
                Guard.note("Couldn't link the processor for starting on connect: $error")
            }
        }
    }

    fun onAssociationResult(accepted: Boolean) {
        val address = _target.value ?: return
        if (accepted) startObserving(address) else Guard.note("Start on connect not set up: the processor wasn't linked")
    }

    private fun startObserving(address: String) {
        ProcessorPresence.observe(getApplication(), address)
            .onSuccess { saveStartOnConnect(true) }
            .onFailure { Guard.note("Couldn't watch for the processor connecting: ${it.message}") }
    }

    private fun saveStartOnConnect(enabled: Boolean) {
        targetStore.startOnConnect = enabled
        _startOnConnect.value = enabled
    }

    fun setStartAtBoot(enabled: Boolean) {
        targetStore.startAtBoot = enabled
        _startAtBoot.value = enabled
    }

    fun reconnectShizuku() {
        refresh()
        if (privileged.value == PrivilegedBridge.Status.ShizukuNotRunning) ProcessRestart.restart(getApplication(), showSetup = _showSetup.value)
    }

    fun requestShizukuPermission() = PrivilegedBridge.requestPermission()

    fun startGuard() = AshaGuardService.send(getApplication())

    fun stopGuard() = AshaGuardService.send(getApplication(), AshaGuardService.ACTION_STOP)

    fun handOff() = AshaGuardService.send(getApplication(), AshaGuardService.ACTION_HAND_OFF)

    fun backToPhone() = AshaGuardService.send(getApplication(), AshaGuardService.ACTION_BACK_TO_PHONE)

    fun restartCochlearApp() {
        val address = _target.value ?: return
        Guard.restartCochlearApp(PrivilegedBridge, address)
    }

    fun openGuideStep(step: GuideStep) {
        if (step == GuideStep.ConnectStreaming) {
            getApplication<Application>().startActivity(GuideStepActivity.intent(getApplication(), step))
        } else {
            openSetup()
        }
    }

    fun dismissGuide() = ManualGuideRunner.dismiss()

    fun clearLog() = Guard.clearLog()
}
