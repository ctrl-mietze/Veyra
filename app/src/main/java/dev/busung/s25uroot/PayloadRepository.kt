package ctrl.mietze.veyraroot

import android.content.Context
import android.system.Os
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.security.MessageDigest
import java.net.URL
import org.json.JSONObject

data class VerifiedPayloads(
    val profile: TargetProfile,
    val exploit: File,
    val kernelSu: File,
    /** The exact bootstrap/helper binary paired with this exploit profile. */
    val rootHelper: File,
    /**
     * Where these files came from. It decides two things: whether the payload may be published to the
     * known-good cache (only a downloaded one may, because only it was checked against the feed), and
     * what the log says about the run.
     */
    val origin: PayloadOrigin = PayloadOrigin.Downloaded,
)

/**
 * The commit a ref resolves to, from either shape of answer GitHub can give.
 *
 * The app asks for `application/vnd.github.sha`, which answers with the bare 40-character commit,
 * and that is what it reads first. A JSON commit object is still understood, because a server or a
 * proxy that ignores the requested media type would otherwise turn a resolvable source into an
 * unreadable one. Anything else - an error page, a truncated body - resolves to nothing rather than
 * to a commit the app then trusts.
 */
internal fun parseCommitResponse(body: String): String? {
    val trimmed = body.trim()
    if (PayloadSource.isCommitValid(trimmed)) return trimmed
    val fromJson = runCatching { JSONObject(trimmed).optString("sha") }.getOrNull() ?: return null
    return fromJson.trim().takeIf(PayloadSource::isCommitValid)
}

/** Where a catalog lives inside a repository. */
internal const val MANIFEST_PATH = "support/targets-v3.json"

/**
 * The URL a catalog is read from at one specific revision.
 *
 * Pure, and named after the revision rather than after the source, because the property that makes a
 * revision summary worth showing is that it reads the revision the user is looking at and not the
 * branch's current head.
 */
internal fun revisionManifestUrl(repository: String, commit: String): String =
    "https://raw.githubusercontent.com/$repository/$commit/$MANIFEST_PATH"

/** The mutable branch URL a repository's files are served from, as a manifest writes them. */
internal fun mutableRawPrefix(repository: String, branch: String): String =
    "https://raw.githubusercontent.com/$repository/$branch/"

/**
 * The prefixes a manifest may name for its own artifacts, tried in order.
 *
 * This list is the whole of the rule. A catalog declares where its artifacts live, and the only
 * declarations honoured are these three repositories, so a manifest cannot send a download anywhere
 * else. Each entry is a fact about how catalogs are written rather than a preference: a source names
 * itself, this fork's feed names itself, and the upstream catalog this fork's feed was forked from still
 * names *itself* in every entry it was copied with. The third is why the source alone is not enough -
 * without it the reader refuses this fork's own catalog on its first artifact, which is exactly what it
 * did.
 */
internal fun allowedMutableRawPrefixes(source: PayloadSource): List<String> = listOf(
    mutableRawPrefix(source.repository, source.branch),
    mutableRawPrefix(PayloadSource.DEFAULT.repository, PayloadSource.DEFAULT.branch),
    mutableRawPrefix(PayloadSource.LEGACY_REPOSITORY, PayloadSource.LEGACY_BRANCH),
)

/**
 * The URL an artifact is read from: the path the manifest named, at the commit the manifest was read at,
 * in the repository the manifest was read *from* rather than the one it was written by. Null when the
 * manifest named somewhere this app will not fetch from, which the caller reports as such.
 */
internal fun pinnedArtifactUrl(source: PayloadSource, url: String, commit: String): String? {
    val prefix = allowedMutableRawPrefixes(source).firstOrNull(url::startsWith) ?: return null
    return mutableRawPrefix(source.repository, commit) + url.removePrefix(prefix)
}

/** A revision a source can be pinned to, as a picker lists it. */
data class SourceRevision(
    val commit: String,
    /** The tag this revision is named by, when it has one; null for a plain commit. */
    val tag: String? = null,
    /** The first line of the commit message, or the tag name. */
    val label: String = "",
    /** The commit date, as the short form shown beside it. */
    val date: String = "",
)

/**
 * The commits GitHub returns, newest first, as the picker lists them.
 *
 * A commit whose SHA is not a full commit is dropped rather than listed: picking it would store a
 * pin that cannot be resolved again, which is the one thing a pin must never be. A message is only
 * ever shown, never parsed, so only its first line and a bounded length survive.
 */
internal fun parseCommits(body: String, limit: Int = Int.MAX_VALUE): List<SourceRevision> {
    val array = runCatching { org.json.JSONArray(body.trim()) }.getOrNull() ?: return emptyList()
    return buildList {
        for (index in 0 until minOf(array.length(), limit)) {
            val entry = array.optJSONObject(index) ?: continue
            val sha = entry.optString("sha").trim()
            if (!PayloadSource.isCommitValid(sha)) continue
            val commit = entry.optJSONObject("commit")
            val message = commit?.optString("message").orEmpty().lineSequence()
                .firstOrNull(String::isNotBlank).orEmpty().trim().take(MESSAGE_MAX_LENGTH)
            val date = commit?.optJSONObject("committer")?.optString("date")
                ?.take(DATE_LENGTH)
                .orEmpty()
            add(SourceRevision(commit = sha, label = message, date = date))
        }
    }
}

/**
 * The commits in a ref's atom feed, newest first.
 *
 * This is the same list the repository page shows, in the one form GitHub publishes without the
 * REST API: each entry carries the commit in `Grit::Commit/<sha>`, its first line in the title and
 * its date in `updated`. Entries that do not carry a full commit are dropped rather than listed,
 * and a feed that cannot be read at all is an empty list, so the caller can fall back instead of
 * failing on the format.
 */
internal fun parseCommitAtom(body: String, limit: Int = Int.MAX_VALUE): List<SourceRevision> {
    if (!body.contains("<entry>")) return emptyList()
    return buildList {
        for (entry in ENTRY.findAll(body)) {
            if (size >= limit) break
            val block = entry.groupValues[1]
            val commit = COMMIT_IN_ENTRY.find(block)?.groupValues?.get(1) ?: continue
            if (!PayloadSource.isCommitValid(commit)) continue
            val title = TITLE_IN_ENTRY.find(block)?.groupValues?.get(1).orEmpty()
                .let { it.replace(WHITESPACE, " ").trim() }
                .take(MESSAGE_MAX_LENGTH)
            val date = UPDATED_IN_ENTRY.find(block)?.groupValues?.get(1).orEmpty().take(DATE_LENGTH)
            add(SourceRevision(commit = commit, label = title, date = date))
        }
    }
}

private val ENTRY = Regex("<entry>(.*?)</entry>", RegexOption.DOT_MATCHES_ALL)
private val COMMIT_IN_ENTRY = Regex("Grit::Commit/([0-9a-f]{40})")
private val TITLE_IN_ENTRY = Regex("<title>(.*?)</title>", RegexOption.DOT_MATCHES_ALL)
private val UPDATED_IN_ENTRY = Regex("<updated>(.*?)</updated>", RegexOption.DOT_MATCHES_ALL)
private val WHITESPACE = Regex("\\s+")

/**
 * The tags GitHub returns, as the picker lists them. A tag carries the commit it points at, which
 * is what gets pinned - a tag can be moved onto another commit, so it is never stored as a pin.
 */
internal fun parseTags(body: String, limit: Int = Int.MAX_VALUE): List<SourceRevision> {
    val array = runCatching { org.json.JSONArray(body.trim()) }.getOrNull() ?: return emptyList()
    return buildList {
        for (index in 0 until minOf(array.length(), limit)) {
            val entry = array.optJSONObject(index) ?: continue
            val name = entry.optString("name").trim()
            val sha = entry.optJSONObject("commit")?.optString("sha").orEmpty().trim()
            if (name.isEmpty() || !PayloadSource.isCommitValid(sha)) continue
            add(SourceRevision(commit = sha, tag = name, label = name.take(MESSAGE_MAX_LENGTH)))
        }
    }
}

/** How long a revision label may be before it stops being a label. */
private const val MESSAGE_MAX_LENGTH = 96

/** Enough of `2026-09-08T19:55:26Z` to read as a date. */
private const val DATE_LENGTH = 10

/** SHA-256 of [bytes] as lowercase hex, the form a manifest declares an artifact hash in. */
internal fun sha256Hex(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).toHex()

internal fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

/**
 * Targets merged from every enabled source, plus one message per source that could not be
 * read. A source that fails is left out rather than taking the whole catalog down with it.
 */
data class LoadedCatalog(
    val targets: List<TargetProfile>,
    val sourceFailures: List<String>,
)

class PayloadRepository(private val context: Context) {
    fun loadCatalog(): LoadedCatalog {
        val configured = AppPreferences.payloadSources(context)

        // A manual source choice is authoritative. Automatic compatibility scanning is deliberately
        // disabled after the person saves the source sheet, because changing a checkbox is an explicit
        // request to test/use that source even when Veyra itself would have chosen another.
        if (AppPreferences.payloadSourcesManuallySelected(context)) {
            val sources = configured.enabledSources()
            require(sources.isNotEmpty()) { context.getString(R.string.repo_no_source_enabled) }
            val loaded = loadConfiguredSources(sources)
            require(loaded.targets.isNotEmpty()) {
                loaded.sourceFailures
                    .ifEmpty { listOf(context.getString(R.string.repo_no_profile)) }
                    .joinToString("\n")
            }
            return loaded
        }

        // Automatic mode is ordered, but a loose regional/KMI match is not treated like verified
        // firmware support. rushiranpise is inspected first and wins immediately only on an exact
        // full kernel-release match. A loose match is remembered as the preferred fallback while
        // Veyra keeps scanning the remaining built-ins for an exact firmware profile.
        val snapshot = DeviceSnapshot.current()
        val flavor = AppPreferences.kernelsuFlavor(context)
        val failures = mutableListOf<String>()
        val inspectedTargets = mutableListOf<TargetProfile>()

        var firstLooseSource: PayloadSource? = null
        var firstLooseTargets: List<TargetProfile>? = null
        var firstLooseMatch: TargetProfile? = null

        fun activateBuiltIn(
            source: PayloadSource,
            targets: List<TargetProfile>,
            match: TargetProfile,
            quality: String,
        ): LoadedCatalog {
            val builtInRepositories = PayloadSource.BUILT_IN_SOURCES
                .map { it.repository.lowercase() }
                .toSet()
            val automaticSelection = buildList {
                PayloadSource.BUILT_IN_SOURCES.forEach { candidate ->
                    add(candidate.copy(enabled = candidate.repository.equals(source.repository, true)))
                }
                configured
                    .filter { it.repository.lowercase() !in builtInRepositories }
                    .forEach { add(it.copy(enabled = false)) }
            }
            AppPreferences.setPayloadSourcesAutomatically(context, automaticSelection)
            AppLog.info(
                AppLogTags.CATALOG,
                "Automatic payload scanner selected ${source.label} for ${match.profileId} ($quality)",
            )
            return LoadedCatalog(targets = targets, sourceFailures = failures)
        }

        PayloadSource.BUILT_IN_SOURCES.filterNot { it.isLocal }.forEach { builtIn ->
            val source = builtIn.copy(enabled = true, pinnedCommit = "")
            val targets = try {
                loadSource(source)
            } catch (error: Throwable) {
                val detail = context.getString(
                    R.string.repo_source_failed,
                    source.label,
                    error.message ?: error.javaClass.simpleName,
                )
                failures += detail
                AppLog.warn(AppLogTags.CATALOG, detail)
                return@forEach
            }
            inspectedTargets += targets
            val match = targets.resolveFor(snapshot, flavor) ?: return@forEach

            when (match.kernelMatch(snapshot)) {
                KernelMatch.Exact -> return activateBuiltIn(
                    source = source,
                    targets = targets,
                    match = match,
                    quality = "exact firmware/kernel release",
                )
                KernelMatch.Version -> if (firstLooseMatch == null) {
                    firstLooseSource = source
                    firstLooseTargets = targets
                    firstLooseMatch = match
                }
                KernelMatch.None -> Unit
            }
        }

        if (firstLooseSource != null && firstLooseTargets != null && firstLooseMatch != null) {
            return activateBuiltIn(
                source = firstLooseSource,
                targets = firstLooseTargets,
                match = firstLooseMatch,
                quality = "loose kernel-version fallback; no exact firmware profile found",
            )
        }

        require(inspectedTargets.isNotEmpty()) {
            failures.ifEmpty { listOf(context.getString(R.string.repo_no_profile)) }.joinToString("\n")
        }
        return LoadedCatalog(inspectedTargets, failures)
    }

    private fun loadConfiguredSources(sources: List<PayloadSource>): LoadedCatalog {
        val targets = mutableListOf<TargetProfile>()
        val failures = mutableListOf<String>()
        sources.forEach { source ->
            try {
                targets += loadSource(source)
            } catch (error: Throwable) {
                val detail = context.getString(
                    R.string.repo_source_failed,
                    source.label,
                    error.message ?: error.javaClass.simpleName,
                )
                failures += detail
                AppLog.warn(AppLogTags.CATALOG, detail)
            }
        }
        AppLog.info(
            AppLogTags.CATALOG,
            "Catalog loaded from ${sources.size - failures.size}/${sources.size} enabled sources, " +
                "${targets.size} targets",
        )
        return LoadedCatalog(targets, failures)
    }

    fun loadTargets(): List<TargetProfile> = loadCatalog().targets

    /**
     * The payload a run should use on this device, preferring the flavour the app is set to.
     *
     * The flavour is a preference and not a filter, because its alternative - refusing when the
     * enabled sources happen to carry only the other one - leaves a user with no run and nothing that
     * says which entry to add. Which flavour was offered is on the profile, and the run reports it.
     */
    fun resolveTarget(snapshot: DeviceSnapshot): TargetProfile {
        val catalog = loadTargets()
        val resolved = catalog.resolveFor(snapshot, AppPreferences.kernelsuFlavor(context))
            ?: error(noProfileReason(snapshot, catalog))
        // This is the payload the run about to start will load, so its KernelSU version is the one the
        // manager rows offer from here on - recorded where the decision is made rather than re-derived
        // by each screen that needs it.
        rememberResolvedPayload(context, resolved)
        return resolved
    }

    fun resolveFastTarget(
        snapshot: DeviceSnapshot,
        flavor: KernelSuFlavor = AppPreferences.kernelsuFlavor(context),
    ): TargetProfile {
        val candidates = loadSource(PayloadSource.LOCAL_DF)
        val matching = candidates.filter { profile ->
            profile.flavor == flavor && profile.matches(snapshot)
        }
        val resolved = matching.firstOrNull { it.buildIds.isNotEmpty() }
            ?: matching.firstOrNull()
            ?: error(noProfileReason(snapshot, candidates))

        rememberResolvedPayload(context, resolved)
        return resolved
    }

    /**
     * Why nothing covered this device, with the entries that came closest.
     *
     * The first line names the identity that was searched for, because that is what the user can
     * compare against the feed themselves; anything after it names what the enabled sources do carry,
     * so a gap of one build reads differently from a gap of one whole model. The caller's message is
     * one line on the screen and the whole of this in the log, which is why the identity is first.
     */
    private fun noProfileReason(snapshot: DeviceSnapshot, catalog: List<TargetProfile>): String {
        val headline = context.getString(R.string.repo_no_profile, TargetGap.describe(snapshot))
        val closest = TargetGap.closest(snapshot, catalog)
        if (closest.isEmpty()) {
            return "$headline\n${context.getString(R.string.repo_no_profile_none)}"
        }
        val named = closest.joinToString("; ") { entry ->
            when (entry.reason) {
                TargetGap.Reason.SameModel -> context.getString(
                    R.string.repo_no_profile_other_kernel,
                    entry.displayName,
                    entry.kernels,
                )
                TargetGap.Reason.SameKernel -> context.getString(
                    R.string.repo_no_profile_other_model,
                    entry.displayName,
                )
            }
        }
        return "$headline\n${context.getString(R.string.repo_no_profile_closest, named)}"
    }

    /** Resolves a catalog selection, which may name the source it came from. */
    fun resolveTarget(selectionId: String): TargetProfile {
        val sourceId = sourceFromSelectionId(selectionId)
        val profileId = profileFromSelectionId(selectionId)
        val source = sourceId?.let { id ->
            AppPreferences.payloadSources(context).firstOrNull { it.id == id }
        }
        if (sourceId != null && source == null) {
            error(context.getString(R.string.repo_source_missing, sourceId))
        }

        val candidates = if (source != null) loadSource(source) else loadTargets()
        return candidates.firstOrNull { it.profileId == profileId }
            ?: error(context.getString(R.string.repo_profile_missing, profileId))
    }

    /**
     * Reads a source without saving it: the check the sources sheet runs before a repository is
     * added. An unreachable repository, a missing manifest, or a schema this app cannot read is
     * reported here instead of becoming a source that fails silently on every later run.
     */
    fun inspect(source: PayloadSource, snapshot: DeviceSnapshot): SourceCoverage {
        val fetched = fetchManifest(source)
        val coverage = fetched.manifest.coverageFor(snapshot, fetched.commit)
        val adapter = LocalSourceAdapters.forSource(source) ?: return coverage
        return coverage.copy(
            adapterCount = 1,
            adapterLabel = adapter.title,
            adapterKind = adapter.kind,
            adapterMatchesDevice = adapter.matches(snapshot),
            adapterCapabilities = adapter.capabilities,
        )
    }

    /**
     * Reads what a source would serve at [commit], with nothing pinned.
     *
     * This is the picker's question - what does *this* revision contain - and it has to be asked while
     * the pin is still a proposal, so the revision is an argument here instead of being resolved from
     * the source's own state. The catalog is read from the revision itself, so the summary describes
     * the revision the user chose rather than whichever one the branch is on.
     */
    fun inspectAt(source: PayloadSource, snapshot: DeviceSnapshot, commit: String): SourceCoverage {
        require(PayloadSource.isCommitValid(commit)) {
            context.getString(R.string.repo_commit_invalid)
        }
        val manifest = SupportManifest.parse(
            downloadBytes(revisionManifestUrl(source.repository, commit), MAX_MANIFEST_BYTES),
        )
        noteIgnored(manifest)
        return manifest.coverageFor(snapshot, commit)
    }

    /**
     * Says what a manifest left out, in the app's own log.
     *
     * A feed can declare a flavour this build does not know - the app that reads it may simply be older
     * - and those entries are dropped so the rest of the catalog still loads. Dropped silently is the
     * one way that is worse than either: the payload is there in the file and nowhere in the app.
     */
    private fun noteIgnored(manifest: SupportManifest) {
        for (ignored in manifest.ignored) {
            AppLog.warn(
                AppLogTags.CATALOG,
                "Ignoring ${ignored.payloadId}: it declares flavour " +
                    "\"${ignored.declaredFlavor}\", and this build knows ${KernelSuFlavor.ids}",
            )
        }
    }

    /** The manifest and the revision it was read at. Both callers need the revision. */
    private data class FetchedManifest(val commit: String, val manifest: SupportManifest)

    private fun fetchManifest(source: PayloadSource): FetchedManifest {
        if (source.isLocal) {
            val key = source.localKey ?: error("Missing local source key")
            val path = "local-sources/$key/targets-v3.json"
            val bytes = context.assets.open(path).use { it.readBytes() }
            require(bytes.size <= MAX_MANIFEST_BYTES) {
                context.getString(R.string.repo_response_too_large)
            }
            val manifest = SupportManifest.parse(bytes)
            noteIgnored(manifest)
            return FetchedManifest("local", manifest)
        }
        val commit = resolveCommit(source)
        val manifestBytes = downloadBytes(
            rawUrl(source, commit, MANIFEST_PATH),
            MAX_MANIFEST_BYTES,
        )
        val manifest = SupportManifest.parse(manifestBytes)
        noteIgnored(manifest)
        return FetchedManifest(commit, manifest)
    }

    private fun loadSource(source: PayloadSource): List<TargetProfile> {
        val fetched = fetchManifest(source)
        val commit = fetched.commit
        return fetched.manifest.targets.map { profile ->
            profile.copy(
                sourceId = source.id,
                sourceLabel = source.label,
                sourceCommit = commit,
                exploit = profile.exploit.copy(
                    url = if (source.isLocal) profile.exploit.url
                    else pinArtifactUrl(source, profile.exploit.url, commit),
                ),
                kernelSu = profile.kernelSu.copy(
                    url = if (source.isLocal) profile.kernelSu.url
                    else pinArtifactUrl(source, profile.kernelSu.url, commit),
                ),
                rootHelper = profile.rootHelper?.let { helper ->
                    helper.copy(
                        url = if (source.isLocal) helper.url
                        else pinArtifactUrl(source, helper.url, commit),
                    )
                },
            )
        }
    }

    /**
     * S25 Ultra CZG3 hotfix: keep the selected exploit/source, but pair it with the current
     * KernelSU v3.3.0 userspace+LKM bundle. This fixes the manager/driver 32601↔32525 mismatch
     * without changing the exploit that already proved reliable on this device.
     */
    private fun applyKernelSuDriverHotfix(
        profile: TargetProfile,
        snapshot: DeviceSnapshot,
        onProgress: (String) -> Unit,
    ): TargetProfile {
        if (profile.sourceId.startsWith("local:")) return profile

        val czg3 = snapshot.manufacturer.equals("samsung", ignoreCase = true) &&
            snapshot.model.equals("SM-S938B", ignoreCase = true) &&
            snapshot.device.equals("pa3q", ignoreCase = true) &&
            snapshot.kernelRelease ==
                "6.6.98-android15-8-pd6ff1cd-abogkiS938BXXSBCZG3-4k"

        if (!czg3 || profile.flavor != KernelSuFlavor.Default) return profile

        val current = RemoteArtifact(
            url = "https://raw.githubusercontent.com/igorcv88/Root-My-Galaxy-Payloads-Extended/" +
                "8d997446d2a8e10eb9062b5588deec7d985f700c/kernelsu/ksud-s25u-kdp-v3.3.0",
            size = 5_101_328,
            sha256 = "9c07ab0f9922cef5a8ef0c7805967d1019bf9e084553bc994e7a42c9347bdab1",
        )

        if (profile.kernelSu.sha256 == current.sha256 && profile.kernelSuVersion == "3.3.0") {
            return profile
        }

        onProgress("KernelSU driver hotfix: v3.3.0 / 32601 for SM-S938B CZG3")
        AppLog.info(
            AppLogTags.CATALOG,
            "KernelSU driver hotfix: keeping ${profile.sourceLabel.ifBlank { profile.profileId }} exploit " +
                "and upgrading only the CZG3 KernelSU bundle to v3.3.0 / 32601",
        )
        return profile.copy(
            kernelSu = current,
            kernelSuVersion = "3.3.0",
        )
    }
    fun download(profile: TargetProfile, onProgress: (String) -> Unit): VerifiedPayloads {
        val effectiveProfile = applyKernelSuDriverHotfix(profile, DeviceSnapshot.current(), onProgress)
        val directory = File(context.filesDir, "payloads/${cacheKey(effectiveProfile)}").apply { mkdirs() }
        val exploitDestination = File(directory, "cve-2026-43499-app.so")
        val exploit = if (AppPreferences.useLocalPayload(context)) {
            importedExploit(directory, onProgress) ?: downloadArtifact(
                effectiveProfile.exploit,
                exploitDestination,
                context.getString(R.string.artifact_exploit),
                onProgress,
            )
        } else {
            try {
                downloadArtifact(
                    effectiveProfile.exploit,
                    exploitDestination,
                    context.getString(R.string.artifact_exploit),
                    onProgress,
                )
            } catch (remoteError: Throwable) {
                // An unselected local build is still the last-resort payload. This keeps a device
                // usable when its configured source cannot serve the exploit, without silently
                // preferring local over a healthy source.
                importedExploit(directory, onProgress) ?: throw remoteError
            }
        }
        val kernelSu = downloadArtifact(
            effectiveProfile.kernelSu,
            File(directory, "ksud-s25u-kdp"),
            // Named after the flavour, because the two daemons are different binaries from different
            // projects and the log is the only place a run says which one it installed.
            context.getString(R.string.artifact_daemon, effectiveProfile.flavor.label),
            onProgress,
        )
        onProgress("Verifying root helper")
        val localHelper = effectiveProfile.rootHelper?.takeIf { it.url.startsWith("asset://") }
        val rootHelper = if (localHelper != null) {
            downloadArtifact(
                localHelper,
                File(directory, "root-helper-local.so"),
                "root helper",
                onProgress,
            ).also { Os.chmod(it.absolutePath, 0b111101101) }
        } else {
            RootHelperBundle.forProfile(context, effectiveProfile)
        }
        onProgress("root helper verified")

        Os.chmod(exploit.absolutePath, 0b100100100)
        Os.chmod(kernelSu.absolutePath, 0b100100100)
        val payloads = VerifiedPayloads(effectiveProfile, exploit, kernelSu, rootHelper)
        KernelSuBootstrapStore.prepare(context, payloads)
        onProgress("KernelSU bootstrap source prepared")
        return payloads
    }

    /**
     * Stages the imported payload in place of the downloaded exploit. KernelSU still comes from the
     * source matched to this device, because nothing about importing an exploit changes which
     * ksud this kernel needs.
     */
    private fun importedExploit(directory: File, onProgress: (String) -> Unit): File? {
        if (LocalPayload.file(context) == null) return null
        onProgress(
            context.getString(
                R.string.repo_using_local_payload,
                LocalPayload.displayName(context).orEmpty(),
            ),
        )
        return LocalPayload.stage(context, File(directory, "cve-2026-43499-app.so"))
    }

    private fun stageAssetArtifact(
        artifact: RemoteArtifact,
        destination: File,
        label: String,
        onProgress: (String) -> Unit,
    ): File {
        val assetPath = artifact.url.removePrefix("asset://")
        onProgress(context.getString(R.string.repo_downloading, label))
        val temporary = File(destination.parentFile, "${destination.name}.part")
        val digest = MessageDigest.getInstance("SHA-256")
        var total = 0L
        context.assets.open(assetPath).use { input ->
            FileOutputStream(temporary).use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    total += count
                    digest.update(buffer, 0, count)
                    output.write(buffer, 0, count)
                }
                output.fd.sync()
            }
        }
        require(!artifact.checksSize || total == artifact.size) {
            context.getString(R.string.repo_incomplete, label)
        }
        artifact.sha256?.let { declared ->
            require(digest.digest().toHex() == declared) {
                context.getString(R.string.repo_hash_mismatch, label)
            }
        }
        if (destination.exists()) destination.delete()
        require(temporary.renameTo(destination)) {
            context.getString(R.string.repo_finalize_failed, label)
        }
        onProgress(context.getString(R.string.repo_verified, label))
        return destination
    }

    private fun downloadArtifact(
        artifact: RemoteArtifact,
        destination: File,
        label: String,
        onProgress: (String) -> Unit,
    ): File {
        if (artifact.url.startsWith("asset://")) {
            return stageAssetArtifact(artifact, destination, label, onProgress)
        }
        // A source can mark an artifact as unverifiable, which is the only way to accept it when
        // its declared size is wrong; the default stays strict for every other download. A declared
        // hash proves more than a size does, so it takes over and the size is then only a limit on
        // how much may be read rather than a value that has to match.
        val checked = artifact.checksSize
        onProgress(context.getString(R.string.repo_downloading, label))
        val temporary = File(destination.parentFile, "${destination.name}.part")
        val connection = open(artifact.url)
        require(!checked || connection.contentLengthLong == -1L || connection.contentLengthLong == artifact.size) {
            context.getString(R.string.repo_size_mismatch, label)
        }
        val digest = MessageDigest.getInstance("SHA-256")
        var total = 0L
        connection.inputStream.use { input ->
            FileOutputStream(temporary).use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    total += count
                    require(!checked || total <= artifact.size) {
                        context.getString(R.string.repo_size_exceeded, label)
                    }
                    digest.update(buffer, 0, count)
                    output.write(buffer, 0, count)
                }
                output.fd.sync()
            }
        }
        connection.disconnect()
        require(!checked || total == artifact.size) {
            context.getString(R.string.repo_incomplete, label)
        }
        artifact.sha256?.let { declared ->
            require(digest.digest().toHex() == declared) {
                context.getString(R.string.repo_hash_mismatch, label)
            }
        }
        if (destination.exists()) destination.delete()
        require(temporary.renameTo(destination)) {
            context.getString(R.string.repo_finalize_failed, label)
        }
        onProgress(context.getString(R.string.repo_verified, label))
        return destination
    }

    /** Keeps two sources offering the same payload id from sharing a download directory. */
    private fun cacheKey(profile: TargetProfile): String {
        val profilePart = sanitize(profile.profileId)
        if (profile.sourceId.isEmpty()) return profilePart
        return "${sanitize(profile.sourceId)}--$profilePart"
    }

    private fun sanitize(value: String): String =
        value.replace(Regex("[^A-Za-z0-9._-]"), "_")

    /** Resolves a source's ref to the commit it currently points at, for pinning it from the UI. */
    fun resolveRevision(source: PayloadSource): String = resolveCommit(source)

    /**
     * Resolves a ref the user named - a branch, a tag, or a commit - to the commit to pin.
     *
     * A source that is already pinned resolves its own ref instead, because the pin is the revision
     * being read; naming a different ref from the picker replaces the pin rather than the ref.
     */
    fun resolveNamedRevision(repository: String, ref: String): String {
        val trimmed = ref.trim()
        if (PayloadSource.isCommitValid(trimmed)) return trimmed
        // A ref that cannot be resolved fails the read, with the answer the repository gave for it.
        return resolveRefToCommit(repository, trimmed)
    }

    /**
     * The revisions a source can be pinned to: its tags, then its most recent commits, newest first.
     *
     * The first commit of a ref *is* that ref's head, so the picker needs no separate call to say
     * where the branch currently stands, and the list is what gives a pin something to choose from
     * rather than only "wherever it is now". A repository with no tags is the normal case and is
     * not an error.
     */
    fun revisions(source: PayloadSource, limit: Int = DEFAULT_REVISION_COUNT): List<SourceRevision> {
        val tags = runCatching { tags(source, limit) }.getOrDefault(emptyList())
        val commits = runCatching { atomRevisions(source.repository, source.branch, limit) }
            .getOrElse { commits(source, limit) }
        return tags + commits
    }

    /**
     * A ref resolved without the REST API where possible.
     *
     * `github.com/{owner}/{repo}/commits/{ref}.atom` answers with the ref's commits - the first of
     * which is the ref's head - and is not subject to the 60-requests-an-hour limit an unauthenticated
     * API client shares with every other app on the same address. That limit is not hypothetical:
     * reaching it is what made adding a source and pinning one fail with `HTTP 403`, and it is spent
     * by nothing the user can see. The API stays as the fallback, for a network where github.com is
     * unreachable but api.github.com is not.
     */
    private fun resolveRefToCommit(repository: String, ref: String): String {
        val fromAtom = runCatching { atomRevisions(repository, ref, 1) }.getOrNull()
            ?.firstOrNull()
            ?.commit
        if (fromAtom != null && PayloadSource.isCommitValid(fromAtom)) return fromAtom

        val response = downloadBytes(
            repositoryCommitApiUrl(repository, ref),
            MAX_COMMIT_RESPONSE_BYTES,
            GITHUB_SHA_MEDIA_TYPE,
        )
        val commit = parseCommitResponse(response.toString(Charsets.UTF_8))
        require(commit != null) { context.getString(R.string.repo_commit_invalid) }
        return commit
    }

    private fun atomRevisions(repository: String, ref: String, limit: Int): List<SourceRevision> =
        parseCommitAtom(
            downloadBytes(atomCommitsUrl(repository, ref), MAX_LIST_RESPONSE_BYTES)
                .toString(Charsets.UTF_8),
            limit,
        )

    private fun tags(source: PayloadSource, limit: Int): List<SourceRevision> = parseTags(
        downloadBytes(tagsApiUrl(source, limit), MAX_LIST_RESPONSE_BYTES).toString(Charsets.UTF_8),
        limit,
    )

    private fun commits(source: PayloadSource, limit: Int): List<SourceRevision> = parseCommits(
        downloadBytes(
            commitsApiUrl(source, limit),
            MAX_LIST_RESPONSE_BYTES,
        ).toString(Charsets.UTF_8),
        limit,
    )

    private fun resolveCommit(source: PayloadSource): String {
        // A pinned source is taken as written: no network at all, so its catalog cannot move and it
        // keeps loading in every way except the raw download.
        if (source.isPinned) return source.pinnedCommit
        return resolveRefToCommit(source.repository, source.branch)
    }

    /**
     * `/commits/{ref}` and not `/git/refs/heads/{branch}`: a source's ref may be a branch, a tag, or
     * a commit, and this resolves all three to the commit it points at. The same is true of the atom
     * feed, which is preferred.
     */
    private fun repositoryCommitApiUrl(repository: String, ref: String) =
        "https://api.github.com/repos/$repository/commits/$ref"

    private fun commitsApiUrl(source: PayloadSource, limit: Int) =
        "https://api.github.com/repos/${source.repository}/commits" +
            "?sha=${source.branch}&per_page=$limit"

    private fun tagsApiUrl(source: PayloadSource, limit: Int) =
        "https://api.github.com/repos/${source.repository}/tags?per_page=$limit"

    private fun atomCommitsUrl(repository: String, ref: String) =
        "https://github.com/$repository/commits/$ref.atom"

    private fun rawRepository(source: PayloadSource) =
        "https://raw.githubusercontent.com/${source.repository}"

    private fun rawUrl(source: PayloadSource, commit: String, path: String) =
        "${rawRepository(source)}/$commit/$path"

    private fun pinArtifactUrl(source: PayloadSource, url: String, commit: String): String =
        pinnedArtifactUrl(source, url, commit)
            ?: error(context.getString(R.string.repo_url_invalid))

    private fun downloadBytes(url: String, maximum: Int, accept: String? = null): ByteArray {
        val connection = open(url, accept)
        val bytes = connection.inputStream.use { input ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                require(output.size() + count <= maximum) {
                    context.getString(R.string.repo_response_too_large)
                }
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        }
        connection.disconnect()
        return bytes
    }

    private fun open(url: String, accept: String? = null): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 60_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "S25URoot/${BuildConfig.VERSION_NAME}")
            accept?.let { setRequestProperty("Accept", it) }
            connect()
            require(responseCode == HttpURLConnection.HTTP_OK) {
                // A limited API answers 403 (and 429 when it is explicit about it). Reporting the
                // status number alone sends the reader looking for a fault in their repository.
                if (responseCode == HttpURLConnection.HTTP_FORBIDDEN || responseCode == 429) {
                    context.getString(R.string.repo_api_limited)
                } else {
                    "HTTP $responseCode"
                }
            }
        }

    companion object {
        /** Asks for the bare commit instead of the commit object, so the answer cannot grow with it. */
        private const val GITHUB_SHA_MEDIA_TYPE = "application/vnd.github.sha"

        // A ceiling, not a target: the SHA answer is 40 bytes. It is generous enough that a server
        // ignoring the media type and sending the commit object still resolves rather than failing
        // on a limit that only ever existed to bound memory.
        private const val MAX_COMMIT_RESPONSE_BYTES = 512 * 1024
        /** How many revisions a picker offers, per section, when a source is pinned. */
        const val DEFAULT_REVISION_COUNT = 15

        // A revision list is ~4 KB per commit, so this bounds memory with room to spare; the tags
        // answer is smaller still. Neither can grow with the commit the way a commit object did.
        private const val MAX_LIST_RESPONSE_BYTES = 1024 * 1024
        private const val MAX_MANIFEST_BYTES = 256 * 1024
    }
}
