package ctrl.mietze.veyraroot

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Debug
import android.os.Process
import java.io.File
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import java.util.zip.ZipFile
import kotlin.concurrent.thread
import kotlin.system.exitProcess

/**
 * Release-only runtime protection.
 *
 * This protects the distributed APK from casual resign/repack/debug/hook modification. It deliberately
 * does not reject root, KernelSU or Magisk themselves because Veyra is a root application.
 */
internal object ReleaseGuard {
    private const val EXPECTED_PACKAGE = "ctrl.mietze.veyraroot"
    private const val CERT_A = "f1d8f55217d1149f88db9e1642735363"
    private const val CERT_B = "d0198c33c8da8d77547987cedf08c582"
    private val watchdogStarted = AtomicBoolean(false)

    private val apkEntrySha256 = linkedMapOf(
        "assets/local-sources/dfroot/targets-v3.json" to "e0246f4f16195bbd71fa864bd81acb698ce609982e517fb39b10d599a1a08058",
        "assets/local-sources/pixel/targets-v3.json" to "bd5a31a2cd793920e2e8e911c345fed9c63ce5ae691f9141325513f40841257d",
        "assets/local-sources/research-oneplus-p2p3p/source.json" to "68c83bcf53408c09bdc8fbf9d345e5f6cfe517b3327e004546e62e549ffab4a4",
        "assets/local-sources/research-rootmys24/source.json" to "ff133da0ca445ce60e4959553a7898ee6864934dbffe382d2422e30b97a6beb9",
        "assets/local-sources/research-honor80gt/source.json" to "d4708060eec5489e8a84aae4cb6c04ddff296ae426415ffb3abb8f7fdadb1157",
        "assets/local-sources/research-oneplus-joinchang/source.json" to "4eb3d09123b76eb5c60cd106b9f2c900853a8810a225cfd1247a004a8417a961",
        "assets/local-sources/research-ionstack-s22u/source.json" to "21690c435a980f68bfddf1f7ac451f7af6d824b6045ce64002da71eb8ee1112e",
        "assets/local-sources/research-rootmyvivo/source.json" to "00b6e994ae1e2b8853e1432ce6c6786d702ebda9804d20ce5f4a3c67a629c1a4",
        "assets/local-sources/research-shizuku-next/source.json" to "9333a811d7ade53dbd24d573c121df710dabe4fde460771b5ace787af42f47b6",
        "lib/arm64-v8a/libbaseline_6_1.so" to "9fca3b2776fe52beabba0c17e2d060a18eb5f6015ef26624ba06b2562bfdb9f1",
        "lib/arm64-v8a/libbaseline_6_12.so" to "800ae74800b3b694c852863547fc67ed0ada32b6324b59861d54cba5506bb9e5",
        "lib/arm64-v8a/libbs.so" to "8c3410cbc7dce25df3274c0e35d293cb38d7ad2384af7ecc549a3400c50695d9",
        "lib/arm64-v8a/libcve43499root.so" to "ef18aa3842a4ddd677a09882510503de045f1f4366819a73ff34df6f0e258756",
        "lib/arm64-v8a/libdfexp.so" to "22247b4f9f1c9b81d47ebe103d4813f801902f9138e32a667e241d16fc531b7f",
        "lib/arm64-v8a/libdfexpnext.so" to "3d94ed544273e510b2359f1589d82c317d3dd74f2bb944d74fa6f3d60dfbcd0b",
        "lib/arm64-v8a/libionstack.so" to "67bedbd7709d40333392e1943cc9314eadf6322f651aa0c67ad6f95edaa2ae94",
        "lib/arm64-v8a/librmgn04root.so" to "c37cef0dd05b97b4845e1dde3927b15b9615be8fda551c23dc8b2cce0c62fc07",
        "lib/arm64-v8a/libs25u_native.so" to "021c12a08023600cb28277c474730869f511cad53f8215baaeeaee91513fc6e1",
    )

    private val mapMarkers = listOf(
        "frida-agent",
        "libfrida",
        "frida-gadget",
        "gum-js-loop",
        "/frida/",
        "xposedbridge",
        "lsposed",
        "edxp",
        "substrate",
        "sandhook",
        "yahfa",
    )

    private val threadMarkers = listOf(
        "gum-js-loop",
        "gmain",
        "frida",
        "linjector",
    )

    fun install(context: Context) {
        if (!BuildConfig.RELEASE_HARDENED) return
        val app = context.applicationContext
        checkOrTerminate(app)
        if (watchdogStarted.compareAndSet(false, true)) {
            thread(
                start = true,
                isDaemon = true,
                name = "VeyraIntegrity",
            ) {
                while (true) {
                    try {
                        Thread.sleep(17_000L)
                        checkOrTerminate(app)
                    } catch (_: InterruptedException) {
                        return@thread
                    }
                }
            }
        }
    }

    fun checkNow(context: Context) {
        if (!BuildConfig.RELEASE_HARDENED) return
        checkOrTerminate(context.applicationContext)
    }

    private fun checkOrTerminate(context: Context) {
        val valid = runCatching {
            context.packageName == EXPECTED_PACKAGE &&
                BuildConfig.APPLICATION_ID == EXPECTED_PACKAGE &&
                !BuildConfig.DEBUG &&
                notDebuggable(context) &&
                signerMatches(context) &&
                trustedApkLocation(context) &&
                apkStructureMatches(context) &&
                System.getenv("LD_PRELOAD").isNullOrBlank() &&
                !debuggerAttached() &&
                !suspiciousMaps() &&
                !suspiciousFileDescriptors() &&
                !suspiciousThreads() &&
                !injectedHookClass()
        }.getOrDefault(false)

        if (!valid) terminate()
    }

    private fun notDebuggable(context: Context): Boolean =
        context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE == 0

    private fun signerMatches(context: Context): Boolean {
        val pm = context.packageManager
        val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES)
        }
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.signingInfo?.apkContentsSigners.orEmpty()
        } else {
            @Suppress("DEPRECATION")
            info.signatures.orEmpty()
        }
        val expected = hex(CERT_A + CERT_B)
        return signatures.size == 1 && signatures.any { signature ->
            val actual = MessageDigest.getInstance("SHA-256").digest(signature.toByteArray())
            MessageDigest.isEqual(actual, expected)
        }
    }

    private fun apkStructureMatches(context: Context): Boolean {
        ZipFile(context.applicationInfo.sourceDir).use { zip ->
            if (zip.getEntry("classes.dex") == null) return false
            if (zip.getEntry("AndroidManifest.xml") == null) return false
            if (zip.getEntry("resources.arsc") == null) return false
            for ((name, expected) in apkEntrySha256) {
                val entry = zip.getEntry(name) ?: return false
                val digest = MessageDigest.getInstance("SHA-256")
                zip.getInputStream(entry).use { input ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        digest.update(buffer, 0, read)
                    }
                }
                val actual = digest.digest().joinToString("") { "%02x".format(Locale.US, it) }
                if (!constantTimeAscii(actual, expected)) return false
            }
        }
        return true
    }

    private fun trustedApkLocation(context: Context): Boolean {
        val source = File(context.applicationInfo.sourceDir)
        if (source.canWrite()) return false
        val path = source.absolutePath
        return path.startsWith("/data/app/") ||
            path.startsWith("/mnt/expand/") ||
            path.startsWith("/system/") ||
            path.startsWith("/system_ext/") ||
            path.startsWith("/product/") ||
            path.startsWith("/vendor/")
    }

    private fun suspiciousFileDescriptors(): Boolean = runCatching {
        File("/proc/self/fd").listFiles().orEmpty().any { fd ->
            val target = runCatching { fd.canonicalPath.lowercase(Locale.ROOT) }.getOrDefault("")
            mapMarkers.any(target::contains)
        }
    }.getOrDefault(true)

    private fun debuggerAttached(): Boolean =
        Debug.isDebuggerConnected() || Debug.waitingForDebugger() || tracerPid() > 0

    private fun tracerPid(): Int = runCatching {
        File("/proc/self/status").useLines { lines ->
            lines.firstOrNull { it.startsWith("TracerPid:") }
                ?.substringAfter(':')
                ?.trim()
                ?.toIntOrNull()
                ?: 0
        }
    }.getOrDefault(1)

    private fun suspiciousMaps(): Boolean = runCatching {
        File("/proc/self/maps").useLines { lines ->
            lines.any { line ->
                val lower = line.lowercase(Locale.ROOT)
                mapMarkers.any(lower::contains)
            }
        }
    }.getOrDefault(true)

    private fun suspiciousThreads(): Boolean = runCatching {
        val tasks = File("/proc/self/task").listFiles().orEmpty()
        tasks.any { task ->
            val name = runCatching { File(task, "comm").readText().trim().lowercase(Locale.ROOT) }
                .getOrDefault("")
            threadMarkers.any(name::contains)
        }
    }.getOrDefault(true)

    private fun injectedHookClass(): Boolean {
        val names = listOf(
            "de.robv.android.xposed.XposedBridge",
            "org.lsposed.lspd.core.Main",
            "com.saurik.substrate.MS",
        )
        return names.any { name ->
            runCatching {
                Class.forName(name, false, ReleaseGuard::class.java.classLoader)
                true
            }.getOrDefault(false)
        }
    }

    private fun constantTimeAscii(left: String, right: String): Boolean {
        val a = left.toByteArray(Charsets.US_ASCII)
        val b = right.toByteArray(Charsets.US_ASCII)
        return MessageDigest.isEqual(a, b)
    }

    private fun hex(value: String): ByteArray {
        if (value.length % 2 != 0) return ByteArray(0)
        return ByteArray(value.length / 2) { index ->
            value.substring(index * 2, index * 2 + 2).toInt(16).toByte()
        }
    }

    private fun terminate(): Nothing {
        Process.killProcess(Process.myPid())
        exitProcess(91)
    }
}
