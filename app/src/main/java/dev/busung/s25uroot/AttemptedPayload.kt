package ctrl.mietze.veyraroot

import android.content.Context
import android.content.SharedPreferences
import android.system.Os
import java.io.File

/**
 * The payload the last run attempted, recorded before the exploit starts.
 *
 * The known-good cache is deliberately the opposite of this: it is written only once a run has
 * completed and verified KernelSU, which is exactly what a failed run has not done. So a device whose
 * last run failed has nothing cached for the payload it failed with - and "retry" means run *that*
 * attempt again, which on a device somebody is testing payloads on is by definition not the last one
 * that worked. It can be a different source, a different commit, or the other KernelSU project.
 *
 * Nothing is copied. The attempt's own download directory is pointed at, and both files are verified
 * again on the way out against the digests the run verified them against in the first place, which is
 * what makes pointing at a directory this app shares with every other run of the same profile safe.
 */
internal object AttemptedPayloadStore {
    private const val PREFERENCES = "attempted_payload"
    private const val DESCRIPTOR = "descriptor"
    private const val EXPLOIT_PATH = "exploit_path"
    private const val KERNEL_SU_PATH = "kernel_su_path"
    private const val ROOT_HELPER_PATH = "root_helper_path"

    /**
     * Records what this run is about to try.
     *
     * Called before the exploit rather than after it, because a record made only on the success path
     * would be a record of the runs that do not need one.
     */
    @Synchronized
    fun save(context: Context, payloads: VerifiedPayloads) {
        require(payloads.origin == PayloadOrigin.Downloaded) {
            "Only a payload downloaded from a source is an attempt to record"
        }
        val profile = payloads.profile
        require(profile.matches(DeviceSnapshot.current())) {
            "Only an attempt for this device can be recorded"
        }
        val helper = payloads.rootHelper
        require(helper.isFile) { "The root helper this attempt uses is unavailable" }
        val helperSha256 = sha256Of(helper)
        val descriptor = CachedPayload(
            id = knownGoodId(
                // The feed's own digests when it states them, since those are what this run verified
                // the files against; computed only for a feed that does not.
                exploitSha256 = profile.exploit.sha256 ?: sha256Of(payloads.exploit),
                kernelSuSha256 = profile.kernelSu.sha256 ?: sha256Of(payloads.kernelSu),
                helperSha256 = helperSha256,
            ),
            profileId = profile.profileId,
            displayName = profile.displayName,
            models = profile.models.toList(),
            kernelVersions = profile.kernelVersions.toList(),
            requiresFreshP0Session = profile.requiresFreshP0Session,
            routePolicy = profile.routePolicy,
            exploit = profile.exploit,
            kernelSu = profile.kernelSu,
            rootHelper = profile.rootHelper,
            flavor = profile.flavor,
            sourceId = profile.sourceId,
            sourceLabel = profile.sourceLabel,
            sourceCommit = profile.sourceCommit,
            helperSha256 = helperSha256,
            helperSize = helper.length(),
        )
        val stored = preferences(context).edit()
            .putString(DESCRIPTOR, descriptor.toJson())
            .putString(EXPLOIT_PATH, payloads.exploit.absolutePath)
            .putString(KERNEL_SU_PATH, payloads.kernelSu.absolutePath)
            .putString(ROOT_HELPER_PATH, payloads.rootHelper.absolutePath)
            .commit()
        require(stored) { "Unable to record the attempted payload" }
    }

    /**
     * Whether an attempt was recorded, without hashing the files behind it.
     *
     * Deliberately cheap: the boot gate asks this before it decides whether this boot has anything to
     * run from, and it is answering in front of the one attempt the boot gets.
     */
    fun hasRecord(context: Context): Boolean = runCatching { descriptor(context) }.getOrNull() != null

    /** What the last attempt was, for a screen that has to name the payload a retry would run. */
    fun describe(context: Context): CachedPayload? = runCatching { descriptor(context) }.getOrNull()

    /**
     * The attempt, ready to run again, or the reason it cannot be, or null when none was recorded.
     *
     * The three outcomes lead somewhere different, which is why they are not flattened into a nullable
     * payload: a runnable attempt is what the retry is for, an unusable one is refused rather than
     * quietly replaced by the cached payload, and a device with nothing recorded is the one case where
     * the cache is still the only thing that could run.
     */
    @Synchronized
    fun resolve(context: Context): Result<VerifiedPayloads>? {
        val descriptor = runCatching { descriptor(context) }.getOrNull() ?: return null
        val stored = preferences(context)
        val exploit = stored.getString(EXPLOIT_PATH, null)?.let(::File)
        val kernelSu = stored.getString(KERNEL_SU_PATH, null)?.let(::File)
        val rootHelper = runCatching {
            RootHelperBundle.forProfile(context, descriptor.profile())
        }.getOrNull()
        val helperSha256 = rootHelper?.let(::sha256Of) ?: ""
        val helperSize = rootHelper?.length() ?: 0L
        attemptedPayloadRejection(
            attempted = descriptor,
            helperSha256 = helperSha256,
            helperSize = helperSize,
            snapshot = DeviceSnapshot.current(),
            exploitFile = exploit,
            kernelSuFile = kernelSu,
            rootHelperFile = rootHelper,
        )?.let { reason -> return Result.failure(IllegalStateException(reason)) }
        // Re-checked on the way out, exactly as the cache is: pointing at a directory the app writes to
        // for every run of this profile is only safe while the files are still what the run verified.
        listOfNotNull(exploit, kernelSu)
            .forEach { Os.chmod(it.absolutePath, 0b100100100) }
        // rootHelper is package-manager-owned under /data/app and is already executable.
        return Result.success(
            VerifiedPayloads(
                descriptor.profile(),
                exploit!!,
                kernelSu!!,
                rootHelper!!,
                PayloadOrigin.Attempted,
            ),
        )
    }

    /** Forgets the attempt, which is what makes the next retry fall back to the cache. */
    @Synchronized
    fun clear(context: Context) {
        preferences(context).edit().clear().commit()
    }

    private fun descriptor(context: Context): CachedPayload {
        val text = preferences(context).getString(DESCRIPTOR, null)
            ?.trim()
            ?.takeIf(String::isNotEmpty)
            ?: error("No payload attempt has been recorded")
        val descriptor = CachedPayload.parse(text)
        require(descriptor.sourceCommit.isNotEmpty() || descriptor.sourceId.isNotEmpty()) {
            "The recorded attempt does not say where it came from"
        }
        return descriptor
    }

    private fun preferences(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
}

/**
 * Why a recorded attempt cannot be run again, or null when it can.
 *
 * The same checks the cached payload gets, plus the one only an attempt needs: its files live in the
 * run's own download directory, which this app does not treat as a store. A later run of the same
 * profile overwrites them and clearing app data removes them, so "still there and still the same bytes"
 * is the whole question. Pure, so it can be checked without a device - and the answer matters more than
 * most: the alternative to this refusal is running a payload the user did not ask for.
 */
internal fun attemptedPayloadRejection(
    attempted: CachedPayload,
    helperSha256: String,
    helperSize: Long,
    snapshot: DeviceSnapshot,
    exploitFile: File?,
    kernelSuFile: File?,
    rootHelperFile: File?,
): String? {
    cacheRejectionReason(
        cached = attempted,
        helperSha256 = helperSha256,
        helperSize = helperSize,
        snapshot = snapshot,
        // No requested profile: a retry is not choosing a target, it is repeating a choice.
        requestedProfileId = null,
    )?.let { return it }
    if (exploitFile == null || !fileMatchesArtifact(exploitFile, attempted.exploit)) {
        return "The exploit this retry was for is no longer on the device; run it again from the app"
    }
    if (kernelSuFile == null || !fileMatchesArtifact(kernelSuFile, attempted.kernelSu)) {
        return "The KernelSU payload this retry was for is no longer on the device; run it again from the app"
    }
    if (rootHelperFile == null || !rootHelperFile.isFile ||
        rootHelperFile.length() != attempted.helperSize ||
        sha256Of(rootHelperFile) != attempted.helperSha256
    ) {
        return "The root helper this retry was verified with is no longer on the device; run it again from the app"
    }
    return null
}
