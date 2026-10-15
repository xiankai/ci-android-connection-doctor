package io.github.xiankai.ciconnectiondoctor.privileged

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothProfile
import android.content.AttributionSource
import android.content.Context
import android.content.ContextWrapper
import android.os.IBinder
import android.os.Process
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.system.exitProcess

@SuppressLint("MissingPermission", "PrivateApi", "DiscouragedPrivateApi")
class HearingAidUserService : IHearingAidControl.Stub() {

    private class ShellContext(base: Context) : ContextWrapper(base) {
        private val attribution = AttributionSource.Builder(Process.SHELL_UID).setPackageName(SHELL_PACKAGE).build()

        override fun getPackageName(): String = SHELL_PACKAGE
        override fun getOpPackageName(): String = SHELL_PACKAGE
        override fun getAttributionSource(): AttributionSource = attribution
        override fun getApplicationContext(): Context = this
    }

    private val context: ShellContext by lazy { ShellContext(systemContext()) }
    private val adapter: BluetoothAdapter by lazy { shellAdapter(context) }

    @Volatile
    private var hearingAid: BluetoothProfile? = null

    @Volatile
    private var pausedAddress: String? = null

    init {
        Runtime.getRuntime().addShutdownHook(Thread(::allowPausedAsha))
    }

    override fun destroy() {
        exitProcess(0)
    }

    override fun exitWhenClientDies(client: IBinder) {
        client.linkToDeath({ exitProcess(0) }, 0)
    }

    override fun keepPauseWithoutGuard() {
        pausedAddress = null
    }

    private fun allowPausedAsha() {
        val address = pausedAddress ?: return
        runCatching { setAshaAllowed(address, true) }
    }

    override fun snapshot(address: String): IntArray {
        if (adapter.state != BluetoothAdapter.STATE_ON) return intArrayOf(0, 0, BluetoothProfile.STATE_DISCONNECTED, 1)
        val device = device(address)
        val linkUp = BluetoothDevice::class.java.getMethod("isConnected").invoke(device) as Boolean
        val profile = hearingAidProxy()
        val policy = profile.javaClass.getMethod("getConnectionPolicy", BluetoothDevice::class.java)
            .invoke(profile, device) as Int
        return intArrayOf(
            1,
            if (linkUp) 1 else 0,
            profile.getConnectionState(device),
            if (policy == CONNECTION_POLICY_FORBIDDEN) 0 else 1,
        )
    }

    override fun setAshaAllowed(address: String, allowed: Boolean): Boolean {
        val profile = hearingAidProxy()
        val policy = if (allowed) CONNECTION_POLICY_ALLOWED else CONNECTION_POLICY_FORBIDDEN
        val accepted = profile.javaClass
            .getMethod("setConnectionPolicy", BluetoothDevice::class.java, Int::class.javaPrimitiveType)
            .invoke(profile, device(address), policy) as Boolean
        if (accepted) pausedAddress = address.takeUnless { allowed }
        return accepted
    }

    override fun connectAsha(address: String): Boolean {
        val profile = hearingAidProxy()
        return profile.javaClass.getMethod("connect", BluetoothDevice::class.java)
            .invoke(profile, device(address)) as Boolean
    }

    override fun restartCochlearApp(): Boolean {
        val launch = arrayOf("monkey", "-p", COCHLEAR_APP_PACKAGE, "-c", "android.intent.category.LAUNCHER", "1")
        return stopCochlearApp() && Runtime.getRuntime().exec(launch).waitFor() == 0 // stack-reviewer-ignore: a false return is logged by the guard and restarts are rate-limited to one per 30 s
    }

    override fun stopCochlearApp(): Boolean =
        Runtime.getRuntime().exec(arrayOf("am", "force-stop", COCHLEAR_APP_PACKAGE)).waitFor() == 0

    private fun device(address: String): BluetoothDevice = adapter.getRemoteDevice(address)

    private fun hearingAidProxy(): BluetoothProfile {
        hearingAid?.takeIf { adapter.state == BluetoothAdapter.STATE_ON }?.let { return it }
        val ready = CountDownLatch(1)
        adapter.getProfileProxy(context, object : BluetoothProfile.ServiceListener {
            override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                hearingAid = proxy
                ready.countDown()
            }

            override fun onServiceDisconnected(profile: Int) {
                hearingAid = null
            }
        }, BluetoothProfile.HEARING_AID)
        check(ready.await(PROXY_TIMEOUT_SECONDS, TimeUnit.SECONDS)) { "hearing aid profile proxy did not connect" }
        return checkNotNull(hearingAid)
    }

    private companion object {
        const val SHELL_PACKAGE = "com.android.shell"
        const val COCHLEAR_APP_PACKAGE = "com.cochlear.clientremote"
        const val CONNECTION_POLICY_FORBIDDEN = 0
        const val CONNECTION_POLICY_ALLOWED = 100
        const val PROXY_TIMEOUT_SECONDS = 5L

        fun systemContext(): Context {
            val threadClass = Class.forName("android.app.ActivityThread")
            val existing = threadClass.getMethod("currentActivityThread").invoke(null)
            val thread = existing ?: threadClass.getDeclaredConstructor().apply { isAccessible = true }.newInstance().also {
                threadClass.getDeclaredField("sCurrentActivityThread").apply { isAccessible = true }.set(null, it)
            }
            return threadClass.getDeclaredMethod("getSystemContext").invoke(thread) as Context
        }

        fun shellAdapter(context: ShellContext): BluetoothAdapter {
            registerBluetoothServiceManager()
            val binder = Class.forName("android.os.ServiceManager")
                .getMethod("getService", String::class.java)
                .invoke(null, "bluetooth_manager") as IBinder
            val managerInterface = Class.forName("android.bluetooth.IBluetoothManager")
            val manager = Class.forName("android.bluetooth.IBluetoothManager\$Stub")
                .getMethod("asInterface", IBinder::class.java)
                .invoke(null, binder)
            val ctor = BluetoothAdapter::class.java.getDeclaredConstructor(
                managerInterface,
                Context::class.java,
                AttributionSource::class.java,
            )
            ctor.isAccessible = true
            return ctor.newInstance(manager, context, context.attributionSource)
        }

        fun registerBluetoothServiceManager() {
            val serviceManagerClass = Class.forName("android.os.BluetoothServiceManager")
            val initializer = Class.forName("android.bluetooth.BluetoothFrameworkInitializer")
            try {
                initializer.getMethod("setBluetoothServiceManager", serviceManagerClass)
                    .invoke(null, serviceManagerClass.getConstructor().newInstance())
            } catch (e: java.lang.reflect.InvocationTargetException) {
                if (e.cause !is IllegalStateException) throw e
            }
        }
    }
}
