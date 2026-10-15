package io.github.xiankai.ciconnectiondoctor.guide

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.provider.Settings

const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
const val SHIZUKU_DOWNLOAD_URL = "https://shizuku.rikka.app/download/"

private const val ADB_WIFI_ENABLED = "adb_wifi_enabled"

data class DeviceSetup(
    val shizukuInstalled: Boolean,
    val developerOptionsOn: Boolean?,
    val wifiConnected: Boolean?,
    val wirelessDebuggingOn: Boolean?,
)

object SetupProbe {

    fun read(context: Context): DeviceSetup {
        val wirelessDebuggingOn = globalFlag(context, ADB_WIFI_ENABLED)
        return DeviceSetup(
            shizukuInstalled = isShizukuInstalled(context),
            developerOptionsOn = developerOptionsOn(context, wirelessDebuggingOn),
            wifiConnected = wifiConnected(context),
            wirelessDebuggingOn = wirelessDebuggingOn,
        )
    }

    private fun developerOptionsOn(context: Context, wirelessDebuggingOn: Boolean?): Boolean? {
        val reportedOn = globalFlag(context, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED) == true
        return if (reportedOn || wirelessDebuggingOn == true) true else null // stack-reviewer-ignore: Android 17 reports development_settings_enabled and adb_enabled as 0 to apps while they are on (seen on the Pixel 8, issue #16), so a 0 can't be trusted and the step shows as unchecked rather than blocking
    }

    fun shizukuLaunchIntent(context: Context): Intent? =
        runCatching { context.packageManager.getLaunchIntentForPackage(SHIZUKU_PACKAGE) }.getOrNull()

    private fun isShizukuInstalled(context: Context): Boolean = try {
        context.packageManager.getPackageInfo(SHIZUKU_PACKAGE, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }

    private fun globalFlag(context: Context, name: String): Boolean? =
        runCatching { Settings.Global.getInt(context.contentResolver, name, 0) == 1 }.getOrNull()

    @Suppress("DEPRECATION")
    private fun wifiConnected(context: Context): Boolean? = runCatching {
        val connectivity = context.getSystemService(ConnectivityManager::class.java)
        connectivity.allNetworks.any { network -> // stack-reviewer-ignore: any joined Wi-Fi is enough, internet or not; wireless debugging only needs the phone to be a Wi-Fi client (checked on the Pixel 8, issue #16)
            connectivity.getNetworkCapabilities(network)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        }
    }.getOrNull()
}
