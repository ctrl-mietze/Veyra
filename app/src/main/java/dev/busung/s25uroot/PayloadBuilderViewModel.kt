package ctrl.mietze.veyraroot

import android.app.Application
import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kernelpack.KernelPack
import com.kernelpack.PackRequest
import com.kernelpack.PackResult
import com.kernelpack.policy.GateDecision
import com.kernelpack.boot.BootImageParser
import com.kernelpack.boot.KernelDecompressor
import com.kernelpack.kallsyms.KallsymsFinder
import com.kernelpack.kallsyms.KallsymsOptions
import com.kernelpack.ota.OtaPayloadExtractor
import com.kernelpack.policy.KernelSchemeSelector
import com.kernelpack.profile.BaselineScheme
import com.kernelpack.profile.BaselineRegistry
import com.kernelpack.profile.BaselineProfiles
import com.kernelpack.vivo.VrKoBypass
import com.kernelpack.vivo.VrKoGateDecision
import com.kernelpack.vivo.VrKoPayloadGate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

/**
 * 构建方案：决定**拿哪一份基础动态库**去打补丁、以及按哪份基线去改写常量。
 *
 * 两份载荷是同一漏洞（CVE-2026-43499）的两个分支，差别在厂商适配：
 * - [Universal]：IonStack 上游的通用分支（Pixel/GKI），不含任何厂商绕过，
 *   靠 `rt_mutex` + `pipe_buffer` 通用链工作 —— 大部分 GKI 6.6 设备可用；
 * - [VivoVrKo]：vivo/iQOO 专用分支（`libbs.so` v1.0.0），额外做 `vr.ko` 反 root 绕过。
 *
 * 两者都靠 kernelpack 把编译期常量改写成用户 boot.img 解析出来的偏移，
 * 所以**换机型不需要重新编译**，只要基线认得出来。
 */
enum class PayloadScheme(
    /** jniLibs 里的文件名。 */
    val library: String,
    /** 产物文件名前缀。 */
    val filePrefix: String,
    /**
     * 这份载荷的**来源版本标签**。
     * 它同时会显示在界面上 —— 用户最关心的一件事就是"我打的到底是哪一份库"，
     * 所以不藏在代码里，直接跟文件名、大小、sha256 一起摊开给人看。
     */
    val versionLabel: String,
    /**
     * 这个方案在 [com.kernelpack.profile.BaselineRegistry] 里对应的方案维度。
     *
     * 为什么要显式映射：闸门要靠它去查「这台机器的四元组有没有对应基线」；
     * 不传的话闸门只能按 UNKNOWN 处理并跳过注册表建议（那就白做了）。
     */
    val baselineScheme: BaselineScheme,
) {
    /** 通用方案：Pixel / GKI 线，不含厂商绕过。 */
    Universal(
        library = "libionstack.so",
        filePrefix = "payload-universal",
        versionLabel = "IonStack · blazer-CP2A.260605.012",
        baselineScheme = BaselineScheme.UNIVERSAL,
    ),
    /** vivo / iQOO 方案：多一条 vr.ko 反 root 绕过。 */
    VivoVrKo(
        library = "libbs.so",
        filePrefix = "payload-vivo",
        // 就是 boxiaolanya2008 仓库 release v1.3.0 里的 preload.so（176544 字节）
        versionLabel = "release v1.3.0",
        baselineScheme = BaselineScheme.VIVO,
    ),
}

/**
 * 构建被**硬拦**的原因类别。
 *
 * 为什么要分类别而不是只给一段文字：P1 定的规矩是「阻断必须是弹窗、不能只是把字标红」，
 * 而不同原因的**补救动作完全不同** ——
 * ```
 *   TEST_KERNEL_DISABLED → 一键去开「5.x 内核支持（beta）」
 *   ABI_CONFLICT         → 去换基线 / boot.img
 *   OTHER                → 看日志
 * ```
 * 只传文字的话，UI 只能把一大段原文塞进弹窗，用户读完还是不知道点哪儿。
 */
enum class BuildBlockKind {
    /** 识别到 5.x 内核，但「5.x 内核支持（beta）」没开。 */
    TEST_KERNEL_DISABLED,

    /** 该 (方案, 内核系列) 组合还没有登记偏移产物（如 6.12 暂无数据）。 */
    BASELINE_NOT_REGISTERED,

    /** 基线 ABI / 强制指定与实测内核冲突。 */
    ABI_CONFLICT,

    /** 认不出内核版本。 */
    UNKNOWN_KERNEL,

    /**
     * 蓝厂方案下，这份载荷**没有** `vr.ko` 反 root 绕过。
     *
     * 这是**唯一**一类"载荷本身不合格"的阻断：别的几类都是"这台机器的数据不够 /
     * 设置没开"，换一份载荷就好的是这一类。所以在 UI 上必须给"换一份"的指引，
     * 而不是像 [BASELINE_NOT_REGISTERED] 那样只能说"知道了"。
     */
    VIVO_VR_KO_MISSING,

    /** 其它（IO、载荷读不到等）。 */
    OTHER,
}

/**
 * 把闸门给的标题归类，供 UI 决定弹哪个阻断框。
 *
 * [为什么是顶层函数而不是 ViewModel 的成员] 它**不依赖任何实例状态** ——
 * 纯粹是"标题 → 类别"的映射。放顶层有两个好处：① 其它包的单测能直接验它
 * （成员函数要求测试同包）；② 顺带说明它没有副作用，不会有人误以为它改了状态。
 *
 * 做法是**看标题里的关键短语**而不是另起一套错误码：闸门的 Blocked 是数据类，
 * 改它的构造会影响既有调用方；而标题本身是给人看的、也稳定。
 * 归类失败一律落 [BuildBlockKind.OTHER] —— 宁可弹一个通用框，
 * 也不要瞎猜类别导致把用户引到错误的设置项上。
 */
internal fun classifyBlock(title: String): BuildBlockKind = when {
        title.contains("5.x") && (title.contains("未开启") || title.contains("支持")) ->
            BuildBlockKind.TEST_KERNEL_DISABLED
        title.contains("ABI") || title.contains("冲突") || title.contains("强制指定") ->
            BuildBlockKind.ABI_CONFLICT
        title.contains("认不出") || title.contains("无法识别") || title.contains("内核版本") ->
            BuildBlockKind.UNKNOWN_KERNEL
        title.contains("vr.ko") || title.contains("vr ko") ->
            BuildBlockKind.VIVO_VR_KO_MISSING
        else -> BuildBlockKind.OTHER
    }

/**
 * Extracts the complete release token from the kernel banner.
 *
 * KallsymsFinder.versionNumber is intentionally only the numeric part (for example 6.6.98).
 * Magic Builder needs the full release (for example
 * 6.6.98-android15-8-...-S938BXXSBCZG3-4k) for exact firmware/kernel matching.
 */
internal fun kernelReleaseFromBanner(
    versionString: String,
    versionNumber: String,
): String {
    val full = Regex("""Linux version\s+([^\s]+)""")
        .find(versionString)
        ?.groupValues
        ?.getOrNull(1)
        ?.trim()
        .orEmpty()
    return full.takeIf { it.startsWith(versionNumber) } ?: versionNumber
}

/** 构建阶段。UI 只需要知道「在忙什么」与「忙完没有」。 */
enum class PayloadBuildPhase {
    Idle,
    Reading,
    Packing,
    Done,
    Failed,
    ;

    val busy: Boolean get() = this == Reading || this == Packing
}

/**
 * 载荷构建的可观察状态。
 *
 * 注意这里**不含**产物的字节：一次构建要用到几十 MB（boot.img + 内核镜像 + 10 万个符号），
 * 产物 .so 也有一百多 KB。把字节放进 StateFlow 会让每次状态刷新都携带它，
 * 也会让快照系统白白比较一遍大数组。字节留在 ViewModel 的私有字段里，
 * 通过 [PayloadBuilderViewModel.outputBytes] / [PayloadBuilderViewModel.saveAsPayload] 取。
 */
data class PayloadBuildState(
    val phase: PayloadBuildPhase = PayloadBuildPhase.Idle,
    /** 本次（或上次）构建用的方案。 */
    val scheme: PayloadScheme? = null,
    /** 已选 boot.img 的显示名与大小。 */
    val sourceName: String = "",
    val sourceSize: Long = 0,
    /** 构建过程的日志（最新的在最后）。 */
    val log: List<String> = emptyList(),
    /** 被硬拦时的原因类别；非 null 时 UI 应弹阻断框（而不是只标红）。 */
    val blockedKind: BuildBlockKind? = null,
    /** 结果摘要（多行）。 */
    val summary: String = "",
    /** 结果里的「提示」行（单独拿出来上色）。 */
    val notices: List<String> = emptyList(),
    /** Parsed full release from the currently selected boot image. */
    val detectedKernelRelease: String = "",
    /**
     * Persistent route intelligence for an exact boot image from the running device.
     * Kept separate from the log so a blocked Standard build can still show usable alternatives.
     */
    val strategyReport: BuilderStrategyReport? = null,
    /** 产物 .so 的名字 / 大小 / sha256。 */
    val outputName: String = "",
    val outputSize: Long = 0,
    val outputSha256: String = "",
    /** target.h 的导出名。 */
    val headerName: String = "",
    /** 本次用的基础库（文件名 / 大小 / sha256）—— 用来让用户核对"打的是哪一份库"。 */
    val baseLibraryName: String = "",
    val baseLibrarySize: Long = 0,
    val baseLibrarySha256: String = "",
    /** 产物落到系统下载目录后的可读路径（拿不到就为空）。 */
    val savedPath: String = "",
    /** 是否已经写入「自定义载荷」（构建成功时是自动写入的）。 */
    val appliedAsPayload: Boolean = false,
    val analysisOnly: Boolean = false,
    val error: String? = null,
) {
    /** 正在构建：期间禁止重复触发、禁止改输入。 */
    val busy: Boolean get() = phase.busy
}

/**
 * 「解析完整包链接」的可观察状态。
 *
 * 与 [PayloadBuildState] 分开：这两件事**可以同时发生**（构建在跑的时候
 * 用户完全可以再去解一个链接），共用一个状态机会互相踩。
 */
data class OtaLinkState(
    val running: Boolean = false,
    /** 用户填的链接；对话框重开时回填。 */
    val url: String = "",
    /** 解析过程的日志（最新的在最后）。 */
    val log: List<String> = emptyList(),
    /** 最近一次失败的说明。 */
    val error: String? = null,
    /** 最近一次成功解出的镜像：显示名与大小。 */
    val resultName: String = "",
    val resultSize: Long = 0,
)

/**
 * 「载荷构建」页面的逻辑：**boot.img → 内核偏移 → 打补丁的动态库**。
 *
 * 真正的算法全部来自 `com.kernelpack`（纯 Kotlin：不依赖 Android、不开线程、
 * 不碰文件系统）—— 本类只负责三件事：读文件、切线程、把内存管好。
 *
 * ### 为什么要专门管内存
 * 一次构建的峰值大致是：boot.img 本体（几十 MB）+ 解压后的内核镜像（几十 MB）
 * + 10 万个符号对象（连同按名索引 ≈ 30 MB）。设备普通堆上限 256 MB
 * （`dalvik.vm.heapgrowthlimit`；manifest 里已开 largeHeap 提到 512 MB），
 * 所以 [build] 结束后会**立刻丢掉**所有大对象，只留下摘要字符串与产物字节。
 */
class PayloadBuilderViewModel(application: Application) : AndroidViewModel(application) {

    private val mutableState = MutableStateFlow(PayloadBuildState())
    val state: StateFlow<PayloadBuildState> = mutableState.asStateFlow()

    /** 产物字节（构建成功后才非 null）。 */
    private var outputLibrary: ByteArray? = null
    private var outputHeader: String = ""
    private var outputOffsetsJson: String = ""

    /** 记住用户选的 boot.img，配置变化后不必重选。 */
    private var bootUri: Uri? = null

    /**
     * 输入也可以是一个**本地文件**而不是 `content://` URI ——
     * 「解析完整包链接」解出来的镜像就在应用私有目录里，没有对应的 content URI。
     *
     * 两者**互斥**：谁最后被设置，谁就是本次输入。留着一个指向旧输入的字段
     * 迟早会出现"界面上写着 A、实际构建用的是 B"。
     */
    private var bootFile: File? = null

    private val mutableOtaState = MutableStateFlow(OtaLinkState())
    val otaState: StateFlow<OtaLinkState> = mutableOtaState.asStateFlow()
    private var otaJob: Job? = null

    fun bootImageName(): String = mutableState.value.sourceName

    fun rememberBootImage(uri: Uri, name: String, size: Long) {
        bootUri = uri
        bootFile = null
        mutableState.value = mutableState.value.copy(
            sourceName = name,
            sourceSize = size,
            error = null,
        )
    }

    /** 记住一个**本地文件**作为输入（「解析完整包链接」的产物走这条路）。 */
    fun rememberBootImageFile(file: File, name: String, size: Long) {
        bootFile = file
        bootUri = null
        mutableState.value = mutableState.value.copy(
            sourceName = name,
            sourceSize = size,
            error = null,
        )
    }

    // ────────────────────────── 解析完整包链接 ──────────────────────────

    /** 用户改了链接输入框 / 关掉错误提示时调用。 */
    fun clearOtaError() {
        if (mutableOtaState.value.error != null) {
            mutableOtaState.value = mutableOtaState.value.copy(error = null)
        }
    }

    fun cancelOtaParse() {
        otaJob?.cancel()
        otaJob = null
        mutableOtaState.value = mutableOtaState.value.copy(running = false)
    }

    /**
     * 解析一个**完整包链接**，把里面的 boot 镜像解出来并设为构建输入。
     *
     * 只接受 `http` / `https`。整个过程靠 HTTP `Range` 请求**只取需要的那几块**
     * —— 完整包 4–8 GiB，全下下来在手机上是不现实的。
     *
     * 成功之后**不在这里预校验**镜像是否可用：那是 [build] 的职责，
     * 它已经有完整的格式识别与内核版本判定。在这里再判一次要么是重复劳动，
     * 要么是拿半个数组做判断 —— 后者会给出**错的**结论。
     */
    fun parseOtaLink(rawUrl: String) {
        val app = getApplication<Application>()
        val url = rawUrl.trim()
        if (otaJob?.isActive == true) return
        if (!url.startsWith("http://", true) && !url.startsWith("https://", true)) {
            mutableOtaState.value = OtaLinkState(
                url = url, error = app.getString(R.string.builder_ota_bad_url),
            )
            return
        }
        otaJob = viewModelScope.launch {
            mutableOtaState.value = OtaLinkState(running = true, url = url)
            val lines = ArrayList<String>(64)
            var pending = 0
            fun publish(line: String) {
                lines.add(line)
                pending++
                if (pending >= 3) {
                    pending = 0
                    mutableOtaState.value =
                        mutableOtaState.value.copy(log = lines.takeLast(MAX_LOG_LINES))
                }
            }
            try {
                val dir = otaWorkDir(app)
                withContext(Dispatchers.IO) { purgeStaleOtaFiles(dir, bootFile) }
                publish(app.getString(R.string.builder_ota_started))
                val result = OtaPayloadExtractor.extractPartitions(url, dir) { raw ->
                    BuilderUserText.ota(app, raw)?.let(::publish)
                }
                val file = result.bootFile
                val size = file.length()
                rememberBootImageFile(file, file.name, size)
                mutableOtaState.value = OtaLinkState(
                    url = url,
                    log = lines.takeLast(MAX_LOG_LINES),
                    resultName = file.name,
                    resultSize = size,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                mutableOtaState.value = mutableOtaState.value.copy(
                    running = false,
                    log = lines.takeLast(MAX_LOG_LINES),
                    error = BuilderUserText.otaError(app, e),
                )
            }
        }
    }

    private fun otaWorkDir(app: Application): File = File(app.cacheDir, OTA_CACHE_DIR)

    /**
     * 清掉上一次解析留下的镜像。
     *
     * boot 镜像动辄 96 MiB，留在 `cacheDir` 里会一直堆到系统来清 ——
     * 而系统清理是不打招呼的，用户下次点构建只会看到"文件不见了"。
     * 所以这里**主动**只留一份，并且跳过当前正在用的那一份。
     */
    private fun purgeStaleOtaFiles(dir: File, keep: File?) {
        val files = dir.listFiles() ?: return
        for (f in files) {
            if (!f.name.startsWith(OTA_FILE_PREFIX)) continue
            if (keep != null && f.absolutePath == keep.absolutePath) continue
            f.delete()
        }
    }

    fun outputBytes(): ByteArray? = outputLibrary

    fun outputHeaderText(): String = outputHeader

    fun outputOffsetsText(): String = outputOffsetsJson

    private fun exportMagicAnalysisArtifacts(
        app: Application,
        snapshot: DeviceSnapshot,
        header: String,
        offsets: String,
    ): String {
        val stem = (snapshot.device + "-" + snapshot.kernelRelease)
            .replace(Regex("[^A-Za-z0-9._+-]"), "_")
            .take(96)
        val headerPath = DownloadStore.save(
            app,
            "Veyra-Magic-$stem-target.generated.h",
            header.toByteArray(),
        )
        val offsetsPath = DownloadStore.save(
            app,
            "Veyra-Magic-$stem-offsets.json",
            offsets.toByteArray(),
        )
        return "$headerPath | $offsetsPath"
    }

    /**
     * 跑一次完整构建。整个过程都在后台线程：
     * 读文件走 IO，解析与打补丁走 Default（纯 CPU，几十秒量级）。
     */
    fun build(scheme: PayloadScheme, magic: Boolean = false) {
        val uri = bootUri
        val file = bootFile
        val app = getApplication<Application>()
        if (uri == null && file == null) {
            mutableState.value = mutableState.value.copy(
                phase = PayloadBuildPhase.Failed,
                error = app.getString(R.string.builder_no_input),
            )
            return
        }
        if (magic && !MagicBuilderController.allowsScheme(app, DeviceSnapshot.current(), scheme)) {
            mutableState.value = mutableState.value.copy(
                phase = PayloadBuildPhase.Failed,
                error = app.getString(R.string.magic_builder_oem_scheme_blocked),
            )
            return
        }
        if (mutableState.value.phase.busy) return

        mutableState.value = PayloadBuildState(
            phase = PayloadBuildPhase.Reading,
            sourceName = mutableState.value.sourceName,
            sourceSize = mutableState.value.sourceSize,
        )
        outputLibrary = null
        outputHeader = ""
        outputOffsetsJson = ""

        viewModelScope.launch {
            val lines = ArrayList<String>(256)
            var pending = 0
            // 日志回调是在解析线程里同步调用的，逐行刷新会让 UI 每秒重组上百次；
            // 攒够几行再推一次，观感上仍然是「实时滚动」。
            fun publish(line: String) {
                lines.add(line)
                pending++
                if (pending >= 6) {
                    pending = 0
                    mutableState.value = mutableState.value.copy(log = lines.takeLast(MAX_LOG_LINES))
                }
            }

            try {
                publish(app.getString(R.string.builder_phase_reading, mutableState.value.sourceName))
                val bootBytes = withContext(Dispatchers.IO) {
                    when {
                        // 「解析完整包链接」的产物在应用私有目录里，直接读文件。
                        // 不走 contentResolver：`file://` 在部分 ROM 上会被直接拒掉，
                        // 而那个失败信息对用户毫无意义。
                        file != null -> {
                            if (!file.isFile) error(app.getString(R.string.builder_input_gone))
                            file.readBytes()
                        }

                        uri != null -> app.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                            ?: error(app.getString(R.string.builder_read_failed))

                        else -> error(app.getString(R.string.builder_no_input))
                    }
                }
                // ── 构建前：先检查内核版本，再决定用哪套方案 ──────────────
                // 这一步刻意放在**打补丁之前**：认不出内核版本、或者 5.x 支持没开时，
                // 应该**零成本**就停下，而不是等把 boot.img 解完、补丁算完才说不行。
                // （KernelPack 内部还有一道同源硬闸兜底 —— 宁可拦两次，也不能有入口绕过。）
                val kernelRelease = withContext(Dispatchers.Default) {
                    val parsed = BootImageParser.parse(bootBytes, KernelDecompressor.default)
                    parsed.diagnosis?.let { diagnosis ->
                        AppLog.warn(AppLogTags.BUILDER, diagnosis)
                        publish(app.getString(R.string.builder_log_parse_diagnostic))
                    }
                    val arch = KallsymsFinder.guessArchitectureFromImage(parsed.image)
                    publish(
                        "[Image] container=" + parsed.info.container +
                            " arm64Header=" + parsed.info.arm64Image +
                            " decompressed=" + parsed.info.decompressed +
                            " arch=" + (arch?.display ?: "unresolved"),
                    )
                    val version = KallsymsFinder.linuxVersionFromImage(parsed.image)
                        ?: error("Kernel release banner could not be resolved from the parsed Image.")
                    kernelReleaseFromBanner(version.first, version.second)
                }
                publish(app.getString(R.string.builder_detected_kernel, kernelRelease))
                mutableState.value = mutableState.value.copy(
                    detectedKernelRelease = kernelRelease,
                )
                val liveTargetSnapshot = DeviceSnapshot.current()
                val exactLiveTarget = liveTargetSnapshot.kernelRelease.isNotBlank() &&
                    kernelRelease.equals(liveTargetSnapshot.kernelRelease, ignoreCase = true)
                if (exactLiveTarget) {
                    val routeSnapshot = liveTargetSnapshot.copy(kernelRelease = kernelRelease)
                    val strategyReport = BuilderStrategyPlanner.evaluate(app, routeSnapshot)
                    mutableState.value = mutableState.value.copy(
                        strategyReport = strategyReport,
                    )
                    strategyReport.recommended?.let { recommended ->
                        publish(
                            "[Strategy] recommended: ${recommended.label} · " +
                                recommended.state.label,
                        )
                    }
                    strategyReport.candidates
                        .sortedBy { it.priority }
                        .take(5)
                        .forEach { candidate ->
                            publish(
                                "[Strategy] ${candidate.label}: " +
                                    "${candidate.state.label} · ${candidate.reason}",
                            )
                        }

                    val dfPlan = DfPlusPlanner.plan(
                        app,
                        routeSnapshot,
                        AppPreferences.kernelsuFlavor(app),
                    )
                    if (dfPlan.available) {
                        publish("[DF+] alternate exact route available: ${dfPlan.routeLabel}")
                        dfPlan.notes.take(3).forEach { note -> publish("[DF+] $note") }
                    } else if (magic) {
                        publish(
                            "[DF+] no exact route for this image: " +
                                dfPlan.blockers.take(3).joinToString("; "),
                        )
                    }
                } else if (!magic) {
                    publish("[DF+] alternate route not evaluated: boot image is not the running device's exact kernel")
                }
                if (magic) {
                    val snapshot = DeviceSnapshot.current()
                    val route = MagicBuilderController.oemRoute(snapshot)
                    publish(
                        app.getString(
                            R.string.magic_builder_oem_log,
                            MagicBuilderController.oemRouteName(route),
                        ),
                    )
                    MagicBuilderController.oemPreflight(snapshot)
                        .forEach { publish("[OEM] $it") }
                    if (route == MagicBuilderOemRoute.Nothing) {
                        MagicBuilderController.nothingPreflight(app, snapshot)
                            .forEach { publish("[Nothing] $it") }
                    }
                    if (route == MagicBuilderOemRoute.Huawei || route == MagicBuilderOemRoute.Honor) {
                        publish("[Huawei Lab] isolated OEM route active")
                        publish("[Huawei Lab] capture policy remains boot -> ramdisk")
                        publish("[Huawei Lab] exact kernel, baseline and symbol gates remain mandatory")
                    }
                    if (route == MagicBuilderOemRoute.VivoIqoo) {
                        publish(app.getString(R.string.magic_builder_vivo_log))
                    }
                }
                val liveKernel = DeviceSnapshot.current().kernelRelease
                val strictKernel = magic || AppPreferences.magicBuilderStrictKernel(app)
                if (strictKernel && liveKernel.isNotBlank() &&
                    !kernelRelease.equals(liveKernel, ignoreCase = true)
                ) {
                    val message = app.getString(
                        R.string.builder_exact_kernel_mismatch,
                        liveKernel,
                        kernelRelease,
                    )
                    AppLog.warn(AppLogTags.BUILDER, message)
                    publish("[X] $message")
                    mutableState.value = mutableState.value.copy(
                        phase = PayloadBuildPhase.Failed,
                        error = message,
                        log = lines.takeLast(MAX_LOG_LINES),
                        blockedKind = BuildBlockKind.ABI_CONFLICT,
                    )
                    return@launch
                }
                val buildSeriesOverride = if (magic) {
                    com.kernelpack.policy.SeriesOverride.AUTO
                } else {
                    AppPreferences.kernelSeriesOverride(app)
                }
                val baseAllowMismatch =
                    if (magic) false else AppPreferences.allowAbiMismatch(app)

                val magicSeries = com.kernelpack.policy.BuildGate.seriesOf(kernelRelease)
                val magicMajor = magicSeries?.substringBefore('.')?.toIntOrNull()
                val unstableFourX = magicMajor == 4
                val buildAllowTestKernel =
                    if (magic) true else AppPreferences.allowTestKernel(app)
                val buildAllowUnstable4x = if (magic) {
                    AppPreferences.magicBuilderAllowUnstable4x(app)
                } else {
                    AppPreferences.builderAllowUnstable4x(app)
                }
                val nearestFamilyEnabled = if (magic) {
                    AppPreferences.magicBuilderNearestFamily(app)
                } else {
                    AppPreferences.builderNearestFamily(app)
                }
                val decision = if (
                    magic &&
                    magicSeries != null &&
                    magicMajor != null &&
                    magicMajor in 4..7 &&
                    (magicMajor != 4 || buildAllowUnstable4x)
                ) {
                    KernelSchemeSelector.Decision.Selected(
                        series = magicSeries,
                        major = magicMajor,
                        useTestScheme = magicMajor != 6,
                        notes = listOf(
                            "[Magic Builder] 4.x-7.x scan path: exact baselines are preferred; " +
                                "unsupported families continue as analysis-only instead of stopping immediately.",
                        ),
                        release = kernelRelease,
                    )
                } else {
                    KernelSchemeSelector.select(
                        kernelRelease = kernelRelease,
                        allowTestKernel = buildAllowTestKernel,
                        allowUnstable4x = buildAllowUnstable4x,
                        // Magic Builder has its own conservative auto policy; normal Loading Builder
                        // continues to honour the person's Builder Settings.
                        override = buildSeriesOverride,
                    )
                }
                if (decision is KernelSchemeSelector.Decision.Blocked) {
                    val kind = classifyBlock(decision.title)
                    AppLog.warn(
                        AppLogTags.BUILDER,
                        listOf(decision.title, *decision.detail.toTypedArray(), decision.remedy)
                            .joinToString(" | "),
                    )
                    val message = BuilderUserText.blockMessage(app, kind)
                    publish("[X] $message")
                    mutableState.value = mutableState.value.copy(
                        phase = PayloadBuildPhase.Failed,
                        error = message,
                        log = lines.takeLast(MAX_LOG_LINES),
                        blockedKind = kind,
                    )
                    return@launch
                }
                val selected = decision as KernelSchemeSelector.Decision.Selected
                publish("[*] " + BuilderUserText.schemeSelected(app, selected))
                if (selected.useTestScheme) {
                    publish(app.getString(R.string.builder_log_test_kernel_beta))
                }

                // ── 按 (方案, 内核系列) 路由到具体载荷档位 ──
                // 这是"两个方案 × 两个主线系列"的唯一路由点。取不到就是**真的没有**
                // 该组合的偏移产物 —— 必须在这里停下并说清楚，绝不能回退到别的系列。
                // 三级路由：**先按完整内核串**，再小版本，最后退回大系列。
                // 不传 release 的话，按小版本登记的上游 50 档永远选不中。
                var nearestEntry: com.kernelpack.profile.BaselineEntry? = null
                var profileId = BaselineRegistry.profileIdFor(
                    scheme.baselineScheme,
                    selected.series,
                    selected.release,
                )
                if (profileId == null && nearestFamilyEnabled && selected.major in 4..6) {
                    nearestEntry = BaselineRegistry.nearestBuildableEntry(
                        scheme = scheme.baselineScheme,
                        targetSeries = selected.series,
                    )
                    profileId = nearestEntry?.profile?.id
                    if (nearestEntry != null) {
                        publish("[!!!] UNVERIFIED NEAREST-FAMILY OVERRIDE ACTIVE")
                        publish(
                            "[!!!] target=${selected.series} -> baseline=${nearestEntry.kernelSeries} " +
                                "(${nearestEntry.device}/${nearestEntry.firmware})",
                        )
                        publish(
                            "[!!!] Different rt_mutex_waiter/task_struct ABI layouts can cause " +
                                "kernel panic, memory corruption or an immediate RAM/kernel crash.",
                        )
                    }
                }
                val effectiveAllowMismatch = baseAllowMismatch || nearestEntry != null
                val extendedMajorAllowed = magic || unstableFourX
                if (profileId == null) {
                    publish(app.getString(R.string.builder_log_no_runnable_baseline))
                    publish(app.getString(R.string.builder_log_analysis_only_safe))
                    val analysis = withContext(Dispatchers.Default) {
                        KernelPack.pack(
                            PackRequest(
                                bootImage = bootBytes,
                                baseLibrary = null,
                                seriesOverride = buildSeriesOverride,
                                allowAbiMismatch = effectiveAllowMismatch,
                                allowTestKernel = buildAllowTestKernel,
                                allowExtendedKernelMajor = extendedMajorAllowed,
                                scheme = scheme.baselineScheme,
                                log = { raw ->
                                    BuilderUserText.technical(app, raw)?.let(::publish)
                                },
                            ),
                        )
                    }
                    outputLibrary = null
                    outputHeader = analysis.targetHeader
                    outputOffsetsJson = analysis.offsetsJson
                    val liveSnapshot = DeviceSnapshot.current()
                    val sourceComparison = if (
                        magic && VivoKonaSourceAssist.supports(liveSnapshot)
                    ) {
                        VivoKonaSourceAssist.compare(analysis.profile)
                    } else {
                        null
                    }
                    sourceComparison?.let { check ->
                        publish(
                            "[Magic] k419 reference: " +
                                "${check.agreements.size} agree, " +
                                "${check.conflicts.size} conflict, " +
                                "${check.unresolved.size} unresolved",
                        )
                        check.conflicts.take(5).forEach { conflict ->
                            publish("[Magic] source conflict: $conflict")
                        }
                    }
                    val summary = buildString {
                        append(buildSummary(app, analysis))
                        sourceComparison?.let { check ->
                            appendLine()
                            appendLine()
                            appendLine("K419 public-source cross-check")
                            appendLine(
                                "${check.agreements.size} agreements · " +
                                    "${check.conflicts.size} conflicts · " +
                                    "${check.unresolved.size} unresolved",
                            )
                            if (check.conflicts.isNotEmpty()) {
                                appendLine("Conflicts:")
                                check.conflicts.take(8).forEach { appendLine("• $it") }
                            }
                        }
                    }
                    val analysisNotices = BuilderUserText.notices(app, analysis).toMutableList().apply {
                        sourceComparison?.let { check ->
                            add(
                                if (check.clean) {
                                    "K419 source cross-check found no conflicting resolved reference values."
                                } else {
                                    "K419 source cross-check found ${check.conflicts.size} conflict(s); runnable output remains blocked."
                                },
                            )
                        }
                    }
                    val analysisSaved = if (
                        magic && AppPreferences.magicBuilderAutoExport(app)
                    ) {
                        withContext(Dispatchers.IO) {
                            runCatching {
                                exportMagicAnalysisArtifacts(
                                    app,
                                    liveSnapshot,
                                    analysis.targetHeader,
                                    analysis.offsetsJson,
                                )
                            }.getOrElse { exportError ->
                                AppLog.warn(
                                    AppLogTags.BUILDER,
                                    "Magic analysis auto-export failed: " +
                                        (exportError.message ?: exportError.javaClass.simpleName),
                                )
                                ""
                            }
                        }.also { paths ->
                            if (paths.isNotBlank()) publish("[Magic] analysis artifacts saved: $paths")
                        }
                    } else {
                        ""
                    }
                    mutableState.value = mutableState.value.copy(
                        phase = PayloadBuildPhase.Done,
                        scheme = scheme,
                        log = lines.takeLast(MAX_LOG_LINES),
                        summary = summary,
                        notices = analysisNotices,
                        headerName = "target.generated.h",
                        savedPath = analysisSaved,
                        analysisOnly = true,
                        error = null,
                        blockedKind = null,
                    )
                    return@launch
                }

                val baseLibrary = withContext(Dispatchers.IO) {
                    readBaseLibrary(app, scheme, nearestEntry?.kernelSeries ?: selected.release)
                }
                publish(
                    app.getString(
                        R.string.builder_loaded,
                        bootBytes.size / 1024,
                        baseLibrary.size / 1024,
                    ),
                )
                val baseSha = withContext(Dispatchers.Default) { sha256Hex(baseLibrary) }
                mutableState.value = mutableState.value.copy(
                    scheme = scheme,
                    baseLibraryName = lastBaseLibraryName ?: scheme.library,
                    baseLibrarySize = baseLibrary.size.toLong(),
                    baseLibrarySha256 = baseSha,
                )
                // ── 蓝厂方案专属闸门：这份载荷到底带没带 vr.ko 抹标记 ──
                //
                // 为什么必须在**构建阶段**拦，而不是装上去再说：抹标记是往内核内存写，
                // 只有载荷 C 侧那套 pipe_phys_write_data / pipe_write64 原语做得到，
                // Kotlin 侧没有这个原语（见 com.kernelpack.vivo.VrKoBypass 的类注释）。
                // 所以"这份载荷带没带绕过"是**装之前唯一查得了的事** —— 不查，
                // 在带 vr.ko 的机器上就会得到"提权成功、子进程随即被 sys_exit 探针杀掉"。
                //
                // 通用方案**不触发**这条检查：VrKoPayloadGate 在 scheme != VIVO 时
                // 直接短路返回 NotApplicable，连 ELF 都不解析。
                val vrDecision = withContext(Dispatchers.Default) {
                    VrKoPayloadGate.decide(
                        scheme = scheme.baselineScheme,
                        libraryName = lastBaseLibraryName ?: scheme.library,
                        bytes = baseLibrary,
                    )
                }
                if (vrDecision is VrKoGateDecision.Blocked) {
                    AppLog.warn(
                        AppLogTags.BUILDER,
                        listOf(vrDecision.title, *vrDecision.detail.toTypedArray(), vrDecision.remedy)
                            .joinToString(" | "),
                    )
                    val message = BuilderUserText.blockMessage(
                        app,
                        BuildBlockKind.VIVO_VR_KO_MISSING,
                    )
                    publish("[X] $message")
                    mutableState.value = mutableState.value.copy(
                        phase = PayloadBuildPhase.Failed,
                        error = message,
                        log = lines.takeLast(MAX_LOG_LINES),
                        blockedKind = BuildBlockKind.VIVO_VR_KO_MISSING,
                    )
                    return@launch
                }
                if (vrDecision is VrKoGateDecision.Pass) {
                    publish(app.getString(R.string.builder_vr_ko_ok))
                }

                // ── 提示（**不是**闸门）：这台机器像是蓝厂的，却选了通用方案 ──
                //
                // 方向与上面的闸门相反：这里只有**正面证据**才算数。
                // 读不到 /proc/modules 不能当成"有 vr.ko" —— SELinux 下非蓝厂机器
                // 一样读不到，那样每台机器都会看到这条提示，提示就成了噪音。
                val vrHintIdentity = withContext(Dispatchers.Default) {
                    listOf(Build.MANUFACTURER, Build.BRAND, Build.MODEL, Build.DEVICE, Build.PRODUCT)
                        .joinToString(" ")
                        .lowercase()
                }
                val modulesText = withContext(Dispatchers.IO) {
                    runCatching { File("/proc/modules").readText() }.getOrNull()
                }
                if (VrKoBypass.shouldSuggestVivoScheme(modulesText, vrHintIdentity, scheme.baselineScheme)) {
                    publish(app.getString(R.string.builder_vr_ko_hint))
                }

                mutableState.value = mutableState.value.copy(phase = PayloadBuildPhase.Packing)

                // 峰值内存就出现在下面这一行：解析 + 打补丁都在里面完成。
                val result = withContext(Dispatchers.Default) {
                    KernelPack.pack(
                        PackRequest(
                            bootImage = bootBytes,
                            baseLibrary = baseLibrary,
                            // 显式指定基线。
                            //
                            // [2026-09-25 修] 原来查的是 [BaselineProfiles.byId] ——
                            // 那个表**只有 2 份手写档**（PD2520 / IONSTACK_P10，都是 6.6）。
                            // 上游那 50 档的 profile 是在 [BaselineRegistry] 里**就地构造**的，
                            // 没登记进去，于是 byId 查不到 → 上层兜底回落到 PD2520（6.6）
                            // → 拿 6.6 的 ABI 去对 6.1 的 boot.img → **误报"基线 ABI 冲突"**，
                            // 用户明明有 6.1 基线却被告知不匹配。
                            //
                            // [BaselineRegistry.byId] 搜的是 allEntries（手写 + 上游 + 蓝厂派生），
                            // 与三级路由用的是同一张表 —— 路由指向哪一档，这里就取到哪一档。
                            baseline = BaselineRegistry.byId(profileId)?.profile,
                            // 设置页的"强制指定内核系列"与"忽略冲突"：默认 AUTO + 不放行，
                            // 也就是**默认按实测走、冲突即拒绝**。
                            seriesOverride = buildSeriesOverride,
                            allowAbiMismatch = effectiveAllowMismatch,
                            // Magic Builder decides this automatically; Loading Builder uses the setting.
                            allowTestKernel = buildAllowTestKernel,
                            allowExtendedKernelMajor = extendedMajorAllowed,
                            // 方案维度也要传：闸门靠它查四元组注册表
                            scheme = scheme.baselineScheme,
                            log = { raw ->
                                BuilderUserText.technical(app, raw)?.let(::publish)
                            },
                        ),
                    )
                }

                val packed = result.packedLibrary
                val summary = buildSummary(app, result)
                val notices = BuilderUserText.notices(app, result)
                val header = result.targetHeader
                val offsetsJson = result.offsetsJson
                result.patchReport?.let { report ->
                    publish(
                        app.getString(
                            R.string.builder_patch_done,
                            report.outcomes.count { it.changed },
                            report.outcomes.sumOf { it.sitesPatched + it.dataLiteralsPatched },
                        ),
                    )
                }

                if (packed == null) {
                    outputHeader = header
                    outputOffsetsJson = offsetsJson
                    mutableState.value = mutableState.value.copy(
                        phase = PayloadBuildPhase.Failed,
                        log = lines.takeLast(MAX_LOG_LINES),
                        summary = summary,
                        notices = notices,
                        // 闸门拒绝时把**具体原因**上屏：gate.blocked 与"没产物"是两回事，
                        // 混成一句"没有产物"会让用户根本不知道该改什么。
                        error = (result.gate as? GateDecision.Blocked)?.let { blocked ->
                            BuilderUserText.blockMessage(app, classifyBlock(blocked.title))
                        } ?: app.getString(R.string.builder_no_output),
                        blockedKind = (result.gate as? GateDecision.Blocked)
                            ?.let { classifyBlock(it.title) },
                    )
                    return@launch
                }

                val sha256 = withContext(Dispatchers.Default) { sha256Hex(packed) }
                outputLibrary = packed
                outputHeader = header
                outputOffsetsJson = offsetsJson
                publish(app.getString(R.string.builder_output_ready, packed.size / 1024))

                // 自动落盘到系统下载目录（MediaStore，不需要任何存储权限），
                // 并把它设为「自定义动态库」——用户点完「开始构建」就不该再手动搬文件。
                val fileName = outputFileName()
                val exportToDownloads = magic || AppPreferences.magicBuilderAutoExport(app)
                val applyAsLocal = magic || AppPreferences.magicBuilderAutoApply(app)

                val saved = if (exportToDownloads) {
                    withContext(Dispatchers.IO) {
                        runCatching { DownloadStore.save(app, fileName, packed) }.getOrNull()
                    }.also { path ->
                        if (path != null) {
                            publish(app.getString(R.string.builder_saved_download, path))
                        } else {
                            publish(app.getString(R.string.builder_save_download_failed))
                        }
                    }
                } else {
                    publish(app.getString(R.string.builder_auto_export_disabled))
                    null
                }

                val applied = if (applyAsLocal) {
                    withContext(Dispatchers.IO) {
                        runCatching {
                            LocalPayload.saveBytes(app, packed, fileName, selected = !magic)
                        }.getOrNull()
                    }.also { name ->
                        if (name != null) {
                            publish(app.getString(R.string.builder_applied, name))
                        }
                    }
                } else {
                    publish(app.getString(R.string.builder_auto_apply_disabled))
                    null
                }
                mutableState.value = mutableState.value.copy(
                    phase = PayloadBuildPhase.Done,
                    log = lines.takeLast(MAX_LOG_LINES),
                    summary = summary,
                    notices = notices,
                    outputName = fileName,
                    outputSize = packed.size.toLong(),
                    outputSha256 = sha256,
                    savedPath = saved.orEmpty(),
                    appliedAsPayload = applied != null,
                )
            } catch (error: Throwable) {
                val snapshot = DeviceSnapshot.current()
                val sourceAssistEligible = magic &&
                    VivoKonaSourceAssist.supports(snapshot) &&
                    (
                        error is com.kernelpack.kallsyms.KallsymsNotFoundException ||
                            error.message.orEmpty().contains("kallsyms", ignoreCase = true) ||
                            error.message.orEmpty().contains("无法识别架构") ||
                            error.message.orEmpty().contains("architecture", ignoreCase = true)
                    )

                if (sourceAssistEligible) {
                    val assisted = VivoKonaSourceAssist.analyze(snapshot, error)
                    outputLibrary = null
                    outputHeader = assisted.header
                    outputOffsetsJson = assisted.offsetsJson
                    publish("[Magic] generic parser fell back to exact Vivo/Kona source evidence")
                    publish("[Magic] runnable payload remains blocked until live physical/symbol evidence is complete")

                    val saved = if (AppPreferences.magicBuilderAutoExport(app)) {
                        withContext(Dispatchers.IO) {
                            runCatching {
                                exportMagicAnalysisArtifacts(
                                    app,
                                    snapshot,
                                    assisted.header,
                                    assisted.offsetsJson,
                                )
                            }.getOrElse { exportError ->
                                AppLog.warn(
                                    AppLogTags.BUILDER,
                                    "Magic source-assisted export failed: " +
                                        (exportError.message ?: exportError.javaClass.simpleName),
                                )
                                ""
                            }
                        }.also { paths ->
                            if (paths.isNotBlank()) publish("[Magic] source-assisted artifacts saved: $paths")
                        }
                    } else {
                        ""
                    }

                    AppLog.warn(
                        AppLogTags.BUILDER,
                        "Generic Magic parser failed; exact Vivo/Kona source-assisted analysis used instead: " +
                            (error.message ?: error.javaClass.simpleName),
                    )
                    mutableState.value = mutableState.value.copy(
                        phase = PayloadBuildPhase.Done,
                        scheme = scheme,
                        log = lines.takeLast(MAX_LOG_LINES),
                        summary = assisted.summary,
                        notices = assisted.notices,
                        headerName = "target.generated.h",
                        savedPath = saved,
                        analysisOnly = true,
                        error = null,
                        blockedKind = null,
                    )
                } else {
                    mutableState.value = mutableState.value.copy(
                        phase = PayloadBuildPhase.Failed,
                        log = lines.takeLast(MAX_LOG_LINES),
                        error = BuilderUserText.error(app, error),
                    )
                }
            }
        }
    }

    /**
     * 把产物写进「自定义载荷」（并切成当前载荷源）。
     * 成功后回调 [onApplied]，失败则把错误写进 state。
     */
    fun saveAsPayload(onApplied: (String) -> Unit) {
        val bytes = outputLibrary ?: return
        viewModelScope.launch {
            try {
                val info = withContext(Dispatchers.IO) {
                    LocalPayload.saveBytes(
                        getApplication(),
                        bytes,
                        mutableState.value.outputName.ifBlank { "libbs-patched.so" },
                    )
                }
                mutableState.value = mutableState.value.copy(appliedAsPayload = true, error = null)
                onApplied(info)
            } catch (error: Throwable) {
                mutableState.value = mutableState.value.copy(
                    error = error.message
                        ?: getApplication<Application>().getString(R.string.custom_import_failed),
                )
            }
        }
    }

    private fun buildSummary(app: Application, result: PackResult): String = buildString {
        val analysis = result.analysis
        appendLine(
            app.getString(R.string.builder_kernel, analysis.versionNumber, analysis.architecture),
        )
        appendLine(
            app.getString(R.string.builder_base, "0x" + java.lang.Long.toHexString(analysis.baseAddress)),
        )
        appendLine(app.getString(R.string.builder_symbols, analysis.symbols.size))
        appendLine(
            app.getString(
                R.string.builder_offsets,
                result.profile.offsets.count { it.value.resolved },
                result.profile.offsets.size,
            ),
        )
        result.baseline?.let { appendLine(app.getString(R.string.builder_baseline, it.variantLabel)) }
        appendLine(app.getString(R.string.builder_variant, result.profile.variantLabel))
        result.patchReport?.let { report ->
            // 这里用 changed / ok 而不是「站点数」：同一台机器上跑出来的旧值本来就等于新值，
            // 那时 .so 一个字节都不用改（产物与原件逐字节相同），
            // 只有换了别的机型/内核才真的会改写。分开报才不会让人以为"它改了什么"。
            val rewritten = report.outcomes.count { it.changed }
            appendLine(
                app.getString(
                    R.string.builder_patch_stats,
                    rewritten,
                    report.outcomes.count { it.ok },
                ),
            )
            if (rewritten == 0) appendLine(app.getString(R.string.builder_patch_noop))
            if (report.untouchedKeys.isNotEmpty()) {
                appendLine(app.getString(R.string.builder_patch_absent, report.untouchedKeys.size))
            }
        }
    }

    /**
     * 清掉"被拦下"的状态（用户关掉阻断框时调用）。
     *
     * 只清 [PayloadBuildState.blockedKind] 与 error，**不动日志** ——
     * 用户可能正要看日志里那几行原始原因，把日志一起抹掉等于把证据删了。
     */
    fun clearBlock() {
        mutableState.value = mutableState.value.copy(blockedKind = null, error = null)
    }

    /** 上一次实际读到的基线库名（可能因结构体族而与 scheme.library 不同）。 */
    private var lastBaseLibraryName: String? = null

    private fun readBaseLibrary(
        context: Context,
        scheme: PayloadScheme,
        kernelRelease: String? = null,
    ): ByteArray {
        // 按**结构体族**选库：6.1/6.12 用自编基线，6.6 用方案原有的那份。
        // 不做这一步的话，6.1/6.12 会拿到 6.6 族的 .so —— 结构体偏移是错的，
        // 而那是编译期烤死的、patch 改不回来。
        val libName = com.kernelpack.profile.BaselineRegistry.BaselineLibraries
            .resolve(scheme.library, kernelRelease)
        val file = File(context.applicationInfo.nativeLibraryDir, libName)
        require(file.exists()) { context.getString(R.string.error_bundled_missing) }
        lastBaseLibraryName = libName
        return file.readBytes()
    }

    private fun outputFileName(): String {
        val stamp = java.text.SimpleDateFormat("yyyyMMdd-HHmmss", java.util.Locale.US)
            .format(java.util.Date())
        val prefix = mutableState.value.scheme?.filePrefix ?: "payload"
        return "$prefix-$stamp.so"
    }

    companion object {
        private const val MAX_LOG_LINES = 400

        /** 「解析完整包链接」的缓存子目录与产物前缀。 */
        private const val OTA_CACHE_DIR = "ota"
        private const val OTA_FILE_PREFIX = "ksuroot_ota_"

        fun sha256Hex(bytes: ByteArray): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
            val builder = StringBuilder(digest.size * 2)
            for (byte in digest) {
                builder.append(Character.forDigit((byte.toInt() shr 4) and 0xf, 16))
                builder.append(Character.forDigit(byte.toInt() and 0xf, 16))
            }
            return builder.toString()
        }
    }
}
