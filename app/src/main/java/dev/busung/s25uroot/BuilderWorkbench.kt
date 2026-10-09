package ctrl.mietze.veyraroot

import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Construction
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@Composable
internal fun BuilderWorkbenchPage(
    padding: PaddingValues,
    onBack: () -> Unit,
    onOpenVeyraBuilder: () -> Unit,
    onOpenLoadingBuilder: () -> Unit,
    onOpenMagicBuilder: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val session = remember { VeyraBuilderSessionStore.load(context) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 20.dp,
            end = 20.dp,
            top = padding.calculateTopPadding() + 20.dp,
            bottom = padding.calculateBottomPadding() + 32.dp,
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
                    Text("Builder Workingbench", style = MaterialTheme.typography.headlineMedium)
                    Text(
                        "Three builders · one workspace",
                        style = MaterialTheme.typography.bodySmall,
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
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Text("Choose the right route", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Veyra Builder guides evidence and provenance. Loading Builder patches an imported boot image. Magic Builder automates exact-device and OEM-aware discovery.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        item {
            WorkbenchBuilderCard(
                icon = Icons.Rounded.Construction,
                title = "Veyra Builder",
                subtitle = "Guided OTA → evidence → Termux → review → export",
                status = if (session.step == VeyraBuilderStep.Welcome && session.otaUrl.isBlank()) {
                    "New session"
                } else {
                    "Resume · ${session.step.label}"
                },
                onClick = onOpenVeyraBuilder,
            )
        }
        item {
            WorkbenchBuilderCard(
                icon = Icons.Rounded.Build,
                title = "Loading Builder",
                subtitle = "Classic boot.img payload builder with independent settings",
                status = LocalPayload.displayName(context)?.let { "Local payload · $it" } ?: "Ready",
                onClick = onOpenLoadingBuilder,
            )
        }
        item {
            WorkbenchBuilderCard(
                icon = Icons.Rounded.AutoFixHigh,
                title = "Magic Builder",
                subtitle = "Automatic OEM-aware builder with OTA intelligence",
                status = MagicOtaCatalog.profileSummary(context, DeviceSnapshot.current()),
                onClick = onOpenMagicBuilder,
            )
        }
    }
}

@Composable
private fun WorkbenchBuilderCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    status: String,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.size(5.dp))
                Text(status, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
            Icon(Icons.Rounded.ChevronRight, contentDescription = null)
        }
    }
}
