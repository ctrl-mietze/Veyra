package ctrl.mietze.veyraroot

enum class RootMethod(val storedValue: String, val label: String) {
    Standard("standard", "Standard"),
    DfCompatible("df-compatible", "DF Compatible"),
    Fast("fast", "Veyra DF+");

    val usesDf: Boolean
        get() = this == DfCompatible || this == Fast

    companion object {
        fun fromStoredValue(value: String?): RootMethod =
            entries.firstOrNull { it.storedValue == value } ?: Standard
    }
}

data class FastRootSupport(
    val available: Boolean,
    val androidMajor: Int,
    val kernelSeries: String,
    val kmi: String?,
    val reason: String,
) {
    companion object {
        private val MATRIX = mapOf(
            12 to setOf("5.10"),
            13 to setOf("5.10", "5.15"),
            14 to setOf("5.15", "6.1"),
            15 to setOf("6.6"),
            16 to setOf("6.12"),
            17 to setOf("6.18"),
        )

        private fun androidMajorFromSdk(sdk: Int): Int = when (sdk) {
            31, 32 -> 12
            33 -> 13
            34 -> 14
            35 -> 15
            36 -> 16
            37 -> 17
            else -> sdk
        }

        fun forSnapshot(snapshot: DeviceSnapshot): FastRootSupport {
            val systemAndroidMajor = snapshot.androidRelease.substringBefore('.').toIntOrNull()
                ?: androidMajorFromSdk(snapshot.sdk)
            val kernelSeries = snapshot.kernelRelease
                .split('.')
                .take(2)
                .joinToString(".")
            // The kernel's own GKI/KMI branch is authoritative when present. Updated phones can run
            // newer Android userspace while retaining an older kernel KMI (for example Android 16
            // userspace on an android15-6.6 kernel).
            val kernelAndroidMajor = Regex("[-_]android(\\d+)(?:[-_]|$)")
                .find(snapshot.kernelRelease)
                ?.groupValues
                ?.getOrNull(1)
                ?.toIntOrNull()
            val androidMajor = kernelAndroidMajor ?: systemAndroidMajor
            val available = kernelSeries in MATRIX[androidMajor].orEmpty()
            val kmi = if (available) "android${androidMajor}-${kernelSeries}" else null
            val reason = if (available) {
                val source = if (kernelAndroidMajor != null) "kernel KMI" else "system fallback"
                "DF-compatible KMI mapping available: $kmi · $source"
            } else {
                "DF is not available for KMI android$androidMajor / kernel $kernelSeries"
            }
            return FastRootSupport(
                available = available,
                androidMajor = androidMajor,
                kernelSeries = kernelSeries,
                kmi = kmi,
                reason = reason,
            )
        }
    }
}

data class RootMethodAvailability(
    val standardAvailable: Boolean,
    val fast: FastRootSupport,
) {
    val fastAvailable: Boolean
        get() = fast.available

    fun effective(preferred: RootMethod): RootMethod = when {
        standardAvailable && fastAvailable -> preferred
        fastAvailable -> RootMethod.Fast
        else -> RootMethod.Standard
    }
}
