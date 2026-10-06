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
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
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

private enum class MigrationMethod {
    FullRestart,
    Soft,
}

@Composable
internal fun RootMigrationPage(
    padding: PaddingValues,
    onBack: () -> Unit,
    onOpenVeyraKsu: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var method by remember { mutableStateOf(MigrationMethod.FullRestart) }
    var candidates by remember { mutableStateOf<List<RmgCandidate>>(emptyList()) }
    var selected by remember { mutableStateOf<RmgCandidate?>(null) }
    var rootVerified by remember {
        mutableStateOf(RootMigrationStore.rootVerifiedForCurrentBoot(context))
    }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var currentPlan by remember { mutableStateOf(RootMigrationStore.plan(context)) }

    LaunchedEffect(Unit) {
        candidates = withContext(Dispatchers.IO) { RmgCandidateScanner.scan(context) }
        val remembered = RootMigrationStore.selectedProviderPackage(context)
        selected = candidates.firstOrNull { it.packageName == remembered }
            ?: candidates.firstOrNull()
        selected?.let { RootMigrationStore.rememberProvider(context, it) }
        currentPlan = RootMigrationStore.plan(context)
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
                        stringResource(R.string.migration_title),
                        style = MaterialTheme.typography.headlineLarge,
                    )
                    Text(
                        stringResource(R.string.migration_subtitle),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        item {
            MigrationMethodCard(
                selected = method == MigrationMethod.FullRestart,
                icon = Icons.Rounded.RestartAlt,
                title = stringResource(R.string.migration_full_title),
                summary = stringResource(R.string.migration_full_summary),
                onClick = { method = MigrationMethod.FullRestart },
            )
        }
        item {
            MigrationMethodCard(
                selected = method == MigrationMethod.Soft,
                icon = Icons.Rounded.Refresh,
                title = stringResource(R.string.migration_soft_title),
                summary = stringResource(R.string.migration_soft_summary),
                onClick = { method = MigrationMethod.Soft },
            )
        }
        if (method == MigrationMethod.Soft) {
            item {
                Button(
                    onClick = onOpenVeyraKsu,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Rounded.Refresh, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.migration_open_veyra_ksu))
                }
            }
        } else {
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
                            stringResource(R.string.migration_root_check_title),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            stringResource(R.string.migration_root_check_summary),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        FilledTonalButton(
                            enabled = !busy,
                            onClick = {
                                busy = true
                                message = null
                                scope.launch {
                                    rootVerified = withContext(Dispatchers.IO) {
                                        RootMigrationStore.verifyRootNow(context)
                                    }
                                    message = context.getString(
                                        if (rootVerified) {
                                            R.string.migration_root_check_ok
                                        } else {
                                            R.string.migration_root_check_failed
                                        },
                                    )
                                    busy = false
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Rounded.Security, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                stringResource(
                                    if (rootVerified) {
                                        R.string.migration_root_checked
                                    } else {
                                        R.string.migration_root_check
                                    },
                                ),
                            )
                        }
                    }
                }
            }
            if (rootVerified) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            stringResource(R.string.migration_provider_title),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f),
                        )
                        FilledTonalButton(
                            enabled = !busy,
                            onClick = {
                                busy = true
                                scope.launch {
                                    val previous = selected?.packageName
                                        ?: RootMigrationStore.selectedProviderPackage(context)
                                    val refreshed = withContext(Dispatchers.IO) {
                                        RmgCandidateScanner.scan(context)
                                    }
                                    candidates = refreshed
                                    selected = refreshed.firstOrNull { it.packageName == previous }
                                        ?: refreshed.firstOrNull()
                                    selected?.let { RootMigrationStore.rememberProvider(context, it) }
                                    message = if (refreshed.isEmpty()) {
                                        context.getString(R.string.migration_no_provider)
                                    } else {
                                        "Providers refreshed · " + refreshed.size
                                    }
                                    busy = false
                                }
                            },
                        ) {
                            Icon(Icons.Rounded.Refresh, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Refresh")
                        }
                    }
                }
                if (candidates.isEmpty()) {
                    item {
                        Text(
                            stringResource(R.string.migration_no_provider),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
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
                item {
                    Button(
                        enabled = !busy && selected != null,
                        onClick = {
                            val target = selected ?: return@Button
                            busy = true
                            message = null
                            scope.launch {
                                val prepared = withContext(Dispatchers.IO) {
                                    RootMigrationStore.prepareFullRestart(context, target)
                                }
                                prepared.onSuccess {
                                    currentPlan = it
                                    message = context.getString(
                                        R.string.migration_prepared,
                                        it.providerLabel ?: target.label,
                                    )
                                }.onFailure {
                                    message = it.message
                                        ?: context.getString(R.string.migration_prepare_failed)
                                }
                                busy = false
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Rounded.CheckCircle, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.migration_check_patch))
                    }
                }
            }
        }

        if (currentPlan.prepared) {
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                    ),
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            stringResource(R.string.migration_ready_title),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            stringResource(R.string.migration_ready_body),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }
        if (busy) {
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp))
                    Text(stringResource(R.string.migration_working))
                }
            }
        }

        message?.let {
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ),
                ) {
                    Text(
                        it,
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun MigrationMethodCard(
    selected: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    summary: String,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerHighest
            },
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(icon, contentDescription = null)
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            RadioButton(selected = selected, onClick = onClick)
        }
    }
}
