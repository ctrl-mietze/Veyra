package ctrl.mietze.veyraroot

enum class RootMethod(val storedValue: String, val label: String) {
    Standard("standard", "Standard"),
    Fast("fast", "New (fast)");

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

        fun forSnapshot(snapshot: DeviceSnapshot): FastRootSupport {
            val androidMajor = snapshot.androidRelease.substringBefore('.').toIntOrNull()
                ?: snapshot.sdk
            val kernelSeries = snapshot.kernelRelease
                .split('.')
                .take(2)
                .joinToString(".")
            val available = kernelSeries in MATRIX[androidMajor].orEmpty()
            val kmi = if (available) "android${androidMajor}-${kernelSeries}" else null
            val reason = if (available) {
                "UniNew/DFRoot KMI available: $kmi"
            } else {
                "New (fast) is not available for Android $androidMajor / kernel $kernelSeries"
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
