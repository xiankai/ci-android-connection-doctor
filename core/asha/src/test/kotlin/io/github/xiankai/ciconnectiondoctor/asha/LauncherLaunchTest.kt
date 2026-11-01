package io.github.xiankai.ciconnectiondoctor.asha

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LauncherLaunchTest {

    private val cochlear = "com.cochlear.clientremote"
    private val launcherActivity = "com.cochlear.clientremote/.MainActivity"

    @Test
    fun resolvesTheLauncherActivityOfThePackage() {
        assertArrayEquals(
            arrayOf("cmd", "package", "resolve-activity", "--brief", "-c", "android.intent.category.LAUNCHER", cochlear),
            LauncherLaunch.resolveCommand(cochlear),
        )
    }

    @Test
    fun takesTheComponentFromTheLastLine() {
        val output = "priority=0 preferredOrder=0 match=0x108000 specificIndex=-1 isDefault=false\n$launcherActivity\n"
        assertEquals(launcherActivity, LauncherLaunch.component(output))
    }

    @Test
    fun ignoresTrailingBlankLinesAndCarriageReturns() {
        val output = "priority=0 preferredOrder=0 match=0x108000\r\n$launcherActivity\r\n\r\n"
        assertEquals(launcherActivity, LauncherLaunch.component(output))
    }

    @Test
    fun noActivityFoundIsNoComponent() {
        assertNull(LauncherLaunch.component("No activity found\n"))
    }

    @Test
    fun emptyOutputIsNoComponent() {
        assertNull(LauncherLaunch.component(""))
    }

    @Test
    fun startsTheComponentAsAHomeScreenTapWould() {
        assertArrayEquals(
            arrayOf(
                "am", "start", "-n", launcherActivity,
                "-a", "android.intent.action.MAIN", "-c", "android.intent.category.LAUNCHER",
            ),
            LauncherLaunch.startCommand(launcherActivity),
        )
    }

    @Test
    fun startingIsSuccess() {
        assertTrue(LauncherLaunch.started(0, "Starting: Intent { act=android.intent.action.MAIN cmp=$launcherActivity }\n"))
    }

    @Test
    fun alreadyRunningWarningIsSuccess() {
        val output = "Starting: Intent { cmp=$launcherActivity }\n" +
            "Warning: Activity not started, intent has been delivered to currently running top-most instance.\n"
        assertTrue(LauncherLaunch.started(0, output))
    }

    @Test
    fun errorLineIsFailureEvenWithExitZero() {
        val output = "Starting: Intent { cmp=$launcherActivity }\n" +
            "Error type 3\nError: Activity class {$launcherActivity} does not exist.\n"
        assertFalse(LauncherLaunch.started(0, output))
    }

    @Test
    fun nonZeroExitIsFailure() {
        assertFalse(LauncherLaunch.started(1, ""))
    }
}
