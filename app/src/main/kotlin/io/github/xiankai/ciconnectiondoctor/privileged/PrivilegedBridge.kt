package io.github.xiankai.ciconnectiondoctor.privileged

import android.bluetooth.BluetoothProfile
import android.content.ComponentName
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Binder
import android.os.IBinder
import io.github.xiankai.ciconnectiondoctor.BuildConfig
import io.github.xiankai.ciconnectiondoctor.asha.AshaState
import io.github.xiankai.ciconnectiondoctor.asha.HearingAidControl
import io.github.xiankai.ciconnectiondoctor.asha.LinkSnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import rikka.shizuku.Shizuku

object PrivilegedBridge : HearingAidControl {

    enum class Status { ShizukuNotRunning, PermissionRequired, Connecting, Ready }

    private const val PERMISSION_REQUEST_CODE = 4201
    private val UNAVAILABLE = setOf(Status.ShizukuNotRunning, Status.PermissionRequired)

    private val processToken = Binder()

    private val _status = MutableStateFlow(Status.ShizukuNotRunning)
    val status: StateFlow<Status> = _status.asStateFlow()

    @Volatile
    private var service: IHearingAidControl? = null

    @Volatile
    private var initialized = false

    private val userServiceArgs = Shizuku.UserServiceArgs(
        ComponentName(BuildConfig.APPLICATION_ID, HearingAidUserService::class.java.name),
    )
        .daemon(false)
        .processNameSuffix("asha")
        .debuggable(BuildConfig.DEBUG)
        .version(BuildConfig.VERSION_CODE)

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            service = binder?.takeIf { it.pingBinder() }?.let(IHearingAidControl.Stub::asInterface)
            runCatching { service?.exitWhenClientDies(processToken) }
            refresh()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            service = null
            refresh()
        }
    }

    @Synchronized
    fun init() {
        if (initialized) return
        initialized = true
        Shizuku.addBinderReceivedListenerSticky { refresh() }
        Shizuku.addBinderDeadListener {
            service = null
            refresh()
        }
        Shizuku.addRequestPermissionResultListener { _, _ -> refresh() }
    }

    @Synchronized
    fun refresh() {
        _status.value = when {
            !Shizuku.pingBinder() -> Status.ShizukuNotRunning
            Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED -> Status.PermissionRequired
            service != null -> Status.Ready
            else -> {
                Shizuku.bindUserService(userServiceArgs, connection)
                Status.Connecting
            }
        }
    }

    fun refreshWhileUnavailable() {
        if (_status.value in UNAVAILABLE) refresh()
    }

    fun requestPermission() {
        if (Shizuku.pingBinder()) Shizuku.requestPermission(PERMISSION_REQUEST_CODE)
    }

    private fun requireService(): IHearingAidControl =
        checkNotNull(service) { "Privileged service not ready (${_status.value})" }

    override fun snapshot(address: String): LinkSnapshot {
        val (adapterOn, linkUp, ashaState, ashaAllowed) = requireService().snapshot(address)
        return LinkSnapshot(
            linkUp = linkUp == 1,
            asha = ashaStateOf(ashaState),
            ashaAllowed = ashaAllowed == 1,
            adapterOn = adapterOn == 1,
        )
    }

    override fun setAshaAllowed(address: String, allowed: Boolean): Boolean =
        requireService().setAshaAllowed(address, allowed)

    override fun connectAsha(address: String): Boolean = requireService().connectAsha(address)

    override fun restartCochlearApp(): Boolean = requireService().restartCochlearApp()

    override fun stopCochlearApp(): Boolean = requireService().stopCochlearApp()

    override fun keepPauseWithoutGuard() = requireService().keepPauseWithoutGuard()

    private fun ashaStateOf(profileState: Int): AshaState = when (profileState) {
        BluetoothProfile.STATE_CONNECTED -> AshaState.Connected
        BluetoothProfile.STATE_CONNECTING -> AshaState.Connecting
        BluetoothProfile.STATE_DISCONNECTING -> AshaState.Disconnecting
        else -> AshaState.Disconnected
    }
}
