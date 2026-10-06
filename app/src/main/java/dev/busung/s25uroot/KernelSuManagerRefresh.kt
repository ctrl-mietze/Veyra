package ctrl.mietze.veyraroot

import android.content.Context

/**
 * Stops an open KernelSU manager so the next time it is opened it reads the kernel that is there now.
 *
 * A manager reads the kernel when it starts: whether the driver answers, which modules are mounted,
 * whether its daemon is reachable. One that was already open when this app loaded the module keeps
 * showing what it read *before* the load - no root, no modules, "KernelSU not installed" - which is
 * the same picture as a load that failed, and it is the report this app keeps getting handed when the
 * load worked.
 *
 * Stopping it costs nothing worth weighing: the allowlist lives in KernelSU's own storage, the modules
 * live on disk and in the kernel, and neither is in that process. The app deliberately does not
 * relaunch it, unlike the reference implementation that force-stops and starts the manager again -
 * pulling another app to the front the moment a run finishes would take the screen away from the
 * result the user is reading, and the stale state is cured by the next open either way.
 *
 * Best effort throughout: a manager that is not installed, not running, or behind a shell this device
 * will not hand over is not a failure of the run that just succeeded.
 */
internal object KernelSuManagerRefresh {

    /** Printed by the shell when the manager was running and is now stopped. */
    private const val STOPPED_MARKER = "RMG_MANAGER_STOPPED"

    /**
     * Stops the managers that are running, or the ones of [flavor] when it is named.
     *
     * Naming a flavour is what the run does, because a run knows which project's module it just
     * loaded. The module screens pass nothing, because a module can be re-applied without the app
     * knowing which project's manager happens to be installed, and stopping a manager that is not
     * running does nothing at all.
     *
     * Returns the packages that were actually stopped, so a log line can be true rather than hopeful.
     */
    fun afterLoad(context: Context, flavor: KernelSuFlavor? = null): List<String> {
        val managers = if (flavor == null) {
            KernelSuManager.installedManagers(context)
        } else {
            listOfNotNull(KernelSuManager.installedFor(context, flavor))
        }
        return managers.mapNotNull { manager -> stopIfRunning(manager.packageName) }
    }

    /**
     * One shell call per manager: ask whether it is running, and stop it only if it is.
     */
    private fun stopIfRunning(packageName: String): String? {
        val command = stopCommand(packageName)
        val result = KernelSuRuntime.rootShell(command) ?: KernelSuRuntime.unprivilegedShell(command)
        return if (stopped(result)) packageName else null
    }

    /**
     * The command that stops [packageName], if and only if it is running.
     *
     * The reading is in the same command as the action because the two have to agree - a separate
     * check would be a second answer to a question that can change in between - and because it keeps
     * the log honest: the marker is printed only by the branch that stopped something. Both uses of
     * the name are quoted, because a manager's package is a string this app reads off the device and
     * not one it chose: KernelSU-Next's spoofed manager build rewrites its own package name.
     */
    internal fun stopCommand(packageName: String): String {
        val quoted = shellQuote(packageName)
        return "if pidof $quoted >/dev/null 2>&1; then " +
            "am force-stop $quoted && echo $STOPPED_MARKER; fi"
    }

    /**
     * Whether a shell's answer says a running manager was stopped: its own marker, from a command that
     * succeeded. A marker without a zero exit is a `force-stop` that failed after printing nothing, and
     * a zero exit without a marker is a manager that was not running - neither of which is a refresh.
     */
    internal fun stopped(result: ShizukuController.ShellResult?): Boolean =
        result != null && result.exitCode == 0 && result.output.contains(STOPPED_MARKER)
}
