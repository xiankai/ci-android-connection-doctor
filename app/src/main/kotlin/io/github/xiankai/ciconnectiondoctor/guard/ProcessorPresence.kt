package io.github.xiankai.ciconnectiondoctor.guard

import android.companion.AssociationRequest
import android.companion.BluetoothDeviceFilter
import android.companion.CompanionDeviceManager
import android.content.Context
import android.content.IntentSender
import android.os.Build

object ProcessorPresence {

    fun isAssociated(context: Context, address: String): Boolean {
        val manager = manager(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return manager.myAssociations.any { it.deviceMacAddress?.toString().equals(address, ignoreCase = true) }
        }
        @Suppress("DEPRECATION")
        return manager.associations.any { it.equals(address, ignoreCase = true) }
    }

    fun associate(context: Context, address: String, onPending: (IntentSender) -> Unit, onFailure: (String) -> Unit) {
        val request = AssociationRequest.Builder()
            .addDeviceFilter(BluetoothDeviceFilter.Builder().setAddress(address).build())
            .setSingleDevice(true)
            .build()
        val callback = object : CompanionDeviceManager.Callback() {
            override fun onAssociationPending(intentSender: IntentSender) = onPending(intentSender)

            @Suppress("OVERRIDE_DEPRECATION")
            override fun onDeviceFound(intentSender: IntentSender) = onPending(intentSender)

            override fun onFailure(error: CharSequence?) = onFailure(error?.toString() ?: "unknown error")
        }
        manager(context).associate(request, callback, null)
    }

    fun observe(context: Context, address: String): Result<Unit> =
        runCatching { manager(context).startObservingDevicePresence(address) }

    fun stopObserving(context: Context, address: String) {
        runCatching { manager(context).stopObservingDevicePresence(address) }
    }

    private fun manager(context: Context): CompanionDeviceManager =
        context.getSystemService(CompanionDeviceManager::class.java)
}
