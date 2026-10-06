package ctrl.mietze.veyraroot

import android.content.Context
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kernelpack.boot.BootImageParser
import com.kernelpack.boot.KernelDecompressor
import com.kernelpack.kallsyms.KallsymsFinder
import com.kernelpack.kallsyms.KallsymsOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

internal data class MagicBuilderCapture(
    val file: File,
    val blockDevice: String,
)

internal data class MagicBuilderSupportBundle(
    val savedPath: String,
    val captured: List<String>,
    val missing: List<String>,
)

internal enum class MagicBuilderOemRoute {
    Samsung,
    Huawei,
    Honor,
    Xiaomi,
    OnePlusOppoRealme,
    Pixel,
    Nothing,
    MotorolaLenovo,
    Sony,
    Asus,
    NubiaRedMagicZte,
    VivoIqoo,
    Meizu,
    Transsion,
    Generic,
}

internal data class NothingMagicProfile(
    val name: String,
    val codename: String,
    val modelNumbers: Set<String>,
    val expectedKernelSeries: Set<String>,
    val platform: String,
    val verifiedKernelReleases: Set<String> = emptySet(),
)

internal object NothingMagicProfiles {
    private val profiles = listOf(
        NothingMagicProfile(
            name = "Nothing Phone (1)",
            codename = "spacewar",
            modelNumbers = setOf("a063"),
            expectedKernelSeries = setOf("5.4"),
            platform = "Qualcomm SM7325",
            verifiedKernelReleases = setOf("5.4.289-qgki-g49c0dcb3dc63"),
        ),
        NothingMagicProfile(
            name = "Nothing Phone (2)",
            codename = "pong",
            modelNumbers = setOf("a065", "ain065"),
            expectedKernelSeries = setOf("5.10"),
            platform = "Qualcomm SM8475",
        ),
        NothingMagicProfile(
            name = "Nothing Phone (2a)",
            codename = "pacman",
            modelNumbers = setOf("a142"),
            expectedKernelSeries = setOf("5.15"),
            platform = "MediaTek MT6886",
        ),
        NothingMagicProfile(
            name = "Nothing Phone (2a) Plus",
            codename = "pacmanpro",
            modelNumbers = setOf("a142p"),
            expectedKernelSeries = setOf("5.15"),
            platform = "MediaTek MT6886 family",
        ),
        NothingMagicProfile(
            name = "Nothing Phone (3a / 3a Pro)",
            codename = "asteroids",
            modelNumbers = setOf("a059", "a059p"),
            expectedKernelSeries = setOf("6.1"),
            platform = "Qualcomm SM7635",
        ),
        NothingMagicProfile(
            name = "Nothing Phone (3)",
            codename = "metroid",
            modelNumbers = setOf("a024"),
            expectedKernelSeries = setOf("6.6"),
            platform = "Qualcomm SM8735",
        ),
        NothingMagicProfile(
            name = "Nothing Phone (3a Lite)",
            codename = "galaxian",
            modelNumbers = setOf("a001t"),
            expectedKernelSeries = emptySet(),
            platform = "Nothing platform",
        ),
        NothingMagicProfile(
            name = "Nothing Phone (4a)",
            codename = "frogger",
            modelNumbers = setOf("a069"),
            expectedKernelSeries = setOf("6.1"),
            platform = "Qualcomm SM7635 family",
        ),
        NothingMagicProfile(
            name = "Nothing Phone (4a Pro)",
            codename = "froggerpro",
            modelNumbers = setOf("a069p"),
            expectedKernelSeries = emptySet(),
            platform = "Nothing platform",
        ),
        NothingMagicProfile(
            name = "Nothing Phone (4b)",
            codename = "supercontra",
            modelNumbers = setOf("a009p"),
            expectedKernelSeries = emptySet(),
            platform = "Nothing platform",
        ),
        NothingMagicProfile(
            name = "CMF Phone 1",
            codename = "tetris",
            modelNumbers = setOf("a015"),
            expectedKernelSeries = setOf("6.1"),
            platform = "MediaTek MT6878",
        ),
        NothingMagicProfile(
            name = "CMF Phone 2 Pro",
            codename = "galaga",
            modelNumbers = setOf("a001"),
            expectedKernelSeries = emptySet(),
            platform = "Nothing / CMF platform",
        ),
    )

    fun detect(snapshot: DeviceSnapshot): NothingMagicProfile? {
        val identity = listOf(
            snapshot.manufacturer,
            snapshot.model,
            snapshot.device,
            snapshot.fingerprint,
            snapshot.buildId,
        ).joinToString(" ").lowercase(Locale.ROOT)
        val model = snapshot.model.lowercase(Locale.ROOT)
        val device = snapshot.device.lowercase(Locale.ROOT)
        return profiles
            .sortedByDescending { it.codename.length }
            .firstOrNull { profile ->
                device == profile.codename ||
                    Regex(
                        "(^|[/ _:-])" + Regex.escape(profile.codename) + "([/ _:-]|$)",
                        RegexOption.IGNORE_CASE,
                    ).containsMatchIn(identity) ||
                    profile.modelNumbers.any { number ->
                        model == number ||
                            identity.contains("/$number/") ||
                            identity.contains(" $number ")
                    }
            }
    }
}
internal object MagicBuilderController {
    private const val MIN_BOOT_BYTES = 4L * 1024L * 1024L

    fun captureLiveBoot(context: Context): MagicBuilderCapture {
        require(CVeyraPreferences.enabled(context)) {
            context.getString(R.string.magic_builder_need_cveyra)
        }
        return when (CVeyraPreferences.backend(context)) {
            CVeyraBackend.Root -> captureThroughRootBroker(context)
            CVeyraBackend.WirelessAdb -> captureThroughStorageProxy(context)
            CVeyraBackend.None -> error(context.getString(R.string.magic_builder_need_cveyra))
        }
    }

    private fun captureThroughRootBroker(context: Context): MagicBuilderCapture {
        val probe = CVeyraController.rootShell(context, "id")
        require(isRootAnswer(probe)) {
            context.getString(R.string.magic_builder_need_root)
        }

        val route = oemRoute(DeviceSnapshot.current())
        val findCommand = if (route == MagicBuilderOemRoute.Huawei ||
            route == MagicBuilderOemRoute.Honor
        ) {
            """
                SLOT=$(getprop ro.boot.slot_suffix 2>/dev/null)
                for N in boot ramdisk; do
                  for P in \
                    /dev/block/by-name/${'$'}N${'$'}SLOT \
                    /dev/block/bootdevice/by-name/${'$'}N${'$'}SLOT \
                    /dev/block/by-name/${'$'}N \
                    /dev/block/bootdevice/by-name/${'$'}N; do
                    if [ -n "${'$'}P" ] && [ -r "${'$'}P" ]; then
                      echo "${'$'}P"
                      exit 0
                    fi
                  done
                done
                exit 2
            """.trimIndent()
        } else {
            """
                SLOT=$(getprop ro.boot.slot_suffix 2>/dev/null)
                for P in \
                  /dev/block/by-name/boot${'$'}SLOT \
                  /dev/block/bootdevice/by-name/boot${'$'}SLOT \
                  /dev/block/by-name/boot \
                  /dev/block/bootdevice/by-name/boot; do
                  if [ -n "${'$'}P" ] && [ -r "${'$'}P" ]; then
                    echo "${'$'}P"
                    exit 0
                  fi
                done
                exit 2
            """.trimIndent()
        }
        val found = CVeyraController.rootShell(context, findCommand)
        val source = found
            ?.takeIf { it.exitCode == 0 }
            ?.output
            ?.lineSequence()
            ?.map(String::trim)
            ?.firstOrNull { it.startsWith("/dev/block/") }
            ?: error(context.getString(R.string.magic_builder_need_boot))

        val destination = magicBootDestination(context)
        FileOutputStream(destination, false).use { it.fd.sync() }

        val copy = CVeyraController.rootShell(
            context,
            "/system/bin/dd if=${shellQuote(source)} of=${shellQuote(destination.absolutePath)} " +
                "bs=4194304 conv=fsync 2>/dev/null",
        )
        require(copy?.exitCode == 0 && destination.isFile && destination.length() >= MIN_BOOT_BYTES) {
            destination.delete()
            context.getString(R.string.magic_builder_capture_failed)
        }

        return MagicBuilderCapture(destination, source)
    }

    private fun captureThroughStorageProxy(context: Context): MagicBuilderCapture {
        require(CVeyraPrivilegedStorageProxy.available(context)) {
            context.getString(R.string.magic_builder_proxy_unavailable)
        }
        val destination = magicBootDestination(context)
        val expectedRelease = DeviceSnapshot.current().kernelRelease
        val candidates = CVeyraPrivilegedStorageProxy.findBootCandidates(context)
        require(candidates.isNotEmpty()) {
            context.getString(R.string.magic_builder_proxy_no_boot)
        }

        val failures = ArrayList<String>()
        for (candidate in candidates) {
            destination.delete()
            val result = runCatching {
                CVeyraPrivilegedStorageProxy.pull(context, candidate, destination)
                require(destination.length() >= MIN_BOOT_BYTES) {
                    "file is only ${destination.length()} bytes"
                }
                val release = kernelReleaseOfBootImage(destination)
                    ?: error("kernel release could not be resolved")
                require(release.equals(expectedRelease, ignoreCase = true)) {
                    "kernel=$release expected=$expectedRelease"
                }
                MagicBuilderCapture(destination, candidate)
            }
            result.getOrNull()?.let { capture ->
                AppLog.info(
                    AppLogTags.BUILDER,
                    "Storage Proxy exact-kernel match: ${capture.blockDevice}",
                )
                return capture
            }
            failures += "$candidate: ${result.exceptionOrNull()?.message ?: "unknown mismatch"}"
        }

        destination.delete()
        error(
            context.getString(R.string.magic_builder_proxy_no_exact_boot, expectedRelease) +
                " " + failures.take(4).joinToString(" | "),
        )
    }

    private fun kernelReleaseOfBootImage(file: File): String? = runCatching {
        val parsed = BootImageParser.parse(file.readBytes(), KernelDecompressor.default)
        val version = KallsymsFinder.linuxVersionFromImage(parsed.image)
            ?: return@runCatching null
        kernelReleaseFromBanner(version.first, version.second)
    }.getOrNull()

    private fun magicBootDestination(context: Context): File {
        val directory = File(context.filesDir, "magic-builder").apply {
            require(mkdirs() || isDirectory) {
                context.getString(R.string.magic_builder_workdir_failed)
            }
        }
        return File(directory, "live-boot.img")
    }

    fun chooseScheme(context: Context, snapshot: DeviceSnapshot): PayloadScheme =
        candidateSchemes(context, snapshot).first()

    fun oemRoute(snapshot: DeviceSnapshot): MagicBuilderOemRoute {
        val identity = listOf(
            snapshot.manufacturer,
            snapshot.model,
            snapshot.device,
            snapshot.fingerprint,
        ).joinToString(" ").lowercase(Locale.ROOT)

        return when {
            snapshot.manufacturer.equals("samsung", ignoreCase = true) ||
                snapshot.model.startsWith("SM-", ignoreCase = true) ||
                snapshot.fingerprint.startsWith("samsung/", ignoreCase = true) ->
                MagicBuilderOemRoute.Samsung

            "honor" in identity || "hihonor" in identity -> MagicBuilderOemRoute.Honor
            "huawei" in identity -> MagicBuilderOemRoute.Huawei
            "xiaomi" in identity || "redmi" in identity || "poco" in identity ->
                MagicBuilderOemRoute.Xiaomi
            "oneplus" in identity || "oppo" in identity || "realme" in identity ->
                MagicBuilderOemRoute.OnePlusOppoRealme
            snapshot.manufacturer.equals("google", ignoreCase = true) || "pixel" in identity ->
                MagicBuilderOemRoute.Pixel
            snapshot.manufacturer.equals("nothing", ignoreCase = true) ||
                "nothing" in identity || "cmf" in identity -> MagicBuilderOemRoute.Nothing
            "motorola" in identity || "lenovo" in identity ->
                MagicBuilderOemRoute.MotorolaLenovo
            "sony" in identity || "xperia" in identity -> MagicBuilderOemRoute.Sony
            "asus" in identity || "rog phone" in identity -> MagicBuilderOemRoute.Asus
            "nubia" in identity || "redmagic" in identity || "red magic" in identity ||
                snapshot.manufacturer.equals("zte", ignoreCase = true) ->
                MagicBuilderOemRoute.NubiaRedMagicZte
            "vivo" in identity || "iqoo" in identity -> MagicBuilderOemRoute.VivoIqoo
            "meizu" in identity -> MagicBuilderOemRoute.Meizu
            "tecno" in identity || "infinix" in identity || "itel" in identity ->
                MagicBuilderOemRoute.Transsion
            else -> MagicBuilderOemRoute.Generic
        }
    }

    fun oemRouteName(route: MagicBuilderOemRoute): String = when (route) {
        MagicBuilderOemRoute.Samsung -> "Samsung / One UI"
        MagicBuilderOemRoute.Huawei -> "Huawei / EMUI"
        MagicBuilderOemRoute.Honor -> "Honor / MagicOS"
        MagicBuilderOemRoute.Xiaomi -> "Xiaomi / Redmi / POCO"
        MagicBuilderOemRoute.OnePlusOppoRealme -> "OnePlus / OPPO / realme"
        MagicBuilderOemRoute.Pixel -> "Google Pixel"
        MagicBuilderOemRoute.Nothing -> "Nothing / CMF"
        MagicBuilderOemRoute.MotorolaLenovo -> "Motorola / Lenovo"
        MagicBuilderOemRoute.Sony -> "Sony Xperia"
        MagicBuilderOemRoute.Asus -> "ASUS / ROG"
        MagicBuilderOemRoute.NubiaRedMagicZte -> "Nubia / RedMagic / ZTE"
        MagicBuilderOemRoute.VivoIqoo -> "vivo / iQOO"
        MagicBuilderOemRoute.Meizu -> "Meizu"
        MagicBuilderOemRoute.Transsion -> "TECNO / Infinix / itel"
        MagicBuilderOemRoute.Generic -> "Generic Android"
    }

    /**
     * OEM-aware retry policy. Only vivo/iQOO gets the vr.ko-specific branch.
     * Every other OEM stays on the universal path and must still pass exact baseline gates.
     */
    fun candidateSchemes(
        context: Context,
        snapshot: DeviceSnapshot,
    ): List<PayloadScheme> {
        val vivoRoute = oemRoute(snapshot) == MagicBuilderOemRoute.VivoIqoo
        val localLegacyEnabled = AppPreferences.magicBuilderVivoLegacyLocal(context)
        return if (vivoRoute && localLegacyEnabled) {
            listOf(PayloadScheme.VivoVrKo, PayloadScheme.Universal)
        } else {
            listOf(PayloadScheme.Universal)
        }
    }

    fun allowsScheme(
        context: Context,
        snapshot: DeviceSnapshot,
        scheme: PayloadScheme,
    ): Boolean = scheme in candidateSchemes(context, snapshot)

    fun oemPreflight(snapshot: DeviceSnapshot): List<String> {
        val route = oemRoute(snapshot)
        val vivoLegacy = VivoLegacyProfiles.detect(snapshot)
        return buildList {
            add("${oemRouteName(route)} route")
            if (vivoLegacy != null) {
                add("local-profile=${vivoLegacy.name} aliases=${vivoLegacy.aliases.joinToString("/")}")
                add("local-expected-kernel=${vivoLegacy.expectedSeries.joinToString("/")}")
                if (vivoLegacy.platform.isNotBlank()) add("source-platform=${vivoLegacy.platform}")
                if (vivoLegacy.architecture.isNotBlank()) add("source-arch=${vivoLegacy.architecture}")
                val exactSourceMatch = vivoLegacy.exactReleasePrefixes.any { prefix ->
                    snapshot.kernelRelease.startsWith(prefix, ignoreCase = true)
                }
                if (vivoLegacy.exactReleasePrefixes.isNotEmpty()) {
                    add(
                        "source-release=" + snapshot.kernelRelease +
                            " exact-source-match=" + exactSourceMatch,
                    )
                }
                vivoLegacy.sourceRepository?.let { add("source-repository=$it") }
                vivoLegacy.sourceFacts.forEach { add("source-evidence=$it") }
            }
            add("model=${snapshot.model} device=${snapshot.device}")
            add("build=${snapshot.buildId}")
            add("kernel=${snapshot.kernelRelease}")
            add("fingerprint=${snapshot.fingerprint}")
            add("android=${snapshot.androidRelease} sdk=${snapshot.sdk}")
            add("pageSize=${snapshot.pageSize} abi=${snapshot.abi}")
            if (route != MagicBuilderOemRoute.VivoIqoo) {
                add("vendor-specific vr.ko branch disabled for this OEM")
            }
        }
    }

    private fun kernelSeries(release: String): String =
        release.split('.').take(2).joinToString(".")

    fun nothingPreflight(context: Context, snapshot: DeviceSnapshot): List<String> {
        if (oemRoute(snapshot) != MagicBuilderOemRoute.Nothing) return emptyList()
        val profile = NothingMagicProfiles.detect(snapshot)
        val actualSeries = kernelSeries(snapshot.kernelRelease)
        val runtime = CVeyraController.shell(
            context,
            """
                echo "slot_suffix=$(getprop ro.boot.slot_suffix 2>/dev/null)"
                echo "slot=$(getprop ro.boot.slot 2>/dev/null)"
                echo "nothing_version=$(getprop ro.nothing.version.id 2>/dev/null)"
                echo "display=$(getprop ro.build.display.id 2>/dev/null)"
                echo "vendor_fingerprint=$(getprop ro.vendor.build.fingerprint 2>/dev/null)"
            """.trimIndent(),
        )
        return buildList {
            add("Nothing exact-device route")
            if (profile != null) {
                add("profile=${profile.name} codename=${profile.codename}")
                add("platform=${profile.platform}")
                if (profile.expectedKernelSeries.isNotEmpty()) {
                    add(
                        "kernel-family=$actualSeries expected=" +
                            profile.expectedKernelSeries.sorted().joinToString("/"),
                    )
                    if (actualSeries !in profile.expectedKernelSeries) {
                        add("kernel-family differs from the known stock family; exact baseline gates stay mandatory")
                    }
                } else {
                    add("kernel-family=$actualSeries (learned from this exact live device)")
                }
            } else {
                add("profile=unknown Nothing model; exact live-device analysis mode")
                add("kernel-family=$actualSeries")
            }
            add("exact-build=${snapshot.buildId}")
            add("A/B policy=read active slot only; Magic Builder never changes slots or flashes partitions")
            runtime?.output
                ?.lineSequence()
                ?.map(String::trim)
                ?.filter(String::isNotBlank)
                ?.forEach { add(it) }
        }
    }
    fun captureSupportBundle(
        context: Context,
        boot: MagicBuilderCapture,
        snapshot: DeviceSnapshot,
    ): MagicBuilderSupportBundle {
        require(CVeyraPreferences.enabled(context)) {
            context.getString(R.string.magic_builder_need_cveyra)
        }
        val backend = CVeyraPreferences.backend(context)
        val route = oemRoute(snapshot)
        val nothingProfile = NothingMagicProfiles.detect(snapshot)
        val vivoProfile = VivoLegacyProfiles.detect(snapshot)
        val rootMode = backend == CVeyraBackend.Root &&
            isRootAnswer(CVeyraController.rootShell(context, "id"))
        val proxyMode = backend == CVeyraBackend.WirelessAdb &&
            CVeyraPrivilegedStorageProxy.available(context)
        require(rootMode || proxyMode) {
            context.getString(R.string.magic_builder_need_cveyra)
        }

        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val work = File(context.cacheDir, "magic-support-$stamp").apply {
            deleteRecursively()
            require(mkdirs() || isDirectory) {
                context.getString(R.string.magic_builder_workdir_failed)
            }
        }
        val captured = ArrayList<String>()
        val missing = ArrayList<String>()

        fun copyExisting(source: File, name: String) {
            val target = File(work, name)
            source.inputStream().use { input ->
                FileOutputStream(target, false).use { output -> input.copyTo(output) }
            }
            captured += name
        }

        fun rootCopy(source: String, name: String, minBytes: Long = 1L) {
            val target = File(work, name)
            FileOutputStream(target, false).use { it.fd.sync() }
            val result = CVeyraController.rootShell(
                context,
                "[ -r ${shellQuote(source)} ] || exit 3; " +
                    "/system/bin/dd if=${shellQuote(source)} of=${shellQuote(target.absolutePath)} " +
                    "bs=1048576 conv=fsync 2>/dev/null",
            )
            if (result?.exitCode == 0 && target.length() >= minBytes) {
                captured += name
            } else {
                target.delete()
                missing += "$name ← $source"
            }
        }

        fun findPartition(name: String): String? {
            val command = """
                SLOT=$(getprop ro.boot.slot_suffix 2>/dev/null)
                for P in \
                  /dev/block/by-name/${name}${'$'}SLOT \
                  /dev/block/bootdevice/by-name/${name}${'$'}SLOT \
                  /dev/block/by-name/${name} \
                  /dev/block/bootdevice/by-name/${name}; do
                  if [ -n "${'$'}P" ] && [ -r "${'$'}P" ]; then
                    echo "${'$'}P"
                    exit 0
                  fi
                done
                exit 2
            """.trimIndent()
            return CVeyraController.rootShell(context, command)
                ?.takeIf { it.exitCode == 0 }
                ?.output
                ?.lineSequence()
                ?.map(String::trim)
                ?.firstOrNull { it.startsWith("/dev/block/") }
        }

        copyExisting(boot.file, "boot.img")
        if (rootMode) {
            rootCopy("/sys/kernel/btf/vmlinux", "vmlinux.btf", minBytes = 4096)
            rootCopy("/proc/kallsyms", "kallsyms.txt", minBytes = 256)
            rootCopy("/proc/modules", "modules.txt")
            rootCopy("/proc/config.gz", "config.gz")
            rootCopy("/proc/iomem", "iomem.txt", minBytes = 64)

            val supportPartitions = buildList {
                add("vendor_boot")
                add("init_boot")
                add("dtbo")
                if (route == MagicBuilderOemRoute.Nothing) {
                    add("vbmeta")
                    add("vbmeta_system")
                    add("vbmeta_vendor")
                }
            }
            supportPartitions.forEach { partition ->
                val source = findPartition(partition)
                if (source == null) {
                    missing += "$partition.img"
                } else {
                    rootCopy(source, "$partition.img", minBytes = 4096)
                }
            }
        } else {
            missing += "vmlinux.btf (root-only live path)"
            missing += "kallsyms.txt (root-only live path)"
            missing += "config.gz (root-only live path)"
            missing += "vendor_boot/init_boot/dtbo raw partitions (root-only)"
        }

        val live = if (rootMode) CVeyraController.rootShell(
            context,
            """
                echo '=== uname ==='
                uname -a
                echo '=== proc_version ==='
                cat /proc/version 2>/dev/null
                echo '=== cmdline ==='
                cat /proc/cmdline 2>/dev/null
                echo '=== selinux ==='
                getenforce 2>/dev/null
                echo '=== properties ==='
                getprop
            """.trimIndent(),
        ) else CVeyraController.shell(
            context,
            """
                echo '=== uname ==='
                uname -a
                echo '=== proc_version ==='
                cat /proc/version 2>/dev/null
                echo '=== cmdline ==='
                cat /proc/cmdline 2>/dev/null
                echo '=== selinux ==='
                getenforce 2>/dev/null
                echo '=== modules ==='
                cat /proc/modules 2>/dev/null
                echo '=== properties ==='
                getprop
            """.trimIndent(),
        )
        if (route == MagicBuilderOemRoute.Nothing) {
            val nothingRuntime = if (rootMode) {
                CVeyraController.rootShell(
                    context,
                    """
                        echo '=== Nothing / CMF exact runtime ==='
                        echo "nothing_version=$(getprop ro.nothing.version.id 2>/dev/null)"
                        echo "display=$(getprop ro.build.display.id 2>/dev/null)"
                        echo "slot_suffix=$(getprop ro.boot.slot_suffix 2>/dev/null)"
                        echo "slot=$(getprop ro.boot.slot 2>/dev/null)"
                        echo "bootctl_current=$(bootctl get-current-slot 2>/dev/null)"
                        echo "hardware=$(getprop ro.boot.hardware 2>/dev/null)"
                        echo "vendor_fingerprint=$(getprop ro.vendor.build.fingerprint 2>/dev/null)"
                        echo '=== by-name inventory ==='
                        ls -l /dev/block/by-name 2>/dev/null
                        echo '=== logical partitions ==='
                        lpdump 2>/dev/null | head -n 320
                    """.trimIndent(),
                )
            } else {
                CVeyraController.shell(
                    context,
                    """
                        echo '=== Nothing / CMF exact runtime ==='
                        echo "nothing_version=$(getprop ro.nothing.version.id 2>/dev/null)"
                        echo "display=$(getprop ro.build.display.id 2>/dev/null)"
                        echo "slot_suffix=$(getprop ro.boot.slot_suffix 2>/dev/null)"
                        echo "slot=$(getprop ro.boot.slot 2>/dev/null)"
                        echo "bootctl_current=$(bootctl get-current-slot 2>/dev/null)"
                        echo "hardware=$(getprop ro.boot.hardware 2>/dev/null)"
                        echo "vendor_fingerprint=$(getprop ro.vendor.build.fingerprint 2>/dev/null)"
                        echo '=== by-name inventory ==='
                        ls -l /dev/block/by-name 2>/dev/null
                    """.trimIndent(),
                )
            }
            nothingRuntime?.output
                ?.takeIf(String::isNotBlank)
                ?.let { output ->
                    File(work, "nothing-runtime.txt").writeText(output)
                    captured += "nothing-runtime.txt"
                }
        }

        if (route == MagicBuilderOemRoute.VivoIqoo && vivoProfile != null) {
            val exactSourceMatch = vivoProfile.exactReleasePrefixes.any { prefix ->
                snapshot.kernelRelease.startsWith(prefix, ignoreCase = true)
            }
            File(work, "vivo-kona-source-evidence.txt").writeText(
                buildString {
                    appendLine("Veyra Magic Builder vivo source evidence")
                    appendLine("profile=${vivoProfile.name}")
                    appendLine("platform=${vivoProfile.platform}")
                    appendLine("architecture=${vivoProfile.architecture}")
                    appendLine("repository=${vivoProfile.sourceRepository.orEmpty()}")
                    appendLine("liveKernel=${snapshot.kernelRelease}")
                    appendLine("exactSourceMatch=$exactSourceMatch")
                    appendLine("aliases=${vivoProfile.aliases.sorted().joinToString(",")}")
                    appendLine("expectedSeries=${vivoProfile.expectedSeries.sorted().joinToString(",")}")
                    appendLine("exactReleasePrefixes=${vivoProfile.exactReleasePrefixes.sorted().joinToString(",")}")
                    vivoProfile.sourceFacts.forEach { appendLine("evidence=$it") }
                },
            )
            captured += "vivo-kona-source-evidence.txt"

            val vivoRuntime = if (rootMode) {
                CVeyraController.rootShell(
                    context,
                    """
                        echo '=== vivo / Kona runtime ==='
                        echo "model=$(getprop ro.product.model 2>/dev/null)"
                        echo "device=$(getprop ro.product.device 2>/dev/null)"
                        echo "name=$(getprop ro.product.name 2>/dev/null)"
                        echo "board=$(getprop ro.product.board 2>/dev/null)"
                        echo "hardware=$(getprop ro.hardware 2>/dev/null)"
                        echo "boot_hardware=$(getprop ro.boot.hardware 2>/dev/null)"
                        echo "platform=$(getprop ro.board.platform 2>/dev/null)"
                        echo "project=$(getprop ro.vivo.product.model 2>/dev/null)"
                        echo "project2=$(getprop ro.vivo.product.platform 2>/dev/null)"
                        echo '=== device tree ==='
                        tr '\0' '\n' < /proc/device-tree/model 2>/dev/null
                        tr '\0' '\n' < /proc/device-tree/compatible 2>/dev/null
                        echo '=== modules ==='
                        cat /proc/modules 2>/dev/null
                    """.trimIndent(),
                )
            } else {
                CVeyraController.shell(
                    context,
                    """
                        echo '=== vivo / Kona runtime ==='
                        echo "model=$(getprop ro.product.model 2>/dev/null)"
                        echo "device=$(getprop ro.product.device 2>/dev/null)"
                        echo "name=$(getprop ro.product.name 2>/dev/null)"
                        echo "board=$(getprop ro.product.board 2>/dev/null)"
                        echo "hardware=$(getprop ro.hardware 2>/dev/null)"
                        echo "boot_hardware=$(getprop ro.boot.hardware 2>/dev/null)"
                        echo "platform=$(getprop ro.board.platform 2>/dev/null)"
                        echo "project=$(getprop ro.vivo.product.model 2>/dev/null)"
                        echo "project2=$(getprop ro.vivo.product.platform 2>/dev/null)"
                        echo '=== device tree ==='
                        tr '\0' '\n' < /proc/device-tree/model 2>/dev/null
                        tr '\0' '\n' < /proc/device-tree/compatible 2>/dev/null
                        echo '=== modules ==='
                        cat /proc/modules 2>/dev/null
                    """.trimIndent(),
                )
            }
            vivoRuntime?.output
                ?.takeIf(String::isNotBlank)
                ?.let { output ->
                    File(work, "vivo-runtime.txt").writeText(output)
                    captured += "vivo-runtime.txt"
                }
        }

        File(work, "device.txt").writeText(
            buildString {
                appendLine("Veyra Magic Builder live support bundle")
                appendLine("model=${snapshot.model}")
                appendLine("device=${snapshot.device}")
                appendLine("manufacturer=${snapshot.manufacturer}")
                appendLine("oemRoute=${oemRoute(snapshot)}")
                appendLine("build=${snapshot.buildId}")
                appendLine("fingerprint=${snapshot.fingerprint}")
                appendLine("android=${snapshot.androidRelease} sdk=${snapshot.sdk}")
                appendLine("kernel=${snapshot.kernelRelease}")
                appendLine("pageSize=${snapshot.pageSize} abi=${snapshot.abi}")
                if (route == MagicBuilderOemRoute.Huawei || route == MagicBuilderOemRoute.Honor) {
                    appendLine("huaweiLab=internal-isolated")
                    appendLine("huaweiCapturePolicy=boot-then-ramdisk")
                    appendLine("huaweiSafetyPolicy=exact-kernel+baseline+required-symbol-gates")
                }
                if (route == MagicBuilderOemRoute.Nothing) {
                    appendLine("nothingProfile=${nothingProfile?.name ?: "unknown"}")
                    appendLine("nothingCodename=${nothingProfile?.codename ?: snapshot.device}")
                    appendLine(
                        "nothingExpectedKernel=" +
                            nothingProfile?.expectedKernelSeries.orEmpty()
                                .sorted()
                                .joinToString("/")
                                .ifBlank { "learn-from-live-device" },
                    )
                    appendLine("nothingPlatform=${nothingProfile?.platform ?: "unknown"}")
                    appendLine(
                        "nothingVerifiedKernelReleases=" +
                            nothingProfile?.verifiedKernelReleases.orEmpty()
                                .sorted()
                                .joinToString(",")
                                .ifBlank { "none" },
                    )
                }
                appendLine("bootSource=${boot.blockDevice}")
                appendLine("captured=${captured.joinToString(",")}")
                appendLine("missing=${missing.joinToString(",")}")
                appendLine()
                append(live?.output.orEmpty())
            },
        )
        captured += "device.txt"

        val zipFile = File(context.cacheDir, "VeyraRoot-MagicBuilder-support-$stamp.zip")
        zipFile.delete()
        ZipOutputStream(FileOutputStream(zipFile)).use { zip ->
            work.walkTopDown()
                .filter { it.isFile }
                .forEach { file ->
                    val relative = file.relativeTo(work).invariantSeparatorsPath
                    zip.putNextEntry(ZipEntry(relative))
                    file.inputStream().use { input -> input.copyTo(zip) }
                    zip.closeEntry()
                }
        }

        val saved = DownloadStore.saveFile(context, zipFile.name, zipFile)
        AppPreferences.setMagicUnlockReport(context, saved)
        work.deleteRecursively()
        zipFile.delete()
        return MagicBuilderSupportBundle(saved, captured.toList(), missing.toList())
    }
}

@Composable
internal fun VeyraMagicBuilderSettingsPage(
    padding: PaddingValues,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val snapshot = remember { DeviceSnapshot.current() }
    val vivoProfile = remember(snapshot) { VivoLegacyProfiles.detect(snapshot) }
    val inventory = remember { DfKmiInventory.read(context) }
    val scope = rememberCoroutineScope()
    var radar by remember { mutableStateOf(VeyraCompatibilityRadar.scan(context)) }
    var radarExportMessage by remember { mutableStateOf<String?>(null) }
    var magicToolsBusy by remember { mutableStateOf(false) }
    var magicToolMessage by remember { mutableStateOf<String?>(null) }
    var magicToolLines by remember { mutableStateOf<List<String>>(emptyList()) }

    fun runMagicDiagnostic(block: () -> MagicDiagnosticReport) {
        if (magicToolsBusy) return
        magicToolsBusy = true
        magicToolMessage = null
        scope.launch {
            val result = withContext(Dispatchers.IO) { runCatching(block) }
            result.onSuccess { report ->
                magicToolLines = report.lines
                magicToolMessage = buildString {
                    append(report.title)
                    report.savedPath?.let { append(" · saved: ").append(it) }
                }
            }.onFailure { error ->
                magicToolLines = emptyList()
                magicToolMessage = error.message ?: error.javaClass.simpleName
            }
            magicToolsBusy = false
        }
    }

    var allow4x by remember {
        mutableStateOf(AppPreferences.magicBuilderAllowUnstable4x(context))
    }
    var nearestFamily by remember {
        mutableStateOf(AppPreferences.magicBuilderNearestFamily(context))
    }
    var vivoLegacy by remember {
        mutableStateOf(AppPreferences.magicBuilderVivoLegacyLocal(context))
    }
    var strictKernel by remember {
        mutableStateOf(AppPreferences.magicBuilderStrictKernel(context))
    }
    var autoExport by remember {
        mutableStateOf(AppPreferences.magicBuilderAutoExport(context))
    }
    var autoApply by remember {
        mutableStateOf(AppPreferences.magicBuilderAutoApply(context))
    }
    var showNearestConfirm by remember { mutableStateOf(false) }

    if (showNearestConfirm) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showNearestConfirm = false },
            icon = { Icon(Icons.Rounded.Warning, contentDescription = null) },
            title = { Text(stringResource(R.string.builder_nearest_family_confirm_title)) },
            text = { Text(stringResource(R.string.builder_nearest_family_confirm_body)) },
            confirmButton = {
                FilledTonalButton(
                    onClick = {
                        nearestFamily = true
                        AppPreferences.setMagicBuilderNearestFamily(context, true)
                        showNearestConfirm = false
                    },
                ) {
                    Text(stringResource(R.string.builder_nearest_family_confirm_action))
                }
            },
            dismissButton = {
                TextButton(onClick = { showNearestConfirm = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 20.dp,
            end = 20.dp,
            top = 20.dp + padding.calculateTopPadding(),
            bottom = 32.dp + padding.calculateBottomPadding(),
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.magic_builder_settings_title),
                        style = MaterialTheme.typography.headlineMedium,
                    )
                    Text(
                        stringResource(R.string.magic_builder_settings_summary),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(
                    Icons.Rounded.AutoFixHigh,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                ),
            ) {
                Column {
                    MagicSettingSwitchRow(
                        title = stringResource(R.string.magic_builder_4x_title),
                        summary = stringResource(R.string.magic_builder_4x_summary),
                        checked = allow4x,
                        onCheckedChange = {
                            allow4x = it
                            AppPreferences.setMagicBuilderAllowUnstable4x(context, it)
                        },
                    )
                    androidx.compose.material3.HorizontalDivider()
                    MagicSettingSwitchRow(
                        title = stringResource(R.string.magic_builder_vivo_legacy_title),
                        summary = stringResource(R.string.magic_builder_vivo_legacy_summary),
                        checked = vivoLegacy,
                        onCheckedChange = {
                            vivoLegacy = it
                            AppPreferences.setMagicBuilderVivoLegacyLocal(context, it)
                        },
                    )
                    androidx.compose.material3.HorizontalDivider()
                    MagicSettingSwitchRow(
                        title = stringResource(R.string.builder_exact_kernel_title),
                        summary = stringResource(R.string.builder_exact_kernel_summary),
                        checked = strictKernel,
                        onCheckedChange = {
                            strictKernel = it
                            AppPreferences.setMagicBuilderStrictKernel(context, it)
                        },
                    )
                    androidx.compose.material3.HorizontalDivider()
                    MagicSettingSwitchRow(
                        title = stringResource(R.string.builder_auto_export_title),
                        summary = stringResource(R.string.builder_auto_export_summary),
                        checked = autoExport,
                        onCheckedChange = {
                            autoExport = it
                            AppPreferences.setMagicBuilderAutoExport(context, it)
                        },
                    )
                    androidx.compose.material3.HorizontalDivider()
                    MagicSettingSwitchRow(
                        title = stringResource(R.string.builder_auto_apply_title),
                        summary = stringResource(R.string.builder_auto_apply_summary),
                        checked = autoApply,
                        onCheckedChange = {
                            autoApply = it
                            AppPreferences.setMagicBuilderAutoApply(context, it)
                        },
                    )
                    androidx.compose.material3.HorizontalDivider()
                    MagicSettingSwitchRow(
                        title = stringResource(R.string.builder_nearest_family_title),
                        summary = stringResource(R.string.builder_nearest_family_summary),
                        checked = nearestFamily,
                        onCheckedChange = { want ->
                            if (want) {
                                showNearestConfirm = true
                            } else {
                                nearestFamily = false
                                AppPreferences.setMagicBuilderNearestFamily(context, false)
                            }
                        },
                    )
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                ),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        "Magic tools",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        "Live checks for the exact device before you touch a payload. These do not change the normal Builder.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    FilledTonalButton(
                        enabled = !magicToolsBusy,
                        onClick = {
                            magicToolsBusy = true
                            magicToolMessage = null
                            scope.launch {
                                val result = withContext(Dispatchers.IO) {
                                    val live = DeviceSnapshot.current()
                                    val root = RootAccessProbe.probe(context)
                                    val backend = if (CVeyraPreferences.enabled(context)) {
                                        CVeyraController.probeBackend(context)
                                    } else {
                                        CVeyraBackend.None
                                    }
                                    val freshRadar = VeyraCompatibilityRadar.scan(context)
                                    Triple(live, root, backend) to freshRadar
                                }
                                val live = result.first.first
                                val root = result.first.second
                                val backend = result.first.third
                                radar = result.second
                                magicToolLines = listOf(
                                    "Device: " + live.manufacturer + " " + live.model + " (" + live.device + ")",
                                    "Android: " + live.androidRelease + " / API " + live.sdk,
                                    "Kernel: " + live.kernelRelease,
                                    "OEM route: " + MagicBuilderController.oemRouteName(
                                        MagicBuilderController.oemRoute(live),
                                    ),
                                    "Root grant: " + if (root.granted) {
                                        root.badge() + " · " + root.providerName()
                                    } else {
                                        "none for Veyra"
                                    },
                                    "CVeyra: " + backend.storedValue,
                                )
                                magicToolMessage = "Live preflight refreshed"
                                magicToolsBusy = false
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Rounded.Security, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Run live preflight")
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        FilledTonalButton(
                            enabled = !magicToolsBusy,
                            onClick = {
                                magicToolsBusy = true
                                scope.launch {
                                    magicToolMessage = withContext(Dispatchers.IO) {
                                        runCatching {
                                            val live = DeviceSnapshot.current()
                                            val route = MagicBuilderController.oemRoute(live)
                                            val body = buildString {
                                                appendLine("Veyra Magic exact fingerprint")
                                                appendLine("Manufacturer: " + live.manufacturer)
                                                appendLine("Model: " + live.model)
                                                appendLine("Device: " + live.device)
                                                appendLine("Android: " + live.androidRelease + " / API " + live.sdk)
                                                appendLine("Build: " + live.buildId)
                                                appendLine("Fingerprint: " + live.fingerprint)
                                                appendLine("Kernel: " + live.kernelRelease)
                                                appendLine("Kernel version: " + live.kernelVersionInfo)
                                                appendLine("Machine: " + live.machine)
                                                appendLine("ABI: " + live.abi)
                                                appendLine("Page size: " + live.pageSize)
                                                appendLine("Magic OEM route: " + MagicBuilderController.oemRouteName(route))
                                            }
                                            DownloadStore.save(
                                                context,
                                                "Veyra-Magic-exact-" + live.device + ".txt",
                                                body.toByteArray(),
                                            )
                                        }.fold(
                                            onSuccess = { "Fingerprint saved: " + it },
                                            onFailure = { "Fingerprint export failed: " + (it.message ?: it.javaClass.simpleName) },
                                        )
                                    }
                                    magicToolsBusy = false
                                }
                            },
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Rounded.FolderOpen, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Exact fingerprint")
                        }
                        FilledTonalButton(
                            enabled = !magicToolsBusy,
                            onClick = {
                                magicToolsBusy = true
                                scope.launch {
                                    val backend = withContext(Dispatchers.IO) {
                                        if (!CVeyraPreferences.enabled(context)) {
                                            CVeyraController.setEnabled(context, true)
                                        }
                                        CVeyraController.probeBackend(context)
                                    }
                                    magicToolMessage = if (backend == CVeyraBackend.None) {
                                        "CVeyra could not establish a privileged backend."
                                    } else {
                                        "CVeyra primed · " + backend.storedValue
                                    }
                                    magicToolsBusy = false
                                }
                            },
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Rounded.AutoFixHigh, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Prime CVeyra")
                        }
                    }

                    androidx.compose.material3.HorizontalDivider()
                    Text(
                        "Deep diagnostics",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        FilledTonalButton(
                            enabled = !magicToolsBusy,
                            onClick = {
                                runMagicDiagnostic {
                                    MagicDiagnostics.sourceMatchMatrix(context)
                                }
                            },
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Rounded.Security, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Source Matrix")
                        }
                        FilledTonalButton(
                            enabled = !magicToolsBusy,
                            onClick = {
                                runMagicDiagnostic {
                                    MagicDiagnostics.kernelFamilyGate(context)
                                }
                            },
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Rounded.AutoFixHigh, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Kernel Gate")
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        FilledTonalButton(
                            enabled = !magicToolsBusy,
                            onClick = {
                                runMagicDiagnostic {
                                    MagicDiagnostics.kmiMatrix(context)
                                }
                            },
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Rounded.Memory, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("KMI Matrix")
                        }
                        FilledTonalButton(
                            enabled = !magicToolsBusy,
                            onClick = {
                                runMagicDiagnostic {
                                    MagicDiagnostics.liveSymbolProbe(context)
                                }
                            },
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Rounded.CheckCircle, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Live Symbols")
                        }
                    }
                    FilledTonalButton(
                        enabled = !magicToolsBusy,
                        onClick = {
                            runMagicDiagnostic {
                                MagicDiagnostics.bootEvidenceReport(context)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Rounded.FolderOpen, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Boot Evidence Report")
                    }
                    if (magicToolsBusy) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp))
                            Text(
                                "Reading live device state…",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                    magicToolMessage?.let { message ->
                        Text(
                            message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    magicToolLines.forEach { line ->
                        Text(
                            line,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        item {
            MagicInfoCard(
                title = stringResource(R.string.magic_sources_title),
                lines = buildList {
                    MagicResearchSources.all.forEach { src ->
                        add(src.label)
                        add("  " + src.repository)
                        add("  " + src.coverage)
                        add("  exact: " + src.exactEvidence)
                    }
                },
            )
        }

        item {
            MagicInfoCard(
                title = stringResource(R.string.magic_df_inventory_title),
                lines = buildList {
                    add(
                        context.getString(
                            R.string.magic_df_inventory_summary,
                            inventory.total,
                            inventory.classic.size,
                            inventory.next.size,
                        ),
                    )
                    add("Classic: " + inventory.classic.joinToString { it.removeSuffix("_kernelsu.ko") })
                    add("Next: " + inventory.next.joinToString { it.removeSuffix("_kernelsu.ko") })
                },
            )
        }

        item {
            MagicInfoCard(
                title = stringResource(R.string.magic_vivo_x60_local_title),
                lines = buildList {
                    add(context.getString(R.string.magic_vivo_x60_local_summary))
                    if (vivoProfile != null) {
                        add("Detected: " + vivoProfile.name)
                        add("Aliases: " + vivoProfile.aliases.joinToString())
                        add("Expected kernel family: " + vivoProfile.expectedSeries.joinToString())
                        add("Current kernel: " + snapshot.kernelRelease)
                    } else {
                        add(
                            "Current device: " + snapshot.manufacturer + " " +
                                snapshot.model + " (" + snapshot.device + ")",
                        )
                    }
                },
            )
        }

        item {
            MagicInfoCard(
                title = "Kernel families",
                lines = listOf(
                    "4.x (unstable): " +
                        com.kernelpack.profile.BaselineRegistry.UNSTABLE_4X_SERIES.joinToString(" / "),
                    "4.x exact public evidence: " +
                        com.kernelpack.profile.BaselineRegistry.VERIFIED_UNSTABLE_RELEASES.joinToString(),
                    "5.x Legacy: " +
                        com.kernelpack.profile.BaselineRegistry.TEST_SERIES.joinToString(" / "),
                    "6.x mainline: " +
                        com.kernelpack.profile.BaselineRegistry.MAINLINE_SERIES.joinToString(" / "),
                    "7.x: Magic analysis path; runnable only with exact registered baseline.",
                ),
            )
        }

        item {
            MagicInfoCard(
                title = "Veyra Compatibility Radar",
                lines = buildList {
                    add("Device signature: " + radar.deviceSignature.take(24) + "…")
                    addAll(radar.lines)
                },
            )
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                ),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        FilledTonalButton(
                            onClick = {
                                radar = VeyraCompatibilityRadar.scan(context)
                                radarExportMessage = "Radar refreshed"
                            },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("Refresh radar")
                        }
                        FilledTonalButton(
                            onClick = {
                                scope.launch {
                                    radarExportMessage = withContext(Dispatchers.IO) {
                                        runCatching {
                                            VeyraCompatibilityRadar.export(context)
                                        }.fold(
                                            onSuccess = { "Saved: " + it },
                                            onFailure = {
                                                "Export failed: " +
                                                    (it.message ?: it.javaClass.simpleName)
                                            },
                                        )
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("Export report")
                        }
                    }
                    radarExportMessage?.let { message ->
                        Text(
                            message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MagicSettingSwitchRow(
    title: String,
    summary: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun MagicInfoCard(
    title: String,
    lines: List<String>,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        ),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            lines.forEach { line ->
                Text(
                    line,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
internal fun VeyraMagicBuilderPage(
    padding: PaddingValues,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenLoadingBuilder: () -> Unit,
    onOpenCVeyraRoot: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val vm: PayloadBuilderViewModel = viewModel()
    val buildState by vm.state.collectAsStateWithLifecycle()
    val snapshot = remember { DeviceSnapshot.current() }
    val oemRoute = remember(snapshot) { MagicBuilderController.oemRoute(snapshot) }
    val nothingProfile = remember(snapshot) { NothingMagicProfiles.detect(snapshot) }
    val vivoProfile = remember(snapshot) { VivoLegacyProfiles.detect(snapshot) }

    var captureBusy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var sourceDevice by remember { mutableStateOf<String?>(null) }
    var lastCapture by remember { mutableStateOf<MagicBuilderCapture?>(null) }
    var attemptedSchemes by remember { mutableStateOf<List<PayloadScheme>>(emptyList()) }
    var autoRetryActive by remember { mutableStateOf(false) }
    var supportBundleBusy by remember { mutableStateOf(false) }
    var supportBundlePath by remember { mutableStateOf<String?>(null) }
    var supportBundleMissing by remember { mutableStateOf<List<String>>(emptyList()) }
    var unlockMode by remember { mutableStateOf(false) }
    var unlockCandidate by remember {
        mutableStateOf(MagicUnlockCandidateStore.loadFor(context, snapshot))
    }
    var lastUnlockRecordKey by remember { mutableStateOf("") }

    val backend = CVeyraPreferences.backend(context)
    val proxyReady = backend == CVeyraBackend.WirelessAdb &&
        CVeyraPrivilegedStorageProxy.available(context)
    val ready = CVeyraPreferences.enabled(context) &&
        (backend == CVeyraBackend.Root || proxyReady)
    val busy = captureBusy || buildState.busy || supportBundleBusy

    LaunchedEffect(
        buildState.phase,
        buildState.outputSize,
        buildState.outputSha256,
        buildState.analysisOnly,
        buildState.blockedKind,
        buildState.error,
        supportBundlePath,
        supportBundleMissing,
        autoRetryActive,
        unlockMode,
    ) {
        val buildFinished = buildState.phase == PayloadBuildPhase.Done ||
            buildState.phase == PayloadBuildPhase.Failed
        if (unlockMode && !buildState.busy && buildFinished) {
            val recordKey = listOf(
                buildState.phase.name,
                buildState.outputSha256,
                buildState.outputSize.toString(),
                buildState.analysisOnly.toString(),
                buildState.blockedKind?.name.orEmpty(),
                buildState.error.orEmpty(),
                attemptedSchemes.joinToString(",") { it.name },
                supportBundlePath.orEmpty(),
                supportBundleMissing.joinToString("|"),
            ).joinToString("::")
            if (recordKey != lastUnlockRecordKey) {
                val recorded = withContext(Dispatchers.IO) {
                    runCatching {
                        MagicUnlockCandidateStore.recordBuild(
                            context = context,
                            snapshot = snapshot,
                            route = oemRoute,
                            nothingProfile = nothingProfile,
                            attemptedSchemes = attemptedSchemes,
                            state = buildState,
                            supportBundlePath = supportBundlePath,
                            supportMissing = supportBundleMissing,
                        )
                    }.getOrNull()
                }
                if (recorded != null) unlockCandidate = recorded
                lastUnlockRecordKey = recordKey
            }
        }

        if (!autoRetryActive || buildState.busy) return@LaunchedEffect
        if (buildState.outputSize > 0) {
            autoRetryActive = false
            return@LaunchedEffect
        }

        val finishedWithoutPayload =
            buildState.phase == PayloadBuildPhase.Failed ||
                (buildState.phase == PayloadBuildPhase.Done && buildState.analysisOnly)
        if (!finishedWithoutPayload) return@LaunchedEffect

        val next = MagicBuilderController.candidateSchemes(context, snapshot)
            .firstOrNull { it !in attemptedSchemes }

        if (next != null) {
            attemptedSchemes = attemptedSchemes + next
            val label = if (next == PayloadScheme.VivoVrKo) {
                context.getString(R.string.builder_scheme_vivo)
            } else {
                context.getString(R.string.builder_scheme_universal)
            }
            status = context.getString(R.string.magic_builder_retry_scheme, label)
            vm.clearBlock()
            vm.build(next, magic = true)
        } else {
            autoRetryActive = false
            val capture = lastCapture
            if (capture != null && ready) {
                supportBundleBusy = true
                val bundle = withContext(Dispatchers.IO) {
                    runCatching {
                        MagicBuilderController.captureSupportBundle(context, capture, snapshot)
                    }
                }
                supportBundleBusy = false
                bundle.onSuccess { result ->
                    supportBundlePath = result.savedPath
                    supportBundleMissing = result.missing
                    if (unlockMode) {
                        unlockCandidate = withContext(Dispatchers.IO) {
                            runCatching {
                                MagicUnlockCandidateStore.recordSupportBundle(
                                    context = context,
                                    snapshot = snapshot,
                                    route = oemRoute,
                                    nothingProfile = nothingProfile,
                                    attemptedSchemes = attemptedSchemes,
                                    state = buildState,
                                    bundle = result,
                                )
                            }.getOrNull()
                        } ?: unlockCandidate
                        lastUnlockRecordKey = ""
                    }
                    status = context.getString(
                        R.string.magic_builder_support_bundle_auto,
                        result.savedPath,
                    )
                }.onFailure { error ->
                    status = if (buildState.analysisOnly) {
                        context.getString(R.string.magic_builder_analysis_only_done)
                    } else {
                        context.getString(R.string.magic_builder_all_strategies_failed)
                    } + " " + (error.message ?: "")
                }
            } else {
                status = if (buildState.analysisOnly) {
                    context.getString(R.string.magic_builder_analysis_only_done)
                } else {
                    context.getString(R.string.magic_builder_all_strategies_failed)
                }
            }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 20.dp,
            end = 20.dp,
            top = 20.dp + padding.calculateTopPadding(),
            bottom = 32.dp + padding.calculateBottomPadding(),
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.magic_builder_title),
                        style = MaterialTheme.typography.headlineMedium,
                    )
                    Text(
                        stringResource(R.string.magic_builder_subtitle),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onOpenSettings) {
                    Icon(
                        Icons.Rounded.AutoFixHigh,
                        contentDescription = stringResource(R.string.magic_builder_settings_title),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                ),
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        stringResource(R.string.magic_builder_device),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(snapshot.model, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        snapshot.kernelRelease,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        stringResource(R.string.magic_builder_range),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        stringResource(
                            R.string.magic_builder_backend,
                            when (backend) {
                                CVeyraBackend.Root -> context.getString(R.string.cveyra_backend_root)
                                CVeyraBackend.WirelessAdb -> context.getString(R.string.cveyra_backend_adb)
                                CVeyraBackend.None -> context.getString(R.string.cveyra_backend_none)
                            },
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (ready) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                ),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(Icons.Rounded.Memory, contentDescription = null)
                        Text(
                            stringResource(
                                R.string.magic_builder_oem_title,
                                MagicBuilderController.oemRouteName(oemRoute),
                            ),
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                    Text(
                        stringResource(
                            R.string.magic_builder_oem_body,
                            MagicBuilderController.oemRouteName(oemRoute),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        "${snapshot.model} · ${snapshot.device} · ${snapshot.kernelRelease}",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.78f),
                    )
                }
            }
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Unlock Mode", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Multi-pass evidence mode. It remembers captures and build results, " +
                            "but never bypasses kernel, baseline or symbol gates.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = unlockMode,
                    onCheckedChange = { enabled ->
                        unlockMode = enabled
                        if (enabled) {
                            unlockCandidate = MagicUnlockCandidateStore.loadFor(context, snapshot)
                            lastUnlockRecordKey = ""
                        }
                    },
                )
            }
        }
        if (unlockMode) {
            item {
                val candidate = unlockCandidate
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    ),
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Icon(Icons.Rounded.Memory, contentDescription = null)
                            Text("Puzzle Candidate", style = MaterialTheme.typography.titleMedium)
                        }
                        if (candidate == null) {
                            Text(
                                "No candidate yet. Start Magic Builder once to create the first evidence round.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            Text(
                                "${candidate.status} · evidence ${candidate.evidenceScore}% · " +
                                    "${candidate.rounds.size} rounds",
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (candidate.ready) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                            Text(
                                "${candidate.route} · ${candidate.device} · ${candidate.kernelRelease}",
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                            )
                            if (candidate.inputSha256.isNotBlank()) {
                                Text(
                                    "capture sha256 ${candidate.inputSha256.take(16)}… · " +
                                        "${candidate.inputSize / 1024 / 1024} MiB",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace,
                                )
                            }
                            Text(
                                "confirmed ${candidate.agreements.size} · conflicts ${candidate.conflicts.size}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            candidate.conflicts.take(3).forEach { conflict ->
                                Text(
                                    "• $conflict",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                TextButton(
                                    onClick = {
                                        scope.launch {
                                            val path = withContext(Dispatchers.IO) {
                                                MagicUnlockCandidateStore.export(context, candidate)
                                            }
                                            status = "Unlock candidate exported: $path"
                                        }
                                    },
                                ) {
                                    Text("Export JSON")
                                }
                                TextButton(
                                    onClick = {
                                        MagicUnlockCandidateStore.clear(context)
                                        unlockCandidate = null
                                        lastUnlockRecordKey = ""
                                        status = "Unlock candidate reset"
                                    },
                                ) {
                                    Text("Reset")
                                }
                            }
                        }
                    }
                }
            }
        }
        if (oemRoute == MagicBuilderOemRoute.Nothing) {
            item {
                val currentSeries = snapshot.kernelVersion
                    .split('.')
                    .take(2)
                    .joinToString(".")
                val expected = nothingProfile?.expectedKernelSeries
                    ?.sorted()
                    ?.joinToString("/")
                    .orEmpty()
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    ),
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Icon(Icons.Rounded.AutoFixHigh, contentDescription = null)
                            Text(
                                stringResource(R.string.magic_builder_nothing_title),
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                        if (nothingProfile != null) {
                            Text(
                                stringResource(
                                    R.string.magic_builder_nothing_profile,
                                    nothingProfile.name,
                                    nothingProfile.codename,
                                    nothingProfile.platform,
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Text(
                                if (expected.isNotBlank()) {
                                    stringResource(
                                        R.string.magic_builder_nothing_kernel_known,
                                        currentSeries,
                                        expected,
                                    )
                                } else {
                                    stringResource(
                                        R.string.magic_builder_nothing_kernel_live,
                                        currentSeries,
                                    )
                                },
                                style = MaterialTheme.typography.bodySmall,
                            )
                        } else {
                            Text(
                                stringResource(R.string.magic_builder_nothing_unknown_profile),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        Text(
                            stringResource(
                                R.string.magic_builder_nothing_exact_build,
                                snapshot.buildId,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                        )
                        Text(
                            stringResource(R.string.magic_builder_nothing_ab_policy),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.78f),
                        )
                    }
                }
            }
        }
        if (oemRoute == MagicBuilderOemRoute.VivoIqoo && vivoProfile != null) {
            item {
                val exactSourceMatch = vivoProfile.exactReleasePrefixes.any { prefix ->
                    snapshot.kernelRelease.startsWith(prefix, ignoreCase = true)
                }
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (exactSourceMatch) {
                            MaterialTheme.colorScheme.secondaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHighest
                        },
                    ),
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Icon(Icons.Rounded.Memory, contentDescription = null)
                            Text(
                                "vivo / Kona source intelligence",
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                        Text(
                            vivoProfile.name + " · " + vivoProfile.platform,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            "Live kernel: " + snapshot.kernelRelease,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                        )
                        Text(
                            if (exactSourceMatch) {
                                "Exact 4.19.152-perf source family matched · ARM64/Kona source-assisted fallback armed"
                            } else {
                                "Known X60/Kona family, but this live release is not an exact source match"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (exactSourceMatch) {
                                MaterialTheme.colorScheme.onSecondaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                        Text(
                            "Kernel source: " + vivoProfile.sourceRepository.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                        )
                        vivoProfile.sourceFacts.take(4).forEach { fact ->
                            Text(
                                "• " + fact,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            "Runnable output still requires complete live physical/symbol evidence; Magic Builder will not guess it.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        if (proxyReady) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    ),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(Icons.Rounded.FolderOpen, contentDescription = null)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.magic_builder_proxy_title),
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                stringResource(R.string.magic_builder_proxy_body),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                        }
                    }
                }
            }
        }

        if (!ready) {
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                    ),
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Icon(Icons.Rounded.Warning, contentDescription = null)
                            Text(
                                stringResource(R.string.magic_builder_missing_title),
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                        Text(stringResource(R.string.magic_builder_missing_root))
                        FilledTonalButton(
                            onClick = onOpenCVeyraRoot,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Rounded.Security, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.magic_builder_open_cveyra))
                        }
                        FilledTonalButton(
                            onClick = onOpenLoadingBuilder,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Rounded.FolderOpen, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.magic_builder_add_manually))
                        }
                    }
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                ),
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        stringResource(R.string.magic_builder_what_it_does),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        stringResource(R.string.magic_builder_what_it_does_body),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(
                        enabled = !busy && ready,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            status = null
                            sourceDevice = null
                            captureBusy = true
                            scope.launch {
                                val result = withContext(Dispatchers.IO) {
                                    runCatching { MagicBuilderController.captureLiveBoot(context) }
                                }
                                captureBusy = false
                                result.onSuccess { capture ->
                                    sourceDevice = capture.blockDevice
                                    lastCapture = capture
                                    if (unlockMode) {
                                        unlockCandidate = withContext(Dispatchers.IO) {
                                            runCatching {
                                                MagicUnlockCandidateStore.recordCapture(
                                                    context = context,
                                                    snapshot = snapshot,
                                                    route = oemRoute,
                                                    nothingProfile = nothingProfile,
                                                    capture = capture,
                                                )
                                            }.getOrNull()
                                        } ?: unlockCandidate
                                        lastUnlockRecordKey = ""
                                    }
                                    supportBundlePath = null
                                    supportBundleMissing = emptyList()
                                    vm.rememberBootImageFile(
                                        capture.file,
                                        "live-boot.img",
                                        capture.file.length(),
                                    )
                                    status = context.getString(R.string.magic_builder_capture_ok)
                                    val firstScheme = MagicBuilderController.chooseScheme(context, snapshot)
                                    attemptedSchemes = listOf(firstScheme)
                                    autoRetryActive = true
                                    vm.build(firstScheme, magic = true)
                                }.onFailure { error ->
                                    status = error.message
                                        ?: context.getString(R.string.magic_builder_capture_failed)
                                }
                            }
                        },
                    ) {
                        if (busy) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(10.dp))
                        } else {
                            Icon(Icons.Rounded.AutoFixHigh, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(
                            if (busy) {
                                stringResource(R.string.magic_builder_running)
                            } else {
                                stringResource(R.string.magic_builder_run)
                            },
                        )
                    }
                }
            }
        }

        sourceDevice?.let { source ->
            item {
                Text(
                    stringResource(R.string.magic_builder_source, source),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        status?.let { message ->
            item {
                Text(
                    message,
                    color = if (buildState.error == null) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                )
            }
        }

        if (lastCapture != null && ready) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    ),
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Icon(Icons.Rounded.FolderOpen, contentDescription = null)
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    stringResource(R.string.magic_builder_support_bundle_title),
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                Text(
                                    stringResource(R.string.magic_builder_support_bundle_body),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        FilledTonalButton(
                            enabled = !busy,
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                val capture = lastCapture ?: return@FilledTonalButton
                                supportBundleBusy = true
                                scope.launch {
                                    val result = withContext(Dispatchers.IO) {
                                        runCatching {
                                            MagicBuilderController.captureSupportBundle(
                                                context,
                                                capture,
                                                snapshot,
                                            )
                                        }
                                    }
                                    supportBundleBusy = false
                                    result.onSuccess { bundle ->
                                        supportBundlePath = bundle.savedPath
                                        supportBundleMissing = bundle.missing
                                        if (unlockMode) {
                                            unlockCandidate = withContext(Dispatchers.IO) {
                                                runCatching {
                                                    MagicUnlockCandidateStore.recordSupportBundle(
                                                        context = context,
                                                        snapshot = snapshot,
                                                        route = oemRoute,
                                                        nothingProfile = nothingProfile,
                                                        attemptedSchemes = attemptedSchemes,
                                                        state = buildState,
                                                        bundle = bundle,
                                                    )
                                                }.getOrNull()
                                            } ?: unlockCandidate
                                            lastUnlockRecordKey = ""
                                        }
                                        status = context.getString(
                                            R.string.magic_builder_support_bundle_saved,
                                            bundle.savedPath,
                                        )
                                    }.onFailure { error ->
                                        status = error.message
                                            ?: context.getString(R.string.magic_builder_support_bundle_failed)
                                    }
                                }
                            },
                        ) {
                            if (supportBundleBusy) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                            }
                            Text(
                                if (supportBundleBusy) {
                                    stringResource(R.string.magic_builder_support_bundle_working)
                                } else {
                                    stringResource(R.string.magic_builder_support_bundle_export)
                                },
                            )
                        }
                        supportBundlePath?.let { path ->
                            Text(
                                path,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                            )
                        }
                        if (supportBundleMissing.isNotEmpty()) {
                            Text(
                                stringResource(
                                    R.string.magic_builder_support_bundle_missing,
                                    supportBundleMissing.joinToString(", "),
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }

        if (buildState.log.isNotEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    ),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            stringResource(R.string.builder_log_title),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            buildState.log.takeLast(18).joinToString("\n"),
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        buildState.error?.let { error ->
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                    ),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(Icons.Rounded.Error, contentDescription = null)
                        Text(error, modifier = Modifier.weight(1f))
                    }
                }
            }
        }

        if (buildState.outputSize > 0) {
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                    ),
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Icon(Icons.Rounded.CheckCircle, contentDescription = null)
                            Text(
                                stringResource(R.string.magic_builder_ready),
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                        Text(buildState.outputName, fontFamily = FontFamily.Monospace)
                        if (buildState.savedPath.isNotBlank()) {
                            Text(
                                stringResource(
                                    R.string.magic_builder_exported,
                                    buildState.savedPath,
                                ),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        if (buildState.appliedAsPayload) {
                            Text(
                                stringResource(R.string.magic_builder_activated),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
        }
    }
}
