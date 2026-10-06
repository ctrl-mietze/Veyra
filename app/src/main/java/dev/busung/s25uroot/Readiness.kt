package ctrl.mietze.veyraroot

/**
 * What the Overview status line can honestly say about the two things a run needs.
 *
 * Readiness is two independent facts, so it is two fields rather than one verdict: a device can have
 * KernelSU loaded and no usable Shizuku, or a working Shizuku and nothing loaded, and a single "ready /
 * not ready" would have to throw away which of those it was - the same collapsing that once made a
 * settings row offer to start a service that was already running.
 *
 * [KernelSuStatus] is three-valued on purpose. The readings behind it are exactly the ones Samsung's
 * policy denies to app domains, so "I could not look" is a real outcome and not the same answer as "it
 * is not there". Reporting the first as the second is what once had the app tell a rooted phone it was
 * unrooted, and a status line is where that is most tempting: it wants a single word.
 */
internal enum class KernelSuStatus {
    /** Loaded in this boot, by at least one of the readings. */
    Active,

    /** A reading looked and found nothing, and no other reading contradicts it. */
    NotLoaded,

    /** Nothing could answer: not a claim about the device, and said as its own thing. */
    Unreadable,
}

internal enum class CVeyraStatus {
    Disabled,
    Waiting,
    WirelessAdb,
    Root,
}

internal fun cveyraStatus(context: android.content.Context): CVeyraStatus {
    if (!CVeyraPreferences.enabled(context)) return CVeyraStatus.Disabled
    return when (CVeyraPreferences.backend(context)) {
        CVeyraBackend.Root -> CVeyraStatus.Root
        CVeyraBackend.WirelessAdb -> CVeyraStatus.WirelessAdb
        CVeyraBackend.None -> CVeyraStatus.Waiting
    }
}

/** The readings, as the Overview card shows them. */
internal data class Readiness(
    val kernelSu: KernelSuStatus,
    val cveyra: CVeyraStatus,
    val managers: ManagerPresence,
    val veyraKsu: VeyraKsuState = VeyraKsuState(),
    val veyraKsuTagged: Boolean = true,
)

/**
 * Which manager apps the phone has, per flavour.
 *
 * KernelSU being loaded and its manager being installed are separate facts, and neither implies the
 * other: root can be in the kernel with no app to manage it, and a manager can be installed on a phone
 * with nothing loaded - which is the state a fresh install leaves, and the one worth seeing before a run
 * finishes rather than after.
 *
 * Every flavour is reported rather than only the configured one. They are different apps that cannot
 * both be in the kernel, so which of them is actually on the phone is part of the picture, and a manager
 * installed for another flavour is exactly what an attempt to open "the manager" then fails to find.
 *
 * Held as the set of flavours found rather than one field each, so a flavour added to [KernelSuFlavor] is
 * a row on the card and a line here without a second place to remember - the shape this was written in
 * had a field per flavour, and the two of them had already drifted once.
 */
internal data class ManagerPresence(
    /** The flavours with a manager installed for them. A manager of no known flavour is in none. */
    val flavors: Set<KernelSuFlavor> = emptySet(),
) {

    fun installed(flavor: KernelSuFlavor): Boolean = flavor in flavors

    companion object {

        /**
         * From the managers this app found, which is the scan that also knows about a spoofed package.
         *
         * A manager whose package has been renamed per build still identifies itself by what it
         * carries, so asking by package name would report the phone as having none. A manager whose
         * package and label say no project at all is dropped here rather than guessed into a row.
         */
        fun of(managers: List<InstalledManager>): ManagerPresence =
            ManagerPresence(managers.mapNotNullTo(mutableSetOf()) { it.flavor })
    }
}

/**
 * Which of the three, from the two independent KernelSU readings.
 *
 * A yes from either source is a yes, because both are direct evidence: the native probe or `su` says
 * KernelSU is answering, and the kernel's module list says the module is loaded even when no shell of
 * ours can run. A no needs a reading that actually looked - the module list is the only source that can
 * report absence, since a `su` that does not answer is indistinguishable from a device with no root.
 * With neither, the answer is [KernelSuStatus.Unreadable] rather than a guess.
 */
internal fun kernelSuStatus(active: Boolean, moduleLoaded: Boolean?): KernelSuStatus = when {
    active || moduleLoaded == true -> KernelSuStatus.Active
    moduleLoaded == false -> KernelSuStatus.NotLoaded
    else -> KernelSuStatus.Unreadable
}
