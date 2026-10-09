package ctrl.mietze.veyraroot

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kernelpack.ota.OtaPayloadExtractor
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun VeyraBuilderPage(
    padding: PaddingValues,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenLoadingBuilder: () -> Unit,
    onOpenMagicBuilder: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sharedBuilder: PayloadBuilderViewModel = viewModel()
    var session by remember { mutableStateOf(VeyraBuilderSessionStore.load(context)) }
    var otaUrl by remember(session.id) { mutableStateOf(session.otaUrl) }
    var otaBusy by remember { mutableStateOf(false) }
    var otaLog by remember { mutableStateOf<List<String>>(emptyList()) }
    var scripts by remember { mutableStateOf<VeyraTermuxExport?>(null) }
    var busyMessage by remember { mutableStateOf<String?>(null) }

    fun persist(next: VeyraBuilderSession) {
        session = VeyraBuilderSessionStore.save(context, next)
    }
    fun move(step: VeyraBuilderStep) = persist(session.copy(step = step))

    val bootPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            busyMessage = "Reading local boot image…"
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    runCatching {
                        context.contentResolver.takePersistableUriPermission(
                            uri,
                            android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
                        )
                    }
                    val bytes = VeyraBuilderFiles.readUri(context, uri)
                    Triple(displayName(context, uri), bytes.size.toLong(), VeyraBuilderFiles.sha256(bytes))
                }
            }
            busyMessage = null
            result.onSuccess { triple ->
                val name = triple.first
                val size = triple.second
                val sha = triple.third
                val match = session.otaBootSha256.takeIf(String::isNotBlank)
                    ?.equals(sha, ignoreCase = true)
                sharedBuilder.rememberBootImage(uri, name, size)
                persist(
                    session.copy(
                        localBootUri = uri.toString(),
                        localBootName = name,
                        localBootSha256 = sha,
                        localBootMatchesOta = match,
                        lastMessage = when (match) {
                            true -> "Local boot image is byte-identical to the OTA boot image."
                            false -> "Local boot image differs from the OTA boot image. Veyra will not merge them silently."
                            null -> "Local boot image recorded; no OTA boot hash is available for comparison."
                        },
                    ),
                )
            }.onFailure { error ->
                persist(session.copy(lastMessage = error.message ?: "Could not read local boot image"))
            }
        }
    }

    val xblPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            busyMessage = "Reading xbl_config image…"
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val bytes = VeyraBuilderFiles.readUri(context, uri, 256L * 1024L * 1024L)
                    displayName(context, uri) to VeyraBuilderFiles.sha256(bytes)
                }
            }
            busyMessage = null
            result.onSuccess { pair ->
                persist(
                    session.copy(
                        localXblUri = uri.toString(),
                        localXblName = pair.first,
                        localXblSha256 = pair.second,
                    ),
                )
            }.onFailure { error ->
                persist(session.copy(lastMessage = error.message ?: "Could not read xbl_config image"))
            }
        }
    }

    val reportPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            busyMessage = "Importing Veyra report…"
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val raw = VeyraBuilderFiles.readUri(context, uri, 8L * 1024L * 1024L)
                        .toString(Charsets.UTF_8)
                    VeyraBuilderReportParser.parse(displayName(context, uri), raw)
                }
            }
            busyMessage = null
            result.onSuccess { report ->
                val live = DeviceSnapshot.current().kernelRelease
                val target = session.otaKernelRelease.ifBlank { live }
                val mismatch = report.kernel.isNotBlank() &&
                    target.isNotBlank() &&
                    !report.kernel.equals(target, ignoreCase = true)
                persist(
                    session.copy(
                        reportName = report.name,
                        reportCreatedUtc = report.createdUtc,
                        reportKernel = report.kernel,
                        evidenceSuccessful = report.successes,
                        evidenceIndependentGroups = report.independentGroups,
                        evidenceConflicts = report.conflicts,
                        lastMessage = if (mismatch) {
                            "Imported report belongs to kernel " + report.kernel +
                                ", while this session targets " + target +
                                ". It remains visible but is not treated as an exact match."
                        } else {
                            "Imported " + report.observations + " observations from " +
                                report.independentGroups + " independent groups."
                        },
                    ),
                )
            }.onFailure { error ->
                persist(session.copy(lastMessage = error.message ?: "Could not import report"))
            }
        }
    }

    val progress = (session.step.ordinal + 1).toFloat() / VeyraBuilderStep.entries.size.toFloat()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 20.dp,
            end = 20.dp,
            top = padding.calculateTopPadding() + 18.dp,
            bottom = padding.calculateBottomPadding() + 34.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("Veyra Builder", style = MaterialTheme.typography.headlineMedium)
                    Text(
                        (session.step.ordinal + 1).toString() + "/" +
                            VeyraBuilderStep.entries.size + " · " + session.step.label,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onOpenSettings) {
                    Icon(Icons.Rounded.Settings, contentDescription = "Veyra Builder Settings")
                }
            }
            Spacer(Modifier.size(6.dp))
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
        }

        busyMessage?.let { message ->
            item {
                Card {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(22.dp))
                        Text(message)
                    }
                }
            }
        }

        session.lastMessage.takeIf(String::isNotBlank)?.let { message ->
            item {
                val alert = message.contains("differs", true) ||
                    message.contains("belongs to kernel", true) ||
                    message.contains("failed", true)
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (alert) {
                            MaterialTheme.colorScheme.errorContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHighest
                        },
                    ),
                ) {
                    Text(message, modifier = Modifier.fillMaxWidth().padding(14.dp))
                }
            }
        }

        when (session.step) {
            VeyraBuilderStep.Welcome -> item {
                WizardCard(
                    title = "Guided builder session",
                    body = "OTA identity, local hashes, evidence provenance, Termux reports and route decisions stay in one resumable session. Incomplete evidence never becomes a runnable payload by guesswork.",
                ) {
                    Button(
                        onClick = {
                            val next = if (
                                session.otaUrl.isBlank() &&
                                session.otaBootSha256.isBlank() &&
                                session.reportName.isBlank()
                            ) {
                                VeyraBuilderSessionStore.newSession(context)
                            } else {
                                session
                            }
                            session = VeyraBuilderSessionStore.save(
                                context,
                                next.copy(step = VeyraBuilderStep.Ota),
                            )
                            otaUrl = session.otaUrl
                        },
                    ) {
                        Text(
                            if (
                                session.otaUrl.isBlank() &&
                                session.reportName.isBlank()
                            ) "Start new session" else "Resume session",
                        )
                    }
                }
            }

            VeyraBuilderStep.Ota -> item {
                WizardCard(
                    title = "OTA or full-package input",
                    body = "Paste an HTTPS full OTA. Veyra uses HTTP Range reads and extracts only required boot data. xbl_config is optional in Veyra Builder Settings.",
                ) {
                    OutlinedTextField(
                        value = otaUrl,
                        onValueChange = { otaUrl = it },
                        label = { Text("OTA URL") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    Button(
                        enabled = !otaBusy && otaUrl.startsWith("https://", ignoreCase = true),
                        onClick = {
                            otaBusy = true
                            otaLog = emptyList()
                            persist(session.copy(otaUrl = otaUrl.trim(), lastMessage = ""))
                            scope.launch {
                                val logs = ArrayList<String>()
                                val result = withContext(Dispatchers.IO) {
                                    runCatching {
                                        val dir = File(
                                            context.filesDir,
                                            "veyra-builder/ota/" + session.id,
                                        ).apply { mkdirs() }
                                        OtaPayloadExtractor.extractPartitions(
                                            url = otaUrl.trim(),
                                            workDir = dir,
                                            includeXblConfig = VeyraBuilderPreferences.includeXbl(context),
                                        ) { line ->
                                            logs += line
                                            scope.launch { otaLog = logs.takeLast(12) }
                                        }
                                    }
                                }
                                otaBusy = false
                                result.onSuccess { extracted ->
                                    val boot = extracted.bootFile
                                    val sha = withContext(Dispatchers.IO) {
                                        VeyraBuilderFiles.sha256(boot)
                                    }
                                    val kernel = MagicBuilderController
                                        .kernelReleaseOfBootImage(boot)
                                        .orEmpty()
                                    val xblSha = extracted.xblConfigFile?.let { file ->
                                        withContext(Dispatchers.IO) {
                                            VeyraBuilderFiles.sha256(file)
                                        }
                                    }.orEmpty()
                                    sharedBuilder.rememberBootImageFile(
                                        boot,
                                        boot.name,
                                        boot.length(),
                                    )
                                    persist(
                                        session.copy(
                                            otaUrl = otaUrl.trim(),
                                            otaBootName = boot.name,
                                            otaBootPath = boot.absolutePath,
                                            otaBootSha256 = sha,
                                            otaKernelRelease = kernel,
                                            otaXblName = extracted.xblConfigFile?.name.orEmpty(),
                                            otaXblSha256 = xblSha,
                                            lastMessage = "OTA boot image extracted and hashed.",
                                        ),
                                    )
                                }.onFailure { error ->
                                    persist(
                                        session.copy(
                                            lastMessage = error.message ?: "OTA scan failed",
                                        ),
                                    )
                                }
                            }
                        },
                    ) {
                        if (otaBusy) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp))
                        } else {
                            Icon(Icons.Rounded.Download, contentDescription = null)
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(if (otaBusy) "Scanning…" else "Scan OTA")
                    }
                    if (otaLog.isNotEmpty()) {
                        Text(
                            otaLog.joinToString("\n"),
                            modifier = Modifier.fillMaxWidth().heightIn(max = 190.dp),
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (session.otaBootSha256.isNotBlank()) {
                        FactRow("Boot", session.otaBootName)
                        FactRow("Kernel", session.otaKernelRelease.ifBlank { "banner unresolved" })
                        FactRow("SHA-256", session.otaBootSha256)
                    }
                    TextButton(onClick = { move(VeyraBuilderStep.Discovery) }) {
                        Text(
                            if (session.otaBootSha256.isBlank()) {
                                "Continue offline / without OTA"
                            } else {
                                "Continue"
                            },
                        )
                    }
                }
            }

            VeyraBuilderStep.Discovery -> item {
                val snapshot = DeviceSnapshot.current()
                val target = session.otaKernelRelease.takeIf(String::isNotBlank)
                    ?.let { snapshot.copy(kernelRelease = it) }
                    ?: snapshot
                val report = remember(session.otaKernelRelease) {
                    BuilderStrategyPlanner.evaluate(context, target)
                }
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    WizardCard(
                        title = "We already know",
                        body = "Observed values stay separate from interpretation. A runnable candidate still has its own runtime/evidence gates.",
                    ) {
                        FactRow(
                            "Device",
                            snapshot.manufacturer + " " + snapshot.model +
                                " (" + snapshot.device + ")",
                        )
                        FactRow("Live kernel", snapshot.kernelRelease)
                        FactRow("OTA kernel", session.otaKernelRelease.ifBlank { "not resolved" })
                        FactRow("OTA boot hash", session.otaBootSha256.ifBlank { "not available" })
                    }
                    BuilderStrategyCard(report = report, title = "Discovery Route Matrix")
                    WizardNav(session, ::move)
                }
            }

            VeyraBuilderStep.LocalImages -> item {
                WizardCard(
                    title = "Compare local images",
                    body = "Choose the boot.img you intend to use. OTA and local conflicts remain visible and are never merged silently. xbl_config is optional.",
                ) {
                    FilledTonalButton(
                        onClick = {
                            bootPicker.launch(arrayOf("application/octet-stream", "*/*"))
                        },
                    ) {
                        Icon(Icons.Rounded.UploadFile, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Choose local boot.img")
                    }
                    if (session.localBootSha256.isNotBlank()) {
                        FactRow("Local boot", session.localBootName)
                        FactRow("SHA-256", session.localBootSha256)
                        FactRow(
                            "Comparison",
                            when (session.localBootMatchesOta) {
                                true -> "MATCH · identical bytes"
                                false -> "CONFLICT · different bytes"
                                null -> "No OTA boot to compare"
                            },
                        )
                    }
                    FilledTonalButton(
                        onClick = {
                            xblPicker.launch(arrayOf("application/octet-stream", "*/*"))
                        },
                    ) {
                        Icon(Icons.Rounded.Memory, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Add optional xbl_config.img")
                    }
                    if (session.localXblSha256.isNotBlank()) {
                        FactRow(
                            "xbl_config",
                            session.localXblName + " · " + session.localXblSha256,
                        )
                    }
                    WizardNav(session, ::move)
                }
            }

            VeyraBuilderStep.Termux -> item {
                val termuxInstalled = remember {
                    packageInstalled(context, "com.termux")
                }
                val shizuku = remember { ShizukuController.availability() }
                val adbPaired = AppPreferences.adbPaired(context)
                WizardCard(
                    title = "Termux & permission preflight",
                    body = "Every exported script prepares its own environment first: dependencies, storage, output path, Shizuku/rish and ADB. Unavailable optional routes are reported, not faked.",
                ) {
                    CapabilityRow(
                        "Termux",
                        if (termuxInstalled) "Installed" else "Install/open Termux before running the exported script",
                        termuxInstalled,
                    )
                    CapabilityRow(
                        "Shizuku / rish",
                        shizuku.name,
                        shizuku == ShizukuAvailability.Ready,
                    )
                    CapabilityRow(
                        "ADB pairing",
                        if (adbPaired) "Previously paired" else "Not recorded",
                        adbPaired,
                    )
                    Button(
                        onClick = {
                            val exported = VeyraBuilderTermux.exportAll(context)
                            scripts = exported
                            persist(
                                session.copy(
                                    lastMessage = "Autonomous Termux scripts exported to Downloads.",
                                ),
                            )
                        },
                    ) {
                        Icon(Icons.Rounded.Terminal, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Export Termux scripts")
                    }
                    scripts?.let { exported ->
                        FactRow("Probe", exported.probePath)
                        FilledTonalButton(
                            onClick = {
                                copy(context, exported.commandFor(exported.probePath))
                            },
                        ) {
                            Icon(Icons.Rounded.ContentCopy, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Copy direct Termux command")
                        }
                    }
                    WizardNav(session, ::move)
                }
            }

            VeyraBuilderStep.ReportImport -> item {
                val target = VeyraBuilderPreferences.verificationTarget(context)
                WizardCard(
                    title = "Import Termux report",
                    body = "Only report format veyra.termux.report/v1 is accepted. Independent groups and conflicts are counted explicitly; importing a file never inflates confidence by itself.",
                ) {
                    FilledTonalButton(
                        onClick = {
                            reportPicker.launch(arrayOf("application/json", "text/plain", "*/*"))
                        },
                    ) {
                        Icon(Icons.Rounded.UploadFile, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Import report")
                    }
                    if (session.reportName.isNotBlank()) {
                        FactRow("Report", session.reportName)
                        FactRow("Kernel", session.reportKernel.ifBlank { "not reported" })
                        FactRow("Successful observations", session.evidenceSuccessful.toString())
                        FactRow(
                            "Independent groups",
                            session.evidenceIndependentGroups.toString() + " / target " + target,
                        )
                        FactRow("Conflicting fields", session.evidenceConflicts.toString())
                        CapabilityRow(
                            "Verification target",
                            if (
                                session.evidenceIndependentGroups >= target &&
                                session.evidenceConflicts == 0
                            ) {
                                "Enough independent evidence groups"
                            } else {
                                "More independent evidence or conflict resolution required"
                            },
                            session.evidenceIndependentGroups >= target &&
                                session.evidenceConflicts == 0,
                        )
                    }
                    WizardNav(session, ::move)
                }
            }

            VeyraBuilderStep.Fallbacks -> item {
                WizardCard(
                    title = "Fallback ladder",
                    body = "Fallbacks remain read-only and provenance-aware: extra metadata, user-provided artifacts, then passive monitoring. No fallback invents offsets.",
                ) {
                    Button(
                        onClick = {
                            scripts = VeyraBuilderTermux.exportAll(context)
                            persist(
                                session.copy(
                                    lastMessage = "Probe and all three fallback scripts exported.",
                                ),
                            )
                        },
                    ) {
                        Icon(Icons.Rounded.Download, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Export probe + fallbacks")
                    }
                    scripts?.let { exported ->
                        FactRow("Fallback 1", exported.fallback1Path)
                        FactRow("Fallback 2", exported.fallback2Path)
                        FactRow("Fallback 3", exported.fallback3Path)
                    }
                    WizardNav(session, ::move)
                }
            }

            VeyraBuilderStep.RiskMode -> item {
                WizardCard(
                    title = "Risk mode",
                    body = "This controls review policy only. It never bypasses native format, ABI, kernel or evidence gates.",
                ) {
                    VeyraBuilderRiskMode.entries.forEach { mode ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = session.riskMode == mode,
                                onClick = {
                                    persist(session.copy(riskMode = mode))
                                },
                            )
                            Column {
                                Text(mode.label)
                                Text(
                                    when (mode) {
                                        VeyraBuilderRiskMode.Standard ->
                                            "Only exact/registered routes may become runnable candidates."
                                        VeyraBuilderRiskMode.Research ->
                                            "Analysis output may include unresolved evidence for research."
                                        VeyraBuilderRiskMode.Experimental ->
                                            "Experimental review is visible, but executable safety gates remain enforced."
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    WizardNav(session, ::move)
                }
            }

            VeyraBuilderStep.Review -> item {
                val snapshot = DeviceSnapshot.current()
                val target = session.otaKernelRelease.takeIf(String::isNotBlank)
                    ?.let { snapshot.copy(kernelRelease = it) }
                    ?: snapshot
                val route = remember(session.otaKernelRelease) {
                    BuilderStrategyPlanner.evaluate(context, target)
                }
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    WizardCard(
                        title = "Review candidate",
                        body = "Profile export and native artifact verification are separate. A candidate .conf is not a .so and is not treated as a root result.",
                    ) {
                        FactRow("Session", session.id)
                        FactRow("Risk mode", session.riskMode.label)
                        FactRow("OTA boot", session.otaBootSha256.ifBlank { "not available" })
                        FactRow("Local boot", session.localBootSha256.ifBlank { "not provided" })
                        FactRow(
                            "Boot consistency",
                            session.localBootMatchesOta?.toString() ?: "not compared",
                        )
                        FactRow(
                            "Evidence",
                            session.evidenceSuccessful.toString() + " observations · " +
                                session.evidenceIndependentGroups + " independent groups · " +
                                session.evidenceConflicts + " conflicts",
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilledTonalButton(onClick = onOpenLoadingBuilder) {
                                Icon(Icons.Rounded.Build, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text("Loading Builder")
                            }
                            FilledTonalButton(onClick = onOpenMagicBuilder) {
                                Icon(Icons.Rounded.AutoFixHigh, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text("Magic Builder")
                            }
                        }
                    }
                    BuilderStrategyCard(route, title = "Final Route Matrix")
                    WizardNav(session, ::move)
                }
            }

            VeyraBuilderStep.Export -> item {
                WizardCard(
                    title = "Export result",
                    body = "Exports a candidate .conf, session JSON and readable report to Android Downloads. PROFILE_EXPORTED, NATIVE_ARTIFACT_VERIFIED and DEVICE_TESTED remain separate.",
                ) {
                    Button(
                        onClick = {
                            busyMessage = "Exporting session…"
                            scope.launch {
                                val result = withContext(Dispatchers.IO) {
                                    runCatching {
                                        VeyraBuilderExporter.export(context, session)
                                    }
                                }
                                busyMessage = null
                                result.onSuccess { exported ->
                                    persist(
                                        session.copy(
                                            exportedPaths = exported.paths,
                                            lastMessage = "Candidate/session/report exported.",
                                        ),
                                    )
                                }.onFailure { error ->
                                    persist(
                                        session.copy(
                                            lastMessage = error.message ?: "Export failed",
                                        ),
                                    )
                                }
                            }
                        },
                    ) {
                        Icon(Icons.Rounded.Download, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Export candidate package")
                    }
                    session.exportedPaths.forEachIndexed { index, path ->
                        FactRow("File " + (index + 1), path)
                    }
                    HorizontalDivider()
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { move(session.step.previous()) }) {
                            Text("Back")
                        }
                        FilledTonalButton(
                            onClick = {
                                session = VeyraBuilderSessionStore.newSession(context)
                                otaUrl = ""
                            },
                        ) {
                            Text("New session")
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun VeyraGuidedBuilderSettingsPage(
    padding: PaddingValues,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    var includeXbl by remember {
        mutableStateOf(VeyraBuilderPreferences.includeXbl(context))
    }
    var maxSources by remember {
        mutableStateOf(VeyraBuilderPreferences.maxSources(context))
    }
    var verifyTarget by remember {
        mutableStateOf(VeyraBuilderPreferences.verificationTarget(context))
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 20.dp,
            end = 20.dp,
            top = padding.calculateTopPadding() + 18.dp,
            bottom = padding.calculateBottomPadding() + 32.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                }
                Column {
                    Text(
                        "Veyra Builder Settings",
                        style = MaterialTheme.typography.headlineMedium,
                    )
                    Text(
                        "Independent from Loading and Magic Builder settings",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        item {
            SettingsToggle(
                title = "Extract optional xbl_config",
                description = "Only when the OTA contains it. boot.img remains the primary required image.",
                checked = includeXbl,
            ) {
                includeXbl = it
                VeyraBuilderPreferences.setIncludeXbl(context, it)
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
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Text(
                        "Termux preparation",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        "Always autonomous. Exported scripts check/install missing tools, configure storage, test rish/ADB, and print the actual output path before analysis starts.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        item {
            NumberSettingCard(
                title = "Maximum methods per field",
                description = "Evidence planning can schedule up to this many source methods.",
                value = maxSources,
                min = 4,
                max = 10,
            ) {
                maxSources = it
                VeyraBuilderPreferences.setMaxSources(context, it)
            }
        }
        item {
            NumberSettingCard(
                title = "Independent verification target",
                description = "Independent evidence groups required before a field can be considered verified.",
                value = verifyTarget,
                min = 2,
                max = 6,
            ) {
                verifyTarget = it
                VeyraBuilderPreferences.setVerificationTarget(context, it)
            }
        }
    }
}

@Composable
private fun WizardCard(
    title: String,
    body: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        ),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            content()
        }
    }
}

@Composable
private fun WizardNav(
    session: VeyraBuilderSession,
    move: (VeyraBuilderStep) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        TextButton(
            onClick = { move(session.step.previous()) },
            enabled = session.step != VeyraBuilderStep.Welcome,
        ) {
            Text("Back")
        }
        Button(
            onClick = { move(session.step.next()) },
            enabled = session.step != VeyraBuilderStep.Export,
        ) {
            Text("Continue")
        }
    }
}

@Composable
private fun FactRow(label: String, value: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
        )
    }
}

@Composable
private fun CapabilityRow(
    label: String,
    value: String,
    good: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (good) Icons.Rounded.CheckCircle else Icons.Rounded.Warning,
            contentDescription = null,
            tint = if (good) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.tertiary
            },
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(label)
            Text(
                value,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SettingsToggle(
    title: String,
    description: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
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
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = checked, onCheckedChange = onChange)
        }
    }
}

@Composable
private fun NumberSettingCard(
    title: String,
    description: String,
    value: Int,
    min: Int,
    max: Int,
    onChange: (Int) -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        ),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                FilledTonalButton(
                    onClick = { onChange((value - 1).coerceAtLeast(min)) },
                    enabled = value > min,
                ) {
                    Text("−")
                }
                Text(value.toString(), style = MaterialTheme.typography.titleMedium)
                FilledTonalButton(
                    onClick = { onChange((value + 1).coerceAtMost(max)) },
                    enabled = value < max,
                ) {
                    Text("+")
                }
            }
        }
    }
}

private fun displayName(context: Context, uri: Uri): String =
    context.contentResolver.query(
        uri,
        arrayOf(OpenableColumns.DISPLAY_NAME),
        null,
        null,
        null,
    )?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getString(0) else null
    } ?: uri.lastPathSegment ?: "file"

private fun copy(context: Context, text: String) {
    val clipboard = context.getSystemService(ClipboardManager::class.java)
    clipboard?.setPrimaryClip(ClipData.newPlainText("Veyra Builder", text))
}

@Suppress("DEPRECATION")
private fun packageInstalled(context: Context, packageName: String): Boolean =
    runCatching { context.packageManager.getPackageInfo(packageName, 0) }.isSuccess
