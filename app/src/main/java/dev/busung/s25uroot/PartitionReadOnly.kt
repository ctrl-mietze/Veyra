package ctrl.mietze.veyraroot

/**
 * The optional safeguard that marks the partitions an image could be written to read-only.
 *
 * It exists because of the shape of this app's own success: bootstrap root is real root, granted
 * before anything has verified that the kernel it is running on belongs to the firmware it was built
 * for. The reported failures are people using that window to write a boot or vbmeta image - the kind
 * of write that produces a device that boots to nothing and needs download mode to recover. Setting
 * those devices read-only first means the write fails instead.
 *
 * **On unless turned off**, and the trade is deliberate: what it blocks is not only mistakes. Flashing a
 * kernel image from the phone, a module that writes a partition directly, and a KernelSU install that
 * patches `boot` rather than loading at runtime are all legitimate and all stop working while it is on.
 * The mistake it blocks is the one with no undo - a boot or vbmeta write that leaves the device needing
 * download mode - so the protection is what a first run gets, and turning it off is a decision the
 * person who knows what they intend on their own device makes, per device, in Settings. It is per boot
 * either way: `blockdev --setro` affects the running kernel, so a reboot - the normal state for
 * flashing - clears it.
 *
 * The script lives in assets because it runs as a file with bootstrap root, and the only thing the app
 * reads back is how many devices it managed to set.
 *
 * It is also the only place that can say the protection *caused* a failure, which is why [refusedByReadOnly]
 * lives here rather than at the screens that report failures: a message that blamed the switch for a
 * write refused by something else would talk people out of the guard that was working.
 */
internal object PartitionReadOnly {

    /** The asset holding the script, so the name is not written out at each call site. */
    const val SCRIPT_ASSET = "set_ro_blocks.sh"

    /**
     * How many devices the script reported setting, given its output.
     *
     * The count is the last number it printed: the script prints nothing else, but a shell that
     * echoed something on the way in would otherwise be read as the answer. Anything unreadable, or
     * negative, counts as none - a run must not report protection it did not get.
     */
    fun countFrom(output: String): Int = output.lineSequence()
        .map(String::trim)
        .filter(String::isNotEmpty)
        .mapNotNull(String::toIntOrNull)
        .lastOrNull()
        ?.takeIf { it > 0 }
        ?: 0

    /**
     * What a refused write leaves in the text, from the three places that produce it.
     *
     * The kernel's own message is the one that matters: a device set read-only refuses the write at
     * the block layer, the kernel reports EROFS, and the shell prints "Read-only file system". The
     * others are the same wall seen from `mount` and from a tool that words it itself.
     */
    private val READ_ONLY_WALL = listOf(
        "read-only file system",
        "readonly file system",
        "is read-only",
    )

    /**
     * Whether some text is the wall this protection puts up, rather than something else entirely.
     *
     * Deliberately narrow. The app's own lines about the protection - "Set 6 partitions to read-only"
     * and "Read-only partition script is missing from this build" - do not match it, so a run can
     * never report the protection as the cause of a failure it did not cause, which would be advice
     * to turn off the very thing that was working.
     */
    fun refusedByReadOnly(text: String): Boolean =
        READ_ONLY_WALL.any { marker -> text.contains(marker, ignoreCase = true) }
}

/**
 * Whether a run that has already failed was refused by the protection that same run set up.
 *
 * Three things have to hold, and each is there to stop a guess. The protection has to have actually set
 * devices, because a script that managed none cannot have refused anything. The refusal has to come
 * *after* it did: the log is read from the point the count was reported, since an EROFS line from
 * before the protection existed belongs to whatever put it there. And the failure's own message counts
 * as evidence beside the log, because whether a command's output reaches the log before it throws
 * depends on the command, while the reason is always about this failure and this failure came later.
 */
internal fun refusedByProtection(
    log: String,
    protectedFrom: Int,
    protectedDevices: Int,
    reason: String = "",
): Boolean {
    if (protectedDevices <= 0) return false
    if (protectedFrom !in 0..log.length) return false
    if (PartitionReadOnly.refusedByReadOnly(log.substring(protectedFrom))) return true
    return PartitionReadOnly.refusedByReadOnly(reason)
}
