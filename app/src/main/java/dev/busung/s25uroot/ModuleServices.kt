package ctrl.mietze.veyraroot

/**
 * A module whose userspace service has to be running before a new Zygote is created.
 *
 * The module id is the directory under `/data/adb/modules`, and the process name is what the module's
 * service reports itself as. Only modules that are installed and enabled are waited for: a device
 * without them is not missing anything, and waiting for a service no module provides would turn every
 * restart into a timeout.
 */
internal data class ModuleService(val moduleId: String, val processName: String)

/**
 * The module services a Zygote restart has to wait for.
 *
 * These are the modules that install themselves *into* Zygote, which is what makes the ordering
 * matter: Zygisk Next provides the injection layer and LSPosed runs inside it, so creating a Zygote
 * before their services are up produces a framework that comes back without them - and a restart
 * meant to load modules would quietly unload them instead.
 */
internal val KNOWN_MODULE_SERVICES = listOf(
    ModuleService(moduleId = "zygisksu", processName = "zn-daemon"),
    ModuleService(moduleId = "zygisk_lsposed", processName = "lspd"),
)

/**
 * How long the wait is bounded to: longer than a cold start of these services, and a bound the app's own
 * acknowledgement window is sized from rather than assumed to fit inside.
 */
internal const val MODULE_SERVICE_WAIT_SECONDS = 20

/**
 * What one iteration of that wait can cost, rather than the second it sleeps.
 *
 * Each pass re-reads the process table - one `ps` and one `grep` per module - and on a loaded device
 * those cost more than the sleep does. It is stated here because the app sizes its own window from
 * this wait, and the two are counted differently: the child counts iterations, the app measures
 * wall-clock time.
 */
internal const val MODULE_SERVICE_WAIT_ITERATION_ALLOWANCE_SECONDS = 1.5

/**
 * The shell that waits on the device for [services], leaving the still-missing module ids in
 * `rmg_missing_services`.
 *
 * Built from [services] rather than written out by hand, so this table is the only place a
 * module/process pair is named and a pair added here is waited for by every script that embeds this.
 *
 * The wait is bounded so the child always answers within the window the app is waiting in, and `ps
 * -A -o NAME` is matched whole: `lspd` is also the start of longer names, and a partial match would
 * report a service as running when it is not. A module that is absent, or present but disabled or
 * marked for removal, is skipped rather than waited on.
 */
internal fun moduleServiceWaitSnippet(
    services: List<ModuleService> = KNOWN_MODULE_SERVICES,
    timeoutSeconds: Int = MODULE_SERVICE_WAIT_SECONDS,
): String {
    val pairs = services.joinToString(" ") { "${it.moduleId}:${it.processName}" }
    return """
        RMG_SERVICE_PAIRS='$pairs'
        rmg_services_missing() {
            rmg_missing=''
            for rmg_pair in ${'$'}RMG_SERVICE_PAIRS; do
                rmg_module_id=${'$'}{rmg_pair%%:*}
                rmg_process_name=${'$'}{rmg_pair##*:}
                rmg_module_dir="/data/adb/modules/${'$'}rmg_module_id"
                [ -d "${'$'}rmg_module_dir" ] || continue
                [ -e "${'$'}rmg_module_dir/disable" ] && continue
                [ -e "${'$'}rmg_module_dir/remove" ] && continue
                if ! /system/bin/ps -A -o NAME 2>/dev/null | /system/bin/grep -qx "${'$'}rmg_process_name"; then
                    rmg_missing="${'$'}rmg_missing ${'$'}rmg_module_id"
                fi
            done
            printf '%s' "${'$'}rmg_missing"
        }
        rmg_service_waited=0
        rmg_missing_services=$(rmg_services_missing)
        while [ -n "${'$'}rmg_missing_services" ] && [ "${'$'}rmg_service_waited" -lt $timeoutSeconds ]; do
            sleep 1
            rmg_service_waited=$((rmg_service_waited + 1))
            rmg_missing_services=$(rmg_services_missing)
        done
    """.trimIndent()
}
