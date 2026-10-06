package ctrl.mietze.veyraroot

/**
 * Why a catalog did not cover a device, in the terms someone can act on.
 *
 * "No compatible payload supports this model and kernel version" is the same sentence for a device
 * the feed has never heard of and for one whose firmware is a week newer than the last port, and only
 * one of those is worth waiting on. So the refusal names the identity that was searched for - which
 * the user can compare with the feed themselves - and the entries that came closest, which is what
 * says whether this is a gap of one build or of one whole model.
 *
 * The selection is here rather than in the repository so it can be tested without a network: what a
 * message says about a catalog is a rule, not a string.
 */
internal object TargetGap {

    /** The device as one line: the identity nothing in the catalog matched. */
    fun describe(snapshot: DeviceSnapshot): String =
        "${snapshot.model} on kernel ${snapshot.kernelRelease}"

    /** Why an entry is close. */
    enum class Reason {
        /** Lists this model, on other kernel versions. */
        SameModel,

        /** Lists this kernel version, for other models. */
        SameKernel,
    }

    data class Closest(
        val displayName: String,
        /** The kernels the entry covers, as the feed states them. */
        val kernels: String,
        val reason: Reason,
    )

    /**
     * The entries nearest to covering the device, most useful first, at most [limit].
     *
     * Same model first, because someone holding a payload for their own phone on another build is one
     * firmware away from a run, while the same kernel version on another model is only a hint about
     * which families the feed follows. Nothing is invented: an entry is named only for something it
     * actually lists, and an entry that matches neither is not named at all.
     *
     * An entry that does cover the device ends the question rather than appearing in the answer: the
     * list is what a refusal names, and a refusal that named the profile it should have used would be
     * worse than the sentence it replaced.
     */
    fun closest(
        snapshot: DeviceSnapshot,
        catalog: List<TargetProfile>,
        limit: Int = 3,
    ): List<Closest> {
        if (catalog.any { profile -> profile.matches(snapshot) }) return emptyList()
        val sameModel = catalog.filter { profile -> profile.matchesDevice(snapshot) }
        val sameModelIds = sameModel.mapTo(mutableSetOf()) { it.profileId }
        val sameKernel = catalog.filter { profile ->
            profile.profileId !in sameModelIds && snapshot.kernelVersion in profile.kernelVersions
        }
        return (
            sameModel.map { Closest(it.displayName, it.supportedKernelVersions, Reason.SameModel) } +
                sameKernel.map { Closest(it.displayName, it.supportedKernelVersions, Reason.SameKernel) }
            ).take(limit)
    }
}
