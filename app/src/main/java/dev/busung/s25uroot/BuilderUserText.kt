package ctrl.mietze.veyraroot

import android.content.Context
import com.kernelpack.PackResult
import com.kernelpack.policy.KernelSchemeSelector

internal object BuilderUserText {
    private val han = Regex("[\\u4E00-\\u9FFF]")

    fun technical(context: Context, raw: String): String? {
        if (raw.isBlank()) return null
        AppLog.debug(AppLogTags.BUILDER, raw)

        Regex("""\[\+\]\s*容器:\s*(\S+).*版本=([^\s]+).*page=([^\s]+).*kernel_off=(\S+)""")
            .find(raw)?.let { match ->
                return context.getString(
                    R.string.builder_log_container,
                    match.groupValues[1],
                    match.groupValues[2],
                    match.groupValues[3],
                    match.groupValues[4],
                )
            }

        Regex("""\[\+\]\s*内核 Image:\s*(\d+)\s*字节(.*)""")
            .find(raw)?.let { match ->
                val decompressed = match.groupValues[2].contains("解压")
                return context.getString(
                    if (decompressed) R.string.builder_log_kernel_image_decompressed
                    else R.string.builder_log_kernel_image,
                    match.groupValues[1],
                )
            }
        Regex("""\[i\]\s*(\S+)\s*由推导得到:.*->\s*(\S+)""")
            .find(raw)?.let { match ->
                return context.getString(
                    R.string.builder_log_derived_offset,
                    match.groupValues[1],
                    match.groupValues[2],
                )
            }

        if (raw.contains("解析诊断")) {
            return context.getString(R.string.builder_log_parse_diagnostic)
        }
        if (raw.contains("符号") && raw.contains("停止打包")) {
            return context.getString(R.string.builder_log_alignment_stopped)
        }
        if (raw.contains("偏移") && raw.contains("失败")) {
            return context.getString(R.string.builder_log_offset_warning)
        }

        // Kernelpack still keeps its exact upstream/internal diagnostics in AppLog. The Builder page
        // only shows messages that have an explicit localized representation.
        return null
    }

    fun ota(context: Context, raw: String): String? {
        if (raw.isBlank()) return null
        AppLog.debug(AppLogTags.BUILDER, raw)

        Regex("""已连接，文件大小\s+(.+)""").find(raw)?.let {
            return context.getString(R.string.builder_ota_log_connected, it.groupValues[1])
        }
        if (raw.contains("裸 payload.bin")) {
            return context.getString(R.string.builder_ota_log_raw_payload)
        }
        if (raw.contains("定位 ZIP 中央目录")) {
            return context.getString(R.string.builder_ota_log_zip_directory)
        }
        Regex("""找到 payload\.bin（(.+)）""").find(raw)?.let {
            return context.getString(R.string.builder_ota_log_payload_found, it.groupValues[1])
        }
        Regex("""直接找到\s+(.+)""").find(raw)?.let {
            return context.getString(R.string.builder_ota_log_direct_boot, it.groupValues[1])
        }
        Regex("""payload 内分区：(\d+) 个，准备提取 (.+)""").find(raw)?.let {
            return context.getString(
                R.string.builder_ota_log_partitions,
                it.groupValues[1],
                it.groupValues[2],
            )
        }
        Regex("""下载中\s+(.+)\s+/\s+(.+?)(?:（(.+?)）)?$""").find(raw)?.let {
            return context.getString(
                R.string.builder_ota_log_downloading,
                it.groupValues[1],
                it.groupValues[2],
            )
        }
        Regex("""已还原\s+(.+?)\s+→\s+(.+)（(.+)）""").find(raw)?.let {
            return context.getString(
                R.string.builder_ota_log_restored,
                it.groupValues[1],
                it.groupValues[2],
                it.groupValues[3],
            )
        }
        Regex("""下载\s+(.+)（(.+)）…""").find(raw)?.let {
            return context.getString(
                R.string.builder_ota_log_download_entry,
                it.groupValues[1],
                it.groupValues[2],
            )
        }
        Regex("""已解出\s+(.+)（(.+)）""").find(raw)?.let {
            return context.getString(
                R.string.builder_ota_log_extracted,
                it.groupValues[1],
                it.groupValues[2],
            )
        }
        return null
    }

    fun otaError(context: Context, error: Throwable): String {
        val raw = error.message.orEmpty()
        AppLog.error(AppLogTags.BUILDER, "OTA builder failure", error)
        return when {
            raw.contains("Range") -> context.getString(R.string.builder_ota_error_range)
            raw.contains("连不上") || raw.contains("服务器") ->
                context.getString(R.string.builder_ota_error_connect)
            raw.contains("XZ") -> context.getString(R.string.builder_ota_error_xz)
            raw.contains("ZIP") || raw.contains("payload") || raw.contains("完整包") ->
                context.getString(R.string.builder_ota_error_package)
            else -> context.getString(R.string.builder_ota_error_generic)
        }
    }

    fun schemeSelected(
        context: Context,
        selected: KernelSchemeSelector.Decision.Selected,
    ): String = context.getString(
        if (selected.useTestScheme) R.string.builder_log_scheme_test
        else R.string.builder_log_scheme_mainline,
        selected.series,
    )
    fun blockMessage(context: Context, kind: BuildBlockKind): String = context.getString(
        when (kind) {
            BuildBlockKind.TEST_KERNEL_DISABLED -> R.string.builder_block_detail_test_kernel
            BuildBlockKind.BASELINE_NOT_REGISTERED -> R.string.builder_block_detail_baseline
            BuildBlockKind.ABI_CONFLICT -> R.string.builder_block_detail_abi
            BuildBlockKind.UNKNOWN_KERNEL -> R.string.builder_block_detail_unknown
            BuildBlockKind.VIVO_VR_KO_MISSING -> R.string.builder_block_detail_vrko
            BuildBlockKind.OTHER -> R.string.builder_block_detail_other
        },
    )

    fun notices(context: Context, result: PackResult): List<String> {
        val out = LinkedHashSet<String>()
        if (result.analysis.symbols.size < 1000) {
            out += context.getString(
                R.string.builder_notice_low_symbols,
                result.analysis.symbols.size,
            )
        }

        result.warnings.forEach { raw ->
            AppLog.warn(AppLogTags.BUILDER, raw)
            val localized = when {
                raw.contains("基址") ->
                    context.getString(R.string.builder_notice_base_mismatch)
                raw.contains("符号") && raw.contains("停止打包") ->
                    context.getString(R.string.builder_notice_alignment_failed)
                raw.contains("偏移") && raw.contains("替换") ->
                    context.getString(R.string.builder_notice_incomplete_patch)
                raw.contains("忽略冲突") || raw.contains("[风险]") ->
                    context.getString(R.string.builder_notice_forced_override)
                raw.contains("基线") ->
                    context.getString(R.string.builder_notice_baseline)
                raw.contains("vr.ko", ignoreCase = true) ->
                    context.getString(R.string.builder_notice_vrko)
                else ->
                    context.getString(R.string.builder_notice_technical)
            }
            out += localized
        }
        return out.toList()
    }
    fun error(context: Context, error: Throwable): String {
        AppLog.error(AppLogTags.BUILDER, "Builder failure", error)
        val raw = error.message.orEmpty()
        return when {
            raw.contains("无法识别架构") || raw.contains("architecture", ignoreCase = true) &&
                raw.contains("identify", ignoreCase = true) ->
                context.getString(R.string.builder_error_architecture)
            error is com.kernelpack.kallsyms.KallsymsNotFoundException ||
                raw.contains("kallsyms", ignoreCase = true) ->
                context.getString(R.string.builder_error_kallsyms)
            error is java.io.FileNotFoundException ->
                context.getString(R.string.builder_error_file_missing)
            error is java.io.IOException ->
                context.getString(R.string.builder_error_io)
            else ->
                context.getString(
                    R.string.builder_error_generic,
                    error.javaClass.simpleName,
                )
        }
    }
}
