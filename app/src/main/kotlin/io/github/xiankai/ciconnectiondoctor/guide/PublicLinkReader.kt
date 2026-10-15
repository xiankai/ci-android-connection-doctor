package io.github.xiankai.ciconnectiondoctor.guide

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import io.github.xiankai.ciconnectiondoctor.asha.AshaState
import io.github.xiankai.ciconnectiondoctor.asha.PublicLinkState

@SuppressLint("MissingPermission")
class PublicLinkReader(context: Context, private val address: String) {

    private val appContext = context.applicationContext
    private val manager = appContext.getSystemService(BluetoothManager::class.java)

    @Volatile
    private var lastLinkDropMs: Long? = null

    private val aclReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
            if (device?.address == address) lastLinkDropMs = System.currentTimeMillis()
        }
    }

    @Volatile
    private var hearingAid: BluetoothProfile? = null

    private val listener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
            hearingAid = proxy
        }

        override fun onServiceDisconnected(profile: Int) {
            hearingAid = null
        }
    }

    init {
        manager.adapter?.getProfileProxy(appContext, listener, BluetoothProfile.HEARING_AID)
        ContextCompat.registerReceiver(
            appContext,
            aclReceiver,
            IntentFilter(BluetoothDevice.ACTION_ACL_DISCONNECTED),
            ContextCompat.RECEIVER_EXPORTED, // stack-reviewer-ignore: ACL_DISCONNECTED is a protected broadcast, so only the system can send it; tested on-device with this flag
        )
    }

    fun read(): PublicLinkState? {
        val adapter = manager.adapter
        if (adapter?.state != BluetoothAdapter.STATE_ON) return PublicLinkState(linkUp = false, asha = AshaState.Disconnected, adapterOn = false)
        val profile = hearingAid ?: return null
        val device = adapter.getRemoteDevice(address)
        return PublicLinkState(
            linkUp = manager.getConnectedDevices(BluetoothProfile.GATT).any { it.address == address },
            asha = ashaStateOf(profile.getConnectionState(device)),
            lastLinkDropMs = lastLinkDropMs,
        )
    }

    private fun ashaStateOf(profileState: Int): AshaState = when (profileState) {
        BluetoothProfile.STATE_CONNECTED -> AshaState.Connected
        BluetoothProfile.STATE_CONNECTING -> AshaState.Connecting
        BluetoothProfile.STATE_DISCONNECTING -> AshaState.Disconnecting
        else -> AshaState.Disconnected
    }

    fun close() {
        runCatching { appContext.unregisterReceiver(aclReceiver) }
        hearingAid?.let { manager.adapter?.closeProfileProxy(BluetoothProfile.HEARING_AID, it) }
        hearingAid = null
    }
}
