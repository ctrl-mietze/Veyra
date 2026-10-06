package dev.busung.s25uroot

object NativeProbe {
    init {
        System.loadLibrary("s25u_native")
    }

    external fun run(): String

    /** Fast sysfs/proc visibility only; safe for main-thread hints, not authoritative on Samsung. */
    external fun isKernelSuVisibleQuick(): Boolean

    /** Full isolated KernelSU control-channel probe with visibility fallback. */
    external fun isKernelSuActive(): Boolean
}
