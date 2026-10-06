package ctrl.mietze.veyraroot

import android.content.Context

/**
 * Which fact decided the manager version the app offers.
 *
 * Carried with the version rather than worked out again by the screen that shows it, because the three
 * answers mean different things to a reader: a named version is a choice, a payload's version is what
 * the phone is about to load, and the built-in one is the app's own fallback for a device that has not
 * resolved a payload yet. Printing the number alone leaves those indistinguishable.
 */
internal enum class ManagerOfferOrigin {
    /** Typed into the manager version field, so it wins over every other fact. */
    Named,

    /** Declared by the payload the app resolved for this device: the KernelSU a run will load. */
    Payload,

    /** Nothing else said, so the flavour's own release - the one whose file name the app knows. */
    BuiltIn,
}

/**
 * The manager version to offer, and where that version came from.
 *
 * [assetNameKnown] is the reason this is not just a version: a download has two ways to arrive, and the
 * difference is whether the file inside the release can be named without asking. Only the flavour's own
 * release can - the app knows that one file name - while any other version has to be looked up, because
 * the file carries a build number its version does not (`KernelSU_Next_v3.4.0_33294-release.apk`).
 */
internal data class ManagerOffer(
    val version: String,
    val origin: ManagerOfferOrigin,
    /** Whether the APK for this version can be named without asking GitHub for the release. */
    val assetNameKnown: Boolean,
)

/**
 * The manager version to offer, from the three facts that can decide one.
 *
 * In order of authority, and the order is the whole rule:
 *
 * 1. **A version the user named.** The field exists so a hand-picked manager is possible - a manager
 *    from a line upstream has not published, or a build from elsewhere - and nothing this app resolves
 *    may silently take that choice back.
 * 2. **The version the resolved payload declares.** This is the fact the app was missing: the payload
 *    stages a specific KernelSU daemon, that daemon's own manager is the only one that is certainly
 *    built against it, and the release the app offered instead was a number compiled into the app -
 *    which is how an app came to hand out a manager its own kernel was never built against, and then
 *    warn the user about the mismatch it had just created.
 * 3. **The flavour's own release.** Nothing to go on: no payload resolved yet, or one whose entry does
 *    not declare a version (every entry written before the feed carried the field).
 *
 * Pure, so the order can be tested without a device, a feed or a preference file.
 */
internal fun managerOffer(named: String?, payload: String?, builtIn: String): ManagerOffer {
    val (version, origin) = when {
        !named.isNullOrBlank() -> named.trim() to ManagerOfferOrigin.Named
        !payload.isNullOrBlank() -> payload.trim() to ManagerOfferOrigin.Payload
        else -> builtIn to ManagerOfferOrigin.BuiltIn
    }
    return ManagerOffer(
        version = version,
        origin = origin,
        // Decided by the value rather than by which fact produced it: a payload built from the release
        // the flavour already names is the usual case, and a version typed by hand that happens to be
        // the flavour's own is the same download - so both skip the lookup the others cannot.
        assetNameKnown = version == builtIn,
    )
}

/** The manager the app offers for [flavor], and which fact decided it. */
internal fun offeredManager(context: Context, flavor: KernelSuFlavor): ManagerOffer = managerOffer(
    named = AppPreferences.managerVersion(context, flavor),
    payload = AppPreferences.payloadKernelSuVersion(context, flavor),
    builtIn = flavor.defaultManagerVersion,
)

/**
 * Records the KernelSU version of a payload the app has just resolved for this device.
 *
 * Called from the three places a payload becomes *the* payload: the run's own resolution, a profile
 * picked by hand in the target sheet, and the offline cache a successful run publishes. What it leaves
 * behind is the one fact the manager rows need to offer the release that matches the daemon this phone
 * is about to load - without re-reading the sources, and without the user having to work out which
 * KernelSU the payload they chose is built from.
 *
 * Nothing is written when the entry declares no version, and the previous record is cleared rather than
 * kept: a payload that says nothing about its KernelSU must not leave an older payload's version behind
 * it, which would offer a manager for a daemon that is no longer the one being staged.
 */
internal fun rememberResolvedPayload(context: Context, profile: TargetProfile) {
    AppPreferences.setPayloadKernelSuVersion(context, profile.flavor, profile.kernelSuVersion)
}
