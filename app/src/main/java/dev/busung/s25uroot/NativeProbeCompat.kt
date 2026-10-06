package ctrl.mietze.veyraroot

internal object NativeProbe {
    fun run(): String = dev.busung.s25uroot.NativeProbe.run()

    fun isKernelSuVisibleQuick(): Boolean =
        dev.busung.s25uroot.NativeProbe.isKernelSuVisibleQuick()

    fun isKernelSuActive(): Boolean =
        dev.busung.s25uroot.NativeProbe.isKernelSuActive()
}
