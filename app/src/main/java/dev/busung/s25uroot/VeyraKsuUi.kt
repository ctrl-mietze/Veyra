package ctrl.mietze.veyraroot

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun VeyraKsuPage(
    padding: PaddingValues,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf(VeyraKsuState()) }
    var candidates by remember { mutableStateOf<List<RmgCandidate>>(emptyList()) }
    var selected by remember { mutableStateOf<RmgCandidate?>(null) }
    // Migration is intentionally opt-in on every visit. Installing/updating VeyraKSU must never
    // silently disable or clear another RMG provider just because one was discovered on the phone.
    var cleanupOldProvider by remember { mutableStateOf(false) }
    var autoInstallAfterRoot by remember {
        mutableStateOf(VeyraKsuPreferences.autoInstallAfterRoot(context))
    }
    var busy by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<String?>(null) }
    var modulePath by remember { mutableStateOf<String?>(null) }

    suspend fun refresh() {
        state = withContext(Dispatchers.IO) { VeyraKsuBridge.readState() }
    }

    LaunchedEffect(Unit) {
        state = withContext(Dispatchers.IO) { VeyraKsuBridge.readState() }
        candidates = withContext(Dispatchers.IO) { RmgCandidateScanner.scan(context) }
        val remembered = RootMigrationStore.selectedProviderPackage(context)
        selected = candidates.firstOrNull { it.packageName == remembered }
            ?: candidates.firstOrNull()
        selected?.let { RootMigrationStore.rememberProvider(context, it) }
    }

    fun runRecovery(tool: RecoveryTool) {
        if (busy) return
        busy = true
        result = null
        scope.launch {
            val outcome = runRecoveryAction(context, tool)
            result = outcome.detail.ifBlank {
                context.getString(
                    if (outcome.accepted) R.string.veyra_ksu_action_ok
                    else R.string.veyra_ksu_action_failed,
                )
            }
            refresh()
            busy = false
        }
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
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null)
                }
                Spacer(Modifier.width(6.dp))
                Column {
                    Text(
                        stringResource(R.string.veyra_ksu_title),
                        style = MaterialTheme.typography.headlineLarge,
                    )
                    Text(
                        stringResource(R.string.veyra_ksu_subtitle),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                ),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(
                            Icons.Rounded.Memory,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            stringResource(R.string.veyra_ksu_bridge_status),
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                    VeyraKsuReading(
                        stringResource(R.string.veyra_ksu_reading_install),
                        stringResource(
                            if (state.installed) R.string.veyra_ksu_status_installed
                            else R.string.veyra_ksu_status_not_installed,
                        ),
                    )
                    VeyraKsuReading(
                        stringResource(R.string.veyra_ksu_reading_load),
                        stringResource(
                            if (state.enabled) R.string.veyra_ksu_status_enabled
                            else R.string.veyra_ksu_status_disabled,
                        ),
                    )
                    if (state.stage.isNotBlank()) {
                        VeyraKsuReading(
                            stringResource(R.string.veyra_ksu_reading_stage),
                            state.stage,
                        )
                    }
                    if (state.bootId.isNotBlank()) {
                        VeyraKsuReading(
                            stringResource(R.string.veyra_ksu_reading_boot),
                            state.bootId.take(13) + "…",
                        )
                    }
                    VeyraKsuReading(
                        stringResource(R.string.veyra_ksu_reading_temp_root),
                        stringResource(
                            if (state.tempRootSeen) R.string.veyra_ksu_yes
                            else R.string.veyra_ksu_no,
                        ),
                    )
                    VeyraKsuReading(
                        stringResource(R.string.veyra_ksu_reading_permission_bridge),
                        stringResource(R.string.veyra_ksu_permission_bridge_inactive),
                    )
                }
            }
        }
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                ),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.veyra_ksu_auto_install_title),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            stringResource(R.string.veyra_ksu_auto_install_summary),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = autoInstallAfterRoot,
                        onCheckedChange = { enabled ->
                            autoInstallAfterRoot = enabled
                            VeyraKsuPreferences.setAutoInstallAfterRoot(context, enabled)
                        },
                    )
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                ),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        stringResource(R.string.veyra_ksu_install_heading),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        stringResource(R.string.veyra_ksu_install_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(
                        enabled = !busy,
                        onClick = {
                            busy = true
                            result = null
                            scope.launch {
                                val export = withContext(Dispatchers.IO) {
                                    runCatching { VeyraKsuModule.export(context) }
                                }
                                export.onFailure {
                                    result = it.message ?: it.javaClass.simpleName
                                    busy = false
                                    return@launch
                                }
                                val artifact = export.getOrThrow()
                                modulePath = artifact.path
                                val install = withContext(Dispatchers.IO) {
                                    VeyraKsuModule.installWithKernelSu(artifact.path)
                                }
                                if (install == null || install.exitCode != 0) {
                                    result = install?.output?.ifBlank { null }
                                        ?: context.getString(R.string.veyra_ksu_install_failed)
                                    busy = false
                                    return@launch
                                }

                                val reload = runRecoveryAction(context, RecoveryTool.ReloadModules)
                                val migration = if (cleanupOldProvider && selected != null) {
                                    withContext(Dispatchers.IO) {
                                        TempRootAdopter.adopt(context, selected!!, true)
                                    }
                                } else {
                                    null
                                }
                                result = buildString {
                                    append(context.getString(R.string.veyra_ksu_installed))
                                    if (reload.accepted) {
                                        append("\n")
                                        append(context.getString(R.string.veyra_ksu_reload_done))
                                    }
                                    when (migration) {
                                        is TempRootAdoptionResult.Success -> {
                                            append("\n")
                                            append(context.getString(R.string.veyra_ksu_old_provider_disabled))
                                        }
                                        is TempRootAdoptionResult.Refused -> {
                                            append("\n")
                                            append(
                                                context.getString(
                                                    R.string.veyra_ksu_old_provider_kept,
                                                ),
                                            )
                                        }
                                        null -> Unit
                                    }
                                }
                                refresh()
                                busy = false
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Rounded.Memory, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.veyra_ksu_install_update))
                    }
                    TextButton(
                        enabled = !busy,
                        onClick = {
                            busy = true
                            scope.launch {
                                runCatching {
                                    withContext(Dispatchers.IO) {
                                        VeyraKsuModule.export(context)
                                    }
                                }.onSuccess {
                                    modulePath = it.path
                                    result = context.getString(
                                        R.string.veyra_ksu_exported,
                                        it.path,
                                    )
                                }.onFailure {
                                    result = it.message ?: it.javaClass.simpleName
                                }
                                busy = false
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Rounded.Archive, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.veyra_ksu_export))
                    }
                }
            }
        }

        if (candidates.isNotEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    ),
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    stringResource(R.string.veyra_ksu_migrate_title),
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                Text(
                                    stringResource(R.string.veyra_ksu_migrate_summary),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Switch(
                                checked = cleanupOldProvider,
                                onCheckedChange = { cleanupOldProvider = it },
                            )
                        }
                        if (cleanupOldProvider) {
                            HorizontalDivider()
                            Text(
                                stringResource(R.string.veyra_ksu_migrate_pick),
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }
                    }
                }
            }
            if (cleanupOldProvider) {
                items(candidates, key = { it.packageName }) { candidate ->
                    val chosen = selected?.packageName == candidate.packageName
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selected = candidate
                                RootMigrationStore.rememberProvider(context, candidate)
                            },
                        colors = CardDefaults.cardColors(
                            containerColor = if (chosen) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerHighest
                            },
                        ),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            RadioButton(
                                selected = chosen,
                                onClick = {
                                    selected = candidate
                                    RootMigrationStore.rememberProvider(context, candidate)
                                },
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    candidate.label,
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                Text(
                                    candidate.packageName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }

        if (selected != null) {
            item {
                Button(
                    enabled = !busy,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = androidx.compose.ui.graphics.Color(0xFFA855F7),
                        contentColor = androidx.compose.ui.graphics.Color.White,
                    ),
                    onClick = {
                        val target = selected ?: return@Button
                        busy = true
                        result = null
                        scope.launch {
                            val rootReady = withContext(Dispatchers.IO) { SuShell.isRoot() }
                            if (!rootReady) {
                                result = context.getString(R.string.veyra_ksu_provider_change_needs_root)
                                busy = false
                                return@launch
                            }

                            if (!state.installed) {
                                val artifact = withContext(Dispatchers.IO) {
                                    runCatching { VeyraKsuModule.export(context) }
                                }.getOrElse {
                                    result = it.message ?: context.getString(
                                        R.string.veyra_ksu_install_failed,
                                    )
                                    busy = false
                                    return@launch
                                }
                                val install = withContext(Dispatchers.IO) {
                                    VeyraKsuModule.installWithKernelSu(artifact.path)
                                }
                                if (install == null || install.exitCode != 0) {
                                    result = install?.output?.ifBlank { null }
                                        ?: context.getString(R.string.veyra_ksu_install_failed)
                                    busy = false
                                    return@launch
                                }
                            }

                            val adopted = withContext(Dispatchers.IO) {
                                TempRootAdopter.adopt(context, target, true)
                            }
                            if (adopted !is TempRootAdoptionResult.Success) {
                                result = (adopted as? TempRootAdoptionResult.Refused)?.detail
                                    ?: context.getString(R.string.veyra_ksu_action_failed)
                                busy = false
                                return@launch
                            }

                            val cleared = withContext(Dispatchers.IO) {
                                TempRootAdopter.clearSourceData(target.packageName)
                            }
                            RootMigrationStore.markCurrentProvider(context, context.packageName)

                            val reload = runRecoveryAction(context, RecoveryTool.ReloadModules)
                            if (!reload.accepted) {
                                result = reload.detail.ifBlank {
                                    context.getString(R.string.veyra_ksu_action_failed)
                                }
                                busy = false
                                return@launch
                            }

                            AppLog.info(
                                AppLogTags.KERNEL_SU,
                                "Provider migration prepared from ${target.packageName}; " +
                                    "old app data cleared=$cleared; restarting Zygote",
                            )
                            result = context.getString(
                                if (cleared) {
                                    R.string.veyra_ksu_provider_change_ready
                                } else {
                                    R.string.veyra_ksu_provider_change_ready_no_clear
                                },
                            )

                            // Reload the KernelSU module lifecycle first, then recreate Android's
                            // runtime. The provider marker above is already persisted, so it survives
                            // the framework restart even if this process disappears immediately.
                            val zygote = runRecoveryAction(context, RecoveryTool.RestartZygote)
                            if (!zygote.accepted) {
                                result = zygote.detail.ifBlank {
                                    context.getString(R.string.veyra_ksu_action_failed)
                                }
                            }
                            busy = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Rounded.Security, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.veyra_ksu_provider_change))
                }
            }
        }

        if (state.installed) {
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    ),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.veyra_ksu_load_toggle),
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                stringResource(R.string.veyra_ksu_load_toggle_summary),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = state.enabled,
                            enabled = !busy,
                            onCheckedChange = { enabled ->
                                busy = true
                                scope.launch {
                                    val changed = withContext(Dispatchers.IO) {
                                        VeyraKsuBridge.setEnabled(enabled)
                                    }
                                    result = context.getString(
                                        if (changed) R.string.veyra_ksu_state_changed
                                        else R.string.veyra_ksu_action_failed,
                                    )
                                    refresh()
                                    busy = false
                                }
                            },
                        )
                    }
                }
            }
        }

        item {
            FilledTonalButton(
                enabled = !busy && state.installed,
                onClick = { runRecovery(RecoveryTool.ReloadModules) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Rounded.Refresh, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.veyra_ksu_reload_modules))
            }
        }
        item {
            TextButton(
                enabled = !busy && state.installed,
                onClick = { runRecovery(RecoveryTool.SoftReboot) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.veyra_ksu_soft_reboot))
            }
        }

        if (busy) {
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp))
                    Text(stringResource(R.string.veyra_ksu_working))
                }
            }
        }

        result?.let { message ->
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(
                            Icons.Rounded.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Text(message, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun VeyraKsuReading(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}
