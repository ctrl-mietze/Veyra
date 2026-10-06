package ctrl.mietze.veyraroot

/**
 * Whether KernelSU is live on this device right now.
 *
 * Two readings, because either one alone is wrong on this hardware: the native paths are fast and can
 * be denied by policy while root is live, and `su` is authoritative and only answers this app when it
 * is the manager KernelSU knows. A yes from either is a yes; a no from both is a no.
 *
 * **Off the main thread only** - the fallback starts a process and waits on it, and a boot broadcast
 * has a budget this must not spend. [isActiveQuick] is the native reading alone, for the places that
 * are allowed to guess and will be corrected by the gate a moment later.
 *
 * Positive answers are cached for the life of the process and negative ones are not. KernelSU does not
 * unload itself while the kernel is up, so a yes stays true; a no can be the truth about a policy or a
 * race, so it is asked again rather than remembered.
 */
internal object RootStatusProbe {

    @Volatile
    private var lastActive = false

    /**
     * Main-thread-safe hint: only paths the app can read directly.
     *
     * Samsung commonly hides both, so false here means "not visible", not "not loaded". The
     * authoritative probe below adds the isolated KernelSU supercall and su.
     */
    fun isActiveQuick(): Boolean {
        if (lastActive) return true
        val visible = runCatching { NativeProbe.isKernelSuVisibleQuick() }.getOrDefault(false)
        if (visible) lastActive = true
        return visible
    }

    /** The authoritative reading: KernelSU control channel, file visibility, then KernelSU's own su. */
    fun isActive(): Boolean {
        if (lastActive) return true
        val native = runCatching { NativeProbe.isKernelSuActive() }.getOrDefault(false)
        if (native) {
            lastActive = true
            return true
        }
        if (!SuProbe.isActive()) return false
        lastActive = true
        return true
    }
}
