package ctrl.mietze.veyraroot

import android.provider.OpenableColumns
import android.text.format.Formatter
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kernelpack.offsets.GhostLockOffsetsIo
import com.kernelpack.ota.OtaPayloadExtractor

@Composable
internal fun VeyraPayloadBuilderPage(
    padding: PaddingValues,
    onBack: () -> Unit,
    onOpenBuilderSettings: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val vm: PayloadBuilderViewModel = viewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val otaState by vm.otaState.collectAsStateWithLifecycle()
    val liveSnapshot = remember { DeviceSnapshot.current() }
    var showSchemePicker by remember { mutableStateOf(false) }
    var showOtaDialog by remember { mutableStateOf(false) }
    var otaUrl by remember { mutableStateOf(otaState.url) }
    var offsetsSummary by remember { mutableStateOf<String?>(null) }
    var offsetsError by remember { mutableStateOf<String?>(null) }

    val bootPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val resolver = context.contentResolver
        val row = resolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
            null,
            null,
            null,
        )?.use { cursor ->
            if (!cursor.moveToFirst()) null else {
                val name = cursor.getString(0) ?: "boot.img"
                val size = if (cursor.isNull(1)) 0L else cursor.getLong(1)
                name to size
            }
        } ?: ("boot.img" to 0L)
        vm.rememberBootImage(uri, row.first, row.second)
    }

    val offsetsPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.openInputStream(uri)
                ?.bufferedReader()
                ?.use { it.readText() }
                ?: error(context.getString(R.string.builder_read_failed))
        }.mapCatching(GhostLockOffsetsIo::read)
            .onSuccess { doc ->
                offsetsError = null
                offsetsSummary = buildString {
                    appendLine(context.getString(R.string.offsets_release, doc.release ?: "—"))
                    appendLine(
                        context.getString(
                            R.string.offsets_counts,
                            doc.symbols.size,
                            doc.structFields.size,
                        ),
                    )
                    val missingSymbols = doc.missingSymbols().size
                    val missingFields = doc.missingStructFields().size
                    if (missingSymbols > 0 || missingFields > 0) {
                        appendLine(
                            context.getString(
                                R.string.offsets_missing,
                                missingSymbols,
                                missingFields,
                            ),
                        )
                    }
                    if (doc.unknown.isNotEmpty()) {
                        appendLine(context.getString(R.string.offsets_unknown, doc.unknown.size))
                    }
                }.trim()
            }
            .onFailure {
                offsetsSummary = null
                offsetsError = it.message ?: context.getString(R.string.builder_read_failed)
            }
    }

    val soExporter = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri ->
        val bytes = vm.outputBytes() ?: return@rememberLauncherForActivityResult
        if (uri != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                    ?: error("open failed")
            }
        }
    }
    val headerExporter = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain"),
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use {
                    it.write(vm.outputHeaderText())
                } ?: error("open failed")
            }
        }
    }
    val offsetsExporter = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use {
                    it.write(vm.outputOffsetsText())
                } ?: error("open failed")
            }
        }
    }

    LaunchedEffect(otaState.url) {
        if (otaState.url.isNotBlank()) otaUrl = otaState.url
    }

    if (showSchemePicker) {
        AlertDialog(
            onDismissRequest = { showSchemePicker = false },
            icon = { Icon(Icons.Rounded.Build, contentDescription = null) },
            title = { Text(stringResource(R.string.builder_scheme_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        stringResource(R.string.builder_scheme_subtitle),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    BuilderSchemeCard(
                        title = stringResource(R.string.builder_scheme_universal),
                        detail = stringResource(R.string.builder_scheme_universal_detail),
                        onClick = {
                            showSchemePicker = false
                            vm.build(PayloadScheme.Universal)
                        },
                    )
                    BuilderSchemeCard(
                        title = stringResource(R.string.builder_scheme_vivo),
                        detail = stringResource(R.string.builder_scheme_vivo_detail),
                        onClick = {
                            showSchemePicker = false
                            vm.build(PayloadScheme.VivoVrKo)
                        },
                    )
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showSchemePicker = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    if (showOtaDialog) {
        AlertDialog(
            onDismissRequest = { showOtaDialog = false },
            icon = { Icon(Icons.Rounded.Link, contentDescription = null) },
            title = { Text(stringResource(R.string.builder_ota_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        stringResource(R.string.builder_ota_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = otaUrl,
                        onValueChange = {
                            otaUrl = it
                            vm.clearOtaError()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.builder_ota_hint)) },
                        enabled = !otaState.running,
                    )
                    if (otaState.running) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp))
                            Text(stringResource(R.string.builder_ota_running))
                        }
                    }
                    otaState.error?.let {
                        Text(it, color = MaterialTheme.colorScheme.error)
                    }
                    if (otaState.resultName.isNotBlank()) {
                        Text(
                            stringResource(
                                R.string.builder_ota_done,
                                otaState.resultName,
                                OtaPayloadExtractor.formatSize(otaState.resultSize),
                            ),
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    if (otaState.log.isNotEmpty()) {
                        Text(
                            otaState.log.takeLast(20).joinToString("\n"),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 160.dp)
                                .verticalScroll(rememberScrollState()),
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            },
            confirmButton = {
                FilledTonalButton(
                    onClick = { vm.parseOtaLink(otaUrl) },
                    enabled = !otaState.running && otaUrl.isNotBlank(),
                ) {
                    Text(stringResource(R.string.builder_ota_run))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        if (otaState.running) vm.cancelOtaParse()
                        showOtaDialog = false
                    },
                ) {
                    Text(stringResource(R.string.builder_ota_close))
                }
            },
        )
    }

    if (offsetsSummary != null || offsetsError != null) {
        AlertDialog(
            onDismissRequest = {
                offsetsSummary = null
                offsetsError = null
            },
            title = { Text(stringResource(R.string.offsets_dialog_title)) },
            text = {
                Text(
                    offsetsSummary ?: context.getString(
                        R.string.offsets_error,
                        offsetsError.orEmpty(),
                    ),
                    color = if (offsetsError != null) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        offsetsSummary = null
                        offsetsError = null
                    },
                ) {
                    Text(stringResource(R.string.action_confirm))
                }
            },
        )
    }

    state.blockedKind?.let { kind ->
        AlertDialog(
            onDismissRequest = vm::clearBlock,
            icon = { Icon(Icons.Rounded.Warning, contentDescription = null) },
            title = { Text(builderBlockTitle(kind)) },
            text = { Text(state.error.orEmpty()) },
            confirmButton = {
                FilledTonalButton(
                    onClick = {
                        vm.clearBlock()
                        if (kind == BuildBlockKind.TEST_KERNEL_DISABLED) {
                            onOpenBuilderSettings()
                        }
                    },
                ) {
                    Text(
                        if (kind == BuildBlockKind.TEST_KERNEL_DISABLED) {
                            stringResource(R.string.builder_open_kernel_settings)
                        } else {
                            stringResource(R.string.action_confirm)
                        },
                    )
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
            BuilderPageHeader(
                title = stringResource(R.string.builder_heading),
                onBack = onBack,
            )
        }
        item {
            Text(
                stringResource(R.string.builder_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                ),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(
                        Icons.Rounded.Memory,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.builder_live_kernel_title),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            liveSnapshot.kernelRelease,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            stringResource(R.string.builder_live_kernel_summary),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        item {
            BuilderActionCard(
                icon = Icons.Rounded.Save,
                title = stringResource(R.string.offsets_import),
                summary = stringResource(R.string.offsets_import_desc),
                onClick = { offsetsPicker.launch(arrayOf("application/json", "*/*")) },
            )
        }
        item {
            BuilderActionCard(
                icon = Icons.Rounded.Settings,
                title = stringResource(R.string.builder_settings_title),
                summary = stringResource(R.string.builder_settings_summary),
                onClick = onOpenBuilderSettings,
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
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(Icons.Rounded.Memory, contentDescription = null)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.builder_boot_image),
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                if (state.sourceName.isBlank()) {
                                    stringResource(R.string.builder_boot_none)
                                } else {
                                    state.sourceName + if (state.sourceSize > 0) {
                                        " · " + Formatter.formatFileSize(context, state.sourceSize)
                                    } else {
                                        ""
                                    }
                                },
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    Text(
                        stringResource(R.string.builder_boot_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    FilledTonalButton(
                        onClick = { bootPicker.launch(arrayOf("application/octet-stream", "*/*")) },
                        enabled = !state.busy,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Rounded.FolderOpen, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.builder_pick_boot))
                    }
                    FilledTonalButton(
                        onClick = { showOtaDialog = true },
                        enabled = !state.busy && !otaState.running,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Rounded.Link, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.builder_ota))
                    }
                    Button(
                        onClick = { showSchemePicker = true },
                        enabled = !state.busy && state.sourceName.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Rounded.Build, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (state.busy) {
                                stringResource(R.string.builder_running)
                            } else {
                                stringResource(R.string.builder_run)
                            },
                        )
                    }
                }
            }
        }

        if (state.busy) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ),
                ) {
                    Row(
                        modifier = Modifier.padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(26.dp))
                        Column {
                            Text(
                                stringResource(R.string.builder_running),
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                state.log.lastOrNull().orEmpty(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }

        if (state.log.isNotEmpty()) {
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
                            state.log.takeLast(28).joinToString("\n"),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 230.dp)
                                .verticalScroll(rememberScrollState()),
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        state.error?.takeIf { state.blockedKind == null }?.let { error ->
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                    ),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(Icons.Rounded.Error, contentDescription = null)
                        Text(error, modifier = Modifier.weight(1f))
                    }
                }
            }
        }

        state.scheme?.let { scheme ->
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    ),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(
                            Icons.Rounded.Build,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                if (scheme == PayloadScheme.Universal) {
                                    stringResource(R.string.builder_scheme_universal)
                                } else {
                                    stringResource(R.string.builder_scheme_vivo)
                                },
                                style = MaterialTheme.typography.titleMedium,
                            )
                            if (state.baseLibraryName.isNotBlank()) {
                                Text(
                                    stringResource(
                                        R.string.builder_base_library,
                                        state.baseLibraryName,
                                        Formatter.formatFileSize(context, state.baseLibrarySize),
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }

        if (state.notices.isNotEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(Icons.Rounded.Warning, contentDescription = null)
                            Text(
                                stringResource(R.string.builder_notices),
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                        state.notices.forEach { notice ->
                            Text(
                                "• $notice",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }

        if (state.summary.isNotBlank()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    ),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            stringResource(R.string.builder_section_result),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Spacer(Modifier.size(8.dp))
                        Text(
                            state.summary.trim(),
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }

        if (state.outputSize > 0) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    ),
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(9.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Icon(
                                Icons.Rounded.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                state.outputName,
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                        Text(
                            stringResource(
                                R.string.builder_output_size,
                                Formatter.formatFileSize(context, state.outputSize),
                            ),
                        )
                        Text(
                            stringResource(
                                R.string.builder_output_sha,
                                state.outputSha256,
                            ),
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        if (state.savedPath.isNotBlank()) {
                            Text(
                                stringResource(R.string.builder_saved_download, state.savedPath),
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        if (state.appliedAsPayload) {
                            Text(
                                stringResource(R.string.builder_applied_state),
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        HorizontalDivider()
                        Button(
                            onClick = { vm.saveAsPayload { } },
                            enabled = !state.busy,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Rounded.CheckCircle, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.builder_set_as_payload))
                        }
                        FilledTonalButton(
                            onClick = {
                                soExporter.launch(
                                    state.outputName.ifBlank { "veyra-payload.so" },
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Rounded.Save, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.builder_export_so))
                        }
                        TextButton(
                            onClick = { headerExporter.launch("target.generated.h") },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Rounded.Code, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.builder_export_header))
                        }
                        TextButton(
                            onClick = { offsetsExporter.launch("offsets.json") },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Rounded.Save, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.builder_export_offsets))
                        }
                    }
                }
            }
        }

        if (state.analysisOnly) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    ),
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            stringResource(R.string.builder_analysis_only_title),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            stringResource(R.string.builder_analysis_only_body),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        FilledTonalButton(
                            onClick = { headerExporter.launch("target.generated.h") },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Rounded.Code, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.builder_export_header))
                        }
                        TextButton(
                            onClick = { offsetsExporter.launch("offsets.json") },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Rounded.Save, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.builder_export_offsets))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BuilderSchemeCard(
    title: String,
    detail: String,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        ),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun BuilderActionCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    summary: String,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun BuilderPageHeader(
    title: String,
    onBack: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null)
        }
        Text(
            title,
            style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun builderBlockTitle(kind: BuildBlockKind): String = when (kind) {
    BuildBlockKind.TEST_KERNEL_DISABLED -> stringResource(R.string.test_kernel_blocked_title)
    BuildBlockKind.BASELINE_NOT_REGISTERED -> stringResource(R.string.builder_block_baseline)
    BuildBlockKind.ABI_CONFLICT -> stringResource(R.string.builder_block_abi)
    BuildBlockKind.UNKNOWN_KERNEL -> stringResource(R.string.builder_block_unknown_kernel)
    BuildBlockKind.VIVO_VR_KO_MISSING -> stringResource(R.string.builder_block_vrko)
    BuildBlockKind.OTHER -> stringResource(R.string.builder_block_other)
}


@Composable
internal fun VeyraBuilderSettingsPage(
    padding: PaddingValues,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    var series by remember { mutableStateOf(AppPreferences.kernelSeriesOverride(context)) }
    var allowMismatch by remember { mutableStateOf(AppPreferences.allowAbiMismatch(context)) }
    var allowTest by remember { mutableStateOf(AppPreferences.allowTestKernel(context)) }
    var allowUnstable4x by remember {
        mutableStateOf(AppPreferences.builderAllowUnstable4x(context))
    }
    var nearestFamily by remember {
        mutableStateOf(AppPreferences.builderNearestFamily(context))
    }
    var showSeries by remember { mutableStateOf(false) }
    var showCoverage by remember { mutableStateOf(false) }
    var showTestConfirm by remember { mutableStateOf(false) }
    var showNearestConfirm by remember { mutableStateOf(false) }

    if (showSeries) {
        AlertDialog(
            onDismissRequest = { showSeries = false },
            title = { Text(stringResource(R.string.builder_kernel_series_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    com.kernelpack.policy.SeriesOverride.entries.forEach { option ->
                        Card(
                            onClick = {
                                series = option
                                AppPreferences.setKernelSeriesOverride(context, option)
                                showSeries = false
                            },
                            colors = CardDefaults.cardColors(
                                containerColor = if (series == option) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainerHighest
                                },
                            ),
                        ) {
                            Column(Modifier.fillMaxWidth().padding(14.dp)) {
                                Text(
                                    when (option) {
                                        com.kernelpack.policy.SeriesOverride.AUTO -> stringResource(R.string.builder_kernel_series_auto)
                                        com.kernelpack.policy.SeriesOverride.FORCE_4 -> stringResource(R.string.builder_kernel_series_4)
                                        com.kernelpack.policy.SeriesOverride.FORCE_5 -> stringResource(R.string.builder_kernel_series_5)
                                        com.kernelpack.policy.SeriesOverride.FORCE_6 -> stringResource(R.string.builder_kernel_series_6)
                                    },
                                    style = MaterialTheme.typography.titleMedium,
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showSeries = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    if (showCoverage) {
        val registry = com.kernelpack.profile.BaselineRegistry
        val allEntries = registry.allEntries
        val coverage = buildString {
            listOf(
                com.kernelpack.profile.BaselineScheme.UNIVERSAL to
                    context.getString(R.string.builder_scheme_universal),
                com.kernelpack.profile.BaselineScheme.VIVO to
                    context.getString(R.string.builder_scheme_vivo),
            ).forEach { (scheme, label) ->
                registry.MAINLINE_SERIES.forEach { seriesName ->
                    val hits = allEntries.filter { entry ->
                        entry.scheme == scheme &&
                            (entry.kernelSeries == seriesName ||
                                entry.kernelSeries.startsWith("$seriesName."))
                    }
                    if (hits.isEmpty()) {
                        appendLine(
                            context.getString(
                                R.string.builder_coverage_missing,
                                seriesName,
                                label,
                            ),
                        )
                    } else {
                        val measured = hits.count { entry -> !entry.beta }
                        appendLine(
                            context.getString(
                                R.string.builder_coverage_registered,
                                seriesName,
                                label,
                                hits.size,
                                measured,
                                hits.size - measured,
                            ),
                        )
                    }
                }
            }
            val testHits = allEntries.count { entry ->
                registry.TEST_SERIES.any { seriesName ->
                    entry.kernelSeries == seriesName ||
                        entry.kernelSeries.startsWith("$seriesName.")
                }
            }
            appendLine(
                context.getString(
                    R.string.builder_coverage_test,
                    registry.TEST_SERIES.joinToString(" / "),
                    testHits,
                ),
            )
            appendLine(
                "4.x (unstable): " +
                    registry.UNSTABLE_4X_SERIES.joinToString(" / ") +
                    " · exact public adapter evidence: " +
                    registry.VERIFIED_UNSTABLE_RELEASES.joinToString(),
            )
            val knownLayouts = allEntries.count { entry ->
                entry.feasibility?.layout?.known == true
            }
            val measuredLandings = allEntries.count { entry ->
                entry.feasibility?.measuredWord != null
            }
            appendLine(
                context.getString(
                    R.string.builder_coverage_feasibility,
                    knownLayouts,
                    measuredLandings,
                    allEntries.size,
                ),
            )
            appendLine(
                context.getString(
                    R.string.builder_coverage_upstream,
                    com.kernelpack.profile.GhostLockKernelCatalog.VERSIONS.size,
                    com.kernelpack.profile.GhostLockKernelOffsets.KERNELS.size,
                ),
            )
            val radar = VeyraCompatibilityRadar.scan(context)
            appendLine()
            appendLine("Veyra Compatibility Radar")
            appendLine("Device signature: " + radar.deviceSignature.take(24) + "…")
            radar.lines.forEach(::appendLine)
        }
        AlertDialog(
            onDismissRequest = { showCoverage = false },
            icon = { Icon(Icons.Rounded.CheckCircle, contentDescription = null) },
            title = { Text(stringResource(R.string.builder_baseline_coverage_title)) },
            text = {
                Text(
                    coverage,
                    modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                )
            },
            confirmButton = {
                TextButton(onClick = { showCoverage = false }) {
                    Text(stringResource(R.string.action_confirm))
                }
            },
        )
    }

    if (showTestConfirm) {
        AlertDialog(
            onDismissRequest = { showTestConfirm = false },
            icon = { Icon(Icons.Rounded.Warning, contentDescription = null) },
            title = { Text(stringResource(R.string.builder_test_kernel_confirm_title)) },
            text = { Text(stringResource(R.string.builder_test_kernel_confirm_body)) },
            confirmButton = {
                FilledTonalButton(
                    onClick = {
                        allowTest = true
                        AppPreferences.setAllowTestKernel(context, true)
                        showTestConfirm = false
                    },
                ) {
                    Text(stringResource(R.string.builder_test_kernel_confirm_action))
                }
            },
            dismissButton = {
                TextButton(onClick = { showTestConfirm = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    if (showNearestConfirm) {
        AlertDialog(
            onDismissRequest = { showNearestConfirm = false },
            icon = { Icon(Icons.Rounded.Warning, contentDescription = null) },
            title = { Text(stringResource(R.string.builder_nearest_family_confirm_title)) },
            text = { Text(stringResource(R.string.builder_nearest_family_confirm_body)) },
            confirmButton = {
                FilledTonalButton(
                    onClick = {
                        nearestFamily = true
                        AppPreferences.setBuilderNearestFamily(context, true)
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
            BuilderPageHeader(
                title = stringResource(R.string.builder_settings_title),
                onBack = onBack,
            )
        }
        item {
            Text(
                stringResource(R.string.builder_settings_summary),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            BuilderActionCard(
                icon = Icons.Rounded.Build,
                title = stringResource(R.string.builder_kernel_series_title),
                summary = when (series) {
                    com.kernelpack.policy.SeriesOverride.AUTO -> stringResource(R.string.builder_kernel_series_auto)
                    com.kernelpack.policy.SeriesOverride.FORCE_4 -> stringResource(R.string.builder_kernel_series_4)
                    com.kernelpack.policy.SeriesOverride.FORCE_5 -> stringResource(R.string.builder_kernel_series_5)
                    com.kernelpack.policy.SeriesOverride.FORCE_6 -> stringResource(R.string.builder_kernel_series_6)
                },
                onClick = { showSeries = true },
            )
        }
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                ),
            ) {
                Column {
                    BuilderSettingSwitchRow(
                        icon = Icons.Rounded.Warning,
                        title = stringResource(R.string.builder_ignore_conflicts_title),
                        summary = stringResource(R.string.builder_ignore_conflicts_summary),
                        checked = allowMismatch,
                        onCheckedChange = {
                            allowMismatch = it
                            AppPreferences.setAllowAbiMismatch(context, it)
                        },
                    )
                    HorizontalDivider()
                    BuilderSettingSwitchRow(
                        icon = Icons.Rounded.Memory,
                        title = stringResource(R.string.test_kernel_switch),
                        summary = stringResource(R.string.test_kernel_switch_description),
                        checked = allowTest,
                        onCheckedChange = { want ->
                            if (want) {
                                showTestConfirm = true
                            } else {
                                allowTest = false
                                AppPreferences.setAllowTestKernel(context, false)
                            }
                        },
                    )
                    HorizontalDivider()
                    BuilderSettingSwitchRow(
                        icon = Icons.Rounded.Build,
                        title = stringResource(R.string.unstable_4x_switch),
                        summary = stringResource(R.string.unstable_4x_switch_description),
                        checked = allowUnstable4x,
                        onCheckedChange = {
                            allowUnstable4x = it
                            AppPreferences.setBuilderAllowUnstable4x(context, it)
                        },
                    )
                    HorizontalDivider()
                    BuilderSettingSwitchRow(
                        icon = Icons.Rounded.Warning,
                        title = stringResource(R.string.builder_nearest_family_title),
                        summary = stringResource(R.string.builder_nearest_family_summary),
                        checked = nearestFamily,
                        onCheckedChange = { want ->
                            if (want) {
                                showNearestConfirm = true
                            } else {
                                nearestFamily = false
                                AppPreferences.setBuilderNearestFamily(context, false)
                            }
                        },
                    )
                }
            }
        }
        item {
            BuilderActionCard(
                icon = Icons.Rounded.CheckCircle,
                title = stringResource(R.string.builder_baseline_coverage_title),
                summary = stringResource(R.string.builder_baseline_coverage_summary),
                onClick = { showCoverage = true },
            )
        }
    }
}

@Composable
private fun BuilderSettingSwitchRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
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
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        androidx.compose.material3.Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    }
}
