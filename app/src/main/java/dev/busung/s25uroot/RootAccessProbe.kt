package ctrl.mietze.veyraroot

import android.content.Context

internal enum class RootAccessKind {
    None,
    VeyraTemporary,
    ExternalTemporary,
    KernelSu,
    Magisk,
    Other,
}

internal data class RootAccessSnapshot(
    val kind: RootAccessKind = RootAccessKind.None,
    val providerPackage: String? = null,
    val providerLabel: String? = null,
) {
    val granted: Boolean get() = kind != RootAccessKind.None
    val shouldOpenMigration: Boolean
        get() = kind == RootAccessKind.ExternalTemporary ||
            kind == RootAccessKind.KernelSu ||
            kind == RootAccessKind.Magisk ||
            kind == RootAccessKind.Other

    fun badge(): String = when (kind) {
        RootAccessKind.None -> ""
        RootAccessKind.VeyraTemporary,
        RootAccessKind.ExternalTemporary -> "[Jailbraked]"
        RootAccessKind.KernelSu -> "[Krnl Root]"
        RootAccessKind.Magisk -> "[Boot Root]"
        RootAccessKind.Other -> "[System Root]"
    }

    fun providerName(): String = when (kind) {
        RootAccessKind.None -> ""
        RootAccessKind.VeyraTemporary -> "Veyra"
        RootAccessKind.ExternalTemporary -> providerLabel ?: "External temp provider"
        RootAccessKind.KernelSu -> "KernelSU"
        RootAccessKind.Magisk -> "Magisk"
        RootAccessKind.Other -> providerLabel ?: "Root provider"
    }
}

internal object RootAccessProbe {
    @Volatile
    private var cached: RootAccessSnapshot? = null

    fun cached(): RootAccessSnapshot? = cached

    fun invalidate() {
        cached = null
    }

    fun probe(context: Context): RootAccessSnapshot {
        val app = context.applicationContext
        if (!SuShell.isRoot()) {
            return RootAccessSnapshot().also { cached = it }
        }

        val rememberedProvider = RootMigrationStore.currentProviderPackage(app)
        val veyraOwnsTemporarySession =
            AppPreferences.loadedFlavor(app) != null ||
                rememberedProvider == app.packageName

        if (veyraOwnsTemporarySession) {
            return RootAccessSnapshot(
                kind = RootAccessKind.VeyraTemporary,
                providerPackage = app.packageName,
                providerLabel = "Veyra",
            ).also { cached = it }
        }

        if (magiskOwnsSu()) {
            return RootAccessSnapshot(
                kind = RootAccessKind.Magisk,
                providerPackage = installedMagiskPackage(app),
                providerLabel = "Magisk",
            ).also { cached = it }
        }

        val kernelSuLive = KernelSuRuntime.status(app) != KernelSuStatus.NotLoaded
        if (kernelSuLive) {
            val candidates = runCatching { RmgCandidateScanner.scan(app) }.getOrDefault(emptyList())
            val selected = RootMigrationStore.selectedProviderPackage(app)
            val external = candidates.firstOrNull { candidate ->
                candidate.packageName == rememberedProvider || candidate.packageName == selected
            } ?: candidates.singleOrNull()

            if (external != null) {
                RootMigrationStore.rememberProvider(app, external)
                return RootAccessSnapshot(
                    kind = RootAccessKind.ExternalTemporary,
                    providerPackage = external.packageName,
                    providerLabel = external.label,
                ).also { cached = it }
            }

            return RootAccessSnapshot(
                kind = RootAccessKind.KernelSu,
                providerPackage = installedKernelSuManagerPackage(app),
                providerLabel = "KernelSU",
            ).also { cached = it }
        }

        return RootAccessSnapshot(
            kind = RootAccessKind.Other,
            providerLabel = "Root",
        ).also { cached = it }
    }

    private fun magiskOwnsSu(): Boolean {
        val result = SuShell.run(
            "if command -v magisk >/dev/null 2>&1; then magisk -v 2>/dev/null; " +
                "elif [ -d /data/adb/magisk ]; then echo magisk; fi",
            timeoutSeconds = 5,
        )
        return result != null &&
            result.exitCode == 0 &&
            result.output.contains("magisk", ignoreCase = true)
    }

    private fun installedMagiskPackage(context: Context): String? {
        val known = listOf(
            "com.topjohnwu.magisk",
            "io.github.vvb2060.magisk",
        )
        return known.firstOrNull { packageInstalled(context, it) }
    }

    private fun installedKernelSuManagerPackage(context: Context): String? {
        val known = listOf(
            "me.weishu.kernelsu",
            "com.rifsxd.ksunext",
            "com.resukisu.resukisu",
        )
        return known.firstOrNull { packageInstalled(context, it) }
    }

    private fun packageInstalled(context: Context, packageName: String): Boolean =
        runCatching {
            context.packageManager.getApplicationInfo(packageName, 0)
            true
        }.getOrDefault(false)
}

internal object OverviewStatusCache {
    @Volatile var readiness: Readiness? = null
    @Volatile var rootAccess: RootAccessSnapshot? = null

    fun update(readiness: Readiness, rootAccess: RootAccessSnapshot) {
        this.readiness = readiness
        this.rootAccess = rootAccess
    }

    fun invalidateRoot() {
        rootAccess = null
        RootAccessProbe.invalidate()
    }
}
