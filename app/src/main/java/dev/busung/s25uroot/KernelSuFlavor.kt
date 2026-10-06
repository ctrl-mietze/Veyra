package ctrl.mietze.veyraroot

import androidx.annotation.StringRes
import org.json.JSONArray
import org.json.JSONObject

/**
 * Which KernelSU the app installs and drives.
 *
 * KernelSU, KernelSU-Next and ReSukiSU are separate projects with separate kernels, separate managers
 * and separate daemons, and no two of them can be in the kernel at once: each hooks the same syscall
 * paths, so a boot carries one of them or neither. That is why this is stored for the app rather than
 * passed to one run, and why changing it has to wait for a restart.
 *
 * The ids are the feed's own. A payload entry declares `"flavor": "kernelsu-next"`, and this decides
 * whether a run may use that entry. An entry that says nothing is [Default], which is what keeps every
 * manifest written before flavours existed readable.
 */
enum class KernelSuFlavor(
    /** The id a feed entry names. */
    val id: String,
    /** What the app calls it in a log line; prose for the screen lives in [summaryRes]. */
    val label: String,
    /** The manager APK's package, which is how the app can tell which one is installed. */
    val managerPackage: String,
    /** Where its releases live, which is what a manager upgrade resolves against. */
    val repository: String,
    /**
     * The version this flavour falls back to, and the only one whose APK file name the app knows.
     *
     * It is no longer the whole answer. What the app offers is the KernelSU the payload it resolved for
     * this device declares, and this is what is left when nothing says: no payload resolved yet, or an
     * entry that does not declare a version. That is why it must name a release the project really
     * builds from rather than the newest that exists - it is the manager for the daemon this project's
     * payloads stage when the feed is silent about which KernelSU that is.
     *
     * The three are not the same number: this project's KernelSU-Next payload pins 3.4.0 (upstream's
     * newest there), KernelSU is still 3.3.0, which is the newest release tiann/KernelSU has published,
     * and ReSukiSU's is the pre-release its own payload was built against.
     *
     * Nothing checks this against the version on the phone - a newer manager installs and is used exactly
     * the same, and one picked by hand takes precedence.
     */
    val defaultManagerVersion: String,
    /** The file name that version was published under, for when the store cannot be asked. */
    val defaultManagerAsset: String,
    /**
     * Whether this flavour's kernel can be told, at runtime, which APK is its manager.
     *
     * This is a property of the module rather than of the app's preference, so it is stated on the
     * flavour: the kernel decides who its manager is by comparing an APK's signature against a table
     * built into the module, and only ReSukiSU's carries the second path that lets a key be given to it
     * afterwards. The other two can only be told by recompiling, which is not something an app can do.
     *
     * False is the safe default and not merely the common one: offering to register a manager with a
     * kernel that has no such feature would put a control on the screen that cannot work.
     */
    val supportsDynamicManager: Boolean = false,
    /** What this flavour is, for the settings row that offers it. */
    @StringRes val summaryRes: Int,
) {
    KernelSu(
        id = "kernelsu",
        label = "KernelSU",
        managerPackage = "me.weishu.kernelsu",
        repository = "tiann/KernelSU",
        defaultManagerVersion = "3.3.0",
        defaultManagerAsset = "KernelSU_v3.3.0_32601-release.apk",
        summaryRes = R.string.flavor_kernelsu_summary,
    ),
    KernelSuNext(
        id = "kernelsu-next",
        label = "KernelSU-Next",
        managerPackage = "com.rifsxd.ksunext",
        repository = "KernelSU-Next/KernelSU-Next",
        defaultManagerVersion = "3.4.0",
        defaultManagerAsset = "KernelSU_Next_v3.4.0_33294-release.apk",
        summaryRes = R.string.flavor_kernelsu_next_summary,
    ),
    ReSukiSU(
        id = "resukisu",
        label = "ReSukiSU",
        managerPackage = "com.resukisu.resukisu",
        repository = "ReSukiSU/ReSukiSU",
        // A pre-release, and named as one everywhere below: this project marks every release it has
        // published as a pre-release, so the newest tag is `v4.2.0-rc2` and there is no `v4.2.0` for a
        // lookup to resolve. The suffix is part of the release's name rather than a description of it -
        // the tag, the asset and the version the pairs declare all carry it - so keeping it is what
        // makes a version named here resolve to the same release the daemon was built from.
        defaultManagerVersion = "4.2.0-rc2",
        // The universal APK, unlike the other two flavours' single release file: this project publishes
        // one per ABI and a manager has to install on whatever phone asks for it.
        defaultManagerAsset = "ReSukiSU_v4.2.0-rc2_35144-universal-release.apk",
        supportsDynamicManager = true,
        summaryRes = R.string.flavor_resukisu_summary,
    ),
    ;

    /**
     * This flavour's own release, without asking for it.
     *
     * What the offer resolves to when it is this version, and what the download falls back to when it
     * is - see [ManagerOffer.assetNameKnown].
     */
    val defaultManagerRelease: ManagerRelease
        get() = ManagerRelease(
            flavor = this,
            version = defaultManagerVersion,
            url = releaseAssetUrl(defaultManagerVersion, defaultManagerAsset),
        )

    companion object {
        /** What a payload entry means when it does not declare a flavour. */
        val Default = KernelSu

        /**
         * The flavour an id names, or null when it is not one this build knows.
         *
         * Null rather than a fallback: a manifest that says `"flavor": "kernel-su"` is a typo, and
         * reading it as the default would install the wrong kernel on a phone that asked for the
         * other one. The caller turns this into a message that lists [ids].
         */
        fun fromId(id: String?): KernelSuFlavor? {
            val trimmed = id?.trim().orEmpty()
            if (trimmed.isEmpty()) return null
            return entries.firstOrNull { it.id.equals(trimmed, ignoreCase = true) }
        }

        /** The ids a feed entry may declare, for a message that says what was expected. */
        val ids: String get() = entries.joinToString { it.id }
    }
}

/** A manager APK: whose it is, which version, and where to download it. */
data class ManagerRelease(
    val flavor: KernelSuFlavor,
    val version: String,
    val url: String,
) {
    val assetName: String
        get() = url.substringAfterLast('/')
}

/** The download URL for one of a flavour's own release assets. */
internal fun KernelSuFlavor.releaseAssetUrl(version: String, asset: String): String =
    "https://github.com/$repository/releases/download/v$version/$asset"

/**
 * The APK inside a GitHub release, as the releases API describes it.
 *
 * A manager's file name carries a build number that its version does not - 3.3.0 publishes
 * `KernelSU_v3.3.0_32601-release.apk` - so a version can only be turned into a download by asking for
 * the release. That is also what makes a manual upgrade possible: nothing here assumes a version, so
 * a version upstream has not shipped is a failed lookup rather than a wrong file.
 *
 * A `spoofed` build is skipped while another APK is present. It is a variant that reports a different
 * signature to the modules that check one, which is not what an unprompted install should hand over.
 * A release carrying only that variant still resolves to it, because a file is better than a refusal
 * once the user has named the version themselves.
 */
internal fun managerApkInRelease(body: String): String? {
    val assets = runCatching { JSONObject(body.trim()).optJSONArray("assets") }.getOrNull() ?: return null
    val apks = buildList {
        for (index in 0 until assets.length()) {
            val asset = assets.optJSONObject(index) ?: continue
            val name = asset.optString("name")
            val url = asset.optString("browser_download_url").trim()
            if (url.isEmpty() || !name.endsWith(".apk")) continue
            add(name to url)
        }
    }
    return (apks.firstOrNull { !it.first.contains("spoofed", ignoreCase = true) }
        ?: apks.firstOrNull())?.second
}

/**
 * The versions a flavour's release listing offers, newest first.
 *
 * What a listing makes possible is *choosing* a version rather than remembering one. Each entry is a tag
 * with its leading `v` removed, which is exactly what the lookup for one version asks for
 * (`releases/tags/v<version>`) - so a version picked from this list resolves through the same code path
 * as one typed by hand, and the two cannot drift apart.
 *
 * Drafts are skipped: a draft's tag is not published yet, so looking it up could only fail. A
 * prerelease is kept, and its own tag is what says it is one - what a flavour's newest release is, is
 * not this app's decision to make.
 */
internal fun managerVersionsInReleases(body: String): List<String> {
    // Left to throw when the answer is not a listing at all, which is what a rate limit or a renamed
    // repository looks like: they are a failure to read, not an empty catalogue.
    val releases = JSONArray(body.trim())
    val versions = mutableListOf<String>()
    for (index in 0 until releases.length()) {
        val release = releases.optJSONObject(index) ?: continue
        if (release.optBoolean("draft", false)) continue
        val version = release.optString("tag_name").trim()
            .removePrefix("v")
            .removePrefix("V")
            .trim()
        if (version.isEmpty() || version in versions) continue
        versions += version
    }
    return versions
}

/**
 * What this boot can do with [selected], given which flavour is already loaded into it.
 *
 * Only one of the two fits in the kernel, and the loader says so rather than replacing what is there,
 * so a run of the other flavour is a restart away rather than an error to retry. [AlreadyLoaded] is
 * not a refusal: loading the same flavour again is what a second run in one boot does anyway, and it
 * ends at the same place.
 */
internal enum class FlavorBootState {
    /** Nothing of either flavour is loaded, so this boot can take [selected]. */
    Loadable,

    /** The flavour being run is the one this boot already carries. */
    AlreadyLoaded,

    /** The other flavour is in the kernel, so this one cannot be loaded until a restart. */
    OtherFlavorLoaded,
}

internal fun flavorBootState(
    selected: KernelSuFlavor,
    loadedInThisBoot: KernelSuFlavor?,
): FlavorBootState = when {
    loadedInThisBoot == null -> FlavorBootState.Loadable
    loadedInThisBoot == selected -> FlavorBootState.AlreadyLoaded
    else -> FlavorBootState.OtherFlavorLoaded
}
