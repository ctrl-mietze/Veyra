package ctrl.mietze.veyraroot

/**
 * A GitHub repository serving a payload catalog. Several sources can be configured at once;
 * each is fetched on its own and every target keeps the identity of the source that provided
 * it, so two sources may offer the same payload without either one shadowing the other.
 */
data class PayloadSource(
    val repository: String,
    /** Branch, tag, or commit that [pinnedCommit] was resolved from, and the ref followed when unpinned. */
    val branch: String,
    val enabled: Boolean = true,
    /**
     * Full commit SHA this source is frozen at, or empty to follow [branch] on every load.
     *
     * A pin is what stops a catalog changing under a test: the branch head is resolved once, at the
     * moment it is pinned, and every later load reads that revision instead. It also means a pinned
     * source needs no GitHub API call to load, so it keeps working when the API is rate limited.
     */
    val pinnedCommit: String = "",
) {
    val isPinned: Boolean
        get() = pinnedCommit.isNotEmpty()

    val isLocal: Boolean
        get() = repository.startsWith("local/")

    val localKey: String?
        get() = repository.takeIf { isLocal }?.substringAfter("local/")

    /**
     * Identifies the catalog, not just the repository: a pinned revision and the branch it came from
     * are two different catalogs, so they can be configured side by side and compared.
     */
    val id: String
        get() = if (isLocal) "local:" + localKey.orEmpty() else "$repository@${if (isPinned) pinnedCommit else branch}"

    val label: String
        get() = if (isLocal) {
            when (localKey) {
                "pixel" -> "Local / Pixel"
                "dfroot" -> "Local / DF Compatible + DF+"
                "research-oneplus-p2p3p" -> "Local / GhostLock OnePlus"
                "research-rootmys24" -> "Local / Root My S24"
                "research-honor80gt" -> "Local / GhostLock Honor 80 GT"
                "research-oneplus-joinchang" -> "Local / GhostLock OnePlus Multi"
                "research-ionstack-s22u" -> "Local / IonStack S22U"
                "research-rootmyvivo" -> "Local / RootMyVivo"
                "research-shizuku-next" -> "Local / Shizuku Next"
                else -> "Local / " + localKey.orEmpty().replace('-', ' ')
            }
        } else if (isPinned) {
            "$repository @ $branch @ ${pinnedCommit.take(7)}"
        } else {
            "$repository @ $branch"
        }

    /** How the ref line reads in the settings sheet. */
    val refLabel: String
        get() = if (isLocal) "local" else if (isPinned) "$branch at ${pinnedCommit.take(7)}" else branch

    companion object {
        /**
         * This fork's payload catalog.
         *
         * Its own rather than the upstream one, because a fresh install has to be able to see the
         * flavours and builds this fork publishes - the upstream catalog never will. It is only the
         * default: a source list already saved on the device is what a run reads, and this changes
         * only what a device with no list yet starts from.
         */
        const val DEFAULT_REPOSITORY = "rushiranpise/Root-My-Galaxy-Payloads"
        const val DEFAULT_BRANCH = "main"

        /** Official upstream payload feed used by BuSung-dev/Root-My-Galaxy. */
        const val UPSTREAM_REPOSITORY = "BuSung-dev/Root-My-Galaxy-Payloads"

        /** Extended firmware feed maintained by Root-My-Galaxy-Payloads-Extended. */
        const val EXTENDED_REPOSITORY = "igorcv88/Root-My-Galaxy-Payloads-Extended"
        const val LOCAL_PIXEL_REPOSITORY = "local/pixel"
        const val LOCAL_FAST_REPOSITORY = "local/new-fast"
        const val LOCAL_DF_REPOSITORY = "local/dfroot"
        const val LOCAL_RESEARCH_ONEPLUS_P2P3P = "local/research-oneplus-p2p3p"
        const val LOCAL_RESEARCH_ROOTMYS24 = "local/research-rootmys24"
        const val LOCAL_RESEARCH_HONOR80GT = "local/research-honor80gt"
        const val LOCAL_RESEARCH_ONEPLUS_JOINCHANG = "local/research-oneplus-joinchang"
        const val LOCAL_RESEARCH_IONSTACK_S22U = "local/research-ionstack-s22u"
        const val LOCAL_RESEARCH_ROOTMYVIVO = "local/research-rootmyvivo"
        const val LOCAL_RESEARCH_SHIZUKU_NEXT = "local/research-shizuku-next"
        const val LOCAL_BRANCH = "local"

        /**
         * The catalog this fork's payloads were copied from, artifacts and all.
         *
         * Named here because a manifest records where its own artifacts live, and a fork's manifest is
         * the original one with a different owner: every entry it was copied with still names this
         * repository. A reader that only accepted the source's own prefix therefore refused the whole
         * catalog on its first artifact, which is what this fork's own feed did until this existed.
         * Reading one keeps the path and re-points it at the source that was actually read, so nothing
         * is ever fetched from here.
         */
        const val LEGACY_REPOSITORY = "BuSung-dev/Root-My-Galaxy-Payloads"
        const val LEGACY_BRANCH = "main"

        val COMMIT_PATTERN = Regex("^[0-9a-f]{40}$")

        val DEFAULT = PayloadSource(
            repository = DEFAULT_REPOSITORY,
            branch = DEFAULT_BRANCH,
            enabled = true,
        )

        val LOCAL_PIXEL = PayloadSource(
            repository = LOCAL_PIXEL_REPOSITORY,
            branch = LOCAL_BRANCH,
            enabled = false,
        )

        val LOCAL_FAST = PayloadSource(
            repository = LOCAL_FAST_REPOSITORY,
            branch = LOCAL_BRANCH,
            enabled = false,
        )

        val LOCAL_DF = PayloadSource(
            repository = LOCAL_DF_REPOSITORY,
            branch = LOCAL_BRANCH,
            enabled = false,
        )

        private fun research(repository: String) = PayloadSource(
            repository = repository,
            branch = LOCAL_BRANCH,
            enabled = false,
        )

        val LOCAL_RESEARCH_SOURCES: List<PayloadSource> = listOf(
            research(LOCAL_RESEARCH_ONEPLUS_P2P3P),
            research(LOCAL_RESEARCH_ROOTMYS24),
            research(LOCAL_RESEARCH_HONOR80GT),
            research(LOCAL_RESEARCH_ONEPLUS_JOINCHANG),
            research(LOCAL_RESEARCH_IONSTACK_S22U),
            research(LOCAL_RESEARCH_ROOTMYVIVO),
            research(LOCAL_RESEARCH_SHIZUKU_NEXT),
        )

        /**
         * Built-in sources shown on a fresh install. Only Veyra's primary feed starts enabled;
         * the upstream and extended feeds stay unchecked until the user enables them or the automatic
         * fallback promotes one because the primary feed has no matching payload for this device.
         */
        val BUILT_IN_SOURCES: List<PayloadSource> = listOf(
            DEFAULT,
            PayloadSource(
                repository = UPSTREAM_REPOSITORY,
                branch = DEFAULT_BRANCH,
                enabled = false,
            ),
            PayloadSource(
                repository = EXTENDED_REPOSITORY,
                branch = DEFAULT_BRANCH,
                enabled = false,
            ),
            LOCAL_PIXEL,
            LOCAL_DF,
        ) + LOCAL_RESEARCH_SOURCES

        val AUTOMATIC_FALLBACK_REPOSITORIES: Set<String> = setOf(
            UPSTREAM_REPOSITORY,
            EXTENDED_REPOSITORY,
        )

        fun isAutomaticFallback(source: PayloadSource): Boolean =
            source.repository in AUTOMATIC_FALLBACK_REPOSITORIES

        fun isCommitValid(commit: String): Boolean = COMMIT_PATTERN.matches(commit.trim())

        /**
         * Builds a source from raw input, or null when either field is empty.
         *
         * What was typed is taken as written: a pattern cannot tell a repository that exists from one
         * that does not, and the only thing that can is reading it - which is what adding a source does.
         * A shape the reader cannot use comes back as that reader's own answer, naming what it could not
         * reach, rather than as a rule about the field before anyone has tried.
         */
        fun create(
            repository: String,
            branch: String,
            enabled: Boolean = true,
        ): PayloadSource? {
            val owner = repository.trim()
            val ref = branch.trim()
            if (owner.isEmpty() || ref.isEmpty()) return null
            return if (isCommitValid(ref)) {
                PayloadSource(owner, ref, enabled, pinnedCommit = ref)
            } else {
                PayloadSource(owner, ref, enabled)
            }
        }
    }
}

private const val SELECTION_SEPARATOR = "|"

/**
 * Identifies a target across sources. A source id cannot contain the separator (it is built
 * from the repository and branch patterns), so the first separator always splits the two parts.
 */
fun selectionIdFor(sourceId: String, profileId: String): String =
    if (sourceId.isEmpty()) profileId else "$sourceId$SELECTION_SEPARATOR$profileId"

fun sourceFromSelectionId(selectionId: String): String? {
    val index = selectionId.indexOf(SELECTION_SEPARATOR)
    return if (index <= 0) null else selectionId.substring(0, index)
}

fun profileFromSelectionId(selectionId: String): String {
    val index = selectionId.indexOf(SELECTION_SEPARATOR)
    return if (index <= 0) selectionId else selectionId.substring(index + 1)
}

fun List<PayloadSource>.enabledSources(): List<PayloadSource> = filter { it.enabled }

fun List<PayloadSource>.withSourceAdded(source: PayloadSource): List<PayloadSource> =
    if (any { it.id == source.id }) this else this + source

fun List<PayloadSource>.withSourceRemoved(sourceId: String): List<PayloadSource> =
    filterNot { it.id == sourceId }

fun List<PayloadSource>.withSourceEnabled(
    sourceId: String,
    enabled: Boolean,
): List<PayloadSource> = map { if (it.id == sourceId) it.copy(enabled = enabled) else it }

/** Freezes a source at [commit]. Its id changes, since a pinned revision is a different catalog. */
fun List<PayloadSource>.withSourcePinned(
    sourceId: String,
    commit: String,
): List<PayloadSource> = map { if (it.id == sourceId) it.copy(pinnedCommit = commit) else it }

/** Puts a source back on its branch, resolving the ref again on every load. */
fun List<PayloadSource>.withSourceUnpinned(sourceId: String): List<PayloadSource> =
    map { if (it.id == sourceId) it.copy(pinnedCommit = "") else it }
