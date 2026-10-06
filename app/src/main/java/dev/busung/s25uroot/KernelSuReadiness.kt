package ctrl.mietze.veyraroot

/** Which mount namespace the module mounts were counted in. */
internal enum class MountNamespace {
    /** The shell is already in init's namespace, so counting here counts the right thing. */
    Same,

    /** Init's namespace differs and was entered to do the count. */
    Entered,

    /** Init's namespace differs and could not be entered, so the count is unknown. */
    Unavailable,
}

/**
 * What a post-root probe found about the modules a restart would pick up.
 *
 * [expected] is what the module directories say should be mounted - every enabled module carrying a
 * `system` overlay - and [mounted] is how many of those mounts are actually visible. Counting the
 * directory and counting the mounts are different questions, which is the whole point: a module that
 * is enabled but not mounted is one a Zygote restart cannot load.
 */
internal data class ModuleMountState(
    val bootToken: String,
    val uidZero: Boolean,
    val namespace: MountNamespace,
    val expected: Int,
    val mounted: Int,
) {
    /** Whether every module that should be mounted is, as far as this probe could tell. */
    val mountsComplete: Boolean
        get() = when {
            expected == 0 -> true
            namespace == MountNamespace.Unavailable -> false
            else -> mounted >= expected
        }

    /** Whether anything was positively found missing, as opposed to not being checkable. */
    val definitelyMissingMounts: Boolean
        get() = namespace != MountNamespace.Unavailable && expected > 0 && mounted < expected
}

/**
 * Reads KernelSU's module mounts from init's mount namespace.
 *
 * Control being reachable is not the same as the module mounts being in place, and the two are
 * deliberately separate contracts: a run needs control, while anything that repairs or reloads
 * modules has to check its own mount result. That check cannot be made from wherever the command
 * happens to run, because module mounts are per-mount-namespace - the same reason a userspace
 * transition has to be asked of init rather than done from here. So the probe compares init's
 * namespace with its own, and enters init's when they differ, using `nsenter` if the device has it.
 *
 * The count comes from the modules themselves rather than from a list kept here: a module is expected
 * to be mounted when it is enabled and carries a `system` overlay, so installing or disabling one
 * changes the expectation without this app having to know which modules exist.
 */
internal object KernelSuReadiness {

    /** The single line the shell prints, e.g. `boot=… uid=0 ns=entered want=2 got=2`. */
    internal const val REPORT_PREFIX = "RMG_MODULE_MOUNTS"

    /**
     * The shared half: sets `rmg_boot`, `rmg_uid`, `rmg_ns`, `rmg_want` and `rmg_got`.
     *
     * Shared with the scripts that repair modules rather than written twice, because "what counts as
     * a module that should be mounted" is one rule, and two copies of it would be two rules the day
     * one of them changed.
     */
    internal fun variables(): String = """
        rmg_boot=$(cat /proc/sys/kernel/random/boot_id 2>/dev/null)
        rmg_uid=$(id -u 2>/dev/null)
        rmg_init_ns=$(readlink /proc/1/ns/mnt 2>/dev/null)
        rmg_self_ns=$(readlink /proc/self/ns/mnt 2>/dev/null)
        rmg_want=0
        for rmg_module in /data/adb/modules/*/; do
            [ -e "${'$'}rmg_module/disable" ] && continue
            [ -e "${'$'}rmg_module/remove" ] && continue
            [ -d "${'$'}rmg_module/system" ] && rmg_want=$((rmg_want + 1))
        done
        rmg_count='grep -c /data/adb/modules /proc/self/mountinfo 2>/dev/null || true'
        if [ "${'$'}rmg_init_ns" = "${'$'}rmg_self_ns" ]; then
            rmg_ns=same
            rmg_got=$(eval "${'$'}rmg_count")
        elif command -v nsenter >/dev/null 2>&1 && nsenter -t 1 -m -- true 2>/dev/null; then
            rmg_ns=entered
            rmg_got=$(nsenter -t 1 -m -- /system/bin/sh -c "${'$'}rmg_count" 2>/dev/null)
        else
            rmg_ns=unavailable
            rmg_got=-1
        fi
    """.trimIndent()

    internal fun command(): String = variables() + "\n" + """
        printf '%s boot=%s uid=%s ns=%s want=%s got=%s\n' \
            '$REPORT_PREFIX' "${'$'}rmg_boot" "${'$'}rmg_uid" "${'$'}rmg_ns" "${'$'}rmg_want" "${'$'}rmg_got"
    """.trimIndent() + "\n"

    /**
     * The same reading, as a condition a script can refuse on: true when nothing was found missing.
     *
     * An unreadable namespace passes rather than fails, exactly as it does app-side - a script that
     * refused whenever its check could not run would refuse on every device without `nsenter`.
     */
    internal fun mountsPresentCondition(): String =
        "[ \"${'$'}rmg_ns\" = unavailable ] || [ \"${'$'}rmg_got\" -ge \"${'$'}rmg_want\" ]"

    /**
     * The probe's own report, or null when it did not print one.
     *
     * Null and "found nothing mounted" are kept apart on purpose: a device where the probe never ran
     * is not a device that is missing its modules.
     */
    internal fun parse(output: String): ModuleMountState? {
        val line = output.lineSequence().map(String::trim).firstOrNull { it.startsWith(REPORT_PREFIX) }
            ?: return null
        val fields = line.substringAfter(REPORT_PREFIX).trim()
            .split(' ')
            .mapNotNull { field ->
                val separator = field.indexOf('=')
                if (separator <= 0) null else field.take(separator) to field.substring(separator + 1)
            }
            .toMap()
        val namespace = when (fields["ns"]) {
            "same" -> MountNamespace.Same
            "entered" -> MountNamespace.Entered
            "unavailable" -> MountNamespace.Unavailable
            else -> return null
        }
        return ModuleMountState(
            bootToken = fields["boot"].orEmpty(),
            uidZero = fields["uid"] == "0",
            namespace = namespace,
            expected = fields["want"]?.toIntOrNull() ?: return null,
            mounted = fields["got"]?.toIntOrNull() ?: return null,
        )
    }

    /**
     * Asks the device, through [shell], which is expected to be a root shell.
     *
     * Best-effort like the rest of the post-root probes: a probe that cannot run reports nothing
     * rather than throwing, because what it is measuring is a condition, not the command's success.
     */
    internal fun probe(shell: (String) -> ShizukuController.ShellResult): ModuleMountState? =
        runCatching { parse(shell(command()).output) }.getOrNull()

    /**
     * Why a module repair must not go ahead, or null when it may.
     *
     * Only a positive finding refuses the action. An unreadable namespace or an unreadable report says
     * the mounts could not be checked, and an action that is refused whenever a check is unavailable
     * would be unusable on the devices that cannot make it - so those are reported and allowed.
     */
    internal fun refusal(state: ModuleMountState?, expectedBootToken: String): String? = when {
        state == null -> null
        !state.uidZero -> "the module mounts could not be read as root"
        state.bootToken != expectedBootToken ->
            "the device rebooted while the module mounts were being read"
        state.definitelyMissingMounts ->
            "only ${state.mounted} of ${state.expected} enabled modules are mounted, so restarting " +
                "Zygote now would not load them"
        else -> null
    }
}
