package ctrl.mietze.veyraroot

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

internal data class CVeyraManagedApp(
    val packageName: String,
    val label: String,
    val system: Boolean,
)

private fun readCVeyraApps(context: Context): List<CVeyraManagedApp> {
    val pm = context.packageManager
    @Suppress("DEPRECATION")
    val source = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        pm.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(0))
    } else {
        pm.getInstalledApplications(0)
    }
    return source.asSequence()
        .filter { it.packageName != context.packageName }
        .map { info ->
            CVeyraManagedApp(
                packageName = info.packageName,
                label = runCatching { pm.getApplicationLabel(info).toString() }
                    .getOrDefault(info.packageName)
                    .ifBlank { info.packageName },
                system = info.flags and (
                    ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP
                ) != 0,
            )
        }
        .distinctBy(CVeyraManagedApp::packageName)
        .sortedWith(
            compareBy<CVeyraManagedApp> { it.label.lowercase(Locale.getDefault()) }
                .thenBy(CVeyraManagedApp::packageName),
        )
        .toList()
}

@Composable
internal fun CVeyraAccessSetupPage(
    padding: PaddingValues,
    onBack: () -> Unit,
    onOpenInfo: () -> Unit,
    onActivated: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf(CVeyraAccessStore.status(context)) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var showReadInfoDialog by remember { mutableStateOf(false) }
    var showInstallDialog by remember { mutableStateOf(false) }

    suspend fun refresh() {
        status = withContext(Dispatchers.IO) { CVeyraAccessStore.status(context) }
    }

    LaunchedEffect(Unit) { refresh() }

    if (showReadInfoDialog) {
        AlertDialog(
            onDismissRequest = { showReadInfoDialog = false },
            icon = { Icon(Icons.Rounded.Info, contentDescription = null) },
            title = { Text("Read CVeyra Provider first") },
            text = {
                Text(
                    "CVeyra Access changes how privileged app actions are routed. " +
                        "Read the provider information once before activation can continue.",
                )
            },
            confirmButton = {
                FilledTonalButton(
                    onClick = {
                        showReadInfoDialog = false
                        onOpenInfo()
                    },
                ) { Text("Open information") }
            },
            dismissButton = {
                TextButton(onClick = { showReadInfoDialog = false }) { Text("Cancel") }
            },
        )
    }

    if (showInstallDialog) {
        AlertDialog(
            onDismissRequest = { if (!busy) showInstallDialog = false },
            icon = {
                Image(
                    painter = painterResource(R.drawable.ic_veyra_mark),
                    contentDescription = null,
                    modifier = Modifier.size(30.dp),
                )
            },
            title = { Text("VeyraKSU 2.0.0 required") },
            text = {
                Text(
                    "CVeyra Permission Access needs the VeyraKSU 2.0.0 bridge. " +
                        "Install saves a copy to Downloads, installs it through KernelSU, " +
                        "then requests a KernelSU soft reboot.",
                )
            },
            confirmButton = {
                FilledTonalButton(
                    enabled = !busy,
                    onClick = {
                        busy = true
                        message = null
                        scope.launch {
                            val outcome = withContext(Dispatchers.IO) {
                                runCatching {
                                    check(SuShell.isRoot()) {
                                        "Grant Veyra Superuser access in KernelSU first."
                                    }
                                    val artifact = VeyraKsuModule.export(context)
                                    val install = VeyraKsuModule.installWithKernelSu(artifact.path)
                                    check(install != null && install.exitCode == 0) {
                                        install?.output?.ifBlank { null }
                                            ?: "KernelSU did not accept the VeyraKSU module."
                                    }
                                    CVeyraAccessStore.markModuleInstalled(context)
                                    artifact.path
                                }
                            }
                            outcome.onSuccess { path ->
                                message = "VeyraKSU 2.0.0 installed. Backup ZIP: $path"
                                showInstallDialog = false
                                refresh()
                                val reboot = runRecoveryAction(context, RecoveryTool.SoftReboot)
                                if (!reboot.accepted) {
                                    message = message + "\nSoft reboot was not accepted: " + reboot.detail
                                }
                            }.onFailure { error ->
                                message = error.message ?: error.javaClass.simpleName
                            }
                            busy = false
                        }
                    },
                ) {
                    if (busy) CircularProgressIndicator(modifier = Modifier.size(18.dp))
                    else Text("Install")
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !busy,
                    onClick = { showInstallDialog = false },
                ) { Text("Cancel") }
            },
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 20.dp,
            end = 20.dp,
            top = padding.calculateTopPadding() + 18.dp,
            bottom = padding.calculateBottomPadding() + 28.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("Grant CVeyra Access", style = MaterialTheme.typography.headlineMedium)
                    Text(
                        "CVeyra Permission Provider 2.0.0",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Image(
                    painter = painterResource(R.drawable.ic_veyra_mark),
                    contentDescription = null,
                    modifier = Modifier.size(34.dp),
                )
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                ),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "CVeyra Permission Activate",
                            style = MaterialTheme.typography.titleLarge,
                        )
                        Text(
                            when (status.phase) {
                                CVeyraAccessPhase.InfoRequired ->
                                    "Read the provider information before activation."
                                CVeyraAccessPhase.VeyraKsuRequired ->
                                    "VeyraKSU 2.0.0 must be installed through KernelSU."
                                CVeyraAccessPhase.RebootRequired ->
                                    "VeyraKSU is staged. A KernelSU soft reboot is required."
                                CVeyraAccessPhase.ReadyToAccept ->
                                    "VeyraKSU is live. Accept to enforce the full permission manager."
                                CVeyraAccessPhase.Active ->
                                    "CVeyra Access is active and enforced through the root broker."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 5.dp),
                        )
                    }
                    Switch(
                        checked = status.active,
                        enabled = !busy,
                        onCheckedChange = { requested ->
                            if (!requested) return@Switch
                            when (status.phase) {
                                CVeyraAccessPhase.InfoRequired -> showReadInfoDialog = true
                                CVeyraAccessPhase.VeyraKsuRequired -> showInstallDialog = true
                                CVeyraAccessPhase.RebootRequired -> {
                                    busy = true
                                    scope.launch {
                                        val result = runRecoveryAction(context, RecoveryTool.SoftReboot)
                                        message = result.detail
                                        busy = false
                                        refresh()
                                    }
                                }
                                CVeyraAccessPhase.ReadyToAccept -> {
                                    busy = true
                                    scope.launch {
                                        val accepted = withContext(Dispatchers.IO) {
                                            CVeyraAccessStore.accept(context)
                                        }
                                        accepted.onSuccess {
                                            status = it
                                            message =
                                                "CVeyra Access 2.0.0 active. KernelSU grants were backed up first."
                                            onActivated()
                                        }.onFailure {
                                            message = it.message ?: it.javaClass.simpleName
                                        }
                                        busy = false
                                    }
                                }
                                CVeyraAccessPhase.Active -> Unit
                            }
                        },
                    )
                }
            }
        }

        item {
            Card(
                onClick = onOpenInfo,
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
                        Icons.Rounded.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text("CVeyra Provider", style = MaterialTheme.typography.titleMedium)
                        Text(
                            if (CVeyraAccessStore.infoRead(context)) {
                                "Provider information read · tap to review"
                            } else {
                                "Required reading before CVeyra Permission Access can be activated"
                            },
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        if (busy) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp))
                    Text("Applying CVeyra Access…")
                }
            }
        }

        message?.let { text ->
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    ),
                ) {
                    Text(
                        text,
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
internal fun CVeyraPermissionManagerPage(
    padding: PaddingValues,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    var apps by remember { mutableStateOf<List<CVeyraManagedApp>>(emptyList()) }
    var directRoot by remember { mutableStateOf<Set<String>>(emptySet()) }
    val storedMode = VsprStore.permissionMode(context)
    var mode by remember {
        mutableStateOf(
            if (storedMode == VsprPermissionMode.Auto) VsprPermissionMode.Self else storedMode,
        )
    }
    var lastManualMode by remember {
        mutableStateOf(
            if (storedMode == VsprPermissionMode.Offline) {
                VsprPermissionMode.Offline
            } else {
                VsprPermissionMode.Self
            },
        )
    }
    var selfApps by remember { mutableStateOf(VsprStore.selfApps(context)) }
    var query by rememberSaveable { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        if (VsprStore.permissionMode(context) == VsprPermissionMode.Auto) {
            VsprStore.setPermissionMode(context, mode)
        }
        loading = true
        val loaded = withContext(Dispatchers.IO) {
            val list = readCVeyraApps(context)
            val direct = KernelSuDirectGrantProbe.grantedPackages(list.map { it.packageName })
            list to direct
        }
        apps = loaded.first
        directRoot = loaded.second
        loading = false
    }

    val visible = remember(apps, query) {
        val needle = query.trim().lowercase(Locale.getDefault())
        if (needle.isEmpty()) apps
        else apps.filter {
            it.label.lowercase(Locale.getDefault()).contains(needle) ||
                it.packageName.lowercase(Locale.ROOT).contains(needle)
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 20.dp,
            end = 20.dp,
            top = padding.calculateTopPadding() + 16.dp,
            bottom = padding.calculateBottomPadding() + 30.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null)
                }
                Text(
                    "Permission Management",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.headlineMedium,
                )
                Image(
                    painter = painterResource(R.drawable.ic_veyra_mark),
                    contentDescription = null,
                    modifier = Modifier.size(30.dp),
                )
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(VsprPermissionMode.Offline, VsprPermissionMode.Self, VsprPermissionMode.Auto)
                    .forEach { candidate ->
                        FilterChip(
                            modifier = Modifier.weight(1f),
                            selected = mode == candidate,
                            onClick = {
                                if (candidate == VsprPermissionMode.Auto) {
                                    Toast.makeText(context, "Soon, stay hyped", Toast.LENGTH_SHORT).show()
                                    mode = lastManualMode
                                    VsprStore.setPermissionMode(context, lastManualMode)
                                } else {
                                    lastManualMode = candidate
                                    mode = candidate
                                    VsprStore.setPermissionMode(context, candidate)
                                }
                            },
                            label = {
                                Text(
                                    when (candidate) {
                                        VsprPermissionMode.Offline -> "Offline"
                                        VsprPermissionMode.Self -> "Self"
                                        VsprPermissionMode.Auto -> "Auto"
                                    },
                                )
                            },
                        )
                    }
            }
        }

        item {
            Text(
                when (mode) {
                    VsprPermissionMode.Offline ->
                        "No app is managed. Existing rules stay stored and can be resumed later."
                    VsprPermissionMode.Self ->
                        "Choose the apps CVeyra Permission Provider manages. Detailed privileges stay on the Grant CVeyra Access page."
                    VsprPermissionMode.Auto ->
                        "Soon, stay hyped"
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (directRoot.isNotEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    ),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(Icons.Rounded.Info, contentDescription = null)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "KernelSU-managed permissions",
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                "${directRoot.size} app(s) still have direct KernelSU Superuser grants. " +
                                    "Those grants were backed up and stay KernelSU-managed, so they cannot be added " +
                                    "to CVeyra Self mode yet. Revoke or migrate the direct grant first, then add the app here.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.82f),
                            )
                        }
                    }
                }
            }
        }

        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                placeholder = { Text("Search apps") },
                shape = RoundedCornerShape(28.dp),
            )
        }

        if (loading) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                    horizontalArrangement = Arrangement.Center,
                ) { CircularProgressIndicator() }
            }
        } else {
            items(visible, key = { it.packageName }) { app ->
                val kernelSuManaged = app.packageName in directRoot
                val selected = app.packageName in selfApps
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    ),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(app.label, style = MaterialTheme.typography.titleSmall)
                            Text(
                                app.packageName +
                                    when {
                                        kernelSuManaged -> " · KernelSU managed"
                                        app.system -> " · System"
                                        else -> ""
                                    },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = selected,
                            enabled = mode == VsprPermissionMode.Self && !kernelSuManaged,
                            onCheckedChange = { enabled ->
                                if (enabled) VsprStore.addSelfApp(context, app.packageName)
                                else VsprStore.removeSelfApp(context, app.packageName)
                                selfApps = VsprStore.selfApps(context)
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun CVeyraManagementPage(
    padding: PaddingValues,
    onBack: () -> Unit,
    onOpenPermissionManager: () -> Unit,
    onOpenVeyraKsu: () -> Unit,
    onOpenMigration: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf(CVeyraAccessStore.status(context)) }
    var backend by remember { mutableStateOf(CVeyraPreferences.backend(context)) }
    var busyModule by remember { mutableStateOf<CVeyraPrivacyModule?>(null) }
    var moduleMessage by remember { mutableStateOf<String?>(null) }
    var apps by remember { mutableStateOf<List<CVeyraManagedApp>>(emptyList()) }
    var blocked by remember { mutableStateOf(CVeyraFirewall.blockedPackages(context)) }
    var firewallQuery by rememberSaveable { mutableStateOf("") }
    var firewallBusy by remember { mutableStateOf<String?>(null) }

    suspend fun refresh() {
        val next = withContext(Dispatchers.IO) {
            Triple(
                CVeyraAccessStore.status(context),
                CVeyraController.probeBackend(context),
                readCVeyraApps(context),
            )
        }
        status = next.first
        backend = next.second
        apps = next.third
        blocked = CVeyraFirewall.blockedPackages(context)
    }

    LaunchedEffect(Unit) { refresh() }

    val firewallApps = remember(apps, firewallQuery) {
        val q = firewallQuery.trim().lowercase(Locale.getDefault())
        val base = apps.filterNot { it.system }
        if (q.isEmpty()) base else base.filter {
            it.label.lowercase(Locale.getDefault()).contains(q) ||
                it.packageName.lowercase(Locale.ROOT).contains(q)
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 20.dp,
            end = 20.dp,
            top = padding.calculateTopPadding() + 16.dp,
            bottom = padding.calculateBottomPadding() + 30.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("CVeyra Management", style = MaterialTheme.typography.headlineMedium)
                    Text(
                        "Permission Provider 2.0.0",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Image(
                    painter = painterResource(R.drawable.ic_veyra_mark),
                    contentDescription = null,
                    modifier = Modifier.size(36.dp),
                )
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                ),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(9.dp),
                    ) {
                        Icon(Icons.Rounded.CheckCircle, contentDescription = null)
                        Text("CVeyra Access enforced", style = MaterialTheme.typography.titleMedium)
                    }
                    Text("Backend: ${backend.storedValue}")
                    Text(
                        "VeyraKSU: ${status.veyraKsu.moduleVersion.ifBlank { "unknown" }} · " +
                            status.veyraKsu.permissionBridge,
                    )
                    if (status.legacyKernelSuGrants > 0) {
                        Text(
                            "${status.legacyKernelSuGrants} older KernelSU app grant(s) were backed up. " +
                                "They remain KernelSU-managed until you add those apps here.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    if (status.backupPath.isNotBlank()) {
                        Text(
                            "Backup: ${status.backupPath}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f),
                        )
                    }
                }
            }
        }

        item {
            FilledTonalButton(
                onClick = onOpenPermissionManager,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Rounded.Apps, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Open Permission Management")
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                FilledTonalButton(
                    onClick = onOpenVeyraKsu,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Rounded.Settings, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("VeyraKSU")
                }
                FilledTonalButton(
                    onClick = onOpenMigration,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Rounded.Refresh, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Root migration")
                }
            }
        }

        item {
            Text("App Module Loader", style = MaterialTheme.typography.titleLarge)
            Text(
                "Veyra modules change the real device setting while enabled and restore the exact previous value when disabled.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        items(CVeyraPrivacyModule.entries, key = { it.id }) { module ->
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                ),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(15.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        when (module) {
                            CVeyraPrivacyModule.Accessibility -> Icons.Rounded.Security
                            CVeyraPrivacyModule.DeveloperOptions -> Icons.Rounded.Settings
                            CVeyraPrivacyModule.UsbDebugging -> Icons.Rounded.CloudOff
                            CVeyraPrivacyModule.PrivateDns -> Icons.Rounded.Security
                        },
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(module.title, style = MaterialTheme.typography.titleMedium)
                        Text(
                            module.summary,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (busyModule == module) {
                        CircularProgressIndicator(modifier = Modifier.size(22.dp))
                    } else {
                        Switch(
                            checked = CVeyraPrivacyModules.enabled(context, module),
                            onCheckedChange = { enabled ->
                                busyModule = module
                                moduleMessage = null
                                scope.launch {
                                    val result = withContext(Dispatchers.IO) {
                                        CVeyraPrivacyModules.setEnabled(context, module, enabled)
                                    }
                                    result.onFailure {
                                        moduleMessage = it.message ?: it.javaClass.simpleName
                                    }
                                    busyModule = null
                                }
                            },
                        )
                    }
                }
            }
        }

        moduleMessage?.let { message ->
            item {
                Text(
                    message,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        }

        item {
            HorizontalDivider()
            Spacer(Modifier.height(2.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(Icons.Rounded.Security, contentDescription = null)
                Column {
                    Text("CVeyra Firewall", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "Root-persistent IPv4/IPv6 UID blocking plus Android's package networking gate. " +
                            "${blocked.size} app(s) blocked.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        item {
            OutlinedTextField(
                value = firewallQuery,
                onValueChange = { firewallQuery = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                placeholder = { Text("Search firewall apps") },
                shape = RoundedCornerShape(28.dp),
            )
        }

        items(firewallApps, key = { "fw:" + it.packageName }) { app ->
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                ),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(app.label, style = MaterialTheme.typography.titleSmall)
                        Text(
                            app.packageName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (firewallBusy == app.packageName) {
                        CircularProgressIndicator(modifier = Modifier.size(22.dp))
                    } else {
                        Switch(
                            checked = app.packageName in blocked,
                            onCheckedChange = { shouldBlock ->
                                firewallBusy = app.packageName
                                scope.launch {
                                    val result = withContext(Dispatchers.IO) {
                                        CVeyraFirewall.setBlocked(
                                            context,
                                            app.packageName,
                                            shouldBlock,
                                        )
                                    }
                                    result.onSuccess {
                                        blocked = CVeyraFirewall.blockedPackages(context)
                                    }.onFailure {
                                        Toast.makeText(
                                            context,
                                            it.message ?: it.javaClass.simpleName,
                                            Toast.LENGTH_LONG,
                                        ).show()
                                    }
                                    firewallBusy = null
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}
