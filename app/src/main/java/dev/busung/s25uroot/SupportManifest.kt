package ctrl.mietze.veyraroot

import org.json.JSONArray
import org.json.JSONObject

data class RemoteArtifact(
    val url: String,
    val size: Long,
    /**
     * Whether the declared [size] is enforced. The feed sets this to false for an artifact whose
     * declared size is not trustworthy, which is the only alternative to disabling the check for
     * every artifact of every source at once. A declared [sha256] proves the same thing and more,
     * so it takes over the check and this flag stops mattering for that artifact.
     */
    val verifySize: Boolean = true,
    /**
     * Lowercase hex SHA-256 of the artifact, when the feed declares one.
     *
     * This is what a size cannot be: verifiable. A size says nothing about the content, so a feed
     * that cannot state a trustworthy one has to turn checking off altogether; a hash lets it state
     * something the app can check either way.
     */
    val sha256: String? = null,
) {
    init {
        require(!verifySize || size > 0) {
            "An enforced size has to be positive: $url declares $size"
        }
        require(sha256 == null || isSha256(sha256)) { "Invalid artifact SHA-256 for $url" }
    }

    /** Whether the declared size still has to be checked, which a hash makes redundant. */
    val checksSize: Boolean
        get() = verifySize && sha256 == null
}

/** Whether [value] is a lowercase hex SHA-256, the only form the app compares against. */
internal fun isSha256(value: String): Boolean =
    value.length == 64 && value.all { it in '0'..'9' || it in 'a'..'f' }

data class TargetProfile(
    val profileId: String,
    val displayName: String,
    val models: Set<String>,
    val devices: Set<String> = emptySet(),
    val buildIds: Set<String> = emptySet(),
    val kernelVersions: Set<String>,
    val requiresFreshP0Session: Boolean = false,
    /** How this target wants its exploit run. Carried with the profile so every path uses it. */
    val routePolicy: ExploitRoutePolicy = ExploitRoutePolicy.LEGACY,
    val exploit: RemoteArtifact,
    val kernelSu: RemoteArtifact,
    /**
     * Firmware/profile-specific bootstrap helper when the feed publishes one.
     *
     * Older feeds omit this and use the helper bundled in the APK. Exact firmware feeds can publish
     * a helper together with their exploit and KernelSU pair; all three then form one verified set and
     * must travel through the run together.
     */
    val rootHelper: RemoteArtifact? = null,
    /**
     * Which KernelSU this entry's daemon and module belong to.
     *
     * Carried with the profile rather than read from the app's setting, because the two have to be
     * the same thing: this entry's `ksud` embeds a module for one flavour's kernel, and installing
     * it into the other one's is what a flavour mix-up looks like from the phone's side. An entry
     * that does not declare one is KernelSU, which is every entry written before flavours existed.
     */
    val flavor: KernelSuFlavor = KernelSuFlavor.Default,
    /**
     * The KernelSU release this entry's daemon and module are built from, when the feed says.
     *
     * Declared rather than worked out from [payloadId], because the id is a name and this is a fact:
     * `pa3q-S938USQSCCZF9-ksun340` spells its version into a string the app would have to parse and
     * could get wrong, while the entry can simply say `"version": "3.4.0"` beside the artifact it
     * describes. Null for every entry written before the field existed, and for a hand-written pair
     * whose version nobody recorded - which the manager offer reads as "nothing declared" rather than
     * as a version, and falls back to the flavour's own.
     *
     * It is the version of the *KernelSU*, not of the payload: the daemon staged by a run and the
     * manager installed to drive it have to be the same release, and this is the side of that pair the
     * feed knows.
     */
    val kernelSuVersion: String? = null,
    val kernelKmi: String? = null,
    /** Source that provided this target, empty when it was not loaded through one. */
    val sourceId: String = "",
    val sourceLabel: String = "",
    /**
     * Commit the source was read at when this target was loaded. Artifact URLs are pinned to it, so
     * recording it is what makes a finished run traceable to the catalog revision it came from
     * rather than to whatever the branch held at the time.
     */
    val sourceCommit: String = "",
) {
    init {
        require(models.isNotEmpty()) { "Payload must support at least one model" }
        require(kernelVersions.isNotEmpty()) { "Payload must support at least one kernel version" }
    }

    fun matchesDevice(snapshot: DeviceSnapshot): Boolean =
        models.any { pattern ->
            pattern == "*" ||
                (pattern.endsWith("*") &&
                    snapshot.model.startsWith(pattern.dropLast(1), ignoreCase = true)) ||
                pattern.equals(snapshot.model, ignoreCase = true)
        } ||
            devices.any { it.equals(snapshot.device, ignoreCase = true) }

    fun matchesBuild(snapshot: DeviceSnapshot): Boolean =
        buildIds.isEmpty() || buildIds.any { build ->
            snapshot.buildId.equals(build, ignoreCase = true) ||
                snapshot.buildId.startsWith(build, ignoreCase = true) ||
                snapshot.buildId.contains(build, ignoreCase = true) ||
                snapshot.fingerprint.contains(build, ignoreCase = true)
        }

    /**
     * Whether this entry covers the device's kernel, in either form a source may declare it.
     *
     * The feed writes the leading three-part `uname -r` value, and an entry may additionally list a
     * full release to say which exact build it documents. Both are a match here because both are a
     * match everywhere else: [resolveFor] and [kernelMatch] already read a listed full release as
     * coverage, so an entry that declared only that used to be selected by neither and refused by
     * this - a payload a source offers, and a device told nothing covers it.
     */
    fun matchesKernelVersion(snapshot: DeviceSnapshot): Boolean =
        kernelVersions.any { declared ->
            snapshot.kernelVersion == declared ||
                snapshot.kernelRelease == declared ||
                (declared.count { it == '.' } == 1 &&
                    snapshot.kernelRelease.startsWith("$declared."))
        }

    fun matches(snapshot: DeviceSnapshot): Boolean =
        matchesDevice(snapshot) && matchesBuild(snapshot) && matchesKernelVersion(snapshot)

    /** Unique across sources, unlike [profileId], which two sources may both offer. */
    val selectionId: String
        get() = selectionIdFor(sourceId, profileId)

    val supportedModels: String
        get() = models.joinToString()

    val supportedKernelVersions: String
        get() = kernelVersions.joinToString()
}

/**
 * Picks the profile for [snapshot], preferring the ones of [flavor].
 *
 * Within a flavour the rules are unchanged: an exact full kernel-release match wins over a three-part
 * one, so regional builds that share a model and kernel version resolve to the profile that documents
 * their build.
 *
 * The flavour is a preference rather than a filter, and the fallback is deliberate. A catalog that
 * only carries the other flavour for this device is still a catalog that roots it, and refusing to
 * use it would leave a user who picked the wrong flavour in the app with no run at all and no way to
 * tell why. Which flavour was actually offered is a property of the returned profile, and the run
 * reports it, so the fallback is visible instead of silent.
 */
fun List<TargetProfile>.resolveFor(
    snapshot: DeviceSnapshot,
    flavor: KernelSuFlavor = KernelSuFlavor.Default,
): TargetProfile? {
    val matching = filter { it.matches(snapshot) }
    val preferred = matching.filter { it.flavor == flavor }
    return preferred.firstOrNull { snapshot.kernelRelease in it.kernelVersions }
        ?: preferred.firstOrNull()
        ?: matching.firstOrNull { snapshot.kernelRelease in it.kernelVersions }
        ?: matching.firstOrNull()
}

/**
 * How a profile's declared kernel versions line up with a device.
 *
 * A profile that lists the device's full `uname -r` release documents this exact build; one that
 * lists only the three-part version may still be the right payload, but the feed has not tied it to
 * this build, which is what regional siblings look like from the app's side.
 */
enum class KernelMatch {
    /** The device's full kernel release is listed. */
    Exact,

    /** Only the three-part kernel version is listed. */
    Version,

    /** Neither is listed; only reachable in the sheet when the device filter is off. */
    None,
}

fun TargetProfile.kernelMatch(snapshot: DeviceSnapshot): KernelMatch = when {
    snapshot.kernelRelease in kernelVersions -> KernelMatch.Exact
    snapshot.kernelVersion in kernelVersions -> KernelMatch.Version
    kernelVersions.any { declared ->
        declared.count { it == '.' } == 1 &&
            snapshot.kernelRelease.startsWith("$declared.")
    } -> KernelMatch.Version
    else -> KernelMatch.None
}

/**
 * An entry the parser left out, kept so the caller can say so.
 *
 * The parser does not log it itself: `parse` is a pure function over bytes, called from unit tests
 * with no Android runtime under it, and a warning that can only be produced on a device is a warning
 * no test can hold anyone to.
 */
data class UnreadablePayload(
    val payloadId: String,
    /** The flavour the entry declared, exactly as written - it is the part being complained about. */
    val declaredFlavor: String,
)

data class SupportManifest(
    val schemaVersion: Int,
    val targets: List<TargetProfile>,
    /** Entries this build could not serve. Empty for a feed written for this build. */
    val ignored: List<UnreadablePayload> = emptyList(),
) {
    companion object {
        fun parse(bytes: ByteArray): SupportManifest {
            val root = JSONObject(bytes.toString(Charsets.UTF_8))
            val schemaVersion = root.getInt("schemaVersion")
            require(schemaVersion == 3) { "Unsupported support manifest schema" }
            val payloadsJson = root.getJSONArray("payloads")
            val ignored = mutableListOf<UnreadablePayload>()
            val payloads = buildList {
                for (index in 0 until payloadsJson.length()) {
                    val payload = payloadsJson.getJSONObject(index)
                    val flavor = payload.flavorOrNull()
                    if (flavor == null) {
                        // One entry the app cannot serve, and not the whole feed with it. Refusing used
                        // to mean the manifest failed to parse, so the day a feed gained a flavour was
                        // the day every install older than it lost every payload; this way only the
                        // entries this build cannot select are left out. It is still not read as the
                        // default, which was the point of refusing - a device must never be offered
                        // the other project's kernel because a name was close.
                        ignored += UnreadablePayload(
                            payloadId = payload.optString("payloadId"),
                            declaredFlavor = payload.optString("flavor").trim(),
                        )
                        continue
                    }
                    val exploit = payload.getJSONObject("exploit")
                    val kernelSu = payload.getJSONObject("kernelsu")
                    val rootHelper = payload.optJSONObject("rootHelper")
                    add(
                        TargetProfile(
                            profileId = payload.getString("payloadId"),
                            displayName = payload.getString("displayName"),
                            models = payload.getJSONArray("models").strings(),
                            devices = payload.optJSONArray("devices").stringsOrEmpty(),
                            buildIds = payload.optJSONArray("buildIds").stringsOrEmpty(),
                            kernelVersions = payload.kernelVersionsWithExactRelease(),
                            requiresFreshP0Session = payload.optBoolean("requiresFreshP0Session", false),
                            routePolicy = ExploitRoutePolicy.parse(payload.optJSONObject("routePolicy")),
                            exploit = exploit.artifact(),
                            kernelSu = kernelSu.artifact(),
                            rootHelper = rootHelper?.artifact(),
                            kernelSuVersion = kernelSu.declaredVersion(),
                            kernelKmi = payload.optString("kernelKmi").trim().takeIf { it.isNotEmpty() },
                            flavor = flavor,
                        ),
                    )
                }
            }
            return SupportManifest(schemaVersion, payloads, ignored)
        }

        private fun JSONArray.strings(): Set<String> = buildSet {
            for (index in 0 until length()) add(getString(index))
        }

        private fun JSONArray?.stringsOrEmpty(): Set<String> =
            this?.strings().orEmpty()

        /**
         * Schema v3 feeds may keep the broad KMI/kernel version in `kernelVersions` and put the
         * firmware-specific full uname release under `exactMatch.kernelRelease`. Older Veyra builds
         * ignored that object, which collapsed an exact firmware profile back into a loose version
         * match and could let a generic regional payload win instead.
         *
         * Fold the full release into the same set the rest of the app already understands. This keeps
         * old feeds unchanged while making `kernelMatch` and `resolveFor` correctly prefer exact
         * firmware profiles from newer feeds.
         */
        private fun JSONObject.kernelVersionsWithExactRelease(): Set<String> = buildSet {
            addAll(getJSONArray("kernelVersions").strings())
            optJSONObject("exactMatch")
                ?.optString("kernelRelease")
                ?.trim()
                ?.takeIf(String::isNotEmpty)
                ?.let(::add)
        }

        /**
         * The flavour an entry declares, the default when it declares none, and null when it names one
         * this build does not know.
         *
         * An unknown id is never read as the default: a manifest that says `"flavor": "kernel-su"`
         * was written for something, and installing the other project's module because the name looked
         * close is the one outcome that cannot be explained afterwards. What the caller does with null
         * is drop that entry and say so - see [parse].
         */
        private fun JSONObject.flavorOrNull(): KernelSuFlavor? {
            val declared = optString("flavor").trim()
            if (declared.isEmpty()) return KernelSuFlavor.Default
            return KernelSuFlavor.fromId(declared)
        }

        /** Reads one artifact. Both artifacts of a payload take the same optional fields. */
        private fun JSONObject.artifact(): RemoteArtifact = RemoteArtifact(
            url = getString("url"),
            size = getLong("size"),
            verifySize = optBoolean("verifySize", true),
            sha256 = optString("sha256").trim().takeIf(String::isNotEmpty),
        )

        /**
         * The release a `kernelsu` block declares, with the tag's own `v` taken off.
         *
         * It used to be read through [releaseOf], which reduced every value to its dotted number, and
         * that turned out to be lossy for a project whose releases are pre-releases: ReSukiSU publishes
         * `v4.2.0-rc2`, there is no `v4.2.0` to look up, and the daemon the feed serves is built from
         * the `rc2` tag. The suffix is therefore part of the release's name and kept, while the leading
         * `v` is dropped so a feed writing the tag (`v3.4.0`) and one writing the version (`3.4.0`) are
         * still the same value here - this is compared against a manager's own `versionName` and against
         * the flavour's built-in default, and both of those name the release the same way the tag does.
         */
        private fun JSONObject.declaredVersion(): String? {
            val declared = optString("version").trim().takeIf(String::isNotEmpty) ?: return null
            return declared.removePrefix("v").removePrefix("V").trim().ifEmpty { declared }
        }
    }
}

/**
 * What a catalog offers, summarised so a source can be judged before it is saved rather than
 * discovered to be useless by a failed run.
 *
 * [models] and [kernelVersions] are the union across every payload, sorted, because the question a
 * source has to answer is what it covers, not which payload happens to be listed first.
 */
data class SourceCoverage(
    /** Revision the catalog was read at, so a summary is tied to the revision that produced it. */
    val commit: String,
    val payloadCount: Int,
    val models: List<String>,
    val kernelVersions: List<String>,
    /** The payload a run would pick on this device, or null when nothing here fits it. */
    val deviceProfileId: String?,
    /** How many payloads list this device's model and kernel version. */
    val deviceProfileCount: Int,
)

/**
 * Summarises a parsed catalog for [snapshot].
 *
 * The device question is answered with the same [resolveFor] the installer uses, so a summary
 * cannot claim a catalog covers a device that a run would then refuse.
 */
fun SupportManifest.coverageFor(snapshot: DeviceSnapshot, commit: String): SourceCoverage =
    SourceCoverage(
        commit = commit,
        payloadCount = targets.size,
        models = targets.flatMap { it.models }.distinct().sorted(),
        kernelVersions = targets.flatMap { it.kernelVersions }.distinct().sorted(),
        deviceProfileId = targets.resolveFor(snapshot)?.profileId,
        deviceProfileCount = targets.count { it.matches(snapshot) },
    )
