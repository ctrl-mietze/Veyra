package ctrl.mietze.veyraroot

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.net.Uri
import android.os.Handler
import android.os.Looper
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** A manager this app found on the phone, whether or not its package is the published one. */
internal data class InstalledManager(
    val packageName: String,
    val label: String,
    /**
     * The version of the manager that is installed, as its own package declares it.
     *
     * Read from the package rather than from anything this app chose, because it is the one reading
     * that says what is actually on the phone: a manager installed by hand, replaced by its own
     * updater, or left over from an older release all read here as themselves. Null when the package
     * could not be asked, which is not the same as a manager with no version.
     */
    val versionName: String?,
    /** The flavour its package name or its label claims, or null when neither says. */
    val flavor: KernelSuFlavor?,
    /** Whether its package is not the one its project publishes. */
    val spoofed: Boolean,
)

/** What a package name and an app label say about which manager this is. */
internal data class ManagerIdentity(
    val flavor: KernelSuFlavor?,
    val spoofed: Boolean,
)

/**
 * Whether an installed manager's own label says anything the row does not already say.
 *
 * It does not for the manager a flavour publishes: its label is the flavour's own name, which the row
 * above the version already carries, so printing it under the title would be the version's duplication
 * moved to a different word. It does for a manager that does not publish this name - KernelSU-Next's
 * spoofed build rewrites its package to three random words per release, and its label is then the only
 * thing on the phone that identifies it.
 *
 * Pure, and case-insensitive because a label is the app's own text: `KernelSU` and `kernelsu` are the
 * same name, and only one of them is the one this app chose.
 */
internal fun managerNameWorthShowing(installed: InstalledManager, flavor: KernelSuFlavor): Boolean =
    !installed.label.trim().equals(flavor.label, ignoreCase = true)

/**
 * The Manager row's value band: the version on the phone, and an empty band when there is none.
 *
 * Empty rather than the version the app would install, which is the row below this one and not a fact about
 * this device. Filling it with the offered version made the row claim a number the phone did not have - and
 * with the description naming the same version to install, say it twice, which is what a reader notices
 * first about a row like this.
 *
 * A manager whose own package would not answer for its version reads the same way, and that is deliberate:
 * the row says what is known, and "installed, version unknown" is not the same fact as "3.3.0 is installed".
 */
internal fun managerRowValue(installed: InstalledManager?): String = installed?.versionName.orEmpty()

/**
 * Which project an installed manager belongs to, from the two things it carries.
 *
 * The package name is authoritative when it is one either project publishes. When it is not, the
 * label is the only remaining signal, and a label that says neither leaves the flavour unknown rather
 * than guessed: a package that carries a KernelSU daemon is still a manager worth offering, and
 * attributing it to the wrong project would put it in the wrong flavour's row.
 *
 * Pure, because the case this exists for is the odd one. KernelSU-Next's spoofed manager build
 * rewrites its package to three random words every time it is built, so that name can never be a
 * constant in this app and the label is what is left.
 */
internal fun identifyManager(packageName: String, label: String): ManagerIdentity {
    val published = KernelSuFlavor.entries.firstOrNull {
        it.managerPackage.equals(packageName.trim(), ignoreCase = true)
    }
    if (published != null) return ManagerIdentity(published, spoofed = false)

    val words = label.lowercase().replace('-', ' ').replace('_', ' ')
    return when {
        "next" in words -> ManagerIdentity(KernelSuFlavor.KernelSuNext, spoofed = true)
        // Ahead of the KernelSU test because the two share a substring: `resukisu` contains `su`, and
        // reading this project's manager as KernelSU's would put it in the wrong row.
        "resukisu" in words || "re suki su" in words ->
            ManagerIdentity(KernelSuFlavor.ReSukiSU, spoofed = true)
        "kernelsu" in words || "kernel su" in words -> ManagerIdentity(KernelSuFlavor.KernelSu, spoofed = true)
        else -> ManagerIdentity(null, spoofed = true)
    }
}

/**
 * The KernelSU manager app: which one is on the phone, what the app offers to install, and opening it.
 *
 * The manager is not part of the payload. It is a plain app that talks to the loaded module over
 * KernelSU's socket, so it can be any version and can be replaced at any time without touching the
 * kernel - which is why nothing here refuses a version it did not choose, and why the apk is found
 * by what it carries rather than by a name this app would have to know in advance.
 */
internal object KernelSuManager {
    /** The manager the app will open for [flavor], or null when none is installed. */
    fun installedFor(context: Context, flavor: KernelSuFlavor): InstalledManager? {
        // A package the user named wins, because that is the only way a build whose name changes on
        // every release can be addressed at all.
        AppPreferences.managerPackage(context, flavor)?.let { named ->
            if (isLaunchable(context, named)) {
                return InstalledManager(
                    packageName = named,
                    label = labelOf(context, named),
                    versionName = versionNameOf(context, named),
                    flavor = flavor,
                    spoofed = true,
                )
            }
        }
        if (isLaunchable(context, flavor.managerPackage)) {
            return InstalledManager(
                packageName = flavor.managerPackage,
                label = labelOf(context, flavor.managerPackage),
                versionName = versionNameOf(context, flavor.managerPackage),
                flavor = flavor,
                spoofed = false,
            )
        }
        // Nothing under a published name, so look for one under any name. A plain build is preferred
        // over a spoofed one only when both are present, which is a tie nobody has.
        val found = installedManagers(context).filter { it.flavor == flavor }
        return found.firstOrNull { !it.spoofed } ?: found.firstOrNull()
    }

    fun isInstalled(context: Context, flavor: KernelSuFlavor): Boolean =
        installedFor(context, flavor) != null

    /**
     * Where the APK of an installed package lives, or null when it cannot be named.
     *
     * `sourceDir` rather than the split list, because the signature the kernel matches is the one on
     * the base APK: a split carries the same signer, and the file `ksud` is asked to read has to be
     * the one whose signing block holds it.
     *
     * Null is a real answer rather than a failure - a package removed between the scan and this call
     * is the usual cause - and it is what keeps a caller from asking the daemon to read a path that is
     * no longer there and reporting the daemon's confusion as its own.
     */
    fun apkPathOf(context: Context, packageName: String): String? = runCatching {
        context.packageManager.getApplicationInfo(packageName, 0).sourceDir
    }.getOrNull()?.takeIf(String::isNotBlank)

    /** The package the app will open for [flavor], installed or not. */
    fun packageFor(context: Context, flavor: KernelSuFlavor): String =
        installedFor(context, flavor)?.packageName ?: flavor.managerPackage

    /**
     * Every installed app that carries a KernelSU daemon.
     *
     * The daemon is the marker: a manager ships `libksud.so`, because the manager is what runs `ksud`
     * on the phone. It is what makes a spoofed build findable at all, since its package name is
     * rewritten to something different every time it is released.
     *
     * Needs `QUERY_ALL_PACKAGES` to see an app that is not already named in the manifest's `queries`,
     * which no fixed list can do for a name that changes per build.
     */
    fun installedManagers(context: Context): List<InstalledManager> {
        val manager = context.packageManager
        return runCatching {
            manager.getInstalledPackages(0).mapNotNull { installed ->
                val app = installed.applicationInfo ?: return@mapNotNull null
                if (app.packageName == context.packageName) return@mapNotNull null
                if (!carriesDaemon(app)) return@mapNotNull null
                if (!isLaunchable(context, app.packageName)) return@mapNotNull null
                val label = labelOf(context, app.packageName)
                val identity = identifyManager(app.packageName, label)
                InstalledManager(
                    packageName = app.packageName,
                    label = label,
                    versionName = versionNameOf(context, app.packageName),
                    flavor = identity.flavor,
                    spoofed = identity.spoofed,
                )
            }.sortedWith(compareBy({ it.spoofed }, { it.label }))
        }.getOrDefault(emptyList())
    }

    /**
     * The release the app offers for [flavor], from [offeredManager]'s three facts.
     *
     * No network when the asset name is known, which is the flavour's own release - so the usual offer
     * costs nothing. Any other version is left pointing at its release page here and resolved for real
     * on the way to installing it, where a failed lookup is worth a message to the person who asked.
     */
    fun offeredRelease(context: Context, flavor: KernelSuFlavor): ManagerRelease {
        val offer = offeredManager(context, flavor)
        if (offer.assetNameKnown) return flavor.defaultManagerRelease
        return flavor.defaultManagerRelease.copy(version = offer.version, url = releasePageUrl(flavor, offer.version))
    }

    /**
     * Opens the manager, or the download for the offered release when it is not installed.
     *
     * The installed case wins deliberately: a manager of any version is what drives the loaded module,
     * and sending a user who already has one to a download page would be an upgrade they did not ask
     * for.
     */
    fun open(context: Context, flavor: KernelSuFlavor, onMessage: (String) -> Unit) {
        val installed = installedFor(context, flavor)
        if (installed != null) {
            val launch = runCatching {
                context.packageManager.getLaunchIntentForPackage(installed.packageName)
            }.getOrNull()
            if (launch != null) {
                context.startActivity(launch)
                return
            }
        }
        // Nothing named, so this is the app's own offer rather than a version the caller has in hand.
        openDownload(context, flavor, null, onMessage)
    }

    /**
     * Opens the download for one version, whether or not a manager is already installed.
     *
     * [open] deliberately prefers the installed manager - a row that opens a manager should open the
     * one the phone has - which leaves no way to *replace* one from this app: a version picked in the
     * picker could never take effect once a manager existed. This is that way, and it exists for the
     * case where replacing it is the whole point: a manager from another line than the KernelSU this
     * boot is running, which [managerVersionState] is what finds.
     */
    fun downloadVersion(
        context: Context,
        flavor: KernelSuFlavor,
        version: String,
        onMessage: (String) -> Unit,
    ) = openDownload(context, flavor, version, onMessage)

    /**
     * The one download path, so a version named by hand, picked from a listing, read off the device and
     * offered by the payload all arrive at the same URL by the same rules.
     *
     * Two things it does not do, both of them deliberate. It does not install anything itself - the
     * release is opened for the phone's own installer, which is the only thing that may replace a
     * manager. And it does not guess an asset name: a version's file carries a build number its version
     * does not (`KernelSU_v3.3.0_32601-release.apk`), so anything but a flavour's own default is
     * resolved through the release it names.
     *
     * A blank [version] means "whatever this app offers", which is the payload's KernelSU when one has
     * been resolved - so the version a run will load is also the version the manager row installs, and
     * neither side has to be told the other's number.
     *
     * The resolve is started on [lookups] rather than awaited here, because every caller of this is a
     * tap and a tap handler runs on the main thread - where the socket that read wants to open is
     * refused before it can be opened at all. A version whose asset name is known never asks, which is
     * why only the versions worth looking up were the ones that could not be reached.
     */
    private fun openDownload(
        context: Context,
        flavor: KernelSuFlavor,
        version: String?,
        onMessage: (String) -> Unit,
    ) {
        val wanted = version?.trim().orEmpty().ifBlank { offeredManager(context, flavor).version }
        if (wanted == flavor.defaultManagerVersion) {
            onMain { view(context, flavor.defaultManagerRelease.url) }
            return
        }
        onMain {
            onMessage(context.getString(R.string.settings_manager_version_looking, flavor.label, wanted))
        }
        lookups.launch {
            val resolved = resolve(context, flavor, wanted)
            onMain {
                if (resolved == null) {
                    AppLog.warn(
                        AppLogTags.KERNEL_SU,
                        "No ${flavor.label} $wanted release could be resolved; opening the releases page",
                    )
                    onMessage(
                        context.getString(R.string.settings_manager_version_missing, flavor.label, wanted),
                    )
                    return@onMain
                }
                AppLog.info(
                    AppLogTags.KERNEL_SU,
                    "Downloading the ${flavor.label} ${resolved.version} manager",
                )
                view(context, resolved.url)
            }
        }
    }

    /**
     * The versions [flavor] has published, newest first, or the failure that stopped the listing.
     *
     * One request for the newest page rather than one per version, and the API rather than the atom feed
     * the payload sources read: the releases endpoint answers with the tag *and* the assets, which is the
     * same endpoint the lookup for a single version already uses - so a version chosen from a listing and
     * a version typed into the field are resolved by one piece of code, and a version that appears here
     * is a version that can be downloaded.
     *
     * Left as a failure rather than flattened to an empty list, because the two mean different things to
     * the screen that asked: no versions at all is a project that has published nothing, and a listing
     * that could not be read is a network or a rate limit, which is what the manual field is for.
     *
     * Read once per flavour per run: the answer changes only when upstream publishes, and a listing is
     * the largest answer this app asks GitHub for - see [MAX_LISTING_BYTES].
     *
     * A network read, so it belongs on a thread that may open a socket: every caller wraps it in
     * `Dispatchers.IO`, and a caller that does not gets a refusal rather than a listing.
     */
    fun availableVersions(flavor: KernelSuFlavor): Result<List<String>> {
        cachedVersions[flavor]?.let { return Result.success(it) }
        return runCatching {
            managerVersionsInReleases(downloadText(releasesApiUrl(flavor), MAX_LISTING_BYTES))
        }.onFailure { error ->
            // The screen already says this; the tab says it next to everything else that was happening,
            // which is what makes a rate limit look like a rate limit rather than a broken project.
            AppLog.warn(
                AppLogTags.KERNEL_SU,
                "Could not list ${flavor.label} versions: " +
                    (error.message ?: error.javaClass.simpleName),
            )
        }.onSuccess { cachedVersions[flavor] = it }
    }

    /**
     * The listings already read, kept for the life of the process.
     *
     * Re-reading one on every open would spend megabytes, and the unauthenticated request budget that
     * the payload sources share, to learn something that changes only when upstream publishes.
     */
    private val cachedVersions = mutableMapOf<KernelSuFlavor, List<String>>()

    /**
     * The APK for one version, resolved through the releases API.
     *
     * Null when the version has no release, the release carries no APK, or the network refused - all
     * three are the same answer to the caller, which is that this version could not be turned into a
     * download. Going through the API is what lets a version be named at all: the asset's file name
     * carries a build number (`KernelSU_v3.3.0_32601-release.apk`) that the version does not.
     *
     * Opens a socket, so it must not be called from the main thread. [openDownload] is its only caller
     * and it starts the read on [lookups] for that reason.
     */
    fun resolve(context: Context, flavor: KernelSuFlavor, version: String): ManagerRelease? {
        val body = runCatching { downloadText(managerReleaseApiUrl(flavor, version), MAX_RELEASE_BYTES) }
            .onFailure { error ->
                AppLog.warn(
                    AppLogTags.KERNEL_SU,
                    "Could not read the ${flavor.label} $version release: " +
                        (error.message ?: error.javaClass.simpleName),
                )
            }
            .getOrNull() ?: return null
        val apk = managerApkInRelease(body) ?: run {
            AppLog.warn(AppLogTags.KERNEL_SU, "The ${flavor.label} $version release carries no APK")
            return null
        }
        return ManagerRelease(flavor = flavor, version = version, url = apk)
    }

    /** Whether a package is installed and has something to open. */
    private fun isLaunchable(context: Context, packageName: String): Boolean =
        runCatching {
            context.packageManager.getLaunchIntentForPackage(packageName) != null
        }.getOrDefault(false)

    private fun labelOf(context: Context, packageName: String): String = runCatching {
        val info = context.packageManager.getApplicationInfo(packageName, 0)
        context.packageManager.getApplicationLabel(info).toString()
    }.getOrDefault(packageName)

    /**
     * The version an installed package declares for itself.
     *
     * `versionName` rather than the version code, because it is the name the releases are tagged with
     * and therefore the only form that can be compared against the running KernelSU's. Null when the
     * package manager would not answer, which sends the comparison to [ManagerVersionState.Unknown]
     * rather than to a mismatch.
     */
    private fun versionNameOf(context: Context, packageName: String): String? = runCatching {
        context.packageManager.getPackageInfo(packageName, 0).versionName
    }.getOrNull()?.trim()?.takeIf(String::isNotBlank)

    /**
     * Whether an app carries the KernelSU daemon.
     *
     * Read from the app's own native library directory, which is where an installed manager's
     * `libksud.so` lands, and which covers both projects: their managers embed `ksud` the same way.
     */
    private fun carriesDaemon(info: ApplicationInfo): Boolean {
        val directory = info.nativeLibraryDir ?: return false
        return runCatching { File(directory, DAEMON_LIBRARY).isFile }.getOrDefault(false)
    }

    private fun view(context: Context, url: String) {
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    /**
     * Runs [block] on the main thread, whichever thread asked for it.
     *
     * Both things a finished lookup does want to be there: a toast is a window, and [view] starts an
     * activity. The lookup itself arrives from [lookups], and the other path through this object runs
     * straight from the tap that asked - so one of the two is always the wrong thread to call into.
     */
    private fun onMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else mainHandler.post(block)
    }

    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * The manager lookups a tap starts, on a thread that may open a socket.
     *
     * This exists because of a bug that hid itself well: [openDownload] used to resolve on the thread
     * that asked, every caller of it is a tap in a Compose handler, and that thread is the main one -
     * where `connect()` throws `NetworkOnMainThreadException` instead of connecting. The consequence
     * was not a crash or an error screen but a specific, plausible-looking sentence: naming 3.4.0, a
     * release that was published and reachable the whole time, produced "could not read the
     * KernelSU-Next 3.4.0 release" and sent the user to the releases page. The versions that always
     * worked were the ones whose asset name this app already knows, because those never ask at all -
     * so the failure looked like it was about the version named rather than about where it was read
     * from, and the listing beside it (which does run on `Dispatchers.IO`) showed that version in the
     * picker the whole time.
     *
     * A process-wide scope because there is nothing here to cancel: one small request whose answer is
     * used once, and it outliving the screen that asked is harmless.
     */
    private val lookups = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private fun downloadText(url: String, ceiling: Int): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "S25URoot/${BuildConfig.VERSION_NAME}")
            setRequestProperty("Accept", "application/vnd.github+json")
            connect()
            require(responseCode == HttpURLConnection.HTTP_OK) { refusal(responseCode) }
        }
        return connection.inputStream.use { input -> readCappedText(input, ceiling) }
            .also { connection.disconnect() }
    }

    /**
     * Why an answer was not read, in words a screen can show.
     *
     * Only the shared refusal is spelled out: an unauthenticated listing is allowed sixty requests an
     * hour per address, which a phone can exhaust on its own, and "could not read the versions" with no
     * reason reads as a broken app rather than a wait.
     */
    private fun refusal(status: Int): String = when (status) {
        HttpURLConnection.HTTP_FORBIDDEN, TOO_MANY_REQUESTS ->
            "GitHub refused it (HTTP $status), which is its request limit for this address"
        else -> "GitHub answered HTTP $status"
    }

    /** The release's own page, which is where a human looks when the API cannot answer. */
    private fun releasePageUrl(flavor: KernelSuFlavor, version: String): String =
        "https://github.com/${flavor.repository}/releases/tag/v$version"

    private fun managerReleaseApiUrl(flavor: KernelSuFlavor, version: String): String =
        "https://api.github.com/repos/${flavor.repository}/releases/tags/v$version"

    private fun releasesApiUrl(flavor: KernelSuFlavor): String =
        "https://api.github.com/repos/${flavor.repository}/releases?per_page=$VERSION_LIST_LIMIT"

    /**
     * How many releases one listing asks for.
     *
     * The API answers newest first, so this is "the versions anyone would pick from" rather than all of
     * them - a project with a hundred releases has a decade of them, and a chooser that long is worse
     * than the field beside it for anything older. It also decides what a listing may weigh, since the
     * page size is what the answer's size follows.
     */
    private const val VERSION_LIST_LIMIT = 10

    /** The name every manager's embedded daemon has once it is installed. */
    private const val DAEMON_LIBRARY = "libksud.so"

    /** An unauthenticated request over the limit, which GitHub also answers with 403. */
    private const val TOO_MANY_REQUESTS = 429
}

/** One release's answer is a few hundred KB; the ceiling only bounds memory on a wrong URL. */
internal const val MAX_RELEASE_BYTES = 1024 * 1024

/**
 * How much of a *listing* this app will read, which is not one release's worth.
 *
 * Every entry in a listing carries its changelog, so ten releases of these two projects measured
 * 0.7 MB (KernelSU) and 2.1 MB (KernelSU-Next), and thirty measured 5.2 MB. Reading them under the
 * single-release ceiling refused both projects at once, which is exactly how it reached the screen: two
 * flavours that could not be read, and no way for the screen to tell that the app had refused its own
 * answer.
 */
internal const val MAX_LISTING_BYTES = 8 * 1024 * 1024

/**
 * Reads a response body into text, refusing anything longer than [ceiling].
 *
 * Kept out of the request so the ceiling can be tested without one: a ceiling is what went wrong here,
 * not a parser, and the reason it refused has to survive into the message - "not read" with no reason
 * is what left the screen with nothing to say.
 */
internal fun readCappedText(input: InputStream, ceiling: Int): String {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    var total = 0
    while (true) {
        val count = input.read(buffer)
        if (count < 0) break
        total += count
        require(total <= ceiling) {
            "the answer was larger than the ${ceiling / (1024 * 1024)} MB this app reads"
        }
        output.write(buffer, 0, count)
    }
    return output.toString(Charsets.UTF_8.name())
}
