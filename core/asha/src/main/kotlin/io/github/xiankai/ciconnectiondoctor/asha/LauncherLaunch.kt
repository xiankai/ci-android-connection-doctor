package io.github.xiankai.ciconnectiondoctor.asha

object LauncherLaunch {
    private const val ACTION_MAIN = "android.intent.action.MAIN"
    private const val CATEGORY_LAUNCHER = "android.intent.category.LAUNCHER"

    fun resolveCommand(packageName: String): Array<String> =
        arrayOf("cmd", "package", "resolve-activity", "--brief", "-c", CATEGORY_LAUNCHER, packageName)

    fun component(resolveOutput: String): String? =
        resolveOutput.lineSequence().map(String::trim).lastOrNull(String::isNotEmpty)
            ?.takeIf { it.contains('/') && !it.contains(' ') }

    fun startCommand(component: String): Array<String> =
        arrayOf("am", "start", "-n", component, "-a", ACTION_MAIN, "-c", CATEGORY_LAUNCHER)

    fun started(exitCode: Int, startOutput: String): Boolean =
        exitCode == 0 && startOutput.lineSequence().none { it.trimStart().startsWith("Error") }
}
