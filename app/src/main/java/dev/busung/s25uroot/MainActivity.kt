package ctrl.mietze.veyraroot

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import android.view.View
import android.widget.Toast
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.ColorLens
import androidx.compose.material.icons.rounded.ViewAgenda
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.BatterySaver
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.BrightnessAuto
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.AccountTree
import androidx.compose.material.icons.rounded.DownloadForOffline
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Difference
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.HourglassEmpty
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Contrast
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import rikka.shizuku.Shizuku
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ctrl.mietze.veyraroot.ui.theme.RootMyGalaxyTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.text.DateFormat
import java.util.Collections
import java.util.Date
import java.util.Locale
import java.util.UUID

class MainActivity : ComponentActivity() {
    private val installViewModel by viewModels<InstallViewModel>()
    private var resumedOnce = false
    private var accentColor by mutableStateOf(AccentColor.Dynamic)
    private var themeMode by mutableStateOf(AppThemeMode.System)
    private var advancedMode by mutableStateOf(false)
	private var disableKsuModules by mutableStateOf(false)
    private var loadKernelSu by mutableStateOf(true)
    private var kernelsuFlavor by mutableStateOf(KernelSuFlavor.Default)
    private var shizukuMode by mutableStateOf(false)
    private var payloadSources by mutableStateOf<List<PayloadSource>>(emptyList())
    private var bootRootMode by mutableStateOf(false)
    private var armedRetry by mutableStateOf<ArmedRetry?>(null)

    /**
     * The settings card another screen asked this one to open on, or null.
     *
     * Held here rather than in the composition because it arrives with an intent - the run screen's
     * failure card hands one over - and an intent outlives the composition it landed in.
     */
    private var settingsTarget by mutableStateOf<String?>(null)

    /**
     * The run a notification asked this app to show, by its history entry, or null.
     *
     * Read from the intent for the same reason the target above is. It is a run rather than a screen because
     * that is what the notification knows: the boot gate's run is in another process and a run whose process
     * is gone has only its record left, so the record - the entry, written as the run goes - is the one thing
     * every notification about a run can open.
     */
    private var openedRunId by mutableStateOf<String?>(null)

    /**
     * What this launch is, when it is one of the launcher's restart shortcuts rather than a tap on the icon.
     *
     * Read here for the same reason the target above is: it arrives with an intent, and an intent outlives the
     * composition it landed in. Two shortcuts, and only one of them picks a target - the sheet's asks which way
     * out, so a long press cannot reboot a phone into Download mode by accident; the soft restart's asks for
     * the one target that leaves the kernel alone, and falls back to the sheet when this phone has no root.
     */
    private var restartShortcut by mutableStateOf<RestartShortcut?>(null)

    /**
     * The payload an armed retry would run, read from the attempt that armed it.
     *
     * On screen because a retry runs the attempt that failed rather than whatever the app would pick on
     * its own, and that difference is invisible until after the reboot otherwise - by which point the
     * phone has already been rooted with something the user did not choose. Null when nothing was
     * recorded, which is a state worth naming too: the restart then falls back to the cached payload.
     */
    private var retryPayload by mutableStateOf<CachedPayload?>(null)
    private var restartAfterRoot by mutableStateOf(false)
    private var shizukuBootMode by mutableStateOf(false)
    private var bootSettleSeconds by mutableStateOf(BootSettle.DEFAULT_SECONDS)
    private var autoRootSettleSeconds by mutableStateOf(BootSettle.AUTO_ROOT_DEFAULT_SECONDS)
    private var runLimits by mutableStateOf(
        RunLimitsSettings(
            totalSeconds = RunLimits.DEFAULT_TOTAL_SECONDS,
            stallSeconds = RunLimits.DEFAULT_STALL_SECONDS,
            helperSeconds = RunLimits.DEFAULT_HELPER_SECONDS,
        ),
    )

    /** Off until a user turns it on, which is the whole point of it being opt-in. */
    private var exploitOverride by mutableStateOf(ExploitOverride.defaults())
    private var shizukuToken by mutableStateOf("")
    private var partitionReadOnly by mutableStateOf(false)
    private var payloadMode by mutableStateOf(PayloadMode.Online)
    private var notificationPermissionAsked = false
    private var batteryUnrestricted by mutableStateOf(false)
    private var batteryPromptAsked = false
    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    private fun isBatteryUnrestricted(): Boolean =
        getSystemService(PowerManager::class.java)
            ?.isIgnoringBatteryOptimizations(packageName)
            ?: true

    /**
     * Battery optimisation is the restriction that can quietly sink a run nobody is watching: in
     * Doze an unattended install loses its network and its process priority, which is exactly when
     * the boot service needs them. Asked once per install; the settings card that mirrors the same
     * state stays available if the prompt is declined or dismissed.
     */
    private fun maybeRequestBatteryExemption() {
        if (batteryPromptAsked) return
        batteryPromptAsked = true
        if (batteryUnrestricted || AppPreferences.batteryPromptShown(this)) return
        AppPreferences.setBatteryPromptShown(this, true)
        requestBatteryExemption()
    }

    /**
     * Lint's BatteryLife check keeps apps out of Play's battery-whitelist flow; this build ships
     * from GitHub releases, and the exemption is what lets an unattended run reach the network
     * with the screen off.
     */
    @SuppressLint("BatteryLife")
    private fun requestBatteryExemption() {
        if (isBatteryUnrestricted()) {
            // Nothing left to ask for — the system dialog would no-op — so show where it can be
            // undone instead of leaving the card unresponsive.
            runCatching {
                startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            }
            return
        }
        val requested = runCatching {
            startActivity(
                Intent(
                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:$packageName"),
                ),
            )
        }.isSuccess
        if (!requested) {
            // Vendor builds without the direct dialog still have the settings list.
            runCatching {
                startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            }
        }
    }

    /**
     * Asks for POST_NOTIFICATIONS once, when the boot option is switched on, so the foreground
     * service's progress notification is visible. Asking at launch instead would prompt people
     * who never enable the feature.
     */
    private fun maybeRequestNotificationPermission() {
        if (notificationPermissionAsked) return
        notificationPermissionAsked = true
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ReleaseGuard.checkNow(this)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        runCatching {
            val display = windowManager.defaultDisplay
            val current = display.mode
            val target = display.supportedModes
                .asSequence()
                .filter {
                    it.physicalWidth == current.physicalWidth &&
                        it.physicalHeight == current.physicalHeight
                }
                .filter { it.refreshRate <= 120.5f }
                .maxByOrNull { it.refreshRate }
            if (target != null && target.refreshRate >= 90f) {
                val attributes = window.attributes
                attributes.preferredDisplayModeId = target.modeId
                attributes.preferredRefreshRate = target.refreshRate
                window.attributes = attributes
                AppLog.info(
                    AppLogTags.APP,
                    "High refresh requested: " + target.refreshRate + " Hz",
                )
            }
        }
        accentColor = AppPreferences.accentColor(this)
        themeMode = AppPreferences.themeMode(this)
        advancedMode = AppPreferences.advancedMode(this)
		disableKsuModules = AppPreferences.disableKsuModules(this)
        loadKernelSu = AppPreferences.loadKernelSu(this)
        kernelsuFlavor = AppPreferences.kernelsuFlavor(this)
        shizukuMode = AppPreferences.shizukuMode(this)
        payloadSources = AppPreferences.payloadSources(this)
        bootRootMode = AppPreferences.bootRootMode(this)
        armedRetry = readArmedRetry()
        retryPayload = readArmedRetryPayload()
        restartAfterRoot = AppPreferences.restartAfterRoot(this)
        shizukuBootMode = AppPreferences.shizukuBootMode(this)
        bootSettleSeconds = AppPreferences.bootSettleSeconds(this)
        autoRootSettleSeconds = AppPreferences.autoRootSettleSeconds(this)
        runLimits = AppPreferences.runLimits(this)
        exploitOverride = AppPreferences.exploitOverride(this)
        shizukuToken = AppPreferences.shizukuAutomationToken(this)
        partitionReadOnly = AppPreferences.partitionReadOnlyMode(this)
        payloadMode = AppPreferences.payloadMode(this)
        settingsTarget = SettingsTarget.named(intent?.getStringExtra(SettingsTarget.EXTRA))
        openedRunId = intent?.getStringExtra(EXTRA_RUN_ID)
        restartShortcut = restartShortcutOf(intent?.action)
        batteryUnrestricted = isBatteryUnrestricted()
        setContent {
            RootMyGalaxyTheme(accentColor = accentColor, themeMode = themeMode) {
                MandatoryReleaseGate {
                    RootApp(
                    installViewModel = installViewModel,
                    accentColor = accentColor,
                    themeMode = themeMode,
                    advancedMode = advancedMode,
					disableKsuModules = disableKsuModules,
                    loadKernelSu = loadKernelSu,
                    kernelsuFlavor = kernelsuFlavor,
                    shizukuMode = shizukuMode,
                    payloadSources = payloadSources,
                    bootRootMode = bootRootMode,
                    armedRetry = armedRetry,
                    retryPayload = retryPayload,
                    restartAfterRoot = restartAfterRoot,
                    shizukuBootMode = shizukuBootMode,
                    bootSettleSeconds = bootSettleSeconds,
                    autoRootSettleSeconds = autoRootSettleSeconds,
                    runLimits = runLimits,
                    exploitOverride = exploitOverride,
                    shizukuToken = shizukuToken,
                    partitionReadOnly = partitionReadOnly,
                    payloadMode = payloadMode,
                    batteryUnrestricted = batteryUnrestricted,
                    onStartArmedRetry = ::startArmedRetry,
                    onCancelArmedRetry = ::cancelArmedRetry,
                    requestNotificationPermission = ::maybeRequestNotificationPermission,
                    onRequestBatteryExemption = ::requestBatteryExemption,
                    onAccentColorChanged = { color ->
                        AppPreferences.setAccentColor(this, color)
                        accentColor = color
                    },
                    onThemeModeChanged = { mode ->
                        AppPreferences.setThemeMode(this, mode)
                        themeMode = mode
                    },
                    onAdvancedModeChanged = { enabled ->
                        AppPreferences.setAdvancedMode(this, enabled)
                        advancedMode = enabled
                    },
					onDisableKsuModulesChanged = { enabled ->
						AppPreferences.setDisableKsuModules(this, enabled)
						disableKsuModules = enabled
					},
                    onLoadKernelSuChanged = { enabled ->
                        AppPreferences.setLoadKernelSu(this, enabled)
                        loadKernelSu = enabled
                    },
                    onKernelsuFlavorChanged = { flavor ->
                        AppPreferences.setKernelsuFlavor(this, flavor)
                        kernelsuFlavor = flavor
                    },
                    // Stored per flavour, so naming one for KernelSU does not name one for
                    // KernelSU-Next as well - they are different projects with different versions.
                    onManagerVersionChanged = { version ->
                        AppPreferences.setManagerVersion(this, kernelsuFlavor, version)
                    },
                    onShizukuModeChanged = { enabled ->
                        AppPreferences.setShizukuMode(this, enabled)
                        shizukuMode = enabled
                    },
                    onPayloadSourcesChanged = { sources ->
                        AppPreferences.setPayloadSources(this, sources)
                        payloadSources = sources
                    },
                    onBootRootModeChanged = { enabled ->
                        AppPreferences.setBootRootMode(this, enabled)
                        bootRootMode = enabled
                        // Turning it off has to reach a gate that is already waiting, not just the
                        // next boot: a foreground service left running would install anyway.
                        if (!enabled) AutoRootService.stop(this)
                    },
                    onRestartAfterRootChanged = { enabled ->
                        AppPreferences.setRestartAfterRoot(this, enabled)
                        restartAfterRoot = enabled
                    },
                    onBootSettleChanged = { seconds ->
                        AppPreferences.setBootSettleSeconds(this, seconds)
                        bootSettleSeconds = seconds
                    },
                    onAutoRootSettleChanged = { seconds ->
                        AppPreferences.setAutoRootSettleSeconds(this, seconds)
                        autoRootSettleSeconds = seconds
                    },
                    onRunLimitChanged = { limit, seconds ->
                        AppPreferences.setRunLimit(this, limit, seconds)
                        // Read back rather than patched in place, so a stored value that was normalized
                        // on the way in is what the row shows.
                        runLimits = AppPreferences.runLimits(this)
                    },
                    onExploitOverrideChanged = { override ->
                        AppPreferences.setExploitOverride(this, override)
                        exploitOverride = AppPreferences.exploitOverride(this)
                    },
                    onShizukuTokenChanged = { token ->
                        AppPreferences.setShizukuAutomationToken(this, token)
                        shizukuToken = token
                    },
                    onPartitionReadOnlyChanged = { enabled ->
                        AppPreferences.setPartitionReadOnlyMode(this, enabled)
                        partitionReadOnly = enabled
                    },
                    onPayloadModeChanged = { mode ->
                        AppPreferences.setPayloadMode(this, mode)
                        payloadMode = mode
                    },
                    onForgetCachedPayload = {
                        // Says nothing on success: the row it was pressed from already shows
                        // "Nothing cached yet" once this returns.
                        KnownGoodPayloadStore.clear(this)
                    },
                    onShizukuBootModeChanged = { enabled ->
                        AppPreferences.setShizukuBootMode(this, enabled)
                        shizukuBootMode = enabled
                    },
                    openInstaller = ::openInstaller,
                    settingsTarget = settingsTarget,
                    onSettingsTargetHandled = { settingsTarget = null },
                    openedRunEntry = openedRunId,
                    onOpenedRunEntryHandled = { openedRunId = null },
                        restartShortcut = restartShortcut,
                        onRestartShortcutHandled = { restartShortcut = null },
                    )
                }
            }
        }
        maybeRequestBatteryExemption()
    }

    /**
     * A target that arrives while this activity is already open.
     *
     * The run screen's failure card is the one caller that does this, and it asks for the existing
     * window rather than a second one: the settings page it wants is in the activity that is already
     * in the back stack, and a fresh instance would put the app's own screens on top of each other.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        settingsTarget = SettingsTarget.named(intent.getStringExtra(SettingsTarget.EXTRA))
        openedRunId = intent.getStringExtra(EXTRA_RUN_ID)
        // The shortcut's own second case: the app is already in the back stack, so the restart is asked for in
        // the window that exists rather than in a second one.
        restartShortcut = restartShortcutOf(intent.action)
    }

    private fun openInstaller(selectionId: String? = null) {
        val installer = Intent(this, InstallActivity::class.java)
            .putExtra(InstallActivity.EXTRA_INSTALL_REQUEST_ID, UUID.randomUUID().toString())
        if (selectionId != null) {
            installer.putExtra(InstallActivity.EXTRA_PROFILE_ID, selectionId)
        }
        startActivity(installer)
    }

    /** The retry this device has armed, read together with the boot it would run in. */
    private fun readArmedRetry(): ArmedRetry? =
        ArmedRetry.of(AppPreferences.retryArmedInBoot(this), kernelBootToken())

    /**
     * The payload the armed retry would run, or null when there is no retry or nothing was recorded.
     *
     * Read only while a retry is armed: the record outlives the retry it was made for, and showing a
     * payload on a screen with no retry on it would imply the next boot was about to run something.
     */
    private fun readArmedRetryPayload(): CachedPayload? =
        if (AppPreferences.retryArmed(this)) AttemptedPayloadStore.describe(this) else null

    /**
     * Starts the install a retry was armed for.
     *
     * Offered on Home rather than taken on the way in, which is what it used to be: the app started the
     * run itself the first time it was opened after the boot the retry was armed for, so a phone could
     * begin installing because its owner opened the app to look at something else. The retry is still
     * theirs to take - it is just a tap now, and the screen says what it is for.
     *
     * Consumed before the install starts, so a process death mid-run cannot leave an armed retry that
     * would start another one on the next launch.
     */
    private fun startArmedRetry() {
        AppPreferences.setRetryAfterReboot(this, null)
        armedRetry = null
        retryPayload = null
        openInstaller()
    }

    /** Takes the retry back, which is the only way to stop it running at the next boot. */
    private fun cancelArmedRetry() {
        AppPreferences.setRetryAfterReboot(this, null)
        armedRetry = null
        retryPayload = null
    }

    override fun onResume() {
        super.onResume()
        // Battery optimisation is a system setting, so it can change while the app is backgrounded.
        batteryUnrestricted = isBatteryUnrestricted()
        // A retry is armed on the run screen and consumed by a boot, so coming back from either is
        // exactly when this screen can be wrong about it.
        armedRetry = readArmedRetry()
        retryPayload = readArmedRetryPayload()
        if (resumedOnce) installViewModel.refresh() else resumedOnce = true
    }
}

private enum class AppPage(@StringRes val label: Int, val icon: ImageVector) {
    Overview(R.string.nav_overview, Icons.Rounded.Home),
    History(R.string.nav_history, Icons.Rounded.History),
    Logs(R.string.nav_logs, Icons.Rounded.Terminal),
    Settings(R.string.nav_settings, Icons.Rounded.Settings),
}

private data class LanguageOption(@StringRes val label: Int, val tag: String)

private enum class CompatibilityWarning {
    Device,
    KernelVersion,
}

private val languageOptions = listOf(
    LanguageOption(R.string.language_system, ""),
    LanguageOption(R.string.language_korean, "ko"),
    LanguageOption(R.string.language_english, "en"),
    LanguageOption(R.string.language_german, "de"),
    LanguageOption(R.string.language_japanese, "ja"),
    LanguageOption(R.string.language_chinese, "zh-CN"),
    LanguageOption(R.string.language_chinese_traditional, "zh-TW"),
    LanguageOption(R.string.language_turkish, "tr"),
    LanguageOption(R.string.language_brazillian_portuguese, "pt-BR"),
    LanguageOption(R.string.language_russian, "ru"),
    LanguageOption(R.string.language_vietnamese, "vi"),
    LanguageOption(R.string.language_uzbek, "uz"),
)

/**
 * The KernelSU project's own documentation, which is about KernelSU rather than one flavour.
 *
 * What the app installs is per flavour and lives in [KernelSuFlavor]; this is only the link offered
 * beside the general explanation, and it stays the upstream project's page for both.
 */
private const val KERNEL_SU_HOME_URL = "https://kernelsu.org/"
private const val SHIZUKU_MANAGER_PACKAGE = "moe.shizuku.manager"
private const val SHIZUKU_MANAGER_URL = "https://github.com/thedjchi/Shizuku/releases/"

private fun openShizukuManager(context: Context) {
    val launch = context.packageManager.getLaunchIntentForPackage(SHIZUKU_MANAGER_PACKAGE)
    if (launch != null) {
        context.startActivity(launch)
    } else {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(SHIZUKU_MANAGER_URL)))
    }
}

@Composable
private fun RootApp(
    installViewModel: InstallViewModel,
    accentColor: AccentColor,
    themeMode: AppThemeMode,
    advancedMode: Boolean,
	disableKsuModules: Boolean,
    loadKernelSu: Boolean,
    kernelsuFlavor: KernelSuFlavor,
    shizukuMode: Boolean,
    payloadSources: List<PayloadSource>,
    bootRootMode: Boolean,
    armedRetry: ArmedRetry?,
    retryPayload: CachedPayload?,
    restartAfterRoot: Boolean,
    shizukuBootMode: Boolean,
    bootSettleSeconds: Int,
    autoRootSettleSeconds: Int,
    runLimits: RunLimitsSettings,
    exploitOverride: ExploitOverrideSettings,
    shizukuToken: String,
    partitionReadOnly: Boolean,
    payloadMode: PayloadMode,
    batteryUnrestricted: Boolean,
    onStartArmedRetry: () -> Unit,
    onCancelArmedRetry: () -> Unit,
    onAccentColorChanged: (AccentColor) -> Unit,
    onThemeModeChanged: (AppThemeMode) -> Unit,
    onAdvancedModeChanged: (Boolean) -> Unit,
	onDisableKsuModulesChanged: (Boolean) -> Unit,
    onLoadKernelSuChanged: (Boolean) -> Unit,
    onKernelsuFlavorChanged: (KernelSuFlavor) -> Unit,
    onManagerVersionChanged: (String) -> Unit,
    onShizukuModeChanged: (Boolean) -> Unit,
    onPayloadSourcesChanged: (List<PayloadSource>) -> Unit,
    onBootRootModeChanged: (Boolean) -> Unit,
    onRestartAfterRootChanged: (Boolean) -> Unit,
    onShizukuBootModeChanged: (Boolean) -> Unit,
    onBootSettleChanged: (Int) -> Unit,
    onAutoRootSettleChanged: (Int) -> Unit,
    onRunLimitChanged: (RunLimit, Int) -> Unit,
    onExploitOverrideChanged: (ExploitOverrideSettings) -> Unit,
    onShizukuTokenChanged: (String) -> Unit,
    onPartitionReadOnlyChanged: (Boolean) -> Unit,
    onPayloadModeChanged: (PayloadMode) -> Unit,
    onForgetCachedPayload: () -> Unit,
    requestNotificationPermission: () -> Unit,
    onRequestBatteryExemption: () -> Unit,
    openInstaller: (String?) -> Unit,
    /** A settings card another screen asked this one to open on, or null. */
    settingsTarget: String?,
    onSettingsTargetHandled: () -> Unit,
    /** The run a notification was about, by its history entry, or null. */
    openedRunEntry: String?,
    onOpenedRunEntryHandled: () -> Unit,
    /** The launcher's restart shortcut was used, or null when this launch did not come from one. */
    restartShortcut: RestartShortcut?,
    onRestartShortcutHandled: () -> Unit,
) {
    val installState by installViewModel.state.collectAsStateWithLifecycle()
    val history by installViewModel.history.collectAsStateWithLifecycle()
    val targetCatalog by installViewModel.targetCatalog.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var selectedPage by remember { mutableStateOf(AppPage.Overview) }
    var cveyraNavActive by remember { mutableStateOf(CVeyraAccessStore.isActive(context)) }
    var localSettingsTarget by remember { mutableStateOf<String?>(null) }
    val activeSettingsTarget = settingsTarget ?: localSettingsTarget
    LaunchedEffect(Unit) {
        if (CVeyraAccessStore.shouldResumeSetup(context)) {
            localSettingsTarget = SettingsTarget.CVeyraAccess
            CVeyraAccessStore.clearResumeRequest(context)
        }
    }
    val pageStateHolder = rememberSaveableStateHolder()
    val historyListState = rememberPageListState()
    var navBarGlass by remember {
        mutableStateOf(AppPreferences.navBarGlass(context))
    }
    // A card to open on is only reachable from the settings page, so the page comes first and the jump
    // is left to the page itself: it is the only thing that knows where its own rows are.
    LaunchedEffect(activeSettingsTarget) {
        if (activeSettingsTarget != null) selectedPage = AppPage.Settings
    }
    // The same shape for a run: the record lives on the History page, so the page comes first and the entry
    // itself is opened by the page, which is the only thing that knows when its list has arrived.
    LaunchedEffect(openedRunEntry) {
        if (openedRunEntry != null) selectedPage = AppPage.History
    }
    var showInstallConfirmation by remember { mutableStateOf(false) }
    var showTargetPicker by remember { mutableStateOf(false) }
    var showRebootSheet by remember { mutableStateOf(false) }
    // The app's one undo surface. Held here rather than per page, so a deletion on History and a deletion in
    // the residue dialog use the same one - and so neither has to know where it is drawn.
    val snackbarHostState = remember { SnackbarHostState() }
    // What the sheet is opened with when a shortcut's own attempt was refused, so the sheet can show the
    // device's words instead of the same rows that were already tried. Cleared with the sheet.
    var rebootNotice by remember { mutableStateOf<RecoveryOutcome?>(null) }
    var selectedProfile by remember { mutableStateOf<TargetProfile?>(null) }
    var compatibilityWarning by remember { mutableStateOf<CompatibilityWarning?>(null) }
    val device = remember { DeviceSnapshot.current() }
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    // One Shizuku start for the whole app, because two screens offer the button: Home's checklist when
    // Shizuku is not running, and the Shizuku rows in Settings. The request, what the attempt said and the
    // result are the same three things wherever it was pressed, and a second implementation would be a
    // second set of routes and a second place for them to disagree with the screen that reports them.
    var shizukuStarting by remember { mutableStateOf(false) }
    var shizukuStartResult by remember { mutableStateOf<String?>(null) }
    // What the attempt said as it went. The result line alone was not enough to act on: "Shizuku could
    // not be started" over three routes with different fixes reads as nothing having happened, which is
    // exactly how it read.
    var shizukuStartLog by remember { mutableStateOf<List<String>>(emptyList()) }
    val startShizuku: () -> Unit = {
        if (!shizukuStarting) {
            shizukuStarting = true
            scope.launch {
                // Written from the start attempt's own thread (it runs on IO) and read here once it is
                // done, so the list is the attempt's, not the screen's.
                val lines = Collections.synchronizedList(mutableListOf<String>())
                val outcome = ShizukuStarter.start(
                    context = context,
                    shell = { command ->
                        KernelSuRuntime.rootShell(command) ?: ShizukuController.ShellResult(
                            NO_ROOT_SHELL_EXIT,
                            context.getString(R.string.error_shizuku_start_no_root),
                        )
                    },
                    onLog = { line -> lines += line },
                )
                shizukuStarting = false
                shizukuStartLog = lines.toList()
                shizukuStartResult =
                    if (outcome.started) context.getString(R.string.status_shizuku_started)
                    else outcome.detail.ifBlank { context.getString(R.string.error_shizuku_start_no_root) }
            }
        }
    }
    // The grant this app can ask for and Shizuku sends no callback about, in the one shape both screens
    // need: whether it landed, so the caller can decide what its own switch or row should say now.
    val requestShizukuPermission: suspend () -> Boolean = { ShizukuController.requestPermission() }

    shizukuStartResult?.let { result ->
        AlertDialog(
            onDismissRequest = { shizukuStartResult = null },
            icon = { Icon(Icons.Rounded.PowerSettingsNew, contentDescription = null) },
            title = {
                DialogDimAmount(0.34f)
                Text(stringResource(R.string.settings_shizuku_start))
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(result)
                    if (shizukuStartLog.isNotEmpty()) {
                        Text(
                            stringResource(R.string.shizuku_start_attempt_log),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = shizukuStartLog.takeLast(SHIZUKU_START_LOG_LINES).joinToString("\n"),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = SHIZUKU_START_LOG_MAX_HEIGHT)
                                .verticalScroll(rememberScrollState()),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            lineHeight = 17.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    clickHaptic(view)
                    shizukuStartResult = null
                }) {
                    Text(stringResource(R.string.action_close))
                }
            },
        )
    }

    // The shortcut's ask, acted on rather than initialised from, so the same path serves a cold start and an
    // app already in the back stack: it arrives either way, is acted on, and is cleared.
    LaunchedEffect(restartShortcut) {
        when (val asked = restartShortcut) {
            null -> Unit
            RestartShortcut.Options -> {
                onRestartShortcutHandled()
                showRebootSheet = true
            }
            // In its own coroutine, because this one is keyed on the ask and the ask is cleared the moment it
            // is read: the probe and the restart outlive the effect that starts them, and a coroutine
            // cancelled for the state change it caused would drop the restart on the floor.
            RestartShortcut.SoftRestart -> scope.launch {
                onRestartShortcutHandled()
                when (val outcome = runSoftRestartShortcut(context)) {
                    // Nothing to show: the userspace it restarted is going away, and a message about it would
                    // be drawn by a process that no longer exists.
                    SoftRestartShortcutOutcome.Requested -> Unit
                    is SoftRestartShortcutOutcome.OpenSheet -> {
                        rebootNotice = outcome.report
                        showRebootSheet = true
                    }
                }
            }
        }
    }
    var updateStatus by remember { mutableStateOf<UpdateStatus>(UpdateStatus.Idle) }
    var updateCardDismissed by remember { mutableStateOf(false) }
    // What the framework came back with after the last restart, read once here because here is the one
    // screen every launch goes through: the restart ends the process that asked for it, so its result
    // has no other place to be read from. Read away as it is read - a result is news, and the app runs
    // in several processes.
    var frameworkRestart by remember { mutableStateOf<FrameworkRestartReport?>(null) }
    var frameworkRestartDismissed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        val report = withContext(Dispatchers.IO) {
            ZygoteRestartReport
                .consume(ZygoteRestartReport.file(context), kernelBootToken())
                ?.also { AppLog.info(AppLogTags.KERNEL_SU, ZygoteRestartReport.logLine(context, it)) }
        }
        frameworkRestart = report
    }
    // What earlier runs left in /data/local/tmp, taken away here because here is the one screen every
    // launch goes through. A sweep after every run is what keeps the directory empty; this is what
    // handles the files a run could not sweep for itself - one that was killed, one whose app was
    // never opened again, and everything staged by the builds that came before the sweep existed.
    //
    // Silent when there is nothing to say. A device with no shell keeps its files and is not told
    // about it: that is the normal state before a first run, and a notice about it would be an alarm
    // about the app not having rooted the phone yet.
    LaunchedEffect(Unit) {
        val sweep = withContext(Dispatchers.IO) { StagingSweep.sweepWhenQuiet(context) }
        sweep.logLine(context)?.let { line -> AppLog.info(AppLogTags.STAGING, line) }
    }
    // The updater stands down while a run is in flight, and says so when it is asked.
    //
    // A run's delicate part is the payload's own timing, and an update check or a download beside it is
    // network, CPU and - if it succeeds - a package install prompt, none of which the run asked for. The
    // validated baseline this app's runtime is measured against keeps its own updater inert for exactly
    // this reason; keeping the feature and refusing to use it during a run is the narrower version of
    // that, and the honest one for an app that does ship update notifications.
    val runInFlight = installState.busy
    var updateRefusedDuringRun by remember { mutableStateOf(false) }
    val checkForUpdate: () -> Unit = { }
    // What the cache holds, kept here because the plan needs it and the plan is built synchronously.
    // Reloaded whenever a run's phase changes, since a finished run is what publishes a cache entry.
    var cachedPayload by remember { mutableStateOf<CachedPayload?>(null) }
    LaunchedEffect(installState.phase) {
        cachedPayload = withContext(Dispatchers.IO) { KnownGoodPayloadStore.describe(context) }
    }
    // Built on demand rather than on every recomposition: it reads the boot id to report the
    // cached offset, and only the run-plan dialog needs it.
    val runPlan: () -> RunPlanDisplay = {
        // Offline mode resolves nothing: the run it is about to start is the cached payload, so the
        // plan describes that one. Reading the catalog here would describe a run the mode exists to
        // avoid, and an offline plan that named some other target's source would be a plan about
        // another app's run.
        val resolved = if (payloadMode == PayloadMode.Offline) {
            cachedPayload?.profile()
        } else {
            targetCatalog.profiles.resolveFor(device)
        }
        val freshSession = resolved?.requiresFreshP0Session == true
        val cachedOffset = installViewModel.cachedOffsetForThisBoot()
        RunPlanDisplay(
            deviceLabel = "${device.model} \u00b7 ${device.kernelRelease}",
            targetLabel = resolved?.let { "${it.displayName} (${it.profileId})" },
            // The revision is named when the catalog resolved one, because "which branch" and "which
            // revision of it" are different answers and only the second one is reproducible.
            sourceLabel = resolved?.sourceLabel?.takeIf(String::isNotBlank)?.let { label ->
                resolved.sourceCommit.takeIf(String::isNotBlank)
                    ?.let { "$label @ ${it.take(7)}" }
                    ?: label
            },
            unresolvedNote = if (resolved != null) {
                null
            } else when {
                payloadMode == PayloadMode.Offline ->
                    context.getString(R.string.run_plan_offline_no_cache)
                targetCatalog.loading -> context.getString(R.string.run_plan_catalog_loading)
                targetCatalog.error != null -> targetCatalog.error
                targetCatalog.profiles.isEmpty() -> context.getString(R.string.run_plan_catalog_empty)
                else -> context.getString(R.string.run_plan_no_target)
            },
            freshSession = freshSession,
            shizuku = shizukuMode,
            payloadMode = payloadMode,
            partitionReadOnly = partitionReadOnly,
            cachedOffset = cachedOffset,
            // The policy a run would actually use, resolved here from the same stored override a run
            // reads - so the preview shows the environment the run will get and names the side that
            // chose each of the three numbers a user can move.
            plan = InstallViewModel.exploitPlan(
                freshSession,
                cachedOffset,
                shizukuMode,
                ExploitOverride.resolve(
                    policy = resolved?.routePolicy ?: ExploitRoutePolicy.LEGACY,
                    override = AppPreferences.exploitOverride(context),
                    freshSession = freshSession,
                ),
                AppPreferences.bootSettleSeconds(context),
                // The same resolution a run performs, from the same stored values: a plan that showed
                // the defaults while the run enforced the user's choices would be a plan about another
                // app's run.
                RunLimits.resolve(AppPreferences.runLimits(context), freshSession),
            ),
        )
    }
    val startDownload: (UpdateInfo) -> Unit = { _ -> }
    // Not asked for at all while a run is in flight: an automatic check the user did not request is the
    // last thing that should reach the network next to an exploit, and the card can wait for the run.

    if (updateRefusedDuringRun) {
        AlertDialog(
            onDismissRequest = { updateRefusedDuringRun = false },
            icon = { Icon(Icons.Rounded.SystemUpdate, contentDescription = null) },
            title = {
                DialogDimAmount(0.34f)
                Text(stringResource(R.string.updater_run_in_progress_title))
            },
            text = { Text(stringResource(R.string.updater_run_in_progress)) },
            confirmButton = {
                TextButton(onClick = {
                    clickHaptic(view)
                    updateRefusedDuringRun = false
                }) {
                    Text(stringResource(R.string.action_close))
                }
            },
        )
    }

    if (showRebootSheet) {
        RebootSheet(
            notice = rebootNotice,
            onDismiss = {
                showRebootSheet = false
                rebootNotice = null
            },
        )
    }

    if (showTargetPicker) {
        TargetSelectionSheet(
            device = device,
            catalog = targetCatalog,
            onDismiss = { showTargetPicker = false },
            onRetry = installViewModel::loadTargetCatalog,
            onNext = { profile ->
                selectedProfile = profile
                // A payload picked by hand is a decision about which KernelSU this phone will load, so
                // the manager rows follow it from here - before the run that proves it works, because
                // the manager is installed to drive the load that run performs.
                rememberResolvedPayload(context, profile)
                showTargetPicker = false
                compatibilityWarning = when {
                    !profile.matchesDevice(device) -> CompatibilityWarning.Device
                    !profile.matchesKernelVersion(device) -> CompatibilityWarning.KernelVersion
                    else -> null
                }
                if (compatibilityWarning == null) showInstallConfirmation = true
            },
        )
    }

    compatibilityWarning?.let { warning ->
        val profile = selectedProfile ?: return@let
        AlertDialog(
            onDismissRequest = {
                compatibilityWarning = null
                showTargetPicker = true
            },
            icon = { Icon(Icons.Rounded.Warning, contentDescription = null) },
            title = {
                DialogDimAmount(0.34f)
                Text(
                    stringResource(when (warning) {
                        CompatibilityWarning.Device -> R.string.device_mismatch_title
                        CompatibilityWarning.KernelVersion -> R.string.kernel_version_mismatch_title
                    }),
                )
            },
            text = {
                Text(
                    when (warning) {
                        CompatibilityWarning.Device -> stringResource(
                            R.string.device_mismatch_body,
                            device.model,
                            profile.supportedModels,
                        )
                        CompatibilityWarning.KernelVersion -> stringResource(
                            R.string.kernel_version_mismatch_body,
                            device.kernelVersion,
                            profile.supportedKernelVersions,
                        )
                    },
                )
            },
            confirmButton = {
                FilledTonalButton(
                    onClick = {
                        clickHaptic(view)
                        compatibilityWarning = when (warning) {
                            CompatibilityWarning.Device -> if (!profile.matchesKernelVersion(device)) {
                                CompatibilityWarning.KernelVersion
                            } else {
                                null
                            }
                            CompatibilityWarning.KernelVersion -> null
                        }
                        if (compatibilityWarning == null) {
                            showInstallConfirmation = true
                        }
                    },
                ) {
                    Text(stringResource(R.string.action_continue))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        clickHaptic(view)
                        compatibilityWarning = null
                        showTargetPicker = true
                    },
                ) {
                    Text(stringResource(R.string.action_back))
                }
            },
        )
    }

    if (showInstallConfirmation) {
        AlertDialog(
            onDismissRequest = { showInstallConfirmation = false },
            icon = { Icon(Icons.Rounded.Security, contentDescription = null) },
            title = {
                DialogDimAmount(0.34f)
                Text(stringResource(R.string.install_confirm_title))
            },
            text = { Text(stringResource(R.string.install_confirm_body)) },
            confirmButton = {
                FilledTonalButton(onClick = {
                    clickHaptic(view)
                    showInstallConfirmation = false
                    openInstaller(selectedProfile?.selectionId)
                    selectedProfile = null
                }) {
                    Text(stringResource(R.string.action_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    clickHaptic(view)
                    showInstallConfirmation = false
                }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    // The bar's own two states, both decided here because both are the shell's: whether it is away, and how
    // far it travels to get there - which is its own height, known only once it has been laid out.
    var navBarHidden by remember { mutableStateOf(false) }
    var navBarHeight by remember { mutableStateOf(0.dp) }
    val navBarShift by animateDpAsState(
        targetValue = if (navBarHidden) navBarHeight else 0.dp,
        label = "navBarShift",
    )
    // A page change brings it back: the pages keep their own scroll states and are rebuilt at the top when
    // one is switched to, so a bar that stayed away would be away over a list that has nowhere to go.
    LaunchedEffect(selectedPage) { navBarHidden = false }
    val density = LocalDensity.current

    // Declared by the shell rather than by a page, because two pages read the phone's state and both
    // go stale on the same event: coming back to the foreground. A manager is installed by another
    // app's installer, a Shizuku grant is made in another app, and each page that kept its own counter
    // would be a second place to remember the same thing.
    var resumeTick by remember { mutableStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            // A tick rather than a read here, so the work still happens off the main thread.
            if (event == Lifecycle.Event.ON_RESUME) {
                resumeTick++
                cveyraNavActive = CVeyraAccessStore.isActive(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // The bar floats over the pages rather than being handed a strip of its own. A pill that reserved its
    // row left the bottom of every screen empty - the page stopped above it and the last card sat in the
    // middle of the screen with an empty band below - while the pill itself covered nothing that could not
    // be scrolled past. As a sibling it costs no layout at all: the pages run to the navigation inset and
    // the bar is drawn last, over whatever is under it.
    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            // One undo surface for the whole app, because the two deletions that can be undone are on
            // different pages and both want the same shape: a message that says what went, and a button that
            // puts it back.
            snackbarHost = { SnackbarHost(snackbarHostState) },
        ) { padding ->
            AnimatedContent(
                targetState = selectedPage,
                label = "page",
                transitionSpec = {
                    val enter = fadeIn(
                        animationSpec = tween(durationMillis = 150, delayMillis = 35),
                    ) + scaleIn(
                        animationSpec = spring(
                            dampingRatio = 0.78f,
                            stiffness = 520f,
                        ),
                        initialScale = 0.94f,
                    ) + slideInVertically(
                        animationSpec = spring(
                            dampingRatio = 0.84f,
                            stiffness = 720f,
                        ),
                        initialOffsetY = { height -> height / 24 },
                    )
                    val exit = fadeOut(
                        animationSpec = tween(durationMillis = 85),
                    ) + scaleOut(
                        animationSpec = tween(durationMillis = 125),
                        targetScale = 0.965f,
                    )
                    ContentTransform(enter, exit, 1f, null)
                },
                // The pages only. The sheets and dialogs are drawn outside this one, and scrolling a list
                // inside one of them is not a page moving under the bar.
                modifier = Modifier.nestedScroll(
                    remember { navBarScrollConnection { hidden -> navBarHidden = hidden } },
                ),
            ) { page ->
                pageStateHolder.SaveableStateProvider(page) {
                    when (page) {
                    AppPage.Overview -> OverviewPage(
                        padding = padding,
                        device = device,
                        installState = installState,
                        armedRetry = armedRetry,
                        retryPayload = retryPayload,
                        updateStatus = updateStatus,
                        updateCardDismissed = updateCardDismissed,
                        onDismissUpdateCard = { updateCardDismissed = true },
                        frameworkRestart = frameworkRestart.takeIf { !frameworkRestartDismissed },
                        onDismissFrameworkRestart = { frameworkRestartDismissed = true },
                        onStartDownload = startDownload,
                        onCheckForUpdate = checkForUpdate,
                        onStartArmedRetry = onStartArmedRetry,
                        onCancelArmedRetry = onCancelArmedRetry,
                        onOpenSettings = { selectedPage = AppPage.Settings },
                        onOpenRootMigration = {
                            localSettingsTarget = SettingsTarget.RootMigration
                            selectedPage = AppPage.Settings
                        },
                        onOpenVeyraKsu = {
                            localSettingsTarget = SettingsTarget.VeyraKsu
                            selectedPage = AppPage.Settings
                        },
                        // Opened from the button rather than from a shortcut, so there is no attempt
                        // behind it to report on.
                        onOpenReboot = {
                            rebootNotice = null
                            showRebootSheet = true
                        },
                        resumeTick = resumeTick,
                        onInstall = {
                            selectedProfile = null
                            if (advancedMode) {
                                showTargetPicker = true
                                installViewModel.loadTargetCatalog()
                            } else {
                                showInstallConfirmation = true
                            }
                        },
                    )
                    AppPage.History -> HistoryPage(
                        padding = padding,
                        history = history,
                        listState = historyListState,
                        snackbarHostState = snackbarHostState,
                        onDeleteEntries = installViewModel::deleteHistoryEntries,
                        onRestoreEntries = installViewModel::restoreHistoryEntries,
                        onOpenHome = { selectedPage = AppPage.Overview },
                        openEntryId = openedRunEntry,
                        onEntryOpened = onOpenedRunEntryHandled,
                        onReloadHistory = installViewModel::reloadHistory,
                    )
                    AppPage.Logs -> LogsPage(padding)
                    AppPage.Settings -> SettingsPage(
                        padding = padding,
                        device = device,
                        accentColor = accentColor,
                        themeMode = themeMode,
                        advancedMode = advancedMode,
                        disableKsuModules = disableKsuModules,
                        loadKernelSu = loadKernelSu,
                        kernelsuFlavor = kernelsuFlavor,
                        shizukuMode = shizukuMode,
                        payloadSources = payloadSources,
                        bootRootMode = bootRootMode,
                        restartAfterRoot = restartAfterRoot,
                        shizukuBootMode = shizukuBootMode,
                        bootSettleSeconds = bootSettleSeconds,
                        autoRootSettleSeconds = autoRootSettleSeconds,
                        runLimits = runLimits,
                        exploitOverride = exploitOverride,
                        shizukuToken = shizukuToken,
                        partitionReadOnly = partitionReadOnly,
                        payloadMode = payloadMode,
                        batteryUnrestricted = batteryUnrestricted,
                        navBarGlass = navBarGlass,
                        resumeTick = resumeTick,
                        onAccentColorChanged = onAccentColorChanged,
                        onThemeModeChanged = onThemeModeChanged,
                        onAdvancedModeChanged = onAdvancedModeChanged,
                        onDisableKsuModulesChanged = onDisableKsuModulesChanged,
                        onLoadKernelSuChanged = onLoadKernelSuChanged,
                        onKernelsuFlavorChanged = onKernelsuFlavorChanged,
                        onManagerVersionChanged = onManagerVersionChanged,
                        onShizukuModeChanged = onShizukuModeChanged,
                        onPayloadSourcesChanged = onPayloadSourcesChanged,
                        onBootRootModeChanged = onBootRootModeChanged,
                        onRestartAfterRootChanged = onRestartAfterRootChanged,
                        onShizukuBootModeChanged = onShizukuBootModeChanged,
                        onBootSettleChanged = onBootSettleChanged,
                        onAutoRootSettleChanged = onAutoRootSettleChanged,
                        onRunLimitChanged = onRunLimitChanged,
                        onExploitOverrideChanged = onExploitOverrideChanged,
                        onShizukuTokenChanged = onShizukuTokenChanged,
                        onPartitionReadOnlyChanged = onPartitionReadOnlyChanged,
                        onPayloadModeChanged = onPayloadModeChanged,
                        onForgetCachedPayload = onForgetCachedPayload,
                        onRequestNotificationPermission = requestNotificationPermission,
                        onRequestBatteryExemption = onRequestBatteryExemption,
                        onNavBarGlassChanged = { enabled ->
                            AppPreferences.setNavBarGlass(context, enabled)
                            navBarGlass = enabled
                        },
                        onCVeyraAccessStateChanged = { active ->
                            cveyraNavActive = active
                        },
                        shizukuStarting = shizukuStarting,
                        startShizuku = startShizuku,
                        requestShizukuPermission = requestShizukuPermission,
                        runPlan = runPlan,
                        openTarget = activeSettingsTarget,
                        onOpenTargetHandled = {
                            if (localSettingsTarget != null) {
                                localSettingsTarget = null
                            } else {
                                onSettingsTargetHandled()
                            }
                        },
                    )
                    }
                }
            }
        }

        AppNavBar(
            selected = selectedPage,
            glass = navBarGlass,
            cveyraAccessActive = cveyraNavActive,
            onOpenCVeyra = {
                clickHaptic(view, sound = null)
                UiSoundEngine.play(context, UiSoundKind.Page)
                localSettingsTarget = SettingsTarget.CVeyraManagement
                selectedPage = AppPage.Settings
            },
            onSelect = { page ->
                if (page != selectedPage) {
                    clickHaptic(view, sound = null)
                    UiSoundEngine.play(context, UiSoundKind.Page)
                    selectedPage = page
                }
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                // Measured rather than assumed: the bar carries the navigation inset, and this has no idea
                // how tall that is. Its own height is also exactly how far it has to travel to be out of the
                // way, so the pill ends below the screen edge rather than peeking at the bottom of it.
                .onGloballyPositioned { coordinates ->
                    navBarHeight = with(density) { coordinates.size.height.toDp() }
                }
                .offset(y = navBarShift),
        )
    }
}

/**
 * The bottom navigation, as a bar that sits clear of the screen edges rather than as a strip across
 * the whole width.
 *
 * The bar carries its own fade, and that is the fade the pages run into: the gradient is drawn on the box the
 * pill sits in, so it covers the bar's band and slides out of the way with it. It is not a page's business
 * and it is not conditional on a page having scrolled - a long page at its top still has rows under the pill,
 * which is exactly the case a scrim that waited for a scroll would leave with a hard edge.
 *
 * The page you are on is the only one that spells its name; the others are icons. Four labelled
 * items on a phone means four narrow columns of wrapped text, and this way the name that matters is
 * wide enough to read while the bar stays one calm piece under whatever the page is showing.
 */
/**
 * What the floating bar takes of the bottom of a page.
 *
 * The bar is drawn over the pages rather than given a strip of its own, so a page's own padding stops at the
 * system navigation inset and this is added back where it matters: under the last row of a list, and under
 * whatever a page keeps in its bottom corner. One value for both, because the two have to clear the same
 * pill - and a list left out of the count is a last row you can only read by scrolling it under the bar.
 *
 * The sum: the pill is 64dp tall (48dp items inside 8dp of padding), it stands 10dp off the navigation
 * inset, and this leaves another 10dp of air above it.
 */
internal val NAV_BAR_HEIGHT = 84.dp

@Composable
private fun AppNavBar(
    selected: AppPage,
    glass: Boolean,
    cveyraAccessActive: Boolean,
    onOpenCVeyra: () -> Unit,
    onSelect: (AppPage) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            // Standard keeps Veyra's existing fade. Glass deliberately leaves the page visible
            // beneath the floating pill, like the KSuRoot 4.1 reference, without changing any tab
            // contents or scroll/hide behaviour.
            .background(
                if (glass) {
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        1f to MaterialTheme.colorScheme.background.copy(alpha = 0.12f),
                    )
                } else {
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        1f to MaterialTheme.colorScheme.surfaceContainer,
                    )
                },
            )
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(50),
            color = if (glass) {
                MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.68f)
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            },
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            border = if (glass) {
                BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.52f),
                )
            } else {
                null
            },
            tonalElevation = 0.dp,
            shadowElevation = if (glass) 12.dp else 6.dp,
        ) {
            Row(
                modifier = Modifier
                    .selectableGroup()
                    .padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppPage.entries.forEach { page ->
                    AppNavBarItem(
                        page = page,
                        selected = page == selected,
                        onClick = { onSelect(page) },
                    )
                    if (page == AppPage.History && cveyraAccessActive) {
                        CVeyraNavBarItem(onClick = onOpenCVeyra)
                    }
                }
            }
        }
    }
}

@Composable
private fun CVeyraNavBarItem(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.72f))
            .clickable(onClick = onClick)
            .padding(12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.ic_veyra_mark),
            contentDescription = "CVeyra Management",
            modifier = Modifier.size(22.dp),
        )
    }
}

@Composable
private fun AppNavBarItem(page: AppPage, selected: Boolean, onClick: () -> Unit) {
    val label = stringResource(page.label)
    val container by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            Color.Transparent
        },
        label = "navItemContainer",
    )
    val content by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.onSecondaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        label = "navItemContent",
    )
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(container)
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            page.icon,
            contentDescription = if (selected) null else label,
            tint = content,
            modifier = Modifier.size(22.dp),
        )
        AnimatedVisibility(
            visible = selected,
            enter = expandHorizontally() + fadeIn(),
            exit = shrinkHorizontally() + fadeOut(),
        ) {
            Text(
                label,
                color = content,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}

/** Shared with the reboot sheet, which is a dialog of its own. */
@Composable
internal fun DialogDimAmount(amount: Float) {
    val window = (LocalView.current.parent as DialogWindowProvider).window
    SideEffect { window.setDimAmount(amount) }
}

@Composable
private fun OverviewPage(
    padding: PaddingValues,
    device: DeviceSnapshot,
    installState: InstallUiState,
    armedRetry: ArmedRetry?,
    retryPayload: CachedPayload?,
    updateStatus: UpdateStatus,
    updateCardDismissed: Boolean,
    onDismissUpdateCard: () -> Unit,
    /** What the last framework restart came back with, until it is dismissed. */
    frameworkRestart: FrameworkRestartReport?,
    onDismissFrameworkRestart: () -> Unit,
    onStartDownload: (UpdateInfo) -> Unit,
    onCheckForUpdate: () -> Unit,
    onStartArmedRetry: () -> Unit,
    onCancelArmedRetry: () -> Unit,
    onInstall: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenRootMigration: () -> Unit,
    onOpenVeyraKsu: () -> Unit,
    onOpenReboot: () -> Unit,
    /**
     * Bumped by every return to the foreground, and owned by the shell rather than by this page.
     *
     * Two screens read the phone's state and both go stale the same way - the package list after a
     * manager is installed, and a Shizuku grant made in another app - so the event is counted once, by
     * the shell, instead of each page keeping a counter of the same thing.
     */
    resumeTick: Int,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    var migrationPlan by remember {
        mutableStateOf(RootMigrationStore.plan(context))
    }
    var migrationMessage by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(resumeTick, installState.phase) {
        migrationPlan = RootMigrationStore.plan(context)
    }
    // Home reports Veyra's own execution stack now: KernelSU, CVeyra and the optional
    // VeyraKSU bridge. Shizuku remains an implementation compatibility option elsewhere, but it is
    // no longer presented as the service Veyra itself depends on.
    var readiness by remember {
        mutableStateOf(
            OverviewStatusCache.readiness ?: Readiness(
                kernelSu = if (RootStatusProbe.isActiveQuick()) {
                    KernelSuStatus.Active
                } else {
                    KernelSuStatus.Unreadable
                },
                cveyra = cveyraStatus(context),
                managers = ManagerPresence(),
                veyraKsu = VeyraKsuState(),
                veyraKsuTagged = VeyraKsuPreferences.autoInstallAfterRoot(context),
            ),
        )
    }
    var rootAccess by remember {
        mutableStateOf(
            OverviewStatusCache.rootAccess
                ?: RootAccessProbe.cached()
                ?: RootAccessSnapshot(),
        )
    }
    var showAbout by remember { mutableStateOf(false) }
    var showRootControlPage by rememberSaveable { mutableStateOf(false) }
    var rootMethod by remember {
        mutableStateOf(AppPreferences.rootMethod(context))
    }
    val fastSupportRaw = remember(device) { FastRootSupport.forSnapshot(device) }
    val currentFlavor = AppPreferences.kernelsuFlavor(context)
    val fastSupport = remember(fastSupportRaw, currentFlavor) {
        if (currentFlavor == KernelSuFlavor.ReSukiSU) {
            fastSupportRaw.copy(
                available = false,
                reason = "New (fast) currently supports KernelSU and KernelSU-Next, not ReSukiSU.",
            )
        } else {
            fastSupportRaw
        }
    }
    LaunchedEffect(fastSupport.available) {
        if (!fastSupport.available && rootMethod == RootMethod.Fast) {
            rootMethod = RootMethod.Standard
            AppPreferences.setRootMethod(context, RootMethod.Standard)
        }
    }

    LaunchedEffect(installState.phase, resumeTick) {
        val refreshed = withContext(Dispatchers.IO) {
            if (CVeyraPreferences.enabled(context)) {
                runCatching { CVeyraController.probeBackend(context) }
            }
            val nextReadiness = Readiness(
                kernelSu = KernelSuRuntime.status(context),
                cveyra = cveyraStatus(context),
                managers = ManagerPresence.of(KernelSuManager.installedManagers(context)),
                veyraKsu = VeyraKsuBridge.readState(),
                veyraKsuTagged = VeyraKsuPreferences.autoInstallAfterRoot(context),
            )
            val nextRoot = RootAccessProbe.probe(context)
            nextReadiness to nextRoot
        }
        readiness = refreshed.first
        rootAccess = refreshed.second
        OverviewStatusCache.update(refreshed.first, refreshed.second)
    }
    val rootActive = rootAccess.granted

    if (showRootControlPage) {
        RootControlPage(
            padding = padding,
            rootAccess = rootAccess,
            current = rootMethod,
            fastSupport = fastSupport,
            onSelect = { method ->
                rootMethod = method
                AppPreferences.setRootMethod(context, method)
            },
            onOpenManager = {
                KernelSuManager.open(
                    context,
                    AppPreferences.kernelsuFlavor(context),
                ) { message ->
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                }
            },
            onOpenVeyraKsu = onOpenVeyraKsu,
            onOpenMigration = onOpenRootMigration,
            onBack = { showRootControlPage = false },
        )
        return
    }

    PageList(
        padding = padding,
        listState = rememberPageListState(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 54.dp, bottom = 14.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                shape = RoundedCornerShape(28.dp),
                tonalElevation = 0.dp,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Image(
                        painter = painterResource(R.drawable.ic_veyra_mark),
                        contentDescription = "Veyra",
                        modifier = Modifier.size(58.dp),
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Veyra",
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                        )
                    }
                    IconButton(
                        onClick = {
                            clickHaptic(view)
                            onOpenReboot()
                        },
                    ) {
                        Icon(
                            Icons.Rounded.PowerSettingsNew,
                            contentDescription = stringResource(R.string.reboot_sheet_title),
                            modifier = Modifier.size(26.dp),
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }
        item { InstallStatusCard(installState, onInstall) }
        if (rootActive) {
            item {
                RootActiveCard(
                    method = rootMethod,
                    rootAccess = rootAccess,
                    onClick = {
                        clickHaptic(view)
                        if (rootAccess.shouldOpenMigration) {
                            onOpenRootMigration()
                        } else {
                            showRootControlPage = true
                        }
                    },
                )
            }
        }
        // Above the readiness card, because it is about something that already happened rather than
        // something to check, and it is the only account of a restart the user asked for: the dialog
        // that started it could only say the request was made.
        if (frameworkRestart != null) {
            item {
                FrameworkRestartCard(
                    report = frameworkRestart,
                    onDismiss = onDismissFrameworkRestart,
                )
            }
        }
        // Above the readiness card, and above everything else that is only information: this one is
        // waiting on a decision, and it changes what the next boot does.
        if (armedRetry != null && !installState.busy) {
            item {
                ArmedRetryCard(
                    retry = armedRetry,
                    payload = retryPayload,
                    onStart = onStartArmedRetry,
                    onCancel = onCancelArmedRetry,
                )
            }
        }
        if (migrationPlan.stage == RootMigrationStage.Prepared) {
            item {
                MigrationReadyHomeCard(
                    provider = migrationPlan.providerLabel
                        ?: migrationPlan.providerPackage
                        ?: stringResource(R.string.migration_unknown_provider),
                    message = migrationMessage,
                    onStart = {
                        migrationMessage = null
                        scope.launch {
                            val requested = requestReboot(context)
                            if (requested) {
                                RootMigrationStore.markRebootRequested(context)
                                migrationPlan = RootMigrationStore.plan(context)
                            } else {
                                migrationMessage = context.getString(
                                    R.string.migration_reboot_failed,
                                )
                            }
                        }
                    },
                    onCancel = {
                        RootMigrationStore.cancel(context)
                        migrationPlan = RootMigrationStore.plan(context)
                        migrationMessage = null
                    },
                )
            }
        }
        item { ReadinessCard(readiness, onOpenSettings) }
        item { DeviceCard(device) }
        item {
            HomeLinkRow(
                icon = Icons.Rounded.Info,
                title = stringResource(R.string.about),
                position = SettingsCardPosition.GroupedSingle,
                onClick = { showAbout = true },
            )
        }
    }
    if (showAbout) {
        AboutDialog(onDismiss = { showAbout = false })
    }
}

@Composable
private fun MigrationReadyHomeCard(
    provider: String,
    message: String?,
    onStart: () -> Unit,
    onCancel: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    Icons.Rounded.SwapHoriz,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.migration_home_title),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        stringResource(R.string.migration_home_provider, provider),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                stringResource(R.string.migration_home_body),
                style = MaterialTheme.typography.bodyMedium,
            )
            message?.let {
                Text(
                    it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Button(
                onClick = onStart,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Rounded.RestartAlt, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.migration_home_start))
            }
            TextButton(
                onClick = onCancel,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.migration_cancel))
            }
        }
    }
}

private sealed interface UpdateStatus {
    data object Idle : UpdateStatus
    data object Checking : UpdateStatus
    data class Available(val info: UpdateInfo) : UpdateStatus
    data class Downloading(val info: UpdateInfo, val progress: Float) : UpdateStatus
    data object UpToDate : UpdateStatus
    data object Failed : UpdateStatus
}

private val UpdateStatus.busy: Boolean
    get() = this is UpdateStatus.Checking || this is UpdateStatus.Downloading

private val UpdateStatus.info: UpdateInfo?
    get() = when (this) {
        is UpdateStatus.Available -> this.info
        is UpdateStatus.Downloading -> this.info
        else -> null
    }

/**
 * What the last framework restart came back with.
 *
 * The dialog that starts the restart can only say the request was made: the framework it would report a
 * result in is the one being replaced. So the result is this, on the screen the app opens on - which is
 * the first place anyone looks once the phone has come back - and it is here rather than only in
 * Settings because the action that produced it can be started from either one.
 *
 * The account comes from [ZygoteRestartReport.describe], the same words the app log gets, so the card
 * and the log cannot end up saying different things about one restart. It is dismissible and does not
 * come back: the record is read away as it is read, because a result read twice is reported twice.
 */
@Composable
private fun FrameworkRestartCard(report: FrameworkRestartReport, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val view = LocalView.current
    val attention = report.needsAttention
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = if (attention) {
                MaterialTheme.colorScheme.errorContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerHighest
            },
            contentColor = if (attention) {
                MaterialTheme.colorScheme.onErrorContainer
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        ),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    if (attention) Icons.Rounded.Warning else Icons.Rounded.RestartAlt,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                )
                Text(
                    text = stringResource(R.string.framework_restart_title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = {
                        clickHaptic(view)
                        onDismiss()
                    },
                    modifier = Modifier.size(24.dp),
                ) {
                    Icon(
                        Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.action_close),
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
            ZygoteRestartReport.describe(context, report).forEach { line ->
                Text(text = line, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun UpdateCard(
    status: UpdateStatus,
    onDismiss: () -> Unit,
    onStartDownload: (UpdateInfo) -> Unit,
) {
    val view = LocalView.current
    val info = status.info
    if (info == null) return
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        ),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    Icons.Rounded.SystemUpdate,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                )
                Text(
                    text = stringResource(R.string.updater_available_title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = {
                        clickHaptic(view)
                        onDismiss()
                    },
                    modifier = Modifier.size(24.dp),
                ) {
                    Icon(
                        Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.action_close),
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
            Text(
                text = stringResource(R.string.updater_available_body, info.versionName),
                style = MaterialTheme.typography.bodyMedium,
            )
            when (status) {
                is UpdateStatus.Downloading -> {
                    LinearProgressIndicator(
                        progress = { status.progress },
                        modifier = Modifier.fillMaxWidth(),
                        color = LocalContentColor.current,
                        trackColor = LocalContentColor.current.copy(alpha = 0.2f),
                        drawStopIndicator = {},
                    )
                    Text(
                        text = stringResource(R.string.updater_downloading),
                        style = MaterialTheme.typography.bodyMedium,
                        color = LocalContentColor.current.copy(alpha = 0.78f),
                    )
                }
                else -> {
                    FilledTonalButton(onClick = {
                        clickHaptic(view)
                        onStartDownload(info)
                    }) {
                        Text(stringResource(R.string.updater_button_download))
                    }
                }
            }
        }
    }
}

/**
 * A row on the home screen that does something, rather than one that reports a state.
 *
 * What separates it from the settings cards it otherwise sits with is that there is no description: the
 * icon, the name and the state are the whole row, so a row that has no state to report is just its name.
 * That is deliberate - this app's settings cards carry chevrons because they open something, and a row
 * whose tap does what its own label already says has nothing left to promise.
 *
 * [busy] dims the row and takes its tap, because a row that is already working has nothing to offer a
 * second tap, and a spinner where the icon was says which row is doing the work.
 */
@Composable
private fun HomeLinkRow(
    icon: ImageVector,
    title: String,
    position: SettingsCardPosition = SettingsCardPosition.Single,
    /** What this row reports, if anything. Blank draws nothing. */
    value: String = "",
    busy: Boolean = false,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val view = LocalView.current
    Card(
        enabled = !busy,
        onClick = {
            clickHaptic(view)
            onClick()
        },
        modifier = Modifier.fillMaxWidth(),
        shape = expressiveClickableCardShape(interactionSource, position),
        interactionSource = interactionSource,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 15.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (busy) {
                LoadingIndicator(modifier = Modifier.size(28.dp))
            } else {
                Icon(icon, contentDescription = null, modifier = Modifier.size(28.dp))
            }
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            if (value.isNotBlank()) {
                Text(
                    value,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    // Capped like a settings card's value: an unweighted Row child is measured before
                    // the weighted one beside it, so a long one would leave the title a character per
                    // line. One line here, because a row this height has no second line to give.
                    modifier = Modifier.widthIn(max = SETTINGS_VALUE_MAX_WIDTH),
                    textAlign = TextAlign.End,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** What the updates row says it is doing, in the one line a home row has room for. */
@Composable
private fun updateRowValue(status: UpdateStatus): String = when (status) {
    is UpdateStatus.Available ->
        stringResource(R.string.updater_available_body_short, status.info.versionName)
    is UpdateStatus.Downloading -> stringResource(R.string.updater_downloading)
    is UpdateStatus.Checking -> stringResource(R.string.updater_checking)
    is UpdateStatus.UpToDate -> stringResource(R.string.updater_up_to_date)
    is UpdateStatus.Failed -> stringResource(R.string.updater_failed)
    UpdateStatus.Idle -> ""
}

/**
 * The one-shot retry, on screen from the moment it is armed until it is used or cancelled.
 *
 * This card is the whole answer to "root on boot was off, and the phone installed anyway". An armed
 * retry overrides that setting by design - the gate's rule is that either way of asking gets the boot's
 * single attempt - and while it was invisible the only conclusion left was that the setting had been
 * ignored. It is not a new promise: the retry ran before and runs now. It can just be seen, taken, and
 * taken back.
 *
 * The two states differ in what it is honest to offer. Before the reboot, only cancelling: the run was
 * armed *because* the user chose a restart over an immediate retry, so offering to start it now would
 * be offering the attempt they turned down. After the reboot, starting it is the one thing left - the
 * boot gate runs it by itself only where a cached payload exists to run from, and the device whose last
 * run failed is the device with no cache.
 */
@Composable
private fun ArmedRetryCard(
    retry: ArmedRetry,
    payload: CachedPayload?,
    onStart: () -> Unit,
    onCancel: () -> Unit,
) {
    val view = LocalView.current
    // Where the payload came from, when the record says. The commit is shown short, the way the history
    // shows it: what a pinned source means is "these bytes", and seven characters are enough to answer
    // "is this the revision I pinned?".
    val payloadSource = payload?.takeIf { it.sourceLabel.isNotBlank() }?.let { attempted ->
        if (attempted.sourceCommit.isBlank()) {
            stringResource(R.string.retry_armed_payload_from, attempted.sourceLabel)
        } else {
            stringResource(
                R.string.retry_armed_payload_from_at,
                attempted.sourceLabel,
                attempted.sourceCommit.take(7),
            )
        }
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Icon(
                    Icons.Rounded.History,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(
                            if (retry.afterReboot) {
                                R.string.retry_armed_pending_title
                            } else {
                                R.string.retry_armed_next_boot_title
                            },
                        ),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        stringResource(
                            if (retry.afterReboot) {
                                R.string.retry_armed_pending_body
                            } else {
                                R.string.retry_armed_next_boot_body
                            },
                        ),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    // Named before the restart, not only in the log afterwards: what a retry runs is the
                    // attempt that failed, which is not necessarily the payload the app would choose by
                    // itself - and on a device somebody is testing payloads on, that is the whole point.
                    if (payload != null) {
                        Text(
                            stringResource(
                                R.string.retry_armed_payload,
                                payload.displayName.ifBlank { payload.profileId },
                            ),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        payloadSource?.let { source ->
                            Text(source, style = MaterialTheme.typography.labelSmall)
                        }
                    } else {
                        Text(
                            stringResource(R.string.retry_armed_payload_none),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            ) {
                TextButton(
                    onClick = {
                        clickHaptic(view)
                        onCancel()
                    },
                ) {
                    Text(stringResource(R.string.retry_armed_cancel))
                }
                if (retry.afterReboot) {
                    Button(
                        onClick = {
                            clickHaptic(view)
                            onStart()
                        },
                    ) {
                        Text(stringResource(R.string.retry_armed_start))
                    }
                }
            }
        }
    }
}

/**
 * One version in the manager chooser.
 *
 * The default is marked rather than given a row of its own, because it is a version like any other: what
 * makes it the default is that it is what the app installs when nothing is named, and a user picking it
 * is asking for that same thing.
 */
@Composable
private fun ManagerVersionRow(
    version: String,
    isDefault: Boolean,
    selected: Boolean,
    onPick: () -> Unit,
) {
    val view = LocalView.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable {
                clickHaptic(view)
                onPick()
            }
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(
            version,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        if (isDefault) {
            Text(
                stringResource(R.string.settings_manager_versions_default),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** How tall the version list may grow before it scrolls, so the dialog still fits a short screen. */
private val MANAGER_VERSION_LIST_MAX = 220.dp

/** How tall the staged-file list may grow before it scrolls inside the dialog. */
private val RESIDUE_LIST_MAX = 300.dp

@Composable
private fun InstallStatusCard(installState: InstallUiState, onInstall: () -> Unit) {
    val verdict = verdictColors(runVerdict(installState.phase, installState.busy))
    val context = LocalContext.current
    val view = LocalView.current
    val interactionSource = remember { MutableInteractionSource() }
    val uriHandler = LocalUriHandler.current
    // Read here rather than passed in, because the card has to name whichever KernelSU the app is set
    // to: the two managers are different apps with different packages and only one of them is the one
    // a finished run leaves needing to be opened.
    val managerFlavor = remember(installState) { AppPreferences.kernelsuFlavor(context) }
    val managerInstalled = remember(installState, managerFlavor) {
        KernelSuManager.isInstalled(context, managerFlavor)
    }
    Card(
        onClick = {
            clickHaptic(view)
            when {
                installState.busy -> Unit
                installState.phase == InstallPhase.Installed -> {
                    // A named version that is not the default has to be looked up before there is a
                    // download to offer, so the card says what it is doing rather than appearing to
                    // ignore the tap.
                    KernelSuManager.open(context, managerFlavor) { message ->
                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                    }
                }
                else -> onInstall()
            }
        },
        modifier = Modifier.fillMaxWidth().animateContentSize(),
        shape = expressiveClickableCardShape(interactionSource),
        interactionSource = interactionSource,
        // The run's own verdict, not the accent: this card said "fine" about a failed run, on the screen a
        // phone opens on.
        colors = CardDefaults.cardColors(
            containerColor = verdict.container,
            contentColor = verdict.content,
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            when {
                installState.busy -> LoadingIndicator(
                    modifier = Modifier.size(44.dp),
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                installState.phase == InstallPhase.Installed -> Icon(
                    Icons.Rounded.CheckCircle, contentDescription = null, modifier = Modifier.size(44.dp),
                )
                installState.phase == InstallPhase.RootOnly -> Icon(
                    Icons.Rounded.LockOpen, contentDescription = null, modifier = Modifier.size(44.dp),
                )
                installState.phase == InstallPhase.Failed -> Icon(
                    Icons.Rounded.Warning, contentDescription = null, modifier = Modifier.size(44.dp),
                )
                else -> Icon(
                    Icons.Rounded.Warning, contentDescription = null, modifier = Modifier.size(44.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                if (installState.phase == InstallPhase.Installed) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_kernelsu),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        Text(
                            text = stringResource(R.string.status_ksu_active),
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                } else {
                    Text(
                        text = when (installState.phase) {
                            InstallPhase.Ready -> stringResource(R.string.status_not_installed)
                            else -> installState.message
                        },
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                Text(
                    text = when (installState.phase) {
                        InstallPhase.Installed -> stringResource(
                            if (managerInstalled) {
                                R.string.install_tap_open_manager
                            } else {
                                R.string.install_tap_manager
                            },
                        )
                        InstallPhase.Failed -> stringResource(R.string.install_tap_retry)
                        else -> stringResource(R.string.install_tap_start)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.86f),
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun RootActiveCard(
    method: RootMethod,
    rootAccess: RootAccessSnapshot,
    onClick: () -> Unit,
) {
    val verdict = verdictColors(runVerdict(InstallPhase.Installed, false))
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = verdict.container,
            contentColor = verdict.content,
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Icon(
                Icons.Rounded.CheckCircle,
                contentDescription = null,
                modifier = Modifier.size(44.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_veyra_mark),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = Color.White,
                    )
                    Text(
                        text = stringResource(R.string.status_root_active),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = if (rootAccess.shouldOpenMigration) {
                            "Tap to open root migration"
                        } else {
                            "Tap to open root manager"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.78f),
                        maxLines = 1,
                    )
                }
                Text(
                    text = when (rootAccess.kind) {
                        RootAccessKind.VeyraTemporary ->
                            rootAccess.badge() + " · " + method.label
                        RootAccessKind.ExternalTemporary ->
                            rootAccess.badge() + " · " + rootAccess.providerName()
                        else ->
                            rootAccess.badge() + " · " + rootAccess.providerName()
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.86f),
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun RootControlPage(
    padding: PaddingValues,
    rootAccess: RootAccessSnapshot,
    current: RootMethod,
    fastSupport: FastRootSupport,
    onSelect: (RootMethod) -> Unit,
    onOpenManager: () -> Unit,
    onOpenVeyraKsu: () -> Unit,
    onOpenMigration: () -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val view = LocalView.current
    var showAddonMenu by remember { mutableStateOf(false) }
    var showMarked by rememberSaveable { mutableStateOf(false) }

    if (showMarked) {
        VeyraMarkedPage(
            padding = padding,
            onBack = { showMarked = false },
        )
        return
    }

    if (showAddonMenu) {
        AlertDialog(
            onDismissRequest = { showAddonMenu = false },
            icon = {
                Image(
                    painter = painterResource(R.drawable.ic_veyra_mark),
                    contentDescription = null,
                    modifier = Modifier.size(30.dp),
                )
            },
            title = { Text("Addon menu") },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    FilledTonalButton(
                        onClick = {
                            showAddonMenu = false
                            onOpenVeyraKsu()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Rounded.Memory, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("VeyraKSU")
                    }
                    FilledTonalButton(
                        onClick = {
                            showAddonMenu = false
                            onOpenMigration()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Rounded.SwapHoriz, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Root provider migration")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAddonMenu = false }) {
                    Text("Close")
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = {
                        clickHaptic(view)
                        onBack()
                    },
                ) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Root manager",
                        style = MaterialTheme.typography.headlineMedium,
                    )
                    Text(
                        rootAccess.badge() + " · " + rootAccess.providerName(),
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                FilledTonalButton(
                    onClick = {
                        clickHaptic(view)
                        showAddonMenu = true
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Rounded.Apps, contentDescription = null)
                    Spacer(Modifier.width(7.dp))
                    Text("Open Addon menu", maxLines = 1)
                }
                FilledTonalButton(
                    onClick = {
                        clickHaptic(view)
                        onOpenManager()
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_kernelsu),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(7.dp))
                    Text("Open root manager", maxLines = 1)
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
                        "Root method",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        "Standard stays the default. New (fast) can only be selected when this Android/kernel combination is supported.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    RootMethodChoice(
                        method = RootMethod.Standard,
                        selected = current == RootMethod.Standard,
                        enabled = true,
                        summary = "Veyra standard payload / Magic Builder route",
                        onClick = { onSelect(RootMethod.Standard) },
                    )
                    RootMethodChoice(
                        method = RootMethod.Fast,
                        selected = current == RootMethod.Fast,
                        enabled = fastSupport.available,
                        summary = fastSupport.reason,
                        onClick = { onSelect(RootMethod.Fast) },
                    )
                    Text(
                        if (fastSupport.available) {
                            "If a device has only one usable route, Veyra automatically uses that route."
                        } else {
                            "New (fast) is disabled for this device."
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        item {
            FilledTonalButton(
                onClick = {
                    clickHaptic(view)
                    showMarked = true
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_veyra_mark),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(Modifier.width(9.dp))
                Text("Veyra Marked +")
            }
        }
    }
}

@Composable
private fun VeyraMarkedPage(
    padding: PaddingValues,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val view = LocalView.current
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            runCatching { VMarkedRepository.refresh(context) }
        }
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(
                start = 22.dp,
                end = 22.dp,
                top = padding.calculateTopPadding() + 20.dp,
                bottom = padding.calculateBottomPadding() + 24.dp,
            ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = {
                    clickHaptic(view)
                    onBack()
                },
            ) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null)
            }
            Image(
                painter = painterResource(R.drawable.ic_veyra_mark),
                contentDescription = null,
                modifier = Modifier.size(38.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                "Veyra Marked +",
                style = MaterialTheme.typography.headlineMedium,
            )
        }

        Spacer(modifier = Modifier.weight(1f))
        HorizontalDivider()
        Text(
            "Soon, stay hyped",
            modifier = Modifier.padding(top = 10.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun RootMethodDialog(
    current: RootMethod,
    fastSupport: FastRootSupport,
    onSelect: (RootMethod) -> Unit,
    onOpenManager: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Root manager")
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                FilledTonalButton(
                    onClick = onOpenManager,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_kernelsu),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Open root manager")
                }

                Text(
                    "Root method",
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    "Standard stays the default. New (fast) can only be selected when this Android/kernel combination is supported.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                RootMethodChoice(
                    method = RootMethod.Standard,
                    selected = current == RootMethod.Standard,
                    enabled = true,
                    summary = "Veyra standard payload / Magic Builder route",
                    onClick = { onSelect(RootMethod.Standard) },
                )
                RootMethodChoice(
                    method = RootMethod.Fast,
                    selected = current == RootMethod.Fast,
                    enabled = fastSupport.available,
                    summary = if (fastSupport.available) {
                        fastSupport.reason
                    } else {
                        fastSupport.reason
                    },
                    onClick = { onSelect(RootMethod.Fast) },
                )

                Text(
                    if (fastSupport.available) {
                        "If a device has only one usable route, Veyra automatically uses that route."
                    } else {
                        "New (fast) is disabled for this device."
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done")
            }
        },
    )
}

@Composable
private fun RootMethodChoice(
    method: RootMethod,
    selected: Boolean,
    enabled: Boolean,
    summary: String,
    onClick: () -> Unit,
) {
    val alpha = if (enabled) 1f else 0.45f
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(alpha)
            .clip(MaterialTheme.shapes.medium)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        RadioButton(
            selected = selected,
            onClick = if (enabled) onClick else null,
            enabled = enabled,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                method.label,
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * The two things a run needs, on the screen the app opens on.
 *
 * Both are facts about the device rather than about this app's settings, which is exactly why they
 * belong here: *is KernelSU loaded this boot* and *can this app use Shizuku* decide what a run can even
 * attempt, and answering them by opening Settings and reading two rows about preferences is the long way
 * round. The rows lead there anyway, because a state that is wrong is something the user will want to
 * fix rather than merely know.
 *
 * KernelSU is loaded **per boot**, so this is a statement about the current boot and not about the
 * device's history - a phone that was rooted yesterday reads as not loaded, which is the reason root on
 * boot exists.
 */
@Composable
private fun ReadinessCard(readiness: Readiness, onOpenSettings: () -> Unit) {
    val context = LocalContext.current
    val view = LocalView.current
    var expanded by remember {
        mutableStateOf(AppPreferences.homeStatusExpanded(context))
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        ),
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        clickHaptic(view, sound = UiSoundKind.Expand)
                        expanded = !expanded
                        AppPreferences.setHomeStatusExpanded(context, expanded)
                    }
                    .padding(horizontal = 18.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.readiness),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = stringResource(
                        if (expanded) R.string.settings_section_collapse
                        else R.string.settings_section_expand,
                    ),
                )
            }
            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier.padding(
                        start = 18.dp,
                        end = 18.dp,
                        bottom = 18.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    InfoRow(
                        icon = Icons.Rounded.Security,
                        label = stringResource(R.string.readiness_kernelsu),
                        value = stringResource(
                            when (readiness.kernelSu) {
                                KernelSuStatus.Active -> R.string.readiness_ksu_active
                                KernelSuStatus.NotLoaded -> R.string.readiness_ksu_not_loaded
                                KernelSuStatus.Unreadable -> R.string.readiness_ksu_unreadable
                            },
                        ),
                        onClick = {
                            clickHaptic(view)
                            onOpenSettings()
                        },
                    )
                    if (readiness.kernelSu == KernelSuStatus.Active) {
                        val activeFlavor = AppPreferences.loadedFlavor(context)
                            ?: AppPreferences.kernelsuFlavor(context)
                        ManagerRow(
                            label = stringResource(
                                R.string.readiness_manager_row,
                                activeFlavor.label,
                            ),
                            installed = readiness.managers.installed(activeFlavor),
                            onClick = onOpenSettings,
                        )
                    }
                    InfoRow(
                        icon = Icons.Rounded.Terminal,
                        label = stringResource(R.string.readiness_cveyra),
                        value = stringResource(
                            when (readiness.cveyra) {
                                CVeyraStatus.Root -> R.string.readiness_cveyra_root
                                CVeyraStatus.WirelessAdb -> R.string.readiness_cveyra_adb
                                CVeyraStatus.Waiting -> R.string.readiness_cveyra_waiting
                                CVeyraStatus.Disabled -> R.string.readiness_cveyra_not_running
                            },
                        ),
                        onClick = {
                            clickHaptic(view)
                            onOpenSettings()
                        },
                    )
                    if (readiness.kernelSu != KernelSuStatus.Active) {
                        for (flavor in KernelSuFlavor.entries) {
                            ManagerRow(
                                label = stringResource(
                                    R.string.readiness_manager_row,
                                    flavor.label,
                                ),
                                installed = readiness.managers.installed(flavor),
                                onClick = onOpenSettings,
                            )
                        }
                    }
                    InfoRow(
                        icon = Icons.Rounded.Memory,
                        label = stringResource(R.string.veyra_ksu_title),
                        value = stringResource(
                            when {
                                readiness.veyraKsu.installed &&
                                    readiness.veyraKsu.enabled &&
                                    readiness.veyraKsu.stage.isNotBlank() ->
                                    R.string.veyra_ksu_status_active
                                readiness.veyraKsu.installed && readiness.veyraKsu.enabled ->
                                    R.string.veyra_ksu_status_enabled
                                readiness.veyraKsu.installed ->
                                    R.string.veyra_ksu_status_disabled
                                readiness.veyraKsuTagged ->
                                    R.string.veyra_ksu_status_tagged
                                else -> R.string.veyra_ksu_status_not_installed
                            },
                        ),
                        onClick = {
                            clickHaptic(view)
                            onOpenSettings()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ManagerRow(label: String, installed: Boolean, onClick: () -> Unit) {
    val view = LocalView.current
    InfoRow(
        icon = Icons.Rounded.SystemUpdate,
        label = label,
        value = stringResource(
            if (installed) R.string.readiness_installed else R.string.readiness_not_installed,
        ),
        onClick = {
            clickHaptic(view)
            onClick()
        },
    )
}

@Composable
private fun DeviceCard(device: DeviceSnapshot) {
    val context = LocalContext.current
    val view = LocalView.current
    var expanded by remember {
        mutableStateOf(AppPreferences.homeDeviceExpanded(context))
    }
    var kernelExpanded by remember {
        mutableStateOf(AppPreferences.homeKernelExpanded(context))
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        ),
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        clickHaptic(view, sound = UiSoundKind.Expand)
                        expanded = !expanded
                        AppPreferences.setHomeDeviceExpanded(context, expanded)
                    }
                    .padding(horizontal = 18.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.device_information),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = stringResource(
                        if (expanded) R.string.settings_section_collapse
                        else R.string.settings_section_expand,
                    ),
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier.padding(
                        start = 18.dp,
                        end = 18.dp,
                        bottom = 18.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    InfoRow(
                        Icons.Rounded.Memory,
                        stringResource(R.string.device),
                        "${device.manufacturer} ${device.model} (${device.device})",
                    )
                    InfoRow(
                        Icons.Rounded.Code,
                        stringResource(R.string.firmware),
                        device.buildId,
                    )
                    InfoRow(
                        Icons.Rounded.Info,
                        stringResource(R.string.system),
                        "Android ${device.androidRelease} (API ${device.sdk})",
                    )
                    InfoRow(
                        icon = Icons.Rounded.Info,
                        label = stringResource(R.string.kernel),
                        value = if (kernelExpanded) {
                            device.kernelVersionFull
                        } else {
                            device.kernelRelease
                        },
                        onClick = {
                            clickHaptic(view, sound = UiSoundKind.Expand)
                            kernelExpanded = !kernelExpanded
                            AppPreferences.setHomeKernelExpanded(context, kernelExpanded)
                        },
                    )
                    InfoRow(
                        Icons.Rounded.Security,
                        stringResource(R.string.system_abi),
                        "${device.abi} (${device.pageSize / 1024}K)",
                    )
                }
            }
        }
    }
}

@Composable
private fun InfoRow(
    icon: ImageVector,
    label: String,
    value: String,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = if (onClick != null) {
            Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.medium)
                .clickable(onClick = onClick)
        } else {
            Modifier
        },
        horizontalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Column {
            Text(label, style = MaterialTheme.typography.titleSmall)
            Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * How often a run's record is re-read while it is still being written.
 *
 * A second, because the entry is a small file saved once per log line and the screen is showing a run that
 * is happening now: a longer tick reads as a stalled log, and a shorter one is a file read per frame for no
 * more information than the file has.
 */
private const val HISTORY_LIVE_TICK_MILLIS = 1000L

/**
 * How long the page waits between looking for a run it was asked to open but cannot find yet.
 *
 * Short, because the entry is already on disk when the notification that named it was posted - what the
 * wait is for is this process's copy of the history being older than that run, not the entry arriving.
 */
private const val RUN_LOOKUP_TICK_MILLIS = 400L

/**
 * How many times it looks before deciding the device does not have that run.
 *
 * Bounded for the same reason the live tick is: a request that can never be answered must not leave the page
 * reloading a file forever, and eight looks over three seconds is longer than a file read needs.
 */
private const val RUN_LOOKUP_ATTEMPTS = 8

/**
 * The result of the entry this page is showing, from the whole history rather than the filtered list.
 *
 * Read from the full list because it is asked about a run in flight: a filter that hides the entry would
 * otherwise stop the record from being re-read, which is exactly the run whose log is still moving.
 */
private fun selectedEntryResult(
    history: List<InstallHistoryEntry>,
    id: String?,
): InstallRunResult? = history.firstOrNull { it.id == id }?.result

@Composable
private fun HistoryPage(
    padding: PaddingValues,
    history: List<InstallHistoryEntry>,
    listState: LazyListState,
    snackbarHostState: SnackbarHostState,
    onDeleteEntries: (Set<String>) -> Unit,
    onRestoreEntries: (List<InstallHistoryEntry>) -> Unit,
    onOpenHome: () -> Unit,
    /** A run something outside this page asked to see, by its entry id, or null. */
    openEntryId: String?,
    onEntryOpened: () -> Unit,
    /** Re-read the history from disk, for a run another process is still writing. */
    onReloadHistory: () -> Unit,
) {
    val view = LocalView.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedHistoryId by remember { mutableStateOf<String?>(null) }
    var selectionIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    /**
     * Deletes now and offers the undo, rather than asking first.
     *
     * A dialog is the right shape for something that cannot be taken back and the wrong one for something that
     * can: it charges every delete a second tap to protect against the rare mistaken one, where the log is on
     * this phone and the undo costs nothing. The entries are captured before they go, because the store is the
     * only copy and an undo cannot re-read what it just removed.
     */
    val deleteWithUndo: (Set<String>) -> Unit = { ids ->
        val doomed = history.filter { it.id in ids }
        if (doomed.isNotEmpty()) {
            onDeleteEntries(ids)
            selectionIds = emptySet()
            scope.launch {
                val answer = snackbarHostState.showSnackbar(
                    message = context.resources.getQuantityString(
                        R.plurals.history_deleted,
                        doomed.size,
                        doomed.size,
                    ),
                    actionLabel = context.getString(R.string.action_undo),
                    withDismissAction = true,
                    duration = SnackbarDuration.Short,
                )
                if (answer == SnackbarResult.ActionPerformed) onRestoreEntries(doomed)
            }
        }
    }
    // saveable: picking a destination starts another activity, which can recreate this one while the
    // picker is up, and the ids are the only record of what the export was for.
    var pendingExportIds by rememberSaveable { mutableStateOf(arrayListOf<String>()) }
    val exportLogsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val ids = pendingExportIds.toSet()
        pendingExportIds = arrayListOf()
        result.data?.data?.let { uri ->
            val entries = history.filter { it.id in ids && it.result != InstallRunResult.Running }
            if (entries.isNotEmpty()) HistoryLogExporter.saveArchive(context, uri, entries)
        }
    }
    val launchExport: (Set<String>) -> Unit = { ids ->
        // A run that is still going has no finished log to archive, so it cannot be part of one.
        val entries = history.filter { it.id in ids && it.result != InstallRunResult.Running }
        if (entries.isNotEmpty()) {
            pendingExportIds = ArrayList(entries.map { it.id })
            exportLogsLauncher.launch(
                Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "application/zip"
                    putExtra(Intent.EXTRA_TITLE, HistoryLogExporter.archiveFileName(entries))
                },
            )
        }
    }
    // saveable for the same reason the export ids are: another activity can recreate this screen, and a
    // filter that quietly resets itself while the user is reading a filtered list is worse than none.
    var resultFilter by rememberSaveable { mutableStateOf(HistoryFilter.All) }
    val resultChoices = remember(history, resultFilter) {
        historyResultFilters(history, resultFilter)
    }
    val filtered = remember(history, resultFilter) {
        filterHistory(history, resultFilter)
    }
    val filtersActive = resultFilter != HistoryFilter.All
    // Picking a filter drops the selection: the stand-down is the safe direction, since a selection the
    // new filter hides would otherwise sit there counting itself on a button nobody can see it under.
    val onResultFilter: (HistoryFilter) -> Unit = { choice ->
        resultFilter = choice
        selectionIds = emptySet()
    }
    /** Back to the whole history. Used by the chip and by the card an emptied list shows. */
    val clearFilters: () -> Unit = {
        resultFilter = HistoryFilter.All
        selectionIds = emptySet()
    }
    // Opened once the entry is in the list: it arrives with an intent, and this page's copy of the history
    // was read when the app started - which can be before the run the intent names began, since a boot run
    // writes its entry from another process. So a miss is looked for again rather than believed, and the
    // look is what turns "the tap did nothing" into the run it asked for.
    //
    // Bounded, because an entry that never turns up is one this device does not have - a record deleted
    // since. That case says so and drops the request, which is the part that matters: the page used to do
    // nothing at all, leaving whatever run was already on screen looking like the one that was asked for.
    // The caller is told either way, so a return to this page does not reopen it.
    var looksForRun by remember(openEntryId) { mutableStateOf(0) }
    LaunchedEffect(openEntryId, history) {
        val wanted = openEntryId ?: return@LaunchedEffect
        if (history.any { it.id == wanted }) {
            // A filter left over from earlier must not stand in the way of the run something asked to see:
            // the request names one run, and a list that hides it would open nothing at all.
            if (filtered.none { it.id == wanted }) resultFilter = HistoryFilter.All
            selectedHistoryId = wanted
            onEntryOpened()
            return@LaunchedEffect
        }
        if (looksForRun >= RUN_LOOKUP_ATTEMPTS) {
            onEntryOpened()
            scope.launch { snackbarHostState.showSnackbar(context.getString(R.string.history_run_not_here)) }
            return@LaunchedEffect
        }
        looksForRun++
        delay(RUN_LOOKUP_TICK_MILLIS)
        onReloadHistory()
    }
    // A run another process is on - the boot gate's, above all - writes its entry as it goes, so the record
    // is live and re-reading it is what makes this screen show that run rather than a snapshot of it.
    //
    // Two things say a run is still going and either is enough. The stored verdict is the usual one. The
    // shared record is the other, and it is not redundant: an app that opens while a boot run is in flight
    // closes every unfinished entry it thinks was interrupted, so a record can read failed while the process
    // that owns it is still writing it - and a screen that stopped following on the verdict alone would stop
    // on a run that is happening. The follow ends when neither says so, and the reload that shows the run's
    // own next line is what puts the verdict back.
    LaunchedEffect(selectedHistoryId, selectedEntryResult(history, selectedHistoryId)) {
        val selectedId = selectedHistoryId ?: return@LaunchedEffect
        while (true) {
            val claim = RunInFlight.holder(context)?.entryId
            if (selectedEntryResult(history, selectedId) != InstallRunResult.Running &&
                claim != selectedId
            ) {
                return@LaunchedEffect
            }
            delay(HISTORY_LIVE_TICK_MILLIS)
            onReloadHistory()
        }
    }
    val selectedEntry = remember(filtered, selectedHistoryId) {
        filtered.firstOrNull { it.id == selectedHistoryId }
    }
    val selectableIds = remember(filtered) {
        filtered
            .filter { it.result != InstallRunResult.Running }
            .map { it.id }
            .toSet()
    }
    val selecting = selectionIds.isNotEmpty()
    BackHandler(enabled = selectedEntry != null || selecting) {
        if (selecting) {
            selectionIds = emptySet()
        } else {
            selectedHistoryId = null
        }
    }

    AnimatedContent(
        targetState = selectedEntry,
        contentKey = { it?.id ?: "history-list" },
        label = "history-detail",
    ) { entry ->
        if (entry == null) {
            HistoryList(
                padding = padding,
                history = filtered,
                listState = listState,
                totalRuns = history.size,
                filtersActive = filtersActive,
                resultFilter = resultFilter,
                resultChoices = resultChoices,
                onResultFilter = onResultFilter,
                onClearFilters = clearFilters,
                onOpenHome = onOpenHome,
                selectionIds = selectionIds,
                selectableIds = selectableIds,
                onToggleSelection = { id ->
                    selectionIds = if (id in selectionIds) {
                        selectionIds - id
                    } else {
                        selectionIds + id
                    }
                },
                onSelectAll = {
                    selectionIds = if (selectionIds.size == selectableIds.size) {
                        emptySet()
                    } else {
                        selectableIds
                    }
                },
                onClearSelection = { selectionIds = emptySet() },
                onEntryClick = { selectedHistoryId = it.id },
                onDeleteSelected = { deleteWithUndo(selectionIds) },
                onExportSelected = { launchExport(selectionIds) },
            )
        } else {
            HistoryDetail(
                padding = padding,
                entry = entry,
                onBack = { selectedHistoryId = null },
            )
        }
    }
}

@Composable
private fun HistoryList(
    padding: PaddingValues,
    history: List<InstallHistoryEntry>,
    listState: LazyListState,
    totalRuns: Int,
    filtersActive: Boolean,
    resultFilter: HistoryFilter,
    resultChoices: List<HistoryFilter>,
    onResultFilter: (HistoryFilter) -> Unit,
    onClearFilters: () -> Unit,
    onOpenHome: () -> Unit,
    selectionIds: Set<String>,
    selectableIds: Set<String>,
    onToggleSelection: (String) -> Unit,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
    onEntryClick: (InstallHistoryEntry) -> Unit,
    onDeleteSelected: () -> Unit,
    onExportSelected: () -> Unit,
) {
    val view = LocalView.current
    val selecting = selectionIds.isNotEmpty()
    Box(modifier = Modifier.fillMaxSize().padding(padding)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(
                start = 20.dp,
                top = 20.dp,
                end = 20.dp,
                // The selection buttons and, above them, the bar the whole app draws over its pages - the
                // last run has to be readable with both stacked over the bottom corner.
                bottom = 96.dp + NAV_BAR_HEIGHT,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.history_title),
                            style = MaterialTheme.typography.headlineLarge,
                        )
                        // What the filter is leaving out, said before the list rather than after it: a
                        // short list and a filtered list look the same otherwise.
                        if (totalRuns > 0) {
                            Text(
                                text = if (filtersActive) {
                                    stringResource(R.string.history_filter_count, history.size, totalRuns)
                                } else {
                                    pluralStringResource(R.plurals.history_run_count, totalRuns, totalRuns)
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    AnimatedVisibility(
                        visible = selecting,
                        enter = fadeIn() + scaleIn(initialScale = 0.9f),
                        exit = fadeOut() + scaleOut(targetScale = 0.9f),
                    ) {
                        Row {
                            IconButton(onClick = {
                                clickHaptic(view)
                                onSelectAll()
                            }) {
                                Icon(
                                    Icons.Rounded.SelectAll,
                                    contentDescription = stringResource(R.string.history_select_all),
                                )
                            }
                            IconButton(onClick = {
                                clickHaptic(view)
                                onClearSelection()
                            }) {
                                Icon(
                                    Icons.Rounded.Close,
                                    contentDescription = stringResource(R.string.history_clear_selection),
                                )
                            }
                        }
                    }
                }
            }
            // One chip per kind of run this history holds, so a filter cannot be offered that would
            // only ever empty the list. Hidden entirely with nothing to filter.
            if (totalRuns > 0) {
                item {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        resultChoices.forEach { choice ->
                            HistoryFilterChip(
                                label = stringResource(historyFilterLabel(choice)),
                                selected = choice == resultFilter,
                                onClick = { onResultFilter(choice) },
                            )
                        }
                    }
                }
            }
            if (history.isEmpty()) {
                item {
                    if (filtersActive && totalRuns > 0) {
                        EmptyHistoryFilterCard(onClearFilters)
                    } else {
                        EmptyHistoryCard(onOpenHome)
                    }
                }
            } else {
                itemsIndexed(history, key = { _, entry -> entry.id }) { _, entry ->
                    HistoryEntryCard(
                        entry = entry,
                        selectionMode = selecting,
                        isSelected = entry.id in selectionIds,
                        selectable = entry.id in selectableIds,
                        onClick = {
                            if (selecting) {
                                onToggleSelection(entry.id)
                            } else {
                                onEntryClick(entry)
                            }
                        },
                        onLongClick = {
                            if (entry.id in selectableIds) onToggleSelection(entry.id)
                        },
                    )
                }
            }
        }
        Column(
            // Lifted by the bar's height, because the bar is drawn over this page: the stack's own 20dp of
            // air, then the pill, or the export and delete buttons would sit behind it.
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp)
                .padding(bottom = NAV_BAR_HEIGHT),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            BackToTopFab(listState)
            AnimatedVisibility(
                visible = selecting,
                enter = fadeIn() + scaleIn(initialScale = 0.85f),
                exit = fadeOut() + scaleOut(targetScale = 0.85f),
            ) {
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    ExtendedFloatingActionButton(
                        onClick = {
                            clickHaptic(view)
                            onExportSelected()
                        },
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        icon = { Icon(Icons.Rounded.Save, contentDescription = null) },
                        text = { Text(stringResource(R.string.history_export_selected, selectionIds.size)) },
                    )
                    ExtendedFloatingActionButton(
                        onClick = {
                            clickHaptic(view)
                            onDeleteSelected()
                        },
                        icon = { Icon(Icons.Rounded.Delete, contentDescription = null) },
                        text = { Text(stringResource(R.string.history_delete_selected, selectionIds.size)) },
                    )
                }
            }
        }
    }
}

/** The label a result chip wears: the same words the card for that result uses. */
private fun historyFilterLabel(filter: HistoryFilter): Int = when (filter) {
    HistoryFilter.All -> R.string.history_filter_all
    HistoryFilter.Succeeded -> R.string.history_succeeded
    HistoryFilter.RootOnly -> R.string.history_root_only
    HistoryFilter.Failed -> R.string.history_failed
    HistoryFilter.Stopped -> R.string.history_stopped
    HistoryFilter.Running -> R.string.history_running
}

@Composable
private fun HistoryFilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val view = LocalView.current
    FilterChip(
        selected = selected,
        onClick = {
            clickHaptic(view)
            onClick()
        },
        label = { Text(label) },
    )
}

/**
 * Shown when a filter emptied the list.
 *
 * It says which of the two empty states this is and offers the way out, because "no runs yet" on a phone
 * with runs in it reads as a bug - and the chips that would explain it are above the fold in a list that
 * is now empty.
 */
@Composable
private fun EmptyHistoryFilterCard(onClearFilters: () -> Unit) {
    val view = LocalView.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        ),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                stringResource(R.string.history_empty_filtered_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                stringResource(R.string.history_empty_filtered_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FilledTonalButton(
                onClick = {
                    clickHaptic(view)
                    onClearFilters()
                },
            ) {
                Text(stringResource(R.string.history_filter_clear))
            }
        }
    }
}

/**
 * An empty history says what would fill it and offers the way there.
 *
 * The list is empty for one reason only - nothing has run yet - so a card that only described the page
 * left the one thing it should say unsaid. Home is where a run is started, and the button is that:
 * the same place the navigation bar goes, reached from the page that has nothing on it.
 */
@Composable
private fun EmptyHistoryCard(onOpenHome: () -> Unit) {
    val view = LocalView.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Icon(Icons.Rounded.History, contentDescription = null, modifier = Modifier.size(32.dp))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.history_empty_title), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.history_empty_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FilledTonalButton(onClick = {
                    clickHaptic(view)
                    onOpenHome()
                }) {
                    Text(stringResource(R.string.history_empty_action))
                }
            }
        }
    }
}

@Composable
private fun HistoryEntryCard(
    entry: InstallHistoryEntry,
    selectionMode: Boolean,
    isSelected: Boolean,
    selectable: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val view = LocalView.current
    val interactionSource = remember { MutableInteractionSource() }
    val shape = expressiveClickableCardShape(interactionSource)
    val containerColor = historyResultContainerColor(entry.result)
    val contentColor = historyResultContentColor(entry.result)
    val borderWidth by animateDpAsState(
        targetValue = if (selectionMode && isSelected) 2.dp else 0.dp,
        label = "history-card-border",
    )
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .combinedClickable(
                interactionSource = interactionSource,
                onClick = {
                    clickHaptic(view)
                    onClick()
                },
                onLongClick = {
                    clickHaptic(view)
                    onLongClick()
                },
            ),
        shape = shape,
        border = if (borderWidth > 0.dp) {
            BorderStroke(borderWidth, MaterialTheme.colorScheme.secondary)
        } else {
            null
        },
        colors = CardDefaults.cardColors(
            containerColor = containerColor,
            contentColor = contentColor,
        ),
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 15.dp)
                .animateContentSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(13.dp),
        ) {
            Crossfade(
                targetState = selectionMode,
                label = "history-leading",
                modifier = Modifier.size(48.dp),
            ) { selecting ->
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    if (selecting) {
                        Checkbox(
                            checked = isSelected,
                            onCheckedChange = null,
                            enabled = selectable,
                        )
                    } else {
                        Icon(historyResultIcon(entry.result), contentDescription = null, modifier = Modifier.size(30.dp))
                    }
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(historyResultLabel(entry.result), style = MaterialTheme.typography.titleMedium)
                Text(
                    formatHistoryTime(entry.startedAtMillis),
                    style = MaterialTheme.typography.bodyMedium,
                    color = contentColor.copy(alpha = 0.78f),
                )
            }
            if (!selectionMode) {
                Icon(Icons.Rounded.ChevronRight, contentDescription = null)
            }
        }
    }
}

@Composable
private fun HistoryDetail(
    padding: PaddingValues,
    entry: InstallHistoryEntry,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val exportLogLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        result.data?.data?.let { uri -> HistoryLogExporter.saveLog(context, uri, entry) }
    }
    PageList(
        padding = padding,
        listState = rememberPageListState(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(
                modifier = Modifier.padding(top = 12.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                IconButton(onClick = {
                    clickHaptic(view)
                    onBack()
                }) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.action_back))
                }
                Text(
                    stringResource(R.string.history_detail_title),
                    style = MaterialTheme.typography.headlineLarge,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = {
                    clickHaptic(view)
                    copyLogToClipboard(context, entry.log)
                }) {
                    Icon(Icons.Rounded.ContentCopy, contentDescription = stringResource(R.string.action_copy_log))
                }
                IconButton(onClick = {
                    clickHaptic(view)
                    exportLogLauncher.launch(
                        Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                            addCategory(Intent.CATEGORY_OPENABLE)
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TITLE, HistoryLogExporter.entryFileName(entry))
                        },
                    )
                }) {
                    Icon(Icons.Rounded.Save, contentDescription = stringResource(R.string.export_log))
                }
            }
        }
        item { HistoryResultCard(entry) }
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                ),
            ) {
                Text(
                    text = entry.log.ifBlank { stringResource(R.string.history_log_empty) },
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun HistoryResultCard(entry: InstallHistoryEntry) {
    val containerColor = historyResultContainerColor(entry.result)
    val contentColor = historyResultContentColor(entry.result)
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = containerColor, contentColor = contentColor),
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Icon(historyResultIcon(entry.result), contentDescription = null, modifier = Modifier.size(38.dp))
            Column {
                Text(historyResultLabel(entry.result), style = MaterialTheme.typography.titleLarge)
                Text(
                    stringResource(R.string.history_started, formatHistoryTime(entry.startedAtMillis)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = contentColor.copy(alpha = 0.78f),
                )
                entry.completedAtMillis?.let { completedAt ->
                    Text(
                        stringResource(R.string.history_completed, formatHistoryTime(completedAt)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = contentColor.copy(alpha = 0.78f),
                    )
                }
                entry.profileId?.let { profileId ->
                    Text(
                        stringResource(R.string.history_payload, profileId),
                        style = MaterialTheme.typography.bodyMedium,
                        color = contentColor.copy(alpha = 0.78f),
                    )
                }
                // Names the catalog, and the revision of it, that this run's payload came from:
                // two sources can offer the same payload id, so the id alone does not say which
                // catalog defined the payload that ran.
                entry.sourceLabel?.let { label ->
                    val commit = entry.sourceCommit
                    Text(
                        if (commit.isNullOrBlank()) {
                            stringResource(R.string.history_source, label)
                        } else {
                            stringResource(R.string.history_source_commit, label, commit.take(7))
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = contentColor.copy(alpha = 0.78f),
                    )
                }
                entry.failureStage?.let { stage ->
                    Text(
                        stringResource(
                            R.string.history_failure,
                            stringResource(stage.label),
                            entry.failureReason.orEmpty(),
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = contentColor.copy(alpha = 0.78f),
                    )
                }
                Text(
                    stringResource(
                        if (entry.usedShizuku) {
                            R.string.history_shizuku_used
                        } else {
                            R.string.history_shizuku_not_used
                        },
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = contentColor.copy(alpha = 0.78f),
                )
            }
        }
    }
}

@Composable
private fun historyResultLabel(result: InstallRunResult): String = stringResource(
    when (result) {
        InstallRunResult.Running -> R.string.history_running
        InstallRunResult.Succeeded -> R.string.history_succeeded
        InstallRunResult.RootOnly -> R.string.history_root_only
        InstallRunResult.Failed -> R.string.history_failed
        InstallRunResult.Stopped -> R.string.history_stopped
    },
)

private fun historyResultIcon(result: InstallRunResult): ImageVector = verdictIcon(runVerdict(result))

/**
 * A stored run's colours, from the one verdict palette every surface shares.
 *
 * The two wrappers are kept because the history rows are drawn from a result and not from a phase, and the
 * mapping between the two is exactly the thing that used to exist in four places.
 */
@Composable
private fun historyResultContainerColor(result: InstallRunResult): Color =
    verdictColors(runVerdict(result)).container

@Composable
private fun historyResultContentColor(result: InstallRunResult): Color =
    verdictColors(runVerdict(result)).content

@Composable
private fun formatHistoryTime(timestamp: Long): String {
    val locale = LocalConfiguration.current.locales[0]
    return remember(timestamp, locale) {
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.MEDIUM, locale)
            .format(Date(timestamp))
    }
}

/**
 * The app's own log, as a tab.
 *
 * Separate from History on purpose, because the two answer different questions. History is what a run
 * did - one entry per run, with the payload's whole output, kept because a result is worth keeping.
 * This is what the app did, line by line and in order, across runs and between them: which transport
 * was chosen, why a boot install stood down, what a download was refused for. It is the thing to read
 * first when something did not work, which is why it is a tab rather than a file.
 */
@Composable
private fun LogsPage(padding: PaddingValues) {
    val view = LocalView.current
    val context = LocalContext.current
    val entries by AppLog.log.collectAsStateWithLifecycle()
    var minLevel by remember { mutableStateOf(AppLogLevel.Debug) }
    var query by rememberSaveable { mutableStateOf("") }
    var confirmClear by remember { mutableStateOf(false) }
    val filter = LogFilter(minLevel = minLevel, query = query)

    // The file is the record and this process is not the only one that writes to it: a boot install,
    // and everything logged before this screen existed, is in there and nowhere else. Read on opening
    // the tab and off the main thread, since it can be half a megabyte.
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) { AppLog.reload() }
    }

    val shown = remember(entries, filter) {
        entries
            .filter(filter::matches)
            .takeLast(MAX_LOG_ROWS)
    }
    val hiddenRows = remember(entries, filter) {
        (entries.count(filter::matches) - shown.size).coerceAtLeast(0)
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            icon = { Icon(Icons.Rounded.Delete, contentDescription = null) },
            title = {
                DialogDimAmount(0.34f)
                Text(stringResource(R.string.logs_clear_title))
            },
            text = { Text(stringResource(R.string.logs_clear_body)) },
            confirmButton = {
                FilledTonalButton(onClick = {
                    clickHaptic(view)
                    AppLog.clear()
                    query = ""
                    minLevel = AppLogLevel.Debug
                    confirmClear = false
                }) {
                    Text(stringResource(R.string.logs_clear))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    clickHaptic(view)
                    confirmClear = false
                }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    PageList(
        padding = padding,
        listState = rememberPageListState(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.logs_title),
                        style = MaterialTheme.typography.headlineLarge,
                    )
                    Text(
                        text = if (hiddenRows > 0) {
                            stringResource(R.string.logs_count_window, shown.size, hiddenRows)
                        } else {
                            pluralStringResource(R.plurals.logs_count, shown.size, shown.size)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(
                    enabled = shown.isNotEmpty(),
                    onClick = {
                        clickHaptic(view)
                        copyLogToClipboard(context, AppLog.asText(shown))
                    },
                ) {
                    Icon(
                        Icons.Rounded.ContentCopy,
                        contentDescription = stringResource(R.string.logs_copy),
                        modifier = Modifier.size(20.dp),
                    )
                }
                IconButton(
                    enabled = entries.isNotEmpty(),
                    onClick = {
                        clickHaptic(view)
                        confirmClear = true
                    },
                ) {
                    Icon(
                        Icons.Rounded.Delete,
                        contentDescription = stringResource(R.string.logs_clear),
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // A floor rather than a match: a warning is what makes someone open this tab, and the
                // reason for it is in the lines below it. Errors is that same floor under the name of the
                // thing people come here to find - one chip, not two, because an error is only the loudest
                // warning and a second chip at that level showed a subset of this one.
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    LogFilterChip(
                        label = R.string.logs_filter_all,
                        selected = filter.minLevel == AppLogLevel.Debug,
                    ) { minLevel = AppLogLevel.Debug }
                    LogFilterChip(
                        label = R.string.logs_filter_info,
                        selected = filter.minLevel == AppLogLevel.Info,
                    ) { minLevel = AppLogLevel.Info }
                    LogFilterChip(
                        label = R.string.logs_filter_error,
                        selected = filter.errorsOnly,
                    ) { minLevel = filter.withErrorsOnly(!filter.errorsOnly).minLevel }
                }
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text(stringResource(R.string.logs_search)) },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    trailingIcon = if (query.isEmpty()) {
                        null
                    } else {
                        {
                            IconButton(onClick = {
                                clickHaptic(view)
                                query = ""
                            }) {
                                Icon(Icons.Rounded.Close, contentDescription = null)
                            }
                        }
                    },
                )
            }
        }
        if (shown.isEmpty()) {
            item {
                EmptyLogsCard(
                    filtered = entries.isNotEmpty(),
                    // The same one tap the history's filtered card has, and the same reason: the body
                    // names the three controls that emptied the list, and a list of three things to go
                    // and undo by hand is a worse answer than a button that undoes all of them.
                    onClearFilters = {
                        minLevel = AppLogLevel.Debug
                        query = ""
                    },
                )
            }
        } else {
            // Newest first: the line someone is looking for is almost always the last thing that
            // happened, and a log that opens at the top of a scroll is a log nobody reads to the end.
            item { LogSectionLabel(stringResource(R.string.logs_newest_first)) }
            items(shown.asReversed()) { entry -> LogEntryRow(entry) }
        }
    }
}

/**
 * How many lines the tab keeps for the screen.
 *
 * The log itself holds more; what this bounds is what one filter pass and one list have to carry while
 * a run is printing. The count beside the title says when it is hiding some, so a missing line is
 * stated rather than silent.
 */
private const val MAX_LOG_ROWS = 1000

@Composable
private fun LogFilterChip(
    label: Int,
    selected: Boolean,
    onSelected: () -> Unit,
) {
    val view = LocalView.current
    FilterChip(
        selected = selected,
        onClick = {
            clickHaptic(view)
            onSelected()
        },
        label = { Text(stringResource(label)) },
    )
}

@Composable
private fun LogSectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 6.dp),
    )
}

/**
 * One line of the log: when, how loud, who - then what.
 *
 * Two rows rather than one, because a message is the thing being read and a tag is only how it is
 * found; side by side, the tag's width would decide how much of the message fits. Monospace for the
 * same reason a terminal uses it: a stack trace or a path lines up with the one above it.
 */
@Composable
private fun LogEntryRow(entry: AppLogEntry) {
    val color = logLevelColor(entry.level)
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = AppLogFormat.stamp(entry.atMillis),
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = entry.level.mark.toString(),
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = color,
            )
            Text(
                text = entry.tag,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
        Text(
            text = entry.message,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = if (entry.level == AppLogLevel.Warn || entry.level == AppLogLevel.Error) {
                color
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        )
    }
}

@Composable
private fun logLevelColor(level: AppLogLevel): Color = when (level) {
    AppLogLevel.Debug -> MaterialTheme.colorScheme.onSurfaceVariant
    AppLogLevel.Info -> MaterialTheme.colorScheme.primary
    AppLogLevel.Warn -> MaterialTheme.colorScheme.tertiary
    AppLogLevel.Error -> MaterialTheme.colorScheme.error
}

@Composable
private fun EmptyLogsCard(filtered: Boolean, onClearFilters: () -> Unit) {
    val view = LocalView.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        ),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(
                    if (filtered) R.string.logs_empty_filtered_title else R.string.logs_empty_title,
                ),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(
                    if (filtered) R.string.logs_empty_filtered_body else R.string.logs_empty_body,
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // Only when there is a filter to clear: an unfiltered empty log has nothing to undo, and the
            // body above says what will fill it instead.
            if (filtered) {
                FilledTonalButton(onClick = {
                    clickHaptic(view)
                    onClearFilters()
                }) {
                    Text(stringResource(R.string.logs_filter_clear))
                }
            }
        }
    }
}

@Composable
private fun SettingsPage(
    padding: PaddingValues,
    device: DeviceSnapshot,
    accentColor: AccentColor,
    themeMode: AppThemeMode,
    advancedMode: Boolean,
	disableKsuModules: Boolean,
    loadKernelSu: Boolean,
    kernelsuFlavor: KernelSuFlavor,
    shizukuMode: Boolean,
    payloadSources: List<PayloadSource>,
    bootRootMode: Boolean,
    restartAfterRoot: Boolean,
    shizukuBootMode: Boolean,
    bootSettleSeconds: Int,
    autoRootSettleSeconds: Int,
    runLimits: RunLimitsSettings,
    exploitOverride: ExploitOverrideSettings,
    shizukuToken: String,
    partitionReadOnly: Boolean,
    payloadMode: PayloadMode,
    batteryUnrestricted: Boolean,
    navBarGlass: Boolean,
    /**
     * Bumped by every return to the foreground, because installing a manager leaves this screen.
     *
     * The manager rows are read from the package list, and the way a manager gets installed is a
     * hand-off to another app's installer: the user comes back to a screen whose package walk was made
     * before the package existed. Keying the read on this is what makes the row say "installed" when it
     * is, instead of waiting for the app to be restarted or the flavour to be changed.
     */
    resumeTick: Int,
    onAccentColorChanged: (AccentColor) -> Unit,
    onThemeModeChanged: (AppThemeMode) -> Unit,
    onAdvancedModeChanged: (Boolean) -> Unit,
	onDisableKsuModulesChanged: (Boolean) -> Unit,
    onLoadKernelSuChanged: (Boolean) -> Unit,
    onKernelsuFlavorChanged: (KernelSuFlavor) -> Unit,
    onManagerVersionChanged: (String) -> Unit,
    onShizukuModeChanged: (Boolean) -> Unit,
    onPayloadSourcesChanged: (List<PayloadSource>) -> Unit,
    onBootRootModeChanged: (Boolean) -> Unit,
    onRestartAfterRootChanged: (Boolean) -> Unit,
    onShizukuBootModeChanged: (Boolean) -> Unit,
    onBootSettleChanged: (Int) -> Unit,
    onAutoRootSettleChanged: (Int) -> Unit,
    onRunLimitChanged: (RunLimit, Int) -> Unit,
    onExploitOverrideChanged: (ExploitOverrideSettings) -> Unit,
    onShizukuTokenChanged: (String) -> Unit,
    onPartitionReadOnlyChanged: (Boolean) -> Unit,
    onPayloadModeChanged: (PayloadMode) -> Unit,
    onForgetCachedPayload: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onRequestBatteryExemption: () -> Unit,
    onNavBarGlassChanged: (Boolean) -> Unit,
    onCVeyraAccessStateChanged: (Boolean) -> Unit,
    /** True while an attempt to start Shizuku is in flight, which the rows report as a state. */
    shizukuStarting: Boolean,
    /** Runs Shizuku's starter through the root shell. One implementation, shared with Home. */
    startShizuku: () -> Unit,
    /** Asks for this app's Shizuku grant, and answers whether it landed. */
    requestShizukuPermission: suspend () -> Boolean,
    runPlan: () -> RunPlanDisplay,
    /** A card to open on, handed over by a screen that was told to open it, or null. */
    openTarget: String? = null,
    /** Called once the jump has been started, so nothing replays it. */
    onOpenTargetHandled: () -> Unit = {},
) {
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showFlavorDialog by remember { mutableStateOf(false) }
    var showManagerVersionDialog by remember { mutableStateOf(false) }
    var managerVersionDraft by remember { mutableStateOf("") }
    var flavorMenuTop by remember { mutableStateOf(0.dp) }
    var showColorDialog by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }
    var showSoundDialog by remember { mutableStateOf(false) }
    var settingsLayoutMode by remember {
        mutableStateOf(AppPreferences.settingsLayoutMode(context))
    }
    var selectedSettingsSection by rememberSaveable {
        mutableStateOf<SettingsSection?>(null)
    }
    var showPayloadBuilderPage by rememberSaveable { mutableStateOf(false) }
    var showMagicBuilderPage by rememberSaveable { mutableStateOf(false) }
    var showMagicBuilderSettingsPage by rememberSaveable { mutableStateOf(false) }
    var showBuilderSettingsPage by rememberSaveable { mutableStateOf(false) }
    var showVeyraKsuPage by rememberSaveable { mutableStateOf(false) }
    var showRootMigrationPage by rememberSaveable { mutableStateOf(false) }
    // What this app has left in /data/local/tmp, read once when the screen is opened rather than on
    // every pass: the staging changes during a run, not while a settings list is on screen, and the
    // reading is a stat per catalogued path. Null is "not read yet" and is shown as such, because a
    // check that has not answered must not look like a check that found nothing.
    var residue by remember { mutableStateOf<ResidueReport?>(null) }
    var showResidueDialog by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        val report = withContext(Dispatchers.IO) { StagedResidue.read() }
        residue = report
        // Filed where the reading can be copied out of, because the list is worth having outside the
        // app for exactly one reason: to compare it against what something else reports seeing.
        AppLog.info(AppLogTags.STAGING, report.logLine(context))
    }
    var showShizukuMissingDialog by remember { mutableStateOf(false) }
    // Read live, not once at composition: Shizuku hands out its binder asynchronously after the
    // service starts, so a snapshot taken while the screen is being built can say "not running" about
    // a service that is already up - which is how this row came to offer a start that had nothing to
    // do. The sticky listener below fires immediately with the current state and again whenever the
    // binder arrives or goes away, so the row follows the service instead of a frame in time.
    var shizukuAvailability by remember { mutableStateOf(ShizukuController.availability()) }
    var cveyraAccessActive by remember { mutableStateOf(CVeyraAccessStore.isActive(context)) }
    DisposableEffect(Unit) {
        val received = Shizuku.OnBinderReceivedListener {
            shizukuAvailability = ShizukuController.availability()
        }
        val dead = Shizuku.OnBinderDeadListener {
            shizukuAvailability = ShizukuController.availability()
        }
        Shizuku.addBinderReceivedListenerSticky(received)
        Shizuku.addBinderDeadListener(dead)
        onDispose {
            Shizuku.removeBinderReceivedListener(received)
            Shizuku.removeBinderDeadListener(dead)
        }
    }
    // The third way the answer changes, and the only one Shizuku will not tell us about: a grant or a
    // revocation made in the Shizuku app itself, which sends this app no callback and does not kill the
    // binder. Reading the state again whenever this screen comes back is what keeps the rows below
    // describing the device rather than a snapshot from the last time the app was in front.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                shizukuAvailability = ShizukuController.availability()
                cveyraAccessActive = CVeyraAccessStore.isActive(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    var showPayloadSourcesSheet by remember { mutableStateOf(false) }
    var showLocalPayloadDialog by remember { mutableStateOf(false) }
    var showRunPlanDialog by remember { mutableStateOf(false) }
    var localPayloadName by remember { mutableStateOf(LocalPayload.displayName(context)) }
    var languageMenuTop by remember { mutableStateOf(32.dp) }
    var colorMenuTop by remember { mutableStateOf(32.dp) }
    var themeMenuTop by remember { mutableStateOf(32.dp) }
    var bootSettleMenuTop by remember { mutableStateOf(32.dp) }
    var showBootSettleDialog by remember { mutableStateOf(false) }
    var showRunLimitsDialog by remember { mutableStateOf(false) }
    var autoRootSettleMenuTop by remember { mutableStateOf(32.dp) }
    var showAutoRootSettleDialog by remember { mutableStateOf(false) }
    var showShizukuTokenDialog by remember { mutableStateOf(false) }
    var tokenDraft by remember { mutableStateOf("") }

    // Veyra-owned privilege service state. Kept separate from the original Shizuku transport
    // so the existing root/run implementation remains intact while CVeyra grows its own API.
    var cveyraEnabled by remember { mutableStateOf(CVeyraPreferences.enabled(context)) }
    var cveyraStartAsRoot by remember { mutableStateOf(CVeyraPreferences.startAsRoot(context)) }
    var cveyraPromoteAfterBoot by remember { mutableStateOf(CVeyraPreferences.promoteAfterBoot(context)) }
    var cveyraStartOnBoot by remember { mutableStateOf(CVeyraPreferences.startOnBoot(context)) }
    var cveyraTokenEnabled by remember { mutableStateOf(CVeyraPreferences.tokenEnabled(context)) }
    var cveyraToken by remember { mutableStateOf(CVeyraPreferences.startToken(context)) }
    var cveyraBackend by remember { mutableStateOf(CVeyraPreferences.backend(context)) }
    var systemUpdateState by remember { mutableStateOf(SystemUpdateControl.snapshot(context)) }
    var systemUpdateBusy by remember { mutableStateOf(false) }
    var systemUpdateMessage by remember { mutableStateOf<String?>(null) }
    var showCVeyraSetupDialog by remember { mutableStateOf(false) }
    var showCVeyraRootDialog by remember { mutableStateOf(false) }
    var cveyraSetupBusy by remember { mutableStateOf(false) }
    var cveyraSetupMessage by remember { mutableStateOf<String?>(null) }
    var showCVeyraAccessPage by remember { mutableStateOf(false) }
    var openCVeyraManagementDirect by remember { mutableStateOf(false) }
    var showLegacyAdbDialog by remember { mutableStateOf(false) }
    var legacyAdbPort by remember { mutableStateOf("5555") }
    var legacyAdbResult by remember { mutableStateOf("") }

    var showWirelessAdbDialog by remember { mutableStateOf(false) }
    var wirelessSnapshot by remember { mutableStateOf<WirelessAdbSnapshot?>(null) }
    var wirelessBusy by remember { mutableStateOf(false) }
    var payloadModeMenuTop by remember { mutableStateOf(32.dp) }
    var showPayloadModeDialog by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    val currentLanguageTag = AppPreferences.languageTag(context)

    val refreshCVeyraBackend: (Boolean) -> Unit = { showMissing ->
        cveyraSetupBusy = true
        scope.launch {
            val backend = withContext(Dispatchers.IO) {
                CVeyraController.probeBackend(context)
            }
            cveyraBackend = backend
            cveyraSetupBusy = false
            if (showMissing && cveyraEnabled && backend == CVeyraBackend.None) {
                cveyraSetupMessage = null
                showCVeyraSetupDialog = true
            } else if (backend != CVeyraBackend.None) {
                showCVeyraSetupDialog = false
                cveyraSetupMessage = null
            }
        }
    }

    LaunchedEffect(resumeTick, cveyraEnabled, cveyraStartAsRoot) {
        if (cveyraEnabled) refreshCVeyraBackend(true)
    }

    LaunchedEffect(resumeTick, cveyraBackend) {
        systemUpdateState = SystemUpdateControl.snapshot(context).copy(backend = cveyraBackend)
    }

    if (showCVeyraRootDialog) {
        CVeyraRootAccessDialog(
            backend = cveyraBackend,
            busy = cveyraSetupBusy,
            message = cveyraSetupMessage,
            onRequestRoot = {
                clickHaptic(view)
                cveyraSetupBusy = true
                cveyraSetupMessage = null
                if (!cveyraEnabled) {
                    cveyraEnabled = true
                    CVeyraController.setEnabled(context, true)
                }
                scope.launch {
                    val granted = withContext(Dispatchers.IO) {
                        CVeyraController.requestRootAccess(context)
                    }
                    cveyraSetupBusy = false
                    if (granted) {
                        cveyraStartAsRoot = true
                        cveyraBackend = CVeyraBackend.Root
                        cveyraSetupMessage =
                            context.getString(R.string.cveyra_root_menu_granted)
                        showCVeyraRootDialog = false
                    } else {
                        cveyraStartAsRoot = false
                        CVeyraPreferences.setStartAsRoot(context, false)
                        cveyraSetupMessage =
                            context.getString(R.string.cveyra_root_menu_failed)
                    }
                }
            },
            onOpenManager = {
                clickHaptic(view)
                KernelSuManager.open(
                    context,
                    AppPreferences.kernelsuFlavor(context),
                ) { message ->
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                }
            },
            onUseAdb = {
                clickHaptic(view)
                cveyraStartAsRoot = false
                CVeyraPreferences.setStartAsRoot(context, false)
                if (!cveyraEnabled) {
                    cveyraEnabled = true
                    CVeyraController.setEnabled(context, true)
                } else {
                    CVeyraController.start(context)
                }
                showCVeyraRootDialog = false
                refreshCVeyraBackend(false)
            },
            onDismiss = {
                cveyraSetupMessage = null
                showCVeyraRootDialog = false
            },
        )
    }

    if (showCVeyraSetupDialog) {
        CVeyraSetupDialog(
            backend = cveyraBackend,
            busy = cveyraSetupBusy,
            message = cveyraSetupMessage,
            onGrantRoot = {
                clickHaptic(view)
                cveyraSetupBusy = true
                cveyraSetupMessage = null
                scope.launch {
                    val granted = withContext(Dispatchers.IO) {
                        CVeyraController.requestRootAccess(context)
                    }
                    cveyraSetupBusy = false
                    if (granted) {
                        cveyraStartAsRoot = true
                        cveyraEnabled = true
                        cveyraBackend = CVeyraBackend.Root
                        showCVeyraSetupDialog = false
                    } else {
                        cveyraSetupMessage = context.getString(R.string.cveyra_setup_root_failed)
                    }
                }
            },
            onPairWireless = {
                clickHaptic(view)
                context.startActivity(
                    AdbPairingSetupActivity.pairingIntent(
                        context,
                        forceRepair = AdbCredentialStore.hasStoredKey(context),
                    ),
                )
                cveyraSetupMessage = context.getString(R.string.cveyra_setup_pair_started)
            },
            onOpenWireless = {
                clickHaptic(view)
                showCVeyraSetupDialog = false
                wirelessSnapshot = WirelessAdbDiagnostics.passiveSnapshot(context)
                showWirelessAdbDialog = true
            },
            onDismiss = { showCVeyraSetupDialog = false },
        )
    }

    if (showShizukuMissingDialog) {
        AlertDialog(
            onDismissRequest = { showShizukuMissingDialog = false },
            icon = { Icon(Icons.Rounded.Info, contentDescription = null) },
            title = {
                DialogDimAmount(0.34f)
                Text(stringResource(R.string.shizuku_not_running_title))
            },
            text = { Text(stringResource(R.string.shizuku_not_running_body)) },
            confirmButton = {
                FilledTonalButton(onClick = {
                    clickHaptic(view)
                    showShizukuMissingDialog = false
                    openShizukuManager(context)
                }) {
                    Text(stringResource(R.string.action_download_shizuku))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    clickHaptic(view)
                    showShizukuMissingDialog = false
                }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    if (showRunPlanDialog) {
        RunPlanDialog(display = runPlan(), onDismiss = { showRunPlanDialog = false })
    }

    if (showRunLimitsDialog) {
        RunLimitsDialog(
            limits = runLimits,
            override = exploitOverride,
            onChanged = onRunLimitChanged,
            onOverrideChanged = onExploitOverrideChanged,
            // Put through the same callback the menu uses, once per ceiling: a reset is three ordinary
            // changes, and going around the path that persists and re-reads them would be a second way
            // for the stored values to be written. The override goes back with them: this dialog is one
            // screen of "how a run is paced", and a reset that left half of it where it was would be
            // the wrong half of a promise.
            onReset = {
                RunLimit.entries.forEach { limit ->
                    onRunLimitChanged(limit, RunLimits.defaultSeconds(limit))
                }
                onExploitOverrideChanged(ExploitOverride.defaults())
                Toast.makeText(
                    context,
                    context.getString(R.string.run_limits_reset_done),
                    Toast.LENGTH_SHORT,
                ).show()
            },
            onDismiss = { showRunLimitsDialog = false },
        )
    }

    if (showLocalPayloadDialog) {
        LocalPayloadDialog(
            initialName = localPayloadName,
            onDismiss = { showLocalPayloadDialog = false },
            onNameChanged = { name -> localPayloadName = name },
        )
    }

    if (showLanguageDialog) {
        SideChoiceMenu(
            choices = languageOptions.map { stringResource(it.label) },
            selectedIndex = languageOptions.indexOfFirst { languageMatches(it, currentLanguageTag) }
                .coerceAtLeast(0),
            topOffset = languageMenuTop,
            onSelected = { index ->
                showLanguageDialog = false
                AppPreferences.setLanguage(context, languageOptions[index].tag)
            },
            onDismiss = { showLanguageDialog = false },
        )
    }

    if (showColorDialog) {
        val colors = AccentColor.entries.filterNot { it == AccentColor.Blue }
        SideChoiceMenu(
            choices = colors.map { accentLabel(it) },
            selectedIndex = colors.indexOf(accentColor).coerceAtLeast(0),
            topOffset = colorMenuTop,
            onSelected = { index ->
                showColorDialog = false
                onAccentColorChanged(colors[index])
            },
            onDismiss = { showColorDialog = false },
        )
    }

    if (showThemeDialog) {
        val modes = AppThemeMode.entries
        SideChoiceMenu(
            choices = modes.map { themeModeLabel(it) },
            selectedIndex = modes.indexOf(themeMode).coerceAtLeast(0),
            topOffset = themeMenuTop,
            onSelected = { index ->
                showThemeDialog = false
                onThemeModeChanged(modes[index])
            },
            onDismiss = { showThemeDialog = false },
        )
    }

    if (showSoundDialog) {
        SoundSettingsDialog(
            onDismiss = { showSoundDialog = false },
        )
    }

    if (showResidueDialog) {
        StagedResidueDialog(
            initial = residue,
            onDismiss = { showResidueDialog = false },
            onRead = { report -> residue = report },
        )
    }

    if (showPayloadModeDialog) {
        SideChoiceMenu(
            // Online first, because it is the default and the one that follows the configured sources.
            choices = listOf(
                stringResource(R.string.settings_payload_mode_online),
                stringResource(R.string.settings_payload_mode_offline),
            ),
            selectedIndex = if (payloadMode == PayloadMode.Offline) 1 else 0,
            topOffset = payloadModeMenuTop,
            onSelected = { index ->
                showPayloadModeDialog = false
                onPayloadModeChanged(if (index == 1) PayloadMode.Offline else PayloadMode.Online)
            },
            onDismiss = { showPayloadModeDialog = false },
        )
    }

    if (showFlavorDialog) {
        SideChoiceMenu(
            choices = KernelSuFlavor.entries.map { it.label },
            selectedIndex = KernelSuFlavor.entries.indexOf(kernelsuFlavor).coerceAtLeast(0),
            topOffset = flavorMenuTop,
            onSelected = { index ->
                showFlavorDialog = false
                onKernelsuFlavorChanged(KernelSuFlavor.entries[index])
            },
            onDismiss = { showFlavorDialog = false },
        )
    }

    if (showManagerVersionDialog) {
        // What this app offers when nothing is named: the KernelSU the payload for this device loads,
        // or the flavour's own release when no payload has declared one. Read here rather than passed
        // in, so the dialog cannot offer a version the row that opened it disagrees with.
        val offer = offeredManager(context, kernelsuFlavor)
        val offeredVersion = offer.version
        // What the phone is running, read when the dialog opens rather than passed in: this is the
        // version a manager has to match, and the picker beside it is where that gets acted on.
        var runningVersion by remember { mutableStateOf<String?>(null) }
        LaunchedEffect(kernelsuFlavor) {
            runningVersion = withContext(Dispatchers.IO) {
                KernelSuVersionProbe.read(context).daemon
            }
        }
        // Asked for while the dialog is open and forgotten with it, because a version list is about the
        // releases that exist right now: keeping one would offer a version the user has already seen.
        var available by remember { mutableStateOf<Result<List<String>>?>(null) }
        LaunchedEffect(kernelsuFlavor) {
            available = withContext(Dispatchers.IO) {
                KernelSuManager.availableVersions(kernelsuFlavor)
            }
        }
        // What a download would take now: the name in the field, or the offer above when it is empty.
        val selectedVersion = managerVersionDraft.trim().ifBlank { offeredVersion }
        val published = available
        AlertDialog(
            onDismissRequest = { showManagerVersionDialog = false },
            title = { Text(stringResource(R.string.settings_manager_dialog_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = stringResource(
                            R.string.settings_manager_dialog_help,
                            kernelsuFlavor.label,
                            offeredVersion,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    // The row's flag, repeated where the choice is actually made: this is the one screen
                    // that can still install a release the phone has moved past, so it says so here
                    // rather than only on the row that opened it.
                    payloadDriftNotice(
                        payloadKernelReading(
                            declared = AppPreferences.payloadKernelSuVersion(context, kernelsuFlavor),
                            running = runningVersion,
                        ),
                    )?.let { notice ->
                        Text(
                            text = notice,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    // The reading the picker exists for, next to the picker: it is the one version
                    // that is certainly right, and naming it is what turns "which do I install" into
                    // one tap. Offered only when it is not the version already offered, because that
                    // one is the row the list below leads with.
                    runningVersion?.let { running ->
                        if (running != offeredVersion) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(
                                    text = stringResource(
                                        R.string.settings_manager_running_version,
                                        kernelsuFlavor.label,
                                        running,
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.weight(1f),
                                )
                                TextButton(onClick = { managerVersionDraft = running }) {
                                    Text(
                                        stringResource(
                                            R.string.settings_manager_running_use,
                                            running,
                                        ),
                                    )
                                }
                            }
                        }
                    }
                    Text(
                        stringResource(R.string.settings_manager_versions_heading),
                        style = MaterialTheme.typography.labelLarge,
                    )
                    when {
                        published == null -> Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            LoadingIndicator(modifier = Modifier.size(18.dp))
                            Text(
                                stringResource(R.string.settings_manager_versions_loading),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }

                        published.isFailure -> Text(
                            // With the reason: "could not read" alone is the same sentence for a rate
                            // limit, a refused answer and a wrong URL, and they need different things
                            // from the person reading it.
                            stringResource(
                                R.string.settings_manager_versions_failed,
                                published.exceptionOrNull()?.message
                                    ?: published.exceptionOrNull()?.javaClass?.simpleName.orEmpty(),
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )

                        else -> {
                            // The app's own offer leads, and is listed even when the listing no longer
                            // carries it: it is the version this app installs when nothing is named, so
                            // it has to be selectable whether or not the network answered.
                            val versions = (
                                listOf(offeredVersion) +
                                    published.getOrDefault(emptyList())
                                ).distinct()
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = MANAGER_VERSION_LIST_MAX),
                            ) {
                                items(versions, key = { it }) { version ->
                                    ManagerVersionRow(
                                        version = version,
                                        isDefault = version == offeredVersion,
                                        selected = version == selectedVersion,
                                        onPick = {
                                            // Picking the offered version stores no name at all, which is
                                            // what "the offer" already means everywhere else - so a
                                            // payload that changes its KernelSU moves this with it rather
                                            // than pinning the number it happened to be at.
                                            managerVersionDraft = if (version == offeredVersion) {
                                                ""
                                            } else {
                                                version
                                            }
                                        },
                                    )
                                }
                            }
                        }
                    }
                    OutlinedTextField(
                        value = managerVersionDraft,
                        onValueChange = { managerVersionDraft = it },
                        label = { Text(stringResource(R.string.settings_manager_version_hint)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            // Both actions in one slot, for the same reason the token dialog puts them there: split
            // across the two slots the button beside Save ends up orphaned on its own line.
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (managerVersionDraft.isNotBlank()) {
                        TextButton(
                            onClick = {
                                showManagerVersionDialog = false
                                onManagerVersionChanged("")
                            },
                        ) {
                            Text(
                                stringResource(
                                    R.string.settings_manager_version_reset,
                                    offeredVersion,
                                ),
                            )
                        }
                    }
                    TextButton(
                        onClick = {
                            showManagerVersionDialog = false
                            onManagerVersionChanged(managerVersionDraft)
                        },
                    ) { Text(stringResource(R.string.action_save)) }
                }
            },
        )
    }

    if (showBootSettleDialog) {
        val settled = BootSettle.allowedSeconds
        SideChoiceMenu(
            choices = settled.map { BootSettle.label(it) },
            selectedIndex = settled.indexOf(bootSettleSeconds).coerceAtLeast(0),
            topOffset = bootSettleMenuTop,
            onSelected = { index ->
                showBootSettleDialog = false
                onBootSettleChanged(settled[index])
            },
            onDismiss = { showBootSettleDialog = false },
        )
    }

    if (showAutoRootSettleDialog) {
        val settled = BootSettle.allowedSeconds
        SideChoiceMenu(
            choices = settled.map { BootSettle.label(it) },
            selectedIndex = settled.indexOf(autoRootSettleSeconds).coerceAtLeast(0),
            topOffset = autoRootSettleMenuTop,
            onSelected = { index ->
                showAutoRootSettleDialog = false
                onAutoRootSettleChanged(settled[index])
            },
            onDismiss = { showAutoRootSettleDialog = false },
        )
    }

    if (showShizukuTokenDialog) {
        AlertDialog(
            onDismissRequest = { showShizukuTokenDialog = false },
            title = { Text(stringResource(R.string.shizuku_token_dialog_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = stringResource(R.string.shizuku_token_dialog_help),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    OutlinedTextField(
                        value = tokenDraft,
                        onValueChange = { tokenDraft = it },
                        label = { Text(stringResource(R.string.shizuku_token_dialog_label)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            // All three actions in one slot. Split across the confirm and dismiss slots they interleave:
            // a stacked dismiss column is placed beside the confirm button, so Delete ended up next to
            // Save with Cancel orphaned on a line of its own below them.
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (shizukuToken.isNotBlank()) {
                        TextButton(
                            onClick = {
                                showShizukuTokenDialog = false
                                onShizukuTokenChanged("")
                            },
                        ) { Text(stringResource(R.string.history_delete)) }
                    }
                    TextButton(onClick = { showShizukuTokenDialog = false }) {
                        Text(stringResource(R.string.action_cancel))
                    }
                    TextButton(
                        onClick = {
                            showShizukuTokenDialog = false
                            onShizukuTokenChanged(tokenDraft)
                        },
                    ) { Text(stringResource(R.string.action_save)) }
                }
            },
        )
    }

    if (showLegacyAdbDialog) {
        AlertDialog(
            onDismissRequest = { showLegacyAdbDialog = false },
            title = { Text(stringResource(R.string.adb_legacy_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        stringResource(R.string.adb_legacy_summary),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    OutlinedTextField(
                        value = legacyAdbPort,
                        onValueChange = { legacyAdbPort = it.filter(Char::isDigit).take(5) },
                        label = { Text(stringResource(R.string.adb_legacy_port)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (legacyAdbResult.isNotBlank()) {
                        Text(
                            legacyAdbResult,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(
                        onClick = {
                            val port = legacyAdbPort.toIntOrNull()?.coerceIn(1024, 65535) ?: 5555
                            legacyAdbPort = port.toString()
                            scope.launch {
                                val result = withContext(Dispatchers.IO) {
                                    KernelSuRuntime.rootShell(
                                        "setprop service.adb.tcp.port $port; stop adbd; start adbd",
                                    )
                                }
                                legacyAdbResult = if (result?.exitCode == 0) {
                                    context.getString(R.string.adb_legacy_enabled, port)
                                } else {
                                    context.getString(R.string.adb_legacy_root_required)
                                }
                            }
                        },
                    ) { Text(stringResource(R.string.adb_legacy_enable)) }
                    TextButton(
                        onClick = {
                            scope.launch {
                                val result = withContext(Dispatchers.IO) {
                                    KernelSuRuntime.rootShell(
                                        "setprop service.adb.tcp.port -1; stop adbd; start adbd",
                                    )
                                }
                                legacyAdbResult = if (result?.exitCode == 0) {
                                    context.getString(R.string.adb_legacy_disabled)
                                } else {
                                    context.getString(R.string.adb_legacy_root_required)
                                }
                            }
                        },
                    ) { Text(stringResource(R.string.adb_legacy_disable)) }
                    TextButton(onClick = { showLegacyAdbDialog = false }) {
                        Text(stringResource(R.string.action_close))
                    }
                }
            },
        )
    }

    // The payload-sources editor takes this page over rather than opening a dialog or a bottom sheet.
    // Both of those are a second window, and typing into this form tore that window down and rebuilt
    // it, which closed the keyboard after every character; as content in this window there is nothing
    // to tear down. The list below is skipped while it is open, so its scroll state is remembered out
    // here, where it survives the editor.
    val settingsList = rememberLazyListState()
    // Where a jump is headed: the link the run screen handed in, or one from this page's own repair
    // section. One state for both, because to the list they are the same jump.
    var jumpTarget by remember { mutableStateOf<String?>(null) }
    var cardHighlighted by remember { mutableStateOf(false) }
    // How tall a pinned heading is, measured as one is laid out rather than assumed from the theme: a jump
    // has to leave that much room above the card it lands on, or the card arrives behind the heading the
    // jump itself pinned - the one place on the page where a card cannot be read. Read here and passed into
    // the jump, because the page cannot know the type scale, and the heading cannot know who is asking.
    var pinnedHeadingHeight by remember { mutableStateOf(0) }
    // Which sections the user has collapsed, read from the store this page writes to. Seeded from it rather
    // than kept beside it: leaving the tab and coming back rebuilds this composable, and what the sections
    // were is the one thing about the page that has to survive that. The collapsed set is the one stored, so
    // an empty preference is the whole page open - which is what a section list has to start as.
    var closedSections by remember { mutableStateOf(AppPreferences.closedSettingsSections(context)) }
    val folderOpenSections = SettingsSection.open(closedSections)
    val openSections = when (settingsLayoutMode) {
        SettingsLayoutMode.Folders -> folderOpenSections
        SettingsLayoutMode.Pages -> selectedSettingsSection?.let(::setOf) ?: emptySet()
    }
    val indexRows = remember(folderOpenSections) {
        SettingsSection.indexRows(folderOpenSections)
    }
    /** Opens or closes one folder, and stores it so folder mode comes back exactly as it was left. */
    val setSectionOpen: (SettingsSection, Boolean) -> Unit = { section, open ->
        val next = if (open) closedSections - section else closedSections + section
        if (next != closedSections) {
            closedSections = next
            AppPreferences.setClosedSettingsSections(context, next)
        }
    }
    val toggleSection: (SettingsSection) -> Unit = { section ->
        if (settingsLayoutMode == SettingsLayoutMode.Pages) {
            UiSoundEngine.play(context, UiSoundKind.Settings)
            selectedSettingsSection = section
            scope.launch { settingsList.scrollToItem(0) }
        } else {
            UiSoundEngine.play(context, UiSoundKind.Expand)
            setSectionOpen(section, section !in folderOpenSections)
        }
    }

    BackHandler(
        enabled = settingsLayoutMode == SettingsLayoutMode.Pages &&
            selectedSettingsSection != null,
    ) {
        UiSoundEngine.play(context, UiSoundKind.Settings)
        selectedSettingsSection = null
        settingsLayoutMode = AppPreferences.settingsLayoutMode(context)
        closedSections = AppPreferences.closedSettingsSections(context)
    }
    LaunchedEffect(openTarget) {
        when (openTarget) {
            SettingsTarget.RootMigration -> {
                showRootMigrationPage = true
                onOpenTargetHandled()
            }
            SettingsTarget.VeyraKsu -> {
                showVeyraKsuPage = true
                onOpenTargetHandled()
            }
            SettingsTarget.CVeyraAccess -> {
                openCVeyraManagementDirect = false
                showCVeyraAccessPage = true
                onOpenTargetHandled()
            }
            SettingsTarget.CVeyraManagement -> {
                openCVeyraManagementDirect = true
                showCVeyraAccessPage = true
                onOpenTargetHandled()
            }
            null -> Unit
            else -> {
                jumpTarget = openTarget
                onOpenTargetHandled()
            }
        }
    }
    LaunchedEffect(jumpTarget) {
        val wanted = jumpTarget ?: return@LaunchedEffect
        // The card this jump is for has to exist before the search below can find it, and a closed section
        // has no rows at all - so its section is opened first. Opening it is not a side effect of the jump
        // either: the card was asked for, and a card inside a closed section is a card that cannot be shown.
        SettingsSection.holding(wanted)?.let { holder ->
            val rowsBefore = settingsList.layoutInfo.totalItemsCount
            if (settingsLayoutMode == SettingsLayoutMode.Pages) {
                selectedSettingsSection = holder
            } else if (holder in closedSections) {
                setSectionOpen(holder, true)
            }
            if (settingsList.layoutInfo.totalItemsCount == rowsBefore) {
                withTimeoutOrNull(JUMP_OPEN_WAIT_MILLIS) {
                    snapshotFlow { settingsList.layoutInfo.totalItemsCount }
                        .first { it != rowsBefore }
                }
            }
        }
        jumpToSettingCard(settingsList, wanted, pinnedHeadingHeight)
        cardHighlighted = true
        delay(SETTINGS_HIGHLIGHT_MILLIS)
        cardHighlighted = false
        jumpTarget = null
    }
    if (showPayloadSourcesSheet) {
        PayloadSourcesEditor(
            padding = padding,
            device = device,
            initialSources = payloadSources,
            onDismiss = { showPayloadSourcesSheet = false },
            onSave = { sources ->
                showPayloadSourcesSheet = false
                onPayloadSourcesChanged(sources)
            },
        )
        return
    }

    if (showCVeyraAccessPage) {
        CVeyraAccessPage(
            padding = padding,
            openManagementInitially = openCVeyraManagementDirect,
            onAccessStateChanged = { active ->
                cveyraAccessActive = active
                onCVeyraAccessStateChanged(active)
            },
            onOpenVeyraKsu = {
                showCVeyraAccessPage = false
                openCVeyraManagementDirect = false
                showVeyraKsuPage = true
            },
            onOpenRootMigration = {
                showCVeyraAccessPage = false
                openCVeyraManagementDirect = false
                showRootMigrationPage = true
            },
            onBack = {
                showCVeyraAccessPage = false
                openCVeyraManagementDirect = false
                cveyraAccessActive = CVeyraAccessStore.isActive(context)
                onCVeyraAccessStateChanged(cveyraAccessActive)
            },
        )
        return
    }

    if (showVeyraKsuPage) {
        VeyraKsuPage(
            padding = padding,
            onBack = { showVeyraKsuPage = false },
        )
        return
    }

    if (showRootMigrationPage) {
        RootMigrationPage(
            padding = padding,
            onBack = { showRootMigrationPage = false },
            onOpenVeyraKsu = {
                showRootMigrationPage = false
                showVeyraKsuPage = true
            },
        )
        return
    }

    if (showMagicBuilderSettingsPage) {
        VeyraMagicBuilderSettingsPage(
            padding = padding,
            onBack = {
                showMagicBuilderSettingsPage = false
                showMagicBuilderPage = true
            },
        )
        return
    }

    if (showMagicBuilderPage) {
        VeyraMagicBuilderPage(
            padding = padding,
            onBack = { showMagicBuilderPage = false },
            onOpenSettings = {
                showMagicBuilderPage = false
                showMagicBuilderSettingsPage = true
            },
            onOpenLoadingBuilder = {
                showMagicBuilderPage = false
                showPayloadBuilderPage = true
            },
            onOpenCVeyraRoot = {
                showMagicBuilderPage = false
                showCVeyraRootDialog = true
            },
        )
        return
    }

    if (showBuilderSettingsPage) {
        VeyraBuilderSettingsPage(
            padding = padding,
            onBack = { showBuilderSettingsPage = false },
        )
        return
    }

    if (showPayloadBuilderPage) {
        VeyraPayloadBuilderPage(
            padding = padding,
            onBack = { showPayloadBuilderPage = false },
            onOpenBuilderSettings = {
                showPayloadBuilderPage = false
                showBuilderSettingsPage = true
            },
        )
        return
    }

    val navigationMode =
        settingsLayoutMode == SettingsLayoutMode.Pages && selectedSettingsSection == null
    val showSectionHeadings =
        settingsLayoutMode == SettingsLayoutMode.Folders || selectedSettingsSection == null

    PageList(
        padding = padding,
        listState = settingsList,
        // No top room: a sticky heading is placed at the top of the padded area, so padding there is a band
        // above the pinned card that the rows below it scroll through on their way past. The title's own top
        // padding carries the space instead, where it scrolls away with the title.
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 20.dp),
        // The seam between two rows of the same card, rather than the gap between two cards: consecutive
        // headings are one card of flush rows, so what the page's own arrangement has to be is the seam. Every
        // gap that used to come from here is paid by the item that wants it, which is what keeps the index
        // dense while an open section keeps its room.
        verticalArrangement = Arrangement.spacedBy(SETTINGS_CARD_SEAM),
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = SETTINGS_TOP_SPACE, bottom = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (settingsLayoutMode == SettingsLayoutMode.Pages &&
                    selectedSettingsSection != null
                ) {
                    IconButton(
                        onClick = {
                            clickHaptic(view, sound = null)
                            UiSoundEngine.play(context, UiSoundKind.Settings)
                            selectedSettingsSection = null
                            settingsLayoutMode = AppPreferences.settingsLayoutMode(context)
                            closedSections = AppPreferences.closedSettingsSections(context)
                        },
                    ) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                }
                AnimatedContent(
                    targetState = selectedSettingsSection
                        .takeIf { settingsLayoutMode == SettingsLayoutMode.Pages },
                    label = "settings-title",
                    modifier = Modifier.weight(1f),
                ) { section ->
                    Column {
                        Text(
                            text = if (section == null) {
                                stringResource(R.string.settings)
                            } else {
                                stringResource(section.title)
                            },
                            style = MaterialTheme.typography.headlineLarge,
                        )
                    }
                }
            }
        }
        settingsSectionHeading(
            SettingsSection.Appearance,
            openSections,
            indexRows,
            toggleSection,
            onPinnedHeight = { pinnedHeadingHeight = it },
            navigationMode = navigationMode,
            visible = showSectionHeadings,
        )
        if (SettingsSection.Appearance in openSections) item {
            SettingsSectionBody {
                SettingsCard(
                    modifier = Modifier.onGloballyPositioned { coordinates ->
                        themeMenuTop = with(density) { coordinates.positionInWindow().y.toDp() }
                    },
                    icon = Icons.Rounded.Palette,
                    title = stringResource(R.string.material_color),
                    description = stringResource(R.string.material_color_description),
                    value = themeModeLabel(themeMode),
                    position = SettingsCardPosition.Top,
                    onClick = {
                        clickHaptic(view)
                        showThemeDialog = true
                    },
                )
                SettingsCard(
                    modifier = Modifier.onGloballyPositioned { coordinates ->
                        colorMenuTop = with(density) { coordinates.positionInWindow().y.toDp() }
                    },
                    icon = Icons.Rounded.ColorLens,
                    title = stringResource(R.string.color_title),
                    description = stringResource(R.string.color_description),
                    value = accentLabel(accentColor),
                    position = SettingsCardPosition.Middle,
                    onClick = {
                        clickHaptic(view)
                        showColorDialog = true
                    },
                )
                SettingsCard(
                    modifier = Modifier.onGloballyPositioned { coordinates ->
                        languageMenuTop = with(density) { coordinates.positionInWindow().y.toDp() }
                    },
                    icon = Icons.Rounded.Language,
                    title = stringResource(R.string.language),
                    description = stringResource(R.string.language_description),
                    value = languageLabel(currentLanguageTag),
                    position = SettingsCardPosition.Middle,
                    onClick = {
                        clickHaptic(view)
                        showLanguageDialog = true
                    },
                )
                SettingsCard(
                    icon = Icons.Rounded.ViewAgenda,
                    title = stringResource(
                        if (settingsLayoutMode == SettingsLayoutMode.Pages) {
                            R.string.settings_layout_folder
                        } else {
                            R.string.settings_layout_pages
                        },
                    ),
                    description = stringResource(
                        if (settingsLayoutMode == SettingsLayoutMode.Pages) {
                            R.string.settings_layout_folder_summary
                        } else {
                            R.string.settings_layout_pages_summary
                        },
                    ),
                    position = SettingsCardPosition.Middle,
                    onClick = {
                        clickHaptic(view)
                        val next = if (settingsLayoutMode == SettingsLayoutMode.Pages) {
                            SettingsLayoutMode.Folders
                        } else {
                            SettingsLayoutMode.Pages
                        }
                        AppPreferences.setSettingsLayoutMode(context, next)
                        if (settingsLayoutMode == SettingsLayoutMode.Folders) {
                            settingsLayoutMode = SettingsLayoutMode.Pages
                            selectedSettingsSection = null
                        } else {
                            settingsLayoutMode = SettingsLayoutMode.Folders
                        }
                    },
                )
                SettingsCard(
                    icon = Icons.Rounded.VolumeUp,
                    title = stringResource(R.string.sound_menu_title),
                    description = stringResource(R.string.sound_menu_summary),
                    value = if (AppPreferences.uiSoundsEnabled(context)) {
                        AppPreferences.uiSoundProfile(context).displayLabel(
                            AppPreferences.uiSoundLegacyV1(context),
                        )
                    } else {
                        stringResource(R.string.sound_menu_off)
                    },
                    position = SettingsCardPosition.Middle,
                    onClick = {
                        clickHaptic(view)
                        showSoundDialog = true
                    },
                )
                SettingsSwitchCard(
                    icon = Icons.Rounded.ViewAgenda,
                    title = stringResource(R.string.nav_style_title),
                    description = stringResource(R.string.nav_style_summary),
                    checked = navBarGlass,
                    position = SettingsCardPosition.Bottom,
                    onCheckedChange = {
                        clickHaptic(view)
                        onNavBarGlassChanged(it)
                    },
                )
            }
        }

        settingsSectionHeading(
            SettingsSection.Payloads,
            openSections,
            indexRows,
            toggleSection,
            onPinnedHeight = { pinnedHeadingHeight = it },
            navigationMode = navigationMode,
            visible = showSectionHeadings,
        )
        if (SettingsSection.Payloads in openSections) item {
            SettingsSectionBody {
                SettingsCard(
                    modifier = Modifier.onGloballyPositioned { coordinates ->
                        payloadModeMenuTop = with(density) { coordinates.positionInWindow().y.toDp() }
                    },
                    // A cloud rather than the crossed-out one it had: this row is about *where* a payload
                    // comes from, and its value already says which of the two it is - so the crossed-out
                    // cloud said "offline" on a row whose value often reads "Online".
                    icon = Icons.Rounded.Cloud,
                    title = stringResource(R.string.settings_payload_mode),
                    description = stringResource(
                        if (payloadMode == PayloadMode.Offline) {
                            R.string.settings_payload_mode_summary_offline
                        } else {
                            R.string.settings_payload_mode_summary_online
                        },
                    ),
                    value = stringResource(
                        if (payloadMode == PayloadMode.Offline) {
                            R.string.settings_payload_mode_offline
                        } else {
                            R.string.settings_payload_mode_online
                        },
                    ),
                    position = SettingsCardPosition.Top,
                    onClick = {
                        clickHaptic(view)
                        showPayloadModeDialog = true
                    },
                )
                // Read when the section is opened rather than on every recomposition: it is a file
                // read, and what it describes changes only when a run finishes.
                var cached by remember { mutableStateOf<CachedPayload?>(null) }
                var showCachedDialog by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) {
                    cached = withContext(Dispatchers.IO) { KnownGoodPayloadStore.describe(context) }
                }
                SettingsCard(
                    // The arrow-into-a-box is the "downloaded and kept" mark, which is what this row is;
                    // the crossed-out cloud only said "not in the cloud", which is not the interesting
                    // half and is also true of every payload on the device.
                    icon = Icons.Rounded.DownloadForOffline,
                    title = stringResource(R.string.settings_cached_payload),
                    description = stringResource(R.string.settings_cached_payload_summary),
                    // The profile's *name*, not its id. An id like
                    // "galaxy-s25-series-2026-06-07" is an identifier - it has no spaces to wrap at,
                    // so it took both lines the value is allowed and left the description a column
                    // two words wide. The name says which device this is cached for, and the id is
                    // still stated in full in the dialog, which is where a precise string belongs.
                    value = cached?.displayName?.takeIf { it.isNotBlank() }
                        ?: cached?.profileId
                        ?: stringResource(R.string.settings_cached_payload_none),
                    position = SettingsCardPosition.Middle,
                    onClick = {
                        clickHaptic(view)
                        showCachedDialog = true
                    },
                )
                if (showCachedDialog) {
                    CachedPayloadDialog(
                        cached = cached,
                        onForget = {
                            showCachedDialog = false
                            cached = null
                            onForgetCachedPayload()
                        },
                        onDismiss = { showCachedDialog = false },
                    )
                }
                SettingsCard(
                    // A tree of catalogs. A chain link said "linking", which is the pairing row's meaning
                    // three sections down, and this row is a list of repositories payloads are read from.
                    icon = Icons.Rounded.AccountTree,
                    title = stringResource(R.string.payload_sources),
                    description = stringResource(R.string.payload_sources_description),
                    // Flat on top: this card sits between two others in the payloads group, and a
                    // second group top there drew it as the start of another list.
                    position = SettingsCardPosition.Middle,
                    onClick = {
                        clickHaptic(view)
                        showPayloadSourcesSheet = true
                    },
                )
                SettingsCard(
                    icon = Icons.Rounded.UploadFile,
                    title = stringResource(R.string.local_payload),
                    description = stringResource(
                        if (localPayloadName == null) {
                            R.string.local_payload_description
                        } else {
                            R.string.local_payload_description_set
                        },
                    ),
                    value = localPayloadName ?: stringResource(R.string.local_payload_none),
                    position = SettingsCardPosition.Middle,
                    onClick = {
                        clickHaptic(view)
                        showLocalPayloadDialog = true
                    },
                )
                SettingsCard(
                    icon = Icons.Rounded.Build,
                    title = stringResource(R.string.loading_builder_title),
                    description = stringResource(R.string.loading_builder_summary),
                    value = LocalPayload.displayName(context)?.let {
                        stringResource(R.string.payload_local_source_title)
                    }.orEmpty(),
                    position = SettingsCardPosition.Middle,
                    onClick = {
                        clickHaptic(view)
                        showPayloadBuilderPage = true
                    },
                )
                SettingsCard(
                    icon = Icons.Rounded.AutoFixHigh,
                    title = stringResource(R.string.magic_builder_title),
                    description = stringResource(R.string.magic_builder_card_summary),
                    position = SettingsCardPosition.Middle,
                    onClick = {
                        clickHaptic(view)
                        showMagicBuilderPage = true
                    },
                )
                SettingsCard(
                    icon = Icons.Rounded.Tune,
                    title = stringResource(R.string.builder_settings_title),
                    description = stringResource(R.string.builder_settings_summary),
                    position = SettingsCardPosition.Bottom,
                    onClick = {
                        clickHaptic(view)
                        showBuilderSettingsPage = true
                    },
                )
            }
        }

        settingsSectionHeading(
            SettingsSection.Run,
            openSections,
            indexRows,
            toggleSection,
            onPinnedHeight = { pinnedHeadingHeight = it },
            navigationMode = navigationMode,
            visible = showSectionHeadings,
        )

        // Keyed by the card something else in the app may ask for: the run screen's read-only failure
        // names this setting and hands its key over, and a key is what lets the page find the row without
        // an index that a new card above it would silently invalidate.
        if (SettingsSection.Run in openSections) item(key = SettingsTarget.PartitionReadOnly) {
            SettingsSectionBody {
                SettingsSwitchCard(
                    // Sliders, not the memory chip this had: the chip is the kernel module the KernelSU
                    // row below is about, and this row reveals rows rather than touching a kernel.
                    icon = Icons.Rounded.Tune,
                    title = stringResource(R.string.advanced_mode),
                    description = stringResource(R.string.advanced_mode_description),
                    checked = advancedMode,
                    position = SettingsCardPosition.Top,
                    onCheckedChange = {
                        clickHaptic(view)
                        onAdvancedModeChanged(it)
                    },
                )
                SettingsSwitchCard(
                    icon = Icons.Rounded.Security,
                    title = stringResource(R.string.disable_ksu_modules),
                    // Moving the modules aside is something a run does *around the load*, so with no
                    // load there is nothing for them to sit out and the setting would quietly do
                    // nothing. Said here rather than left to be discovered.
                    description = stringResource(
                        if (loadKernelSu) {
                            R.string.disable_ksu_modules_description
                        } else {
                            R.string.disable_ksu_modules_needs_load
                        },
                    ),
                    checked = disableKsuModules,
                    position = SettingsCardPosition.Middle,
                    enabled = loadKernelSu,
                    onCheckedChange = {
                        clickHaptic(view)
                        onDisableKsuModulesChanged(it)
                    },
                )
                // Outlined, not marked in some other way, because what a jump has to answer is "which
                // of these rows is it" - and the outline sits exactly on the card's own edge, at its own
                // corner radius, so it reads as the row being pointed at rather than as a new control.
                //
                // Drawn rather than added as a border modifier: a border is laid out with the content it
                // wraps, so a transparent one held open for the outline to appear in would leave this row
                // a few pixels narrower than the two cards stacked with it, on every frame. This changes
                // nothing but the pixels.
                val highlightColor = MaterialTheme.colorScheme.primary
                Box(
                    modifier = Modifier.drawWithContent {
                        drawContent()
                        if (cardHighlighted) {
                            drawOutline(
                                outline = settingsCardRestingShape(SettingsCardPosition.Middle)
                                    .createOutline(size, layoutDirection, this),
                                color = highlightColor,
                                style = Stroke(width = SETTINGS_HIGHLIGHT_WIDTH.toPx()),
                            )
                        }
                    },
                ) {
                    SettingsSwitchCard(
                        icon = Icons.Rounded.Lock,
                        title = stringResource(R.string.partition_read_only),
                        description = stringResource(R.string.partition_read_only_description),
                        checked = partitionReadOnly,
                        position = SettingsCardPosition.Middle,
                        onCheckedChange = {
                            clickHaptic(view)
                            onPartitionReadOnlyChanged(it)
                        },
                    )
                }
                SettingsCard(
                    modifier = Modifier.onGloballyPositioned { coordinates ->
                        bootSettleMenuTop = with(density) { coordinates.positionInWindow().y.toDp() }
                    },
                    icon = Icons.Rounded.HourglassEmpty,
                    title = stringResource(R.string.settings_boot_settle),
                    description = stringResource(R.string.settings_boot_settle_summary),
                    value = BootSettle.label(bootSettleSeconds),
                    position = SettingsCardPosition.Middle,
                    onClick = {
                        clickHaptic(view)
                        showBootSettleDialog = true
                    },
                )
                SettingsCard(
                    icon = Icons.Rounded.Timer,
                    title = stringResource(R.string.settings_run_limits),
                    description = stringResource(R.string.settings_run_limits_summary),
                    // No value. Three ceilings listed as numbers beside a row are three numbers to
                    // read on every pass through Settings, and they answer a question the dialog
                    // answers properly - which ceiling is which, and what it decides. The row's job
                    // is to say the settings exist and to open them, and the run plan states the
                    // ceilings that are actually in force where a run is about to be started.
                    position = SettingsCardPosition.Middle,
                    onClick = {
                        clickHaptic(view)
                        showRunLimitsDialog = true
                    },
                )
                SettingsCard(
                    icon = Icons.Rounded.Schedule,
                    title = stringResource(R.string.run_plan),
                    description = stringResource(R.string.run_plan_description),
                    value = "",
                    position = SettingsCardPosition.Bottom,
                    onClick = {
                        clickHaptic(view)
                        showRunPlanDialog = true
                    },
                )
            }
        }
        settingsSectionHeading(
            SettingsSection.Shizuku,
            openSections,
            indexRows,
            toggleSection,
            onPinnedHeight = { pinnedHeadingHeight = it },
            navigationMode = navigationMode,
            visible = showSectionHeadings,
        )
        if (SettingsSection.Shizuku in openSections) item {
            SettingsSectionBody {
                if (!cveyraAccessActive) {
                    SettingsSwitchCard(
                        icon = Icons.Rounded.Terminal,
                        title = stringResource(R.string.cveyra_use_title),
                        description = if (cveyraEnabled) {
                            stringResource(
                                when (cveyraBackend) {
                                    CVeyraBackend.Root -> R.string.cveyra_backend_root
                                    CVeyraBackend.WirelessAdb -> R.string.cveyra_backend_adb
                                    CVeyraBackend.None -> R.string.cveyra_backend_none
                                },
                            )
                        } else {
                            stringResource(R.string.cveyra_use_summary)
                        },
                        checked = cveyraEnabled,
                        position = SettingsCardPosition.Top,
                        onCheckedChange = { enabled ->
                            clickHaptic(view)
                            cveyraEnabled = enabled
                            CVeyraController.setEnabled(context, enabled)
                            if (enabled) refreshCVeyraBackend(true)
                        },
                    )
                    SettingsSwitchCard(
                        icon = Icons.Rounded.Security,
                        title = stringResource(R.string.cveyra_root_title),
                        description = if (CVeyraPreferences.rootBroker(context)) {
                            stringResource(R.string.cveyra_root_active)
                        } else {
                            stringResource(R.string.cveyra_root_summary)
                        },
                        checked = cveyraStartAsRoot,
                        position = SettingsCardPosition.Middle,
                        onCheckedChange = { enabled ->
                            clickHaptic(view)
                            if (enabled) {
                                cveyraSetupMessage = null
                                showCVeyraRootDialog = true
                            } else {
                                cveyraStartAsRoot = false
                                CVeyraPreferences.setStartAsRoot(context, false)
                                if (cveyraEnabled) {
                                    CVeyraController.start(context)
                                    refreshCVeyraBackend(false)
                                }
                            }
                        },
                    )
                    SettingsSwitchCard(
                        icon = Icons.Rounded.Bolt,
                        title = stringResource(R.string.cveyra_promote_title),
                        description = stringResource(R.string.cveyra_promote_summary),
                        checked = cveyraPromoteAfterBoot,
                        position = SettingsCardPosition.Middle,
                        onCheckedChange = { enabled ->
                            clickHaptic(view)
                            cveyraPromoteAfterBoot = enabled
                            CVeyraPreferences.setPromoteAfterBoot(context, enabled)
                            if (enabled && !cveyraEnabled) {
                                cveyraEnabled = true
                                CVeyraController.setEnabled(context, true)
                                refreshCVeyraBackend(true)
                            }
                        },
                    )
                } else {
                    SettingsCard(
                        icon = Icons.Rounded.Security,
                        title = "CVeyra Access 2.0.0",
                        description = "Always on · Root broker enforced through VeyraKSU",
                        value = if (cveyraBackend == CVeyraBackend.Root) "Active" else "Waiting for root",
                        position = SettingsCardPosition.Top,
                        onClick = {
                            clickHaptic(view)
                            showCVeyraAccessPage = true
                        },
                    )
                }
                SettingsSwitchCard(
                    icon = Icons.Rounded.Key,
                    title = stringResource(R.string.cveyra_token_title),
                    description = stringResource(
                        if (cveyraTokenEnabled) {
                            R.string.cveyra_token_summary_on
                        } else {
                            R.string.cveyra_token_summary_off
                        },
                    ),
                    checked = cveyraTokenEnabled,
                    position = SettingsCardPosition.Middle,
                    onCheckedChange = { enabled ->
                        clickHaptic(view)
                        cveyraTokenEnabled = enabled
                        CVeyraPreferences.setTokenEnabled(context, enabled)
                        if (enabled) cveyraToken = CVeyraPreferences.startToken(context)
                    },
                )
                if (cveyraTokenEnabled) {
                    SettingsCard(
                        icon = Icons.Rounded.ContentCopy,
                        title = stringResource(R.string.cveyra_token_copy_title),
                        description = stringResource(R.string.cveyra_token_copy_summary),
                        value = cveyraToken.take(10) + "…",
                        position = SettingsCardPosition.Middle,
                        onClick = {
                            clickHaptic(view)
                            copyLogToClipboard(context, cveyraToken)
                            Toast.makeText(
                                context,
                                context.getString(R.string.cveyra_token_copied),
                                Toast.LENGTH_SHORT,
                            ).show()
                        },
                    )
                }
                if (!cveyraAccessActive) {
                    SettingsSwitchCard(
                        icon = Icons.Rounded.Schedule,
                        title = stringResource(R.string.cveyra_boot_title),
                        description = stringResource(R.string.cveyra_boot_summary),
                        checked = cveyraStartOnBoot,
                        position = SettingsCardPosition.Middle,
                        onCheckedChange = { enabled ->
                            clickHaptic(view)
                            cveyraStartOnBoot = enabled
                            CVeyraPreferences.setStartOnBoot(context, enabled)
                            if (enabled && cveyraEnabled) CVeyraController.start(context)
                        },
                    )
                }
                SettingsCard(
                    modifier = if (cveyraAccessActive) Modifier else Modifier.alpha(0.62f),
                    icon = Icons.Rounded.Security,
                    title = if (cveyraAccessActive) {
                        "CVeyra Permission Management"
                    } else {
                        stringResource(R.string.cveyra_grant_title)
                    },
                    description = if (cveyraAccessActive) {
                        "Access 2.0.0 is active · manage provider rules, modules and firewall"
                    } else {
                        stringResource(R.string.cveyra_grant_summary)
                    },
                    value = if (cveyraAccessActive) {
                        "Active"
                    } else {
                        stringResource(R.string.cveyra_grant_value)
                    },
                    position = SettingsCardPosition.Bottom,
                    onClick = {
                        clickHaptic(view)
                        showCVeyraAccessPage = true
                    },
                )
            }
        }

        settingsSectionHeading(
            SettingsSection.WirelessAdb,
            openSections,
            indexRows,
            toggleSection,
            onPinnedHeight = { pinnedHeadingHeight = it },
            navigationMode = navigationMode,
            visible = showSectionHeadings,
        )
        if (SettingsSection.WirelessAdb in openSections) item {
            SettingsSectionBody {
                // Read when the screen is built rather than on every recomposition: it is a file read
                // plus a settings lookup, and what it describes changes only when something is done
                // to it.
                LaunchedEffect(Unit) {
                    wirelessSnapshot = WirelessAdbDiagnostics.passiveSnapshot(context)
                }
                val snapshot = wirelessSnapshot
                SettingsCard(
                    icon = Icons.Rounded.Link,
                    title = "Start via Wireless Debugging",
                    description = "Pair or reuse Veyra's authenticated Wireless ADB transport.",
                    value = if (snapshot == null) {
                        ""
                    } else {
                        wirelessAdbStateLabel(snapshot.authState)
                    },
                    position = SettingsCardPosition.Top,
                    onClick = {
                        clickHaptic(view)
                        wirelessSnapshot = WirelessAdbDiagnostics.passiveSnapshot(context)
                        showWirelessAdbDialog = true
                    },
                )
                SettingsCard(
                    icon = Icons.Rounded.Terminal,
                    title = "Start via USB debugging",
                    description = "Use the classic ADB TCP port. Veyra can reopen and test it when the required privilege is available.",
                    position = SettingsCardPosition.Middle,
                    onClick = {
                        clickHaptic(view)
                        showLegacyAdbDialog = true
                    },
                )
                SettingsCard(
                    icon = Icons.Rounded.ContentCopy,
                    title = "Start via Computer",
                    description = "Copy the ADB command that opens the classic port from a connected computer.",
                    value = "adb tcpip 5555",
                    position = SettingsCardPosition.Bottom,
                    onClick = {
                        clickHaptic(view)
                        copyLogToClipboard(context, "adb tcpip 5555")
                        Toast.makeText(
                            context,
                            "Copied: adb tcpip 5555",
                            Toast.LENGTH_SHORT,
                        ).show()
                    },
                )
                if (showWirelessAdbDialog) {
                    WirelessAdbDialog(
                        snapshot = wirelessSnapshot,
                        busy = wirelessBusy,
                        writeSecureSettingsMissing = !PermissionGrant.hasPermission(context),
                        onGrantPermission = {
                            wirelessBusy = true
                            scope.launch {
                                val outcome = PermissionGrant.writeSecureSettings(context)
                                // Re-read rather than assume: the transport that worked is worth naming,
                                // and a grant that did not take has to leave the screen saying so.
                                wirelessSnapshot = WirelessAdbDiagnostics.passiveSnapshot(context)
                                wirelessBusy = false
                                Toast.makeText(
                                    context,
                                    outcome.message(context),
                                    Toast.LENGTH_LONG,
                                ).show()
                            }
                        },
                        onPair = { forceRepair ->
                            // The code field lives in a notification, so pairing is started by an
                            // activity that asks for the permission first and clears a stale pairing
                            // when asked for a fresh one.
                            context.startActivity(
                                AdbPairingSetupActivity.pairingIntent(context, forceRepair),
                            )
                            showWirelessAdbDialog = false
                        },
                        onOpenDeveloperOptions = {
                            // Closed first: the screen this opens is where the user has to be next, and
                            // a dialog left behind it would be in the way on the way back.
                            showWirelessAdbDialog = false
                            if (!DeveloperOptions.open(context)) {
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.developer_options_unavailable),
                                    Toast.LENGTH_LONG,
                                ).show()
                            }
                        },
                        onTest = {
                            wirelessBusy = true
                            scope.launch {
                                wirelessSnapshot = WirelessAdbDiagnostics.testConnection(context)
                                wirelessBusy = false
                            }
                        },
                        onForget = {
                            AdbCredentialStore.forgetLocalCredential(context)
                            wirelessSnapshot = WirelessAdbDiagnostics.passiveSnapshot(context)
                        },
                        onDismiss = { showWirelessAdbDialog = false },
                    )
                }
            }
        }

        settingsSectionHeading(
            SettingsSection.Root,
            openSections,
            indexRows,
            toggleSection,
            onPinnedHeight = { pinnedHeadingHeight = it },
            navigationMode = navigationMode,
            visible = showSectionHeadings,
        )
        if (SettingsSection.Root in openSections) item {
            SettingsSectionBody {
                // The flavour is first because everything below it is about this flavour's module:
                // which daemon a run stages, which manager opens afterwards, and which module root
                // on boot puts back.
                // Re-read when the flavour changes, because the marker below is exactly the state a
                // change produces.
                val loadedFlavor = remember(kernelsuFlavor) { AppPreferences.loadedFlavor(context) }
                SettingsCard(
                    modifier = Modifier.onGloballyPositioned { coordinates ->
                        flavorMenuTop = with(density) { coordinates.positionInWindow().y.toDp() }
                    },
                    icon = Icons.Rounded.Security,
                    title = stringResource(R.string.settings_ksu_flavor),
                    description = stringResource(kernelsuFlavor.summaryRes),
                    // The selected flavour sits in the band every other row puts its setting in, so it
                    // lands on their centre line instead of riding up beside the title. That band is
                    // measured before the text column next to it, which is why these two descriptions
                    // are one line long: anything longer wraps into a second line at half the card's
                    // width, and reads as a row that overflowed rather than one that fits.
                    value = kernelsuFlavor.label,
                    // The pending marker is the only warning this screen can give: the two flavours
                    // cannot both be in the kernel, so a switch made in a boot that already carries
                    // one only takes effect after a restart. It says which flavour this boot is
                    // holding, because "after a restart" on its own leaves the reason to be guessed.
                    notice = loadedFlavor
                        ?.takeIf { it != kernelsuFlavor }
                        ?.let { stringResource(R.string.settings_ksu_flavor_pending, it.label) },
                    position = SettingsCardPosition.Top,
                    onClick = {
                        clickHaptic(view)
                        showFlavorDialog = true
                    },
                )
                // The version this app offers, which is the KernelSU the payload for this device loads
                // when the user has named nothing - so a manager installed from this row is the one
                // built against the daemon the next run stages.
                val managerOffer = offeredManager(context, kernelsuFlavor)
                val offeredManagerVersion = managerOffer.version
                // Re-read on the way back from anywhere, because the usual way a manager arrives is
                // another app's installer - see [resumeTick].
                val installedManager = remember(kernelsuFlavor, offeredManagerVersion, resumeTick) {
                    KernelSuManager.installedFor(context, kernelsuFlavor)
                }
                // Read here, where the rows that say what it means are, and off the main thread: the
                // read is a root shell, which on a device that has not answered its grant prompt is a
                // wait rather than a failure - and a settings list is not worth a frozen frame.
                var runningKernelSu by remember { mutableStateOf<KernelSuVersionReading?>(null) }
                LaunchedEffect(kernelsuFlavor) {
                    runningKernelSu = withContext(Dispatchers.IO) {
                        KernelSuVersionProbe.read(context)
                    }
                }
                val managerVersion = installedManager?.versionName
                // The pair, from the two readings the comparison itself uses, so what this card prints
                // and what it claims cannot come apart.
                val versionPair = remember(managerVersion, runningKernelSu) {
                    versionPairDisplay(managerVersion, runningKernelSu?.daemon)
                }
                // The KernelSU the resolved payload declares, against the one this boot is running.
                // The offer below takes its number from the payload, which is right for the *next* run
                // - so where the phone has already moved past it, the row has to say so rather than
                // present a release that mismatches the boot the moment it is installed.
                val payloadKernel = payloadKernelReading(
                    declared = AppPreferences.payloadKernelSuVersion(context, kernelsuFlavor),
                    running = runningKernelSu?.daemon,
                )
                SettingsCard(
                    icon = Icons.Rounded.VerifiedUser,
                    title = stringResource(R.string.settings_manager),
                    // The version is the row's own value, so the description does not repeat it - it
                    // says what that number is. The one thing it adds is the name of a manager whose own
                    // name is not this flavour's: a spoofed build rewrites its package to words that
                    // change on every release, and its label is then the only thing on the phone that
                    // says which manager this is. Showing it where it says nothing would be the same
                    // duplication the version used to be.
                    description = when {
                        installedManager == null ->
                            stringResource(R.string.settings_manager_summary, offeredManagerVersion)
                        managerNameWorthShowing(installedManager, kernelsuFlavor) ->
                            stringResource(R.string.settings_manager_summary_named, installedManager.label)
                        // The value band is empty here, so the description cannot be one that promises a
                        // number is on the row - which is the state a package that would not answer for
                        // its own version leaves: installed, and unreadable about it.
                        managerVersion == null ->
                            stringResource(R.string.settings_manager_summary_unreadable)
                        else -> stringResource(R.string.settings_manager_summary_installed)
                    },
                    // What is on the phone, not what the app would install: the offered version is
                    // the row below this one, and the two were the same number in the same place
                    // until a manager from another line could be installed without the app noticing.
                    // Nothing installed is nothing to show - filling the band with the offered version
                    // put a number on this row that the phone did not have, and put it there twice with
                    // the description above it.
                    value = managerRowValue(installedManager),
                    position = SettingsCardPosition.Middle,
                    // Opens whatever manager is on the phone, of whatever version; the download is
                    // only offered when there is none. Nothing here rejects a version the user
                    // installed themselves, which is the point of not pinning this.
                    onClick = {
                        clickHaptic(view)
                        KernelSuManager.open(context, kernelsuFlavor) { message ->
                            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                        }
                    },
                )
                // The pair as two readings rather than as a sentence. A mismatch is the one thing this
                // screen could never see: any manager talks to the loaded module over KernelSU's
                // socket, so a manager from one line against a kernel from another installs and runs
                // exactly like a matching one. Both numbers are printed for that reason - and the
                // reading that could not be made is printed as the absence it is, instead of leaving a
                // row that looks like a comparison nobody made.
                SettingsReadingsCard(
                    icon = Icons.Rounded.Difference,
                    title = stringResource(R.string.settings_versions),
                    description = stringResource(
                        if (versionPair.mismatched) {
                            R.string.settings_versions_summary_mismatch
                        } else {
                            R.string.settings_versions_summary
                        },
                    ),
                    readings = listOf(
                        SettingsReading(
                            label = stringResource(R.string.settings_versions_manager),
                            value = versionPair.manager
                                ?: stringResource(R.string.settings_versions_absent),
                            // The mark goes on the manager, which is the reading the action below
                            // replaces: the running KernelSU is the one of the two that is not a
                            // choice, and a mark on both would say nothing about which one to act on.
                            mark = if (versionPair.mismatched) {
                                stringResource(R.string.settings_versions_mismatch_mark)
                            } else {
                                null
                            },
                        ),
                        SettingsReading(
                            label = stringResource(R.string.settings_versions_kernel),
                            value = versionPair.kernel
                                ?: stringResource(R.string.settings_versions_unread),
                        ),
                    ),
                    // The fix, under the reading that is marked: the one version that is certainly
                    // right is the one the kernel is already running. It also becomes the offered
                    // version, because "install this" is a statement about which one is wanted - so
                    // the app's own default stops disagreeing with the phone the moment it is asked.
                    action = managerMismatchTarget(versionPair.state, runningKernelSu?.daemon)?.let { target ->
                        NoticeAction(
                            label = stringResource(R.string.settings_manager_install_running, target),
                            onClick = {
                                onManagerVersionChanged(target)
                                KernelSuManager.downloadVersion(
                                    context = context,
                                    flavor = kernelsuFlavor,
                                    version = target,
                                    onMessage = { message ->
                                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                                    },
                                )
                            },
                        )
                    },
                    position = SettingsCardPosition.Middle,
                )
                SettingsCard(
                    icon = Icons.Rounded.SystemUpdate,
                    title = stringResource(R.string.settings_manager_version),
                    // Where the number comes from when the user did not type one: naming the payload
                    // turns "3.4.0" from a number the app chose into the release the phone is about to
                    // load, which is the only reason to prefer it over the version already installed.
                    description = stringResource(
                        when (managerOffer.origin) {
                            ManagerOfferOrigin.Payload ->
                                R.string.settings_manager_version_summary_payload
                            else -> R.string.settings_manager_version_summary
                        },
                    ),
                    value = offeredManagerVersion,
                    // Where the two disagree the offer is still the payload's version - that is what the
                    // next run will load - but it is not presented as unremarkable, and a phone that has
                    // moved past the payload gets the one tap that stops the offer pointing back at it.
                    notice = payloadDriftNotice(payloadKernel),
                    noticeIcon = if (payloadKernel.state == PayloadKernelState.Behind) {
                        Icons.Rounded.History
                    } else {
                        Icons.Rounded.RestartAlt
                    },
                    noticeAction = payloadBehindTarget(payloadKernel)?.let { running ->
                        NoticeAction(
                            label = stringResource(R.string.settings_manager_version_keep_boot, running),
                            onClick = { onManagerVersionChanged(running) },
                        )
                    },
                    position = SettingsCardPosition.Middle,
                    onClick = {
                        clickHaptic(view)
                        managerVersionDraft = AppPreferences.managerVersion(context, kernelsuFlavor).orEmpty()
                        showManagerVersionDialog = true
                    },
                )
                // Only for the module that can be told. The other two decide which APK is their
                // manager with a signature table compiled into the kernel, so a row here would be a
                // control that cannot do anything on them - and worse, one whose failure would read
                // as a problem with the phone rather than with the request.
                if (kernelsuFlavor.supportsDynamicManager) {
                    DynamicManagerCard(
                        installedManager = installedManager,
                        kernelsuFlavor = kernelsuFlavor,
                        context = context,
                        view = view,
                        scope = scope,
                        resumeTick = resumeTick,
                        position = SettingsCardPosition.Middle,
                    )
                }
                // The load decision follows the flavour because the rest of the group depends on it:
                // root on boot exists to put KernelSU back after a reboot, and a boot run with
                // nothing to load is not a boot run at all.
                SettingsSwitchCard(
                    icon = Icons.Rounded.Memory,
                    title = stringResource(R.string.settings_ksu_load),
                    description = stringResource(R.string.settings_ksu_load_summary),
                    checked = loadKernelSu,
                    position = SettingsCardPosition.Middle,
                    onCheckedChange = { enabled ->
                        clickHaptic(view)
                        onLoadKernelSuChanged(enabled)
                    },
                )
                SettingsSwitchCard(
                    // The same bolt the Shizuku-at-boot row uses, because the two rows mean the same
                    // thing by it: this happens by itself, on a boot, without anyone asking. It wore the
                    // restart arrow, which is the row directly below it - the one that *does* restart.
                    icon = Icons.Rounded.Bolt,
                    title = stringResource(R.string.settings_boot_root),
                    // Disabled rather than turned off: the stored choice is kept, so turning loading
                    // back on restores it exactly - and the reason is on the row either way.
                    description = stringResource(
                        if (loadKernelSu) {
                            R.string.settings_boot_root_summary
                        } else {
                            R.string.settings_boot_root_needs_load
                        },
                    ),
                    checked = bootRootMode,
                    position = SettingsCardPosition.Middle,
                    enabled = loadKernelSu,
                    onCheckedChange = { enabled ->
                        clickHaptic(view)
                        if (enabled) onRequestNotificationPermission()
                        onBootRootModeChanged(enabled)
                    },
                )
                // Follows the load decision for the same reason root on boot does: what it applies its
                // modules to is the KernelSU a run loaded, and with loading off there is nothing to
                // apply. It is not tied to root on boot - a manual run can load KernelSU with that off.
                SettingsSwitchCard(
                    icon = Icons.Rounded.RestartAlt,
                    title = stringResource(R.string.settings_restart_after_root),
                    description = stringResource(R.string.settings_restart_after_root_summary),
                    checked = restartAfterRoot,
                    position = SettingsCardPosition.Middle,
                    enabled = loadKernelSu,
                    onCheckedChange = { enabled ->
                        clickHaptic(view)
                        onRestartAfterRootChanged(enabled)
                    },
                )
                var veyraKsuState by remember(resumeTick) {
                    mutableStateOf(VeyraKsuState())
                }
                LaunchedEffect(resumeTick) {
                    veyraKsuState = withContext(Dispatchers.IO) {
                        VeyraKsuBridge.readState()
                    }
                }
                SettingsCard(
                    icon = Icons.Rounded.Memory,
                    title = stringResource(R.string.veyra_ksu_title),
                    description = stringResource(R.string.veyra_ksu_card_summary),
                    value = stringResource(
                        when {
                            !veyraKsuState.installed && VeyraKsuPreferences.autoInstallAfterRoot(context) ->
                                R.string.veyra_ksu_status_tagged
                            !veyraKsuState.installed -> R.string.veyra_ksu_status_not_installed
                            veyraKsuState.enabled && veyraKsuState.stage.isNotBlank() ->
                                R.string.veyra_ksu_status_active
                            veyraKsuState.enabled -> R.string.veyra_ksu_status_enabled
                            else -> R.string.veyra_ksu_status_disabled
                        },
                    ),
                    position = SettingsCardPosition.Middle,
                    onClick = {
                        clickHaptic(view)
                        showVeyraKsuPage = true
                    },
                )
                if (RootMigrationStore.migrationAvailable(context)) {
                    SettingsCard(
                        icon = Icons.Rounded.SwapHoriz,
                        title = stringResource(R.string.migration_title),
                        description = stringResource(R.string.migration_card_summary),
                        value = RootMigrationStore.plan(context).providerLabel.orEmpty(),
                        position = SettingsCardPosition.Middle,
                        onClick = {
                            clickHaptic(view)
                            showRootMigrationPage = true
                        },
                    )
                }
                SettingsCard(
                    modifier = Modifier.onGloballyPositioned { coordinates ->
                        autoRootSettleMenuTop =
                            with(density) { coordinates.positionInWindow().y.toDp() }
                    },
                    icon = Icons.Rounded.HourglassEmpty,
                    title = stringResource(R.string.settings_autoroot_settle),
                    description = stringResource(R.string.settings_autoroot_settle_summary),
                    value = BootSettle.label(autoRootSettleSeconds),
                    position = SettingsCardPosition.Bottom,
                    enabled = loadKernelSu,
                    onClick = {
                        clickHaptic(view)
                        showAutoRootSettleDialog = true
                    },
                )
            }
        }

        settingsSectionHeading(
            SettingsSection.Recovery,
            openSections,
            indexRows,
            toggleSection,
            onPinnedHeight = { pinnedHeadingHeight = it },
            navigationMode = navigationMode,
            visible = showSectionHeadings,
        )
        if (SettingsSection.Recovery in openSections) item {
            SettingsSectionBody {
                RootRecoverySection(
                    // Root on boot is what would bring root back, so it is turned off before the reboot
                    // is asked for and this screen has to follow whatever was stored.
                    onBootRootModeChanged = onBootRootModeChanged,
                    // Every action here consumes the root a verified load installed, so with loading
                    // switched off they are not offered as things that will work.
                    kernelSuLoadingEnabled = loadKernelSu,
                    // The card a refusal points at is in this same list, so the jump is a scroll rather
                    // than a new window.
                    onOpenSetting = { target -> jumpTarget = target },
                )
            }
        }

        settingsSectionHeading(
            SettingsSection.System,
            openSections,
            indexRows,
            toggleSection,
            onPinnedHeight = { pinnedHeadingHeight = it },
            navigationMode = navigationMode,
            visible = showSectionHeadings,
        )
        if (SettingsSection.System in openSections) item {
            SettingsSectionBody {
                SettingsCard(
                    icon = Icons.Rounded.BatterySaver,
                    title = stringResource(R.string.settings_battery),
                    description = stringResource(
                        if (batteryUnrestricted) {
                            R.string.settings_battery_summary_allowed
                        } else {
                            R.string.settings_battery_summary_restricted
                        },
                    ),
                    value = stringResource(
                        if (batteryUnrestricted) {
                            R.string.settings_battery_allowed
                        } else {
                            R.string.settings_battery_allow
                        },
                    ),
                    // One group, like every other section of the page: the two cards here are separate
                    // settings but they are read as one part of the app, and two single cards with a gap
                    // between them made this the one section that looked like a different screen.
                    position = SettingsCardPosition.Top,
                    onClick = onRequestBatteryExemption,
                )
                SettingsSwitchCard(
                    icon = Icons.Rounded.SystemUpdate,
                    title = "Disable Updates",
                    description = when {
                        systemUpdateBusy -> "Applying through CVeyra…"
                        cveyraBackend == CVeyraBackend.None ->
                            "Requires CVeyra through Root or Wireless ADB."
                        systemUpdateState.controlled ->
                            "Automatic OTA and supported OEM updater components are blocked."
                        else ->
                            "Blocks automatic OTA plus the installed OEM updater app when one is safely identifiable."
                    },
                    checked = systemUpdateState.controlled,
                    position = SettingsCardPosition.Middle,
                    enabled = !systemUpdateBusy && cveyraBackend != CVeyraBackend.None,
                    onCheckedChange = { disable ->
                        systemUpdateBusy = true
                        systemUpdateMessage = null
                        scope.launch {
                            val result = withContext(Dispatchers.IO) {
                                if (disable) {
                                    SystemUpdateControl.disable(context, device)
                                } else {
                                    SystemUpdateControl.enable(context)
                                }
                            }
                            systemUpdateState = result.snapshot
                            systemUpdateMessage = result.detail
                            systemUpdateBusy = false
                            Toast.makeText(context, result.detail, Toast.LENGTH_LONG).show()
                        }
                    },
                )
                systemUpdateMessage?.let { message ->
                    Text(
                        message,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                SettingsCard(
                    icon = Icons.Rounded.Folder,
                    title = stringResource(R.string.residue_card_title),
                    description = stringResource(R.string.residue_card_summary),
                    value = residue?.summaryLine(context)
                        ?: stringResource(R.string.residue_reading),
                    position = SettingsCardPosition.Bottom,
                    onClick = {
                        clickHaptic(view)
                        showResidueDialog = true
                    },
                )
            }
        }

    }
}

/**
 * What this app has left in `/data/local/tmp`, one row per file, read the way another app reads it.
 *
 * Read again here rather than handed the reading the card took: the card's reading was taken when
 * Settings was opened, and a run could have happened since - the list this is for is the one that is
 * true now. The fresh reading is passed back so the card's own line follows it, which is what keeps
 * the two from disagreeing about the same device.
 */
@Composable
private fun StagedResidueDialog(
    initial: ResidueReport?,
    onDismiss: () -> Unit,
    onRead: (ResidueReport) -> Unit,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    var report by remember { mutableStateOf(initial) }
    LaunchedEffect(Unit) {
        val fresh = withContext(Dispatchers.IO) { StagedResidue.read() }
        report = fresh
        onRead(fresh)
    }
    // Emptying the directory is a second step and a wider claim than anything else on this screen: it
    // takes the names this app cannot account for as well, so it is asked for, confirmed, and only
    // then attempted - and what came of it is said where the button was.
    var confirmingClear by remember { mutableStateOf(false) }
    var clearOutcome by remember { mutableStateOf<SweepOutcome?>(null) }
    // A row's own delete, waiting for its confirmation. Held with its label and whether it is this
    // app's, because that is what the confirmation has to say: taking an entry this app did not stage
    // out of a shared directory is a different claim from clearing up after itself.
    var pendingDelete by remember { mutableStateOf<PendingDelete?>(null) }
    var deleteOutcome by remember { mutableStateOf<Pair<PendingDelete, SweepOutcome>?>(null) }
    var clearing by remember { mutableStateOf(false) }
    /**
     * Removes one entry and reports what came of it.
     *
     * A named function rather than a body inside the confirmation, because two kinds of row reach it now: one
     * that confirms first and one that does not.
     */
    val deleteNow: (PendingDelete) -> Unit = { pending ->
        clearing = true
        clearOutcome = null
        scope.launch {
            val outcome = withContext(Dispatchers.IO) {
                StagingSweep.removeWhenQuiet(context, listOf(pending.path))
            }
            // The list is read again here for the same reason it is after a clear: a row's absence is the
            // receipt, and a name that survived the delete has to come back.
            val fresh = withContext(Dispatchers.IO) { StagedResidue.read() }
            report = fresh
            onRead(fresh)
            AppLog.info(
                AppLogTags.STAGING,
                context.getString(R.string.residue_log_delete, pending.label),
            )
            if (outcome !is SweepOutcome.Done || outcome.left.isNotEmpty() ||
                outcome.complaint.isNotEmpty()
            ) {
                AppLog.info(AppLogTags.STAGING, outcome.clearLogLine(context))
            }
            deleteOutcome = pending to outcome
            clearing = false
        }
    }
    val reading = report
    val present = reading?.present.orEmpty()
    // The half a catalog cannot produce: names the app does not write, listed through a shell. Shown
    // rather than counted, because what makes them worth knowing is which names they are.
    val extras = reading?.extras.orEmpty()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.residue_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    stringResource(R.string.residue_dialog_help),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                when {
                    reading == null -> Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        LoadingIndicator(modifier = Modifier.size(18.dp))
                        Text(
                            stringResource(R.string.residue_reading),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }

                    reading.blind -> Text(
                        stringResource(R.string.residue_blind_body),
                        style = MaterialTheme.typography.bodyMedium,
                    )

                    // Two clean sentences, because there are two clean readings: an empty directory,
                    // and a directory that could not be listed and holds none of the known names.
                    present.isEmpty() && extras.isEmpty() -> Text(
                        stringResource(
                            if (reading.directoryListed) {
                                R.string.residue_clean_listed_body
                            } else {
                                R.string.residue_clean_by_name_body
                            },
                            reading.findings.size,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                    )

                    else -> LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = RESIDUE_LIST_MAX),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        if (present.isNotEmpty()) {
                            item(key = "staged") {
                                ResidueSectionLabel(stringResource(R.string.residue_section_staged))
                            }
                            items(present, key = { it.staged.path }) { finding ->
                                ResidueRow(
                                    finding = finding,
                                    deleteEnabled = !clearing,
                                    // This app's own staging, removed on one tap: nothing else is lost, because
                                    // the app holds its own copy of every file it staged, and the run that
                                    // would need them has finished. Asking first charged every cleanup a
                                    // confirmation to protect against a mistake with no consequence.
                                    onDelete = {
                                        deleteNow(
                                            PendingDelete(
                                                label = finding.staged.name,
                                                path = finding.staged.path,
                                            ),
                                        )
                                    },
                                )
                            }
                        }
                        if (extras.isNotEmpty()) {
                            item(key = "others") {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    ResidueSectionLabel(
                                        stringResource(R.string.residue_section_others),
                                    )
                                    Text(
                                        stringResource(R.string.residue_others_body),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            items(extras, key = { "extra:${it.name}" }) { entry ->
                                TempEntryRow(
                                    entry = entry,
                                    deleteEnabled = !clearing,
                                    onDelete = {
                                        pendingDelete = PendingDelete(entry.name, entry.path)
                                    },
                                )
                            }
                        }
                    }
                }
                // What the check looked at, said out loud, because the count of what it found means
                // nothing without the count of what it asked about - and because a path that could not
                // be read is not a path that is not there.
                if (reading != null && !reading.blind) {
                    Text(
                        text = stringResource(
                            R.string.residue_dialog_checked,
                            reading.findings.size,
                            reading.findings.count { it.reading is ResidueReading.Unreadable },
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    // Said where the names are, not in the help text at the top: what it changes is how
                    // much this particular list is worth.
                    if (!reading.directoryListed) {
                        Text(
                            text = stringResource(R.string.residue_unlisted_body),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                // The one action that changes the device rather than describing it, and the only one
                // here that can take something that is not this app's - so it is offered last, in the
                // error colour, and only when there is something to remove.
                if (reading != null && !reading.blind && (present.isNotEmpty() || extras.isNotEmpty())) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilledTonalButton(
                            enabled = !clearing,
                            onClick = {
                                clickHaptic(view)
                                confirmingClear = true
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                            ),
                        ) {
                            if (clearing) {
                                LoadingIndicator(modifier = Modifier.size(18.dp))
                            } else {
                                Text(stringResource(R.string.residue_clear))
                            }
                        }
                        clearOutcome?.let { outcome ->
                            Text(
                                text = clearOutcomeLine(context, outcome),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        deleteOutcome?.let { (deleted, outcome) ->
                            Text(
                                text = deleteOutcomeLine(context, deleted.label, outcome),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = reading != null && (present.isNotEmpty() || extras.isNotEmpty()),
                onClick = {
                    clickHaptic(view)
                    val lines = present.map { finding ->
                        val at = finding.reading as ResidueReading.Present
                        "${finding.staged.name}\t${StagedResidue.sizeLabel(at.sizeBytes)}\t" +
                            StagedResidue.ageLabelOf(at.modifiedAtMillis)
                    } + extras.map { entry -> tempEntryLine(context, entry) }
                    copyLogToClipboard(context, lines.joinToString("\n"))
                },
            ) {
                Text(stringResource(R.string.residue_copy))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_close))
            }
        },
    )

    if (confirmingClear) {
        AlertDialog(
            onDismissRequest = { confirmingClear = false },
            title = { Text(stringResource(R.string.residue_clear_title)) },
            text = { Text(stringResource(R.string.residue_clear_body, extras.size)) },
            confirmButton = {
                TextButton(onClick = {
                    clickHaptic(view)
                    confirmingClear = false
                    clearing = true
                    deleteOutcome = null
                    scope.launch {
                        val outcome = withContext(Dispatchers.IO) {
                            StagingSweep.clearWhenQuiet(context)
                        }
                        // The list is the receipt, not the outcome: what the read finds afterwards is the
                        // only thing that says whether the delete actually happened.
                        val fresh = withContext(Dispatchers.IO) { StagedResidue.read() }
                        report = fresh
                        onRead(fresh)
                        AppLog.info(AppLogTags.STAGING, outcome.clearLogLine(context))
                        clearOutcome = outcome
                        clearing = false
                    }
                }) {
                    Text(stringResource(R.string.residue_clear_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    clickHaptic(view)
                    confirmingClear = false
                }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    pendingDelete?.let { pending ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.residue_delete_title, pending.label)) },
            text = { Text(stringResource(R.string.residue_delete_other)) },
            confirmButton = {
                TextButton(onClick = {
                    clickHaptic(view)
                    pendingDelete = null
                    deleteNow(pending)
                }) {
                    Text(stringResource(R.string.residue_clear_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    clickHaptic(view)
                    pendingDelete = null
                }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

/**
 * A row's delete, held between the button that asked and the confirmation that agrees to it.
 *
 * Only rows this app did not stage ever get here: one of its own files goes on a single tap, because the app
 * holds its own copy of everything it staged and a finished run does not need it back. A name in that
 * directory that the app did not write is a different thing to remove, and that is what the confirmation is
 * for.
 */
private data class PendingDelete(val label: String, val path: String)

/** What one row's delete came to, said under the list rather than beside a row that may be gone. */
private fun deleteOutcomeLine(context: Context, label: String, outcome: SweepOutcome): String =
    when (outcome) {
        SweepOutcome.NoShell -> context.getString(R.string.residue_clear_no_shell)
        SweepOutcome.SkippedRun -> context.getString(R.string.residue_clear_skipped)
        is SweepOutcome.Done -> when {
            outcome.complaint.isNotEmpty() ->
                context.getString(R.string.residue_delete_refused, label, outcome.complaint)
            outcome.left.isNotEmpty() -> context.getString(R.string.residue_delete_left, label)
            else -> context.getString(R.string.residue_delete_done, label)
        }
    }

/** What a clear came to, said where the button that asked for it was. */
private fun clearOutcomeLine(context: Context, outcome: SweepOutcome): String = when (outcome) {
    SweepOutcome.NoShell -> context.getString(R.string.residue_clear_no_shell)
    SweepOutcome.SkippedRun -> context.getString(R.string.residue_clear_skipped)
    is SweepOutcome.Done -> when {
        outcome.complaint.isNotEmpty() ->
            context.getString(R.string.residue_clear_refused, outcome.complaint)
        outcome.left.isNotEmpty() ->
            context.getString(R.string.residue_clear_left, outcome.left.size)
        else -> context.getString(R.string.residue_clear_done, outcome.removed)
    }
}

/** One staged file: the name a detector matches on, then what it is and how long it has been there. */
@Composable
private fun ResidueRow(
    finding: ResidueFinding,
    deleteEnabled: Boolean,
    onDelete: () -> Unit,
) {
    val reading = finding.reading as? ResidueReading.Present ?: return
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = finding.staged.name,
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace,
            )
            Text(
                text = stringResource(
                    R.string.residue_row_detail,
                    stringResource(finding.staged.role.labelRes),
                    StagedResidue.sizeLabel(reading.sizeBytes),
                    StagedResidue.ageLabelOf(reading.modifiedAtMillis),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        ResidueDeleteButton(name = finding.staged.name, enabled = deleteEnabled, onDelete = onDelete)
    }
}

/**
 * The delete at the end of a row, which is the only thing in this list that changes the device.
 *
 * One per row rather than a selection mode with a shared action, because a residue list is read one
 * name at a time - the name is what makes somebody want it gone - and a mode would be a second thing
 * to explain before anything could be deleted at all.
 */
@Composable
private fun ResidueDeleteButton(name: String, enabled: Boolean, onDelete: () -> Unit) {
    val view = LocalView.current
    IconButton(
        enabled = enabled,
        onClick = {
            clickHaptic(view)
            onDelete()
        },
    ) {
        Icon(
            imageVector = Icons.Rounded.Delete,
            contentDescription = stringResource(R.string.residue_delete_row, name),
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.error,
        )
    }
}

/** A heading inside the residue list, which has two halves worth telling apart. */
@Composable
private fun ResidueSectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 6.dp),
    )
}

/**
 * One entry that this app did not stage: its name, then everything that can honestly be said about it.
 *
 * Which is less than a staged row says, and deliberately so: the role is unknown by definition, and a
 * name whose stat was denied is reported as unreadable rather than left out of the list. What is worth
 * knowing here is the name, because the name is the whole of what a detector matches on.
 */
@Composable
private fun TempEntryRow(
    entry: TempEntry,
    deleteEnabled: Boolean,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current
    val at = entry.reading as? ResidueReading.Present
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = entry.name,
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace,
            )
            Text(
                text = stringResource(
                    R.string.residue_row_detail,
                    stringResource(R.string.residue_role_other),
                    tempEntrySizeLabel(context, entry),
                    StagedResidue.ageLabelOf(at?.modifiedAtMillis),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        ResidueDeleteButton(name = entry.name, enabled = deleteEnabled, onDelete = onDelete)
    }
}

/** The size line for an entry, which for a folder or a denied stat is not a number at all. */
private fun tempEntrySizeLabel(context: Context, entry: TempEntry): String = when {
    entry.reading !is ResidueReading.Present -> context.getString(R.string.residue_size_unreadable)
    entry.isDirectory -> context.getString(R.string.residue_size_folder)
    else -> StagedResidue.sizeLabel(entry.reading.sizeBytes)
}

/** One such entry as a line of the copied list, which is pasted next to a report rather than read. */
private fun tempEntryLine(context: Context, entry: TempEntry): String {
    val at = entry.reading as? ResidueReading.Present
    return "${entry.name}\t${tempEntrySizeLabel(context, entry)}\t" +
        StagedResidue.ageLabelOf(at?.modifiedAtMillis) + "\t" +
        context.getString(R.string.residue_role_other)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TargetSelectionSheet(
    device: DeviceSnapshot,
    catalog: TargetCatalogUiState,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
    onNext: (TargetProfile) -> Unit,
) {
    val context = LocalContext.current
    // Read once and written on every change: the answer is a standing preference, not a question for
    // this visit, and a sheet that forgot it would have to be corrected at every run - which is how
    // the wrong target gets picked.
    var showOnlyMyDevice by remember { mutableStateOf(AppPreferences.targetFitsDeviceOnly(context)) }
    var query by rememberSaveable { mutableStateOf("") }
    var selectedSelectionId by remember { mutableStateOf<String?>(null) }
    val view = LocalView.current
    val visibleProfiles = remember(catalog.profiles, showOnlyMyDevice, device, query) {
        visibleTargets(
            profiles = catalog.profiles,
            device = device,
            fitsDeviceOnly = showOnlyMyDevice,
            query = query,
        )
    }
    val selectedProfile = catalog.profiles.firstOrNull { it.selectionId == selectedSelectionId }

    // Preselect what the catalog prefers, so a device whose feed lists an exact kernel release
    // starts on that profile instead of an arbitrary three-part sibling. Only fills an empty
    // selection: a profile the user picked is never replaced by a catalog reload.
    LaunchedEffect(catalog.profiles) {
        if (selectedSelectionId == null) {
            selectedSelectionId = catalog.profiles.resolveFor(device)?.selectionId
        }
    }

    ModalBottomSheet(
        // Fully expanded, not peeking: the payloads above the fold are the ones a device actually
        // fits, and opening half-height hid them behind a swipe that looked like the list ending.
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        onDismissRequest = onDismiss,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    stringResource(R.string.select_device_title),
                    style = MaterialTheme.typography.headlineSmall,
                )
                Text(
                    stringResource(R.string.select_device_description),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .toggleable(
                        value = showOnlyMyDevice,
                        role = Role.Checkbox,
                        onValueChange = { enabled ->
                            clickHaptic(view)
                            showOnlyMyDevice = enabled
                            AppPreferences.setTargetFitsDeviceOnly(context, enabled)
                            if (enabled && selectedProfile?.matches(device) == false) {
                                selectedSelectionId = null
                            }
                        },
                    )
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Checkbox(checked = showOnlyMyDevice, onCheckedChange = null)
                Text(stringResource(R.string.show_my_device_only), style = MaterialTheme.typography.titleMedium)
            }

            // Beside the toggle rather than over the list: with a dozen sources configured the sheet
            // can hold every device its catalogs know, and the row being looked for is found by name
            // long before it is found by scrolling.
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text(stringResource(R.string.target_search)) },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                trailingIcon = if (query.isEmpty()) {
                    null
                } else {
                    {
                        IconButton(onClick = {
                            clickHaptic(view)
                            query = ""
                        }) {
                            Icon(Icons.Rounded.Close, contentDescription = null)
                        }
                    }
                },
            )

            if (catalog.sourceFailures.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        stringResource(R.string.payload_sources_failed),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.error,
                    )
                    catalog.sourceFailures.forEach { failure ->
                        Text(
                            failure,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }

            when {
                catalog.loading -> Box(
                    modifier = Modifier.fillMaxWidth().height(220.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    LoadingIndicator(color = MaterialTheme.colorScheme.onSurface)
                }
                catalog.error != null -> Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(catalog.error, color = MaterialTheme.colorScheme.error)
                    FilledTonalButton(onClick = onRetry) {
                        Text(stringResource(R.string.action_retry))
                    }
                }
                // Which of the two controls emptied the list, said rather than left to be worked out -
                // and with the way out of it under the sentence, since a search that matches nothing
                // is one tap from a list that does.
                visibleProfiles.isEmpty() -> Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = if (query.isBlank()) {
                            stringResource(R.string.no_matching_devices)
                        } else {
                            stringResource(R.string.no_matching_devices_query, query.trim())
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    FilledTonalButton(onClick = {
                        clickHaptic(view)
                        query = ""
                        showOnlyMyDevice = false
                        AppPreferences.setTargetFitsDeviceOnly(context, false)
                    }) {
                        Text(stringResource(R.string.target_show_everything))
                    }
                }
                else -> LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 480.dp)
                        .selectableGroup(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(visibleProfiles, key = { it.selectionId }) { profile ->
                        val selected = selectedSelectionId == profile.selectionId
                        val matchingModel = profile.models.firstOrNull {
                            it.equals(device.model, ignoreCase = true)
                        }
                        val modelLabel = matchingModel ?: profile.models.take(3).joinToString().let {
                            if (profile.models.size > 3) "$it +${profile.models.size - 3}" else it
                        }
                        // Regional siblings share a model and a three-part kernel version, so the
                        // only thing telling them apart in this list is whether the feed ties the
                        // profile to this build's full release.
                        val kernelMatch = profile.kernelMatch(device)
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.large,
                            color = if (selected) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerHighest
                            },
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .selectable(
                                        selected = selected,
                                        role = Role.RadioButton,
                                        onClick = {
                                            clickHaptic(view)
                                            selectedSelectionId = profile.selectionId
                                        },
                                    )
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                RadioButton(selected = selected, onClick = null)
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        profile.displayName,
                                        style = MaterialTheme.typography.titleMedium,
                                    )
                                    Text(
                                        modelLabel,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Text(
                                        when (kernelMatch) {
                                            KernelMatch.Exact ->
                                                stringResource(R.string.kernel_match_exact)
                                            KernelMatch.Version -> stringResource(
                                                R.string.kernel_match_version,
                                                device.kernelVersion,
                                            )
                                            KernelMatch.None ->
                                                stringResource(R.string.kernel_match_none)
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = when (kernelMatch) {
                                            KernelMatch.Exact -> MaterialTheme.colorScheme.primary
                                            KernelMatch.Version -> MaterialTheme.colorScheme.onSurfaceVariant
                                            KernelMatch.None -> MaterialTheme.colorScheme.error
                                        },
                                    )
                                    // What the run this candidate would start stages - the KernelSU
                                    // whose manager is the one built against it. It is the fact the
                                    // manager offer is derived from, so it belongs where the choice is
                                    // made rather than in Settings after the fact, and a sibling that
                                    // declares nothing says so instead of leaving the gap unexplained.
                                    Text(
                                        text = profile.kernelSuVersion?.let { version ->
                                            stringResource(
                                                R.string.target_loads_kernelsu,
                                                profile.flavor.label,
                                                version,
                                            )
                                        } ?: stringResource(
                                            R.string.target_loads_kernelsu_unknown,
                                            profile.flavor.label,
                                        ),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    if (profile.sourceLabel.isNotEmpty()) {
                                        Text(
                                            profile.sourceLabel,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            HorizontalDivider()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TextButton(onClick = {
                    clickHaptic(view)
                    onDismiss()
                }, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.action_cancel))
                }
                Button(
                    onClick = {
                        clickHaptic(view)
                        selectedProfile?.let(onNext)
                    },
                    enabled = selectedProfile != null,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.action_next))
                }
            }
        }
    }
}

/**
 * Everything the run-plan dialog reports: which target the app would pick, which transport it
 * would use, and the environment and ceilings that go with them.
 */
private data class RunPlanDisplay(
    val deviceLabel: String,
    val targetLabel: String?,
    val sourceLabel: String?,
    val unresolvedNote: String?,
    val freshSession: Boolean,
    val shizuku: Boolean,
    val payloadMode: PayloadMode,
    val partitionReadOnly: Boolean,
    val cachedOffset: String?,
    val plan: ExploitPlan,
)

/**
 * What is cached for offline use, and the way to get rid of it.
 *
 * The digests are shown because they are the reason to trust the cache at all: every one of them is
 * checked again before a run uses the files, so what is displayed here is what will be enforced.
 */
@Composable
private fun CachedPayloadDialog(
    cached: CachedPayload?,
    onForget: () -> Unit,
    onDismiss: () -> Unit,
) {
    val view = LocalView.current
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.DownloadForOffline, contentDescription = null) },
        title = {
            DialogDimAmount(0.34f)
            Text(stringResource(R.string.cached_payload_title))
        },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (cached == null) {
                    Text(stringResource(R.string.settings_cached_payload_none))
                } else {
                    // The name first and the id under it, which together are what the settings row
                    // used to carry on one line of value - and the id is here in full, in the place
                    // where a long precise string costs nothing.
                    RunPlanRow(
                        stringResource(R.string.cached_payload_device),
                        cached.displayName,
                        first = true,
                    )
                    RunPlanRow(stringResource(R.string.cached_payload_profile), cached.profileId)
                    RunPlanRow(
                        stringResource(R.string.cached_payload_exploit_sha),
                        cached.exploit.sha256 ?: stringResource(R.string.cached_payload_no_digest),
                    )
                    RunPlanRow(
                        stringResource(R.string.cached_payload_kernelsu_sha),
                        cached.kernelSu.sha256 ?: stringResource(R.string.cached_payload_no_digest),
                    )
                    Text(
                        stringResource(R.string.cached_payload_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                clickHaptic(view)
                onDismiss()
            }) {
                Text(stringResource(R.string.action_close))
            }
        },
        dismissButton = if (cached == null) {
            null
        } else {
            {
                TextButton(onClick = {
                    clickHaptic(view)
                    onForget()
                }) {
                    Text(stringResource(R.string.cached_payload_forget))
                }
            }
        },
    )
}

/**
 * Shows what a run will be handed before it is started, so a run that ends at a ceiling says so
 * here first. Every value comes from the same constants the run uses.
 */
@Composable
private fun RunPlanDialog(
    display: RunPlanDisplay,
    onDismiss: () -> Unit,
) {
    val view = LocalView.current
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Schedule, contentDescription = null) },
        title = {
            DialogDimAmount(0.34f)
            Text(stringResource(R.string.run_plan_title))
        },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                RunPlanRow(stringResource(R.string.run_plan_device), display.deviceLabel, first = true)
                RunPlanRow(
                    stringResource(R.string.run_plan_target),
                    display.targetLabel ?: display.unresolvedNote.orEmpty(),
                )
                // Always shown, even with nothing to put in it. It used to be dropped when the profile
                // had no source, which made a cached run look like a target from nowhere - and the
                // question "which catalog is this from" is exactly the one the row is for.
                RunPlanRow(
                    stringResource(R.string.run_plan_source),
                    display.sourceLabel
                        ?: stringResource(R.string.run_plan_source_none),
                )
                RunPlanRow(
                    stringResource(R.string.run_plan_transport),
                    stringResource(
                        if (display.shizuku) R.string.run_plan_transport_shizuku
                        else R.string.run_plan_transport_direct,
                    ),
                )
                RunPlanRow(
                    stringResource(R.string.run_plan_boot_settle),
                    BootSettle.label(display.plan.bootSettleSeconds),
                )
                RunPlanRow(
                    stringResource(R.string.run_plan_payload_mode),
                    stringResource(
                        if (display.payloadMode == PayloadMode.Offline) {
                            R.string.settings_payload_mode_offline
                        } else {
                            R.string.settings_payload_mode_online
                        },
                    ),
                )
                RunPlanRow(
                    stringResource(R.string.run_plan_partition_read_only),
                    stringResource(
                        if (display.partitionReadOnly) R.string.run_plan_partition_read_only_on
                        else R.string.run_plan_partition_read_only_off,
                    ),
                )
                RunPlanRow(
                    stringResource(R.string.run_plan_session),
                    stringResource(
                        if (display.freshSession) R.string.run_plan_fresh_yes
                        else R.string.run_plan_fresh_no,
                    ),
                )
                RunPlanSection(stringResource(R.string.run_plan_variables))
                // Where these come from, said where they are shown. The second wording is the one this
                // feature exists for: once the app supplies any of them, "set by the payload profile, not
                // by this app" would be a lie about the run in front of the reader.
                Text(
                    stringResource(
                        if (display.plan.routePolicy.anyFromApp) {
                            R.string.run_plan_variables_note_overridden
                        } else {
                            R.string.run_plan_variables_note
                        },
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                // The three a user can move, as rows carrying the side that chose them - the half of the
                // answer the raw variables below cannot give. Their values still come from the
                // environment the run is handed, so a row cannot show a number the payload never gets.
                val policy = display.plan.routePolicy
                RunPlanRow(
                    stringResource(R.string.run_plan_attempts),
                    display.plan.environment["EXPLOIT_ATTEMPTS"]
                        ?: policy.policy.attempts.toString(),
                    note = originNote(policy.attemptsOrigin),
                )
                RunPlanRow(
                    stringResource(R.string.run_plan_attempt_timeout),
                    display.plan.environment["EXPLOIT_ATTEMPT_TIMEOUT_SEC"]
                        ?: stringResource(R.string.run_plan_value_not_applied),
                    note = originNote(policy.attemptTimeoutOrigin),
                )
                RunPlanRow(
                    stringResource(R.string.run_plan_slide_route),
                    display.plan.environment["SLIDE_SOURCE"]
                        ?: stringResource(R.string.run_plan_value_payload_default),
                    note = originNote(policy.slideRouteOrigin),
                )
                val otherVariables = display.plan.environment
                    .filterKeys { it !in ExploitRoutePolicy.OVERRIDABLE_ENV_NAMES }
                if (otherVariables.isEmpty()) {
                    // Reachable only for a fresh session, whose environment is its one attempt: the
                    // wording used to promise "the payload uses its own defaults", which is the opposite
                    // of what that case does.
                    RunPlanMonospace(stringResource(R.string.run_plan_variables_none))
                } else {
                    otherVariables.forEach { (name, value) ->
                        RunPlanMonospace("$name=$value")
                    }
                }
                if (display.plan.shizukuArguments.isNotEmpty()) {
                    RunPlanSection(stringResource(R.string.run_plan_shizuku_arguments))
                    display.plan.shizukuArguments.forEach { (name, value) ->
                        RunPlanMonospace("$name=$value")
                    }
                }
                RunPlanRow(
                    stringResource(R.string.run_plan_cached_offset),
                    display.cachedOffset
                        ?: stringResource(R.string.run_plan_cached_offset_none),
                )
                RunPlanSection(stringResource(R.string.run_plan_limits))
                Text(
                    stringResource(R.string.run_plan_limits_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                RunPlanRow(
                    stringResource(R.string.run_plan_stall),
                    display.plan.stallLimitMillis
                        ?.let(::formatDuration)
                        ?: stringResource(R.string.run_plan_stall_none),
                )
                RunPlanRow(
                    stringResource(R.string.run_plan_total),
                    formatDuration(display.plan.totalLimitMillis),
                )
                RunPlanRow(
                    stringResource(R.string.run_plan_helper),
                    formatDuration(display.plan.helperLimitMillis),
                )
                Text(
                    stringResource(R.string.run_plan_footnote),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                clickHaptic(view)
                onDismiss()
            }) {
                Text(stringResource(R.string.action_close))
            }
        },
    )
}

/**
 * The three ceilings, all in one dialog.
 *
 * One dialog rather than a menu per row: they are three answers to one question - how long this app lets
 * a run go on - and a value only makes sense beside the other two. Each group says what it does and, for
 * the two that have one, the rule that can override the choice: a fresh session keeps the app's own hour
 * whatever the whole-run setting says, and a stall limit is never applied to one.
 */
@Composable
private fun RunLimitsDialog(
    limits: RunLimitsSettings,
    override: ExploitOverrideSettings,
    onChanged: (RunLimit, Int) -> Unit,
    onOverrideChanged: (ExploitOverrideSettings) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
) {
    val view = LocalView.current
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Timer, contentDescription = null) },
        title = {
            DialogDimAmount(0.34f)
            Text(stringResource(R.string.run_limits_title))
        },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                RunLimitGroup(
                    title = stringResource(R.string.run_limits_total),
                    note = stringResource(R.string.run_limits_total_note),
                    limit = RunLimit.Total,
                    selected = limits.totalSeconds,
                    onChanged = onChanged,
                )
                RunLimitGroup(
                    title = stringResource(R.string.run_limits_stall),
                    note = stringResource(R.string.run_limits_stall_note),
                    limit = RunLimit.Stall,
                    selected = limits.stallSeconds,
                    onChanged = onChanged,
                )
                RunLimitGroup(
                    title = stringResource(R.string.run_limits_helper),
                    note = stringResource(R.string.run_limits_helper_note),
                    limit = RunLimit.Helper,
                    selected = limits.helperSeconds,
                    onChanged = onChanged,
                )
                // The other half of the answer: the payload's numbers, handed over as its own
                // variables, and the switch that lets the app's numbers take their place.
                Text(
                    stringResource(R.string.run_limits_payload_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
                ExploitOverrideGroup(
                    override = override,
                    onChanged = onOverrideChanged,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                clickHaptic(view)
                onDismiss()
            }) {
                Text(stringResource(R.string.action_close))
            }
        },
        // In the dismiss slot, which is where the "other" action belongs: it is not the way out of the
        // dialog, and it is the one thing here that changes more than the value just tapped.
        dismissButton = {
            TextButton(onClick = {
                clickHaptic(view)
                onReset()
            }) {
                Text(stringResource(R.string.run_limits_reset))
            }
        },
    )
}

@Composable
private fun RunLimitGroup(
    title: String,
    note: String,
    limit: RunLimit,
    selected: Int,
    onChanged: (RunLimit, Int) -> Unit,
) {
    val view = LocalView.current
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
        Text(
            note,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // A wrapped row of choices rather than a menu: three values fit on one line, six do not, and a
        // scrollable list of six per group would bury the value in use.
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            RunLimits.options(limit).forEach { seconds ->
                FilterChip(
                    selected = seconds == selected,
                    onClick = {
                        clickHaptic(view)
                        onChanged(limit, seconds)
                    },
                    label = { Text(RunLimits.label(seconds)) },
                )
            }
        }
    }
}

/**
 * The opt-in half of this dialog: the app's own numbers for the payload's exploit.
 *
 * A switch and then nothing until it is on, because the off state is not a choice among values - it is
 * the payload's numbers, which is what every shipped target was validated with, and putting three more
 * pickers in front of someone who does not want them is how a settings screen turns into a wall. The
 * note says what turning it on actually does, including the case where it does not apply at all.
 */
@Composable
private fun ExploitOverrideGroup(
    override: ExploitOverrideSettings,
    onChanged: (ExploitOverrideSettings) -> Unit,
) {
    val view = LocalView.current
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        HorizontalDivider(
            modifier = Modifier.padding(top = 10.dp, bottom = 6.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                stringResource(R.string.run_limits_override),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            Switch(
                checked = override.enabled,
                onCheckedChange = { enabled ->
                    clickHaptic(view)
                    onChanged(override.copy(enabled = enabled))
                },
            )
        }
        Text(
            stringResource(R.string.run_limits_override_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (!override.enabled) return
        OverrideChoiceGroup(
            title = stringResource(R.string.run_limits_override_attempts),
            options = ExploitOverride.allowedAttempts,
            selected = override.attempts,
            label = { it.toString() },
            onSelected = { onChanged(override.copy(attempts = it)) },
        )
        OverrideChoiceGroup(
            title = stringResource(R.string.run_limits_override_timeout),
            options = ExploitOverride.allowedTimeouts,
            selected = override.attemptTimeoutSec,
            label = { RunLimits.label(it) },
            onSelected = { onChanged(override.copy(attemptTimeoutSec = it)) },
        )
        OverrideChoiceGroup(
            title = stringResource(R.string.run_limits_override_route),
            options = ExploitOverride.allowedRoutes,
            selected = override.slideRoute,
            label = { stringResource(routeLabelRes(it)) },
            onSelected = { onChanged(override.copy(slideRoute = it)) },
        )
    }
}

/** One value of the override, as a wrapped row of chips joined by a single label. */
@Composable
private fun <T> OverrideChoiceGroup(
    title: String,
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelected: (T) -> Unit,
) {
    val view = LocalView.current
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 6.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            options.forEach { option ->
                FilterChip(
                    selected = option == selected,
                    onClick = {
                        clickHaptic(view)
                        onSelected(option)
                    },
                    label = { Text(label(option)) },
                )
            }
        }
    }
}

/** Who chose one of the three overridable values, as the plan's note line words it. */
@Composable
private fun originNote(origin: PolicyOrigin): String = stringResource(
    when (origin) {
        PolicyOrigin.Payload -> R.string.run_plan_from_payload
        PolicyOrigin.App -> R.string.run_plan_from_app
        PolicyOrigin.FreshSession -> R.string.run_plan_from_fresh_session
    },
)

/** The settings label for a route: the two raw tokens stay as the payload spells them. */
private fun routeLabelRes(route: SlideRoute): Int = when (route) {
    SlideRoute.Default -> R.string.run_limits_route_default
    SlideRoute.Auto -> R.string.run_limits_route_auto
    SlideRoute.Tracefs -> R.string.run_limits_route_tracefs
    SlideRoute.Legacy -> R.string.run_limits_route_legacy
}

/**
 * One row of the plan: its name, its value, and the hairline that separates it from the next.
 *
 * The line is what makes this a list rather than a paragraph. Fourteen label-and-value pairs stacked
 * with nothing between them read as one block, and the pair a reader is looking for is the one they
 * have to hunt through; a rule per row is what lets the eye run down the names instead.
 */
@Composable
private fun RunPlanRow(
    label: String,
    value: String,
    note: String? = null,
    first: Boolean = false,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        if (!first) {
            HorizontalDivider(
                modifier = Modifier.padding(bottom = 6.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
            )
        }
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(value, style = MaterialTheme.typography.bodyMedium)
        // Under the value rather than beside it: the value is the thing being read, and the note is the
        // answer to the question it raises.
        if (note != null) {
            Text(
                note,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun RunPlanSection(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(top = 4.dp),
    )
}

@Composable
private fun RunPlanMonospace(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        fontFamily = FontFamily.Monospace,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

private fun formatDuration(millis: Long): String {
    val seconds = millis / 1000
    return when {
        seconds % 3600 == 0L -> "${seconds / 3600} h"
        seconds % 60 == 0L -> "${seconds / 60} min"
        else -> "$seconds s"
    }
}

/**
 * Import, replace, or drop the payload used in place of the downloaded exploit. The file is copied
 * into app storage here rather than referenced by URI, so an unattended run at boot can use it.
 */
@Composable
private fun LocalPayloadDialog(
    initialName: String?,
    onDismiss: () -> Unit,
    onNameChanged: (String?) -> Unit,
) {
    val context = LocalContext.current
    val view = LocalView.current
    var name by remember(initialName) { mutableStateOf(initialName) }
    var error by remember { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching { LocalPayload.import(context, uri) }
            .onSuccess { imported ->
                name = imported
                error = null
                onNameChanged(imported)
            }
            .onFailure { failure ->
                // The previously imported payload is still in place; only the message changes.
                error = failure.message ?: failure.javaClass.simpleName
            }
    }
    val current = name
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.UploadFile, contentDescription = null) },
        title = {
            DialogDimAmount(0.34f)
            Text(stringResource(R.string.local_payload_title))
        },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    if (current == null) {
                        stringResource(R.string.local_payload_summary_none)
                    } else {
                        stringResource(R.string.local_payload_summary_set, current)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (error != null) {
                    Text(
                        error.orEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            FilledTonalButton(onClick = {
                clickHaptic(view)
                // Some providers report .so files as octet-stream and others as nothing usable, so
                // the picker is left unfiltered and the import validates what comes back.
                picker.launch(
                    arrayOf("application/octet-stream", "application/x-sharedlib", "*/*"),
                )
            }) {
                Text(
                    stringResource(
                        if (current == null) R.string.local_payload_choose
                        else R.string.local_payload_replace,
                    ),
                )
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (current != null) {
                    TextButton(onClick = {
                        clickHaptic(view)
                        LocalPayload.clear(context)
                        name = null
                        error = null
                        onNameChanged(null)
                    }) {
                        Text(stringResource(R.string.local_payload_remove))
                    }
                }
                TextButton(onClick = {
                    clickHaptic(view)
                    onDismiss()
                }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        },
    )
}

@Composable
private fun PayloadSourcesEditor(
    padding: PaddingValues,
    device: DeviceSnapshot,
    initialSources: List<PayloadSource>,
    onDismiss: () -> Unit,
    onSave: (List<PayloadSource>) -> Unit,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    var sources by remember(initialSources) { mutableStateOf(initialSources) }
    var showAddSource by remember { mutableStateOf(false) }
    var pinError by remember { mutableStateOf<String?>(null) }
    // The pin is a choice of revision, so the lock opens a picker around one source rather than
    // pinning to whatever the branch happens to point at the moment it is tapped.
    var revisionTarget by remember { mutableStateOf<PayloadSource?>(null) }
    // What each source was found to read, keyed by source id and kept for the life of the sheet.
    // Keying by id is what makes the add form work: a repository that failed to read leaves its
    // failure under that candidate id, so editing the field clears the message without any extra
    // state, and the entry a successful check stored is the one the added row then shows.
    var checks by remember { mutableStateOf<Map<String, Result<SourceCoverage>>>(emptyMap()) }
    var checking by remember { mutableStateOf<String?>(null) }
    var repository by remember { mutableStateOf("") }
    var branch by remember { mutableStateOf(PayloadSource.DEFAULT_BRANCH) }
    var duplicate by remember { mutableStateOf(false) }
    var localSelected by remember {
        mutableStateOf(AppPreferences.useLocalPayload(context))
    }

    // Reading a source is also how it is validated: an unreachable repository, a missing manifest,
    // or a schema this app cannot read must not reach the saved list, where it would sit failing on
    // every later load. Shared with the row action, since both ask what a source actually covers.
    fun checkSource(source: PayloadSource, onCovered: () -> Unit = {}) {
        scope.launch {
            checking = source.id
            // Cancellable: the row this check belongs to can be removed, or the sheet closed, while
            // the read is out - and the failure branch of the result is what the row then shows.
            val outcome = runCatchingCancellable {
                withContext(Dispatchers.IO) { PayloadRepository(context).inspect(source, device) }
            }
            checks = checks + (source.id to outcome)
            checking = null
            if (outcome.isSuccess) onCovered()
        }
    }
    // No rule about the shape of what is typed here on purpose. A pattern cannot tell a repository that
    // exists from one that does not, so a half-typed owner only means the app argues with a form the
    // user has not finished; the read that adding a source performs is the thing that can tell, and it
    // reports what it found. The fields show examples instead of enforcing a format.
    val candidate = remember(repository, branch) { PayloadSource.create(repository, branch) }
    val candidateCheck = candidate?.let { checks[it.id] }
    val addError = candidateCheck?.exceptionOrNull()?.let {
        stringResource(
            R.string.payload_source_check_failed,
            it.message ?: it.javaClass.simpleName,
        )
    } ?: if (duplicate) {
        stringResource(R.string.payload_source_duplicate)
    } else {
        null
    }
    val enabledCount = sources.count { it.enabled }
    // The picker takes the whole screen rather than sitting above the list, so the one scroll this
    // content needs moves with it: the picker is taller than the space above a keyboard and scrolls as
    // a whole, while the list scrolls inside a fixed frame and leaves the form above it alone.
    val revisionPickerOpen = revisionTarget != null
    // Content in this app's own window, and deliberately neither a dialog nor a bottom sheet: both of
    // those are a second window, and a second window here was torn down and rebuilt as soon as the
    // form was typed into - the keyboard went with it, once per character. Nothing below can tear a
    // window down, so the cursor keeps its input connection however often this recomposes.
    //
    // Back leaves the editor, or steps out of the revision picker inside it first: the picker is a
    // step within this screen, and back must not skip both.
    BackHandler {
        if (revisionPickerOpen) revisionTarget = null else onDismiss()
    }
    Surface(
        modifier = Modifier.fillMaxSize().padding(padding),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                // The keyboard is the one inset this screen has to answer for itself: it takes the
                // space the footer needs, and the list above gives it up rather than scrolling under
                // the keys.
                .imePadding()
                .then(
                    if (revisionPickerOpen) {
                        Modifier.verticalScroll(rememberScrollState())
                    } else {
                        Modifier
                    },
                )
                .padding(horizontal = 20.dp)
                .padding(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Inside the same dialog rather than a second one: two of them would fight over the same
            // dismiss and back handling, and this one is the host the add form above needs anyway.
            revisionTarget?.let { target ->
                RevisionPicker(
                    source = target,
                    device = device,
                    onBack = { revisionTarget = null },
                    onPick = { commit ->
                        clickHaptic(view)
                        revisionTarget = null
                        val updated = if (commit == null) {
                            sources.withSourceUnpinned(target.id)
                        } else {
                            sources.withSourcePinned(target.id, commit)
                        }
                        sources = updated
                        pinError = null
                        // What a revision serves is the question a pin raises, so the picked source
                        // is read again and its coverage replaces the previous one.
                        updated.firstOrNull { it.id == target.id }?.let { checkSource(it) }
                    },
                )
                return@Column
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(Icons.Rounded.Link, contentDescription = null)
                Text(
                    stringResource(R.string.payload_sources_title),
                    style = MaterialTheme.typography.headlineSmall,
                )
            }
            Text(
                stringResource(R.string.payload_sources_summary, enabledCount, sources.size),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                ),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(
                        imageVector = if (LocalPayload.file(context) != null) {
                            Icons.Rounded.CheckCircle
                        } else {
                            Icons.Rounded.UploadFile
                        },
                        contentDescription = null,
                        tint = if (LocalPayload.file(context) != null) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.payload_local_source_title),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            LocalPayload.displayName(context)
                                ?: stringResource(R.string.payload_local_source_none),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Checkbox(
                        checked = localSelected && LocalPayload.file(context) != null,
                        enabled = LocalPayload.file(context) != null,
                        onCheckedChange = { enabled ->
                            clickHaptic(view)
                            localSelected = enabled
                            AppPreferences.setUseLocalPayload(context, enabled)
                        },
                    )
                }
            }

            // Above the list and collapsed by default: a long list of added sources can then
            // never push it out of reach.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        clickHaptic(view)
                        showAddSource = !showAddSource
                    }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    Icons.Rounded.Add,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    stringResource(R.string.payload_source_add),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    if (showAddSource) {
                        Icons.Rounded.ExpandLess
                    } else {
                        Icons.Rounded.ExpandMore
                    },
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
            }

            if (showAddSource) {
                OutlinedTextField(
                    value = repository,
                    onValueChange = {
                        repository = it
                        duplicate = false
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = duplicate,
                    label = { Text(stringResource(R.string.payload_repository_label)) },
                    placeholder = { Text(PayloadSource.DEFAULT_REPOSITORY) },
                )
                OutlinedTextField(
                    value = branch,
                    onValueChange = {
                        branch = it
                        duplicate = false
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = false,
                    label = { Text(stringResource(R.string.payload_branch)) },
                    placeholder = { Text(PayloadSource.DEFAULT_BRANCH) },
                    supportingText = { Text(stringResource(R.string.payload_branch_hint)) },
                )
                addError?.let {
                    Text(
                        it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                val candidateChecking = candidate != null && checking == candidate.id
                Button(
                    onClick = {
                        clickHaptic(view)
                        val source = candidate ?: return@Button
                        if (sources.any { it.id == source.id }) {
                            duplicate = true
                        } else {
                            // Added only once the source has been read, so the list never holds a
                            // repository nobody has confirmed serves a catalog.
                            checkSource(source) {
                                sources = sources.withSourceAdded(source)
                                repository = ""
                                branch = PayloadSource.DEFAULT_BRANCH
                                duplicate = false
                            }
                        }
                    },
                    enabled = candidate != null && !candidateChecking,
                ) {
                    if (candidateChecking) {
                        LoadingIndicator(modifier = Modifier.size(18.dp))
                    } else {
                        Icon(
                            Icons.Rounded.Add,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(
                            if (candidateChecking) R.string.payload_source_checking
                            else R.string.payload_source_add_action,
                        ),
                    )
                }
            }

            HorizontalDivider()

            if (sources.isEmpty()) {
                Text(
                    stringResource(R.string.payload_sources_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LazyColumn(
                    // The only part that scrolls, and it takes the space the form above leaves: the
                    // dialog's height depends on the screen rather than on what the list holds, so
                    // adding a source cannot move the fields out from under the cursor.
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(sources, key = { it.id }) { source ->
                        PayloadSourceRow(
                            source = source,
                            device = device,
                            coverage = checks[source.id]?.getOrNull(),
                            checkFailure = checks[source.id]?.exceptionOrNull()?.let {
                                it.message ?: it.javaClass.simpleName
                            },
                            checking = checking == source.id,
                            onCheck = { checkSource(source) },
                            onEnabledChange = { checked ->
                                clickHaptic(view)
                                sources = sources.withSourceEnabled(source.id, checked)
                            },
                            onPinChange = {
                                clickHaptic(view)
                                pinError = null
                                revisionTarget = source
                            },
                            onRemove = {
                                clickHaptic(view)
                                sources = sources.withSourceRemoved(source.id)
                            },
                        )
                    }
                }
            }

            pinError?.let { message ->
                Text(
                    stringResource(R.string.payload_source_pin_failed, message),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            if (PayloadSource.BUILT_IN_SOURCES.any { builtIn ->
                    sources.none {
                        it.repository.equals(builtIn.repository, ignoreCase = true) &&
                            it.branch.equals(builtIn.branch, ignoreCase = true)
                    }
                }
            ) {
                TextButton(onClick = {
                    clickHaptic(view)
                    PayloadSource.BUILT_IN_SOURCES.forEach { builtIn ->
                        if (sources.none {
                                it.repository.equals(builtIn.repository, ignoreCase = true) &&
                                    it.branch.equals(builtIn.branch, ignoreCase = true)
                            }
                        ) {
                            sources = sources.withSourceAdded(builtIn)
                        }
                    }
                }) {
                    Text(stringResource(R.string.payload_source_default))
                }
            }

            HorizontalDivider()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TextButton(
                    onClick = {
                        clickHaptic(view)
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.action_cancel))
                }
                Button(
                    onClick = {
                        clickHaptic(view)
                        if (sources == initialSources) {
                            // Merely opening the sheet and pressing Save is not a source-policy
                            // decision. Keep Auto mode intact unless the person actually changed a
                            // checkbox/source/pin.
                            onDismiss()
                        } else {
                            onSave(sources)
                        }
                    },
                    enabled = enabledCount > 0,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.action_save))
                }
            }
        }
    }
}

/**
 * Choosing the revision a source is pinned to.
 *
 * The lock used to pin whatever the branch pointed at the moment it was tapped, which left the
 * actual revision - the thing a pin is - out of the user's hands. This lists what there is to pin:
 * the ref itself (no pin), the repository's tags, and its most recent commits, each with the date
 * and the first line of its message. Naming a branch, tag, or commit by hand covers the revision
 * that is not in either list, and naming a tag resolves it to the commit it points at now, because
 * a tag can be moved onto another commit.
 */
@Composable
private fun RevisionPicker(
    source: PayloadSource,
    device: DeviceSnapshot,
    onBack: () -> Unit,
    onPick: (String?) -> Unit,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    var loading by remember(source.id) { mutableStateOf(true) }
    var revisions by remember(source.id) { mutableStateOf<List<SourceRevision>>(emptyList()) }
    var listFailure by remember(source.id) { mutableStateOf<String?>(null) }
    var manual by remember(source.id) { mutableStateOf("") }
    var applying by remember(source.id) { mutableStateOf(false) }
    var applyFailure by remember(source.id) { mutableStateOf<String?>(null) }
    // What is being considered, which is not yet what is pinned: the revision is chosen first, then
    // read, and only then stored. Tapping used to store the pin and describe it afterwards, which is
    // the wrong order for the one decision a catalog cannot take back.
    // Opening the picker already has a subject: what the source is on now, so the first thing shown is
    // what the pin currently means rather than an empty panel.
    var choice by remember(source.id) {
        mutableStateOf<RevisionChoice?>(
            if (source.isPinned) RevisionChoice.Commit(source.pinnedCommit) else RevisionChoice.Branch,
        )
    }
    var coverage by remember(source.id) { mutableStateOf<SourceCoverage?>(null) }
    var coverageFailure by remember(source.id) { mutableStateOf<String?>(null) }
    var reading by remember(source.id) { mutableStateOf(false) }

    LaunchedEffect(source.id) {
        loading = true
        // Cancellable, and here it is the effect's own key that changes: this is keyed on the source,
        // so leaving the picker cancels the read, and a cancellation reported as a read that failed
        // would put an error under a list that has nothing to do with it.
        runCatchingCancellable {
            withContext(Dispatchers.IO) { PayloadRepository(context).revisions(source) }
        }.onSuccess { listed ->
            revisions = listed
        }.onFailure { failure ->
            listFailure = failure.message ?: failure.javaClass.simpleName
        }
        loading = false
    }

    LaunchedEffect(source.id, choice) {
        val chosen = choice ?: return@LaunchedEffect
        reading = true
        coverage = null
        coverageFailure = null
        // Cancellable, and this one is keyed on the *choice* as well as the source: picking a different
        // revision cancels the read of the previous one, whose cancellation would otherwise arrive as
        // "this revision could not be read" under the revision now selected.
        runCatchingCancellable {
            withContext(Dispatchers.IO) {
                val repository = PayloadRepository(context)
                when (chosen) {
                    // Following the branch has an answer too, and it is the one a branch's coverage has
                    // to be read at: the branch's head, with no pin in the way.
                    RevisionChoice.Branch ->
                        repository.inspect(source.copy(pinnedCommit = ""), device)
                    is RevisionChoice.Commit -> repository.inspectAt(source, device, chosen.commit)
                }
            }
        }.onSuccess { read ->
            coverage = read
        }.onFailure { failure ->
            coverageFailure = failure.message ?: failure.javaClass.simpleName
        }
        reading = false
    }

    // A ref's head is the newest commit that is not a tag, which is the first one listed.
    val head = revisions.firstOrNull { it.tag == null }?.commit

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            IconButton(onClick = {
                clickHaptic(view)
                onBack()
            }) {
                Icon(
                    Icons.Rounded.ArrowBack,
                    contentDescription = stringResource(R.string.action_back),
                    modifier = Modifier.size(20.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.payload_pin_title, source.repository),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    if (source.isPinned) {
                        stringResource(R.string.payload_pin_pinned_at, source.pinnedCommit.take(7))
                    } else {
                        stringResource(R.string.payload_pin_follow_summary)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        RevisionRow(
            title = stringResource(R.string.payload_source_unpin),
            subtitle = source.branch,
            detail = null,
            selected = choice == RevisionChoice.Branch,
            icon = Icons.Rounded.LockOpen,
            pinned = !source.isPinned,
            onClick = { choice = RevisionChoice.Branch },
        )

        HorizontalDivider()

        OutlinedTextField(
            value = manual,
            onValueChange = {
                manual = it
                applyFailure = null
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text(stringResource(R.string.payload_pin_manual)) },
            supportingText = { Text(stringResource(R.string.payload_pin_manual_hint)) },
        )
        applyFailure?.let { reason ->
            Text(
                stringResource(R.string.payload_source_pin_failed, reason),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        OutlinedButton(
            onClick = {
                clickHaptic(view)
                val ref = manual.trim()
                if (ref.isEmpty() || applying) return@OutlinedButton
                scope.launch {
                    applying = true
                    applyFailure = null
                    runCatchingCancellable {
                        withContext(Dispatchers.IO) {
                            PayloadRepository(context).resolveNamedRevision(source.repository, ref)
                        }
                    }.onSuccess { commit -> choice = RevisionChoice.Commit(commit) }
                        .onFailure { failure ->
                            applyFailure = failure.message ?: failure.javaClass.simpleName
                        }
                    applying = false
                }
            },
            enabled = manual.isNotBlank() && !applying,
        ) {
            if (applying) {
                LoadingIndicator(modifier = Modifier.size(18.dp))
            } else {
                Icon(Icons.Rounded.Search, contentDescription = null, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.payload_pin_resolve))
        }

        // What the chosen revision serves, stated before it is what the source is pinned to. The
        // lists are the whole catalog's, because a pin is a decision about the catalog and not only
        // about this phone, and the last line of the block answers the phone's half of it.
        choice?.let { chosen ->
            Text(
                if (chosen is RevisionChoice.Commit) {
                    stringResource(R.string.payload_pin_serves_at, chosen.commit.take(7))
                } else {
                    stringResource(R.string.payload_pin_serves_branch, source.branch)
                },
                style = MaterialTheme.typography.titleSmall,
            )
            val read = coverage
            val failure = coverageFailure
            when {
                reading -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    LoadingIndicator(modifier = Modifier.size(18.dp))
                    Text(
                        stringResource(R.string.payload_pin_reading),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                read != null -> SourceCoverageBlock(read, device, inset = 0.dp)
                failure != null -> Text(
                    stringResource(R.string.payload_pin_read_failed, failure),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }

        Button(
            onClick = {
                clickHaptic(view)
                onPick((choice as? RevisionChoice.Commit)?.commit)
            },
            // Deliberately not gated on the read having succeeded: a pin is a decision about a
            // revision, and a network refusal while summarising it is not a reason to leave the user
            // unable to pin or to stop following a branch at all.
            enabled = choice != null && !reading && !applying,
        ) {
            Icon(
                if (choice is RevisionChoice.Commit) Icons.Rounded.Lock else Icons.Rounded.LockOpen,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                if (choice is RevisionChoice.Commit) {
                    stringResource(R.string.payload_pin_apply)
                } else {
                    stringResource(R.string.payload_pin_follow_action)
                },
            )
        }

        // The revision list sits below the decision, not above it. It is the longest thing on this
        // screen and the only one that is a browse rather than a choice, so putting it first pushed
        // the branch field and the action past the fold on a phone - the two controls a user who
        // already knows the ref they want came here for.
        if (loading) {
            LoadingIndicator(modifier = Modifier.size(24.dp))
        } else {
            listFailure?.let { reason ->
                Text(
                    stringResource(R.string.payload_pin_list_failed, reason),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            if (revisions.isNotEmpty()) {
                Text(
                    stringResource(R.string.payload_pin_recent),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 300.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(revisions, key = { "${it.tag ?: ""}:${it.commit}" }) { revision ->
                        RevisionRow(
                            title = revision.tag ?: revision.label.ifBlank { revision.commit.take(7) },
                            // A tag names the row, and the commit under it is what a pin stores, so
                            // pinning by tag is still visibly a decision about a commit.
                            subtitle = revision.commit.take(7),
                            detail = revision.date.ifBlank { null },
                            selected = choice == RevisionChoice.Commit(revision.commit),
                            current = revision.commit == head,
                            pinned = source.pinnedCommit == revision.commit,
                            icon = if (revision.tag == null) Icons.Rounded.Lock else Icons.Rounded.Link,
                            onClick = { choice = RevisionChoice.Commit(revision.commit) },
                        )
                    }
                }
            }
        }
    }
}

/** Which revision is being considered: the branch as it stands, or one commit of it. */
private sealed interface RevisionChoice {
    data object Branch : RevisionChoice

    data class Commit(val commit: String) : RevisionChoice
}

/** One revision as a selectable line: what it is, the commit, and when. */
@Composable
private fun RevisionRow(
    title: String,
    subtitle: String,
    detail: String?,
    selected: Boolean,
    icon: ImageVector,
    current: Boolean = false,
    pinned: Boolean = false,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            if (selected) Icons.Rounded.CheckCircle else icon,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        detail?.let {
            Text(
                it,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // Exactly one label, because the two states are alternatives rather than degrees: `current`
        // is where the branch points now, `pinned` is what the source is frozen at.
        if (!selected) {
            val label = when {
                current -> R.string.payload_pin_current
                pinned -> R.string.payload_pin_pinned
                else -> null
            }
            label?.let {
                Text(
                    stringResource(it),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun PayloadSourceRow(
    source: PayloadSource,
    device: DeviceSnapshot,
    coverage: SourceCoverage?,
    checkFailure: String?,
    checking: Boolean,
    onCheck: () -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    onPinChange: () -> Unit,
    onRemove: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Checkbox(checked = source.enabled, onCheckedChange = onEnabledChange)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    if (source.isLocal) source.label else source.repository,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    source.refLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (source.isPinned) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (checking) {
                LoadingIndicator(modifier = Modifier.size(20.dp))
            } else {
                IconButton(onClick = onCheck) {
                    Icon(
                        Icons.Rounded.CheckCircle,
                        contentDescription = stringResource(R.string.payload_source_check),
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            if (!source.isLocal) {
                IconButton(onClick = onPinChange) {
                    Icon(
                        if (source.isPinned) Icons.Rounded.Lock else Icons.Rounded.LockOpen,
                        contentDescription = stringResource(
                            if (source.isPinned) {
                                R.string.payload_source_unpin
                            } else {
                                R.string.payload_source_pin
                            },
                        ),
                        modifier = Modifier.size(20.dp),
                    )
                }
                IconButton(onClick = onRemove) {
                    Icon(
                        Icons.Rounded.Delete,
                        contentDescription = stringResource(R.string.payload_source_remove),
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
        checkFailure?.let { message ->
            Text(
                stringResource(R.string.payload_source_check_failed, message),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(start = SOURCE_ROW_INSET, top = 2.dp),
            )
        }
        coverage?.let { SourceCoverageBlock(it, device) }
    }
}

/** Lines up a row's detail with the text column, past the checkbox and the row spacing. */
private val SOURCE_ROW_INSET = 52.dp

/**
 * What a checked source covers, as the sheet reports it.
 *
 * The models and kernel versions are the whole catalog's, not this device's, because the point of
 * reading a source before saving it is to see what it is for. The last line answers the other half
 * of the question, which the lists alone cannot: whether any of it fits this phone.
 */
@Composable
private fun SourceCoverageBlock(
    coverage: SourceCoverage,
    device: DeviceSnapshot,
    // The sheet's rows align their detail past a checkbox; the picker's panel has none to clear.
    inset: Dp = SOURCE_ROW_INSET,
) {
    val models = coverage.models.joinToString()
    val kernels = coverage.kernelVersions.joinToString()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = inset, top = 4.dp, end = 4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            stringResource(R.string.payload_source_verified, coverage.commit.take(7)),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            stringResource(R.string.payload_source_coverage_payloads, coverage.payloadCount),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (models.isNotEmpty()) {
            Text(
                stringResource(R.string.payload_source_coverage_models, models),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (kernels.isNotEmpty()) {
            Text(
                stringResource(R.string.payload_source_coverage_kernels, kernels),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
        }
        val deviceProfileId = coverage.deviceProfileId
        if (deviceProfileId != null) {
            // A catalog usually has one payload per device, but a regional sibling makes two; the
            // count is how a user learns the other one is there without opening the picker.
            Text(
                if (coverage.deviceProfileCount > 1) {
                    stringResource(
                        R.string.payload_source_device_match_more,
                        deviceProfileId,
                        coverage.deviceProfileCount - 1,
                    )
                } else {
                    stringResource(R.string.payload_source_device_match, deviceProfileId)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        } else {
            Text(
                stringResource(R.string.payload_source_device_none, device.model),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

/**
 * Draws one section's heading, pinned at the top of the page while its own rows scroll under it.
 *
 * A section with room for more than a screen is the case this exists for: once its heading has scrolled away,
 * nothing on the screen says whose cards these are - and on this page every section is made of the same kind
 * of card, so the answer cannot be read off the rows themselves. An **open** section's heading is therefore a
 * sticky header: it stays put while its own cards pass beneath it, and the next section's heading pushes it
 * away as that one arrives.
 *
 * A **closed** section's heading is an ordinary row of the index card, and deliberately so. A sticky header
 * pins whatever is at the top of the list, and the index card's rows have no content under them at all - so a
 * closed heading would be held over the rows of the section *below* it, claiming authorship of somebody
 * else's content, which is the opposite of what the pin is for.
 *
 * [onPinnedHeight] reports this heading's height as it is laid out. Who needs it is the jump: a card scrolled
 * to the top of the viewport arrives *behind* a heading that is pinned there, so the height is what says how
 * much room the jump has to leave above it.
 */
@OptIn(ExperimentalFoundationApi::class)
private fun LazyListScope.settingsSectionHeading(
    section: SettingsSection,
    openSections: Set<SettingsSection>,
    indexRows: Map<SettingsSection, SettingsIndexRow>,
    onToggle: (SettingsSection) -> Unit,
    onPinnedHeight: (Int) -> Unit,
    navigationMode: Boolean = false,
    visible: Boolean = true,
) {
    if (!visible) return
    if (section in openSections && !navigationMode) {
        stickyHeader {
            // The pinned heading has to be opaque *around* itself and not only on itself. It is drawn over the
            // rows of its own section, which are its own width, so without this they show through its rounded
            // top corners - the one part of a card that cannot cover what is behind it.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .onGloballyPositioned { coordinates -> onPinnedHeight(coordinates.size.height) },
            ) {
                SettingsSectionHeader(
                    section,
                    openSections,
                    indexRows,
                    onToggle,
                    navigationMode = navigationMode,
                )
            }
        }
    } else {
        item {
            SettingsSectionHeader(
                section,
                openSections,
                indexRows,
                onToggle,
                navigationMode = navigationMode,
            )
        }
    }
}

/**
 * One row of the settings index: the section's glyph, its name, and the way in.
 *
 * A card rather than a bare line of text, because this is the page's table of contents and it is read as a
 * list: headings of collapsed sections sit flush against each other as one card - [SettingsIndexRow] cuts
 * their corners for it - and each heading is the same material as the cards it opens, at the same height, so
 * a row that opens a section and a row that sets something are recognisably the same kind of thing.
 *
 * A heading whose section is **open** is a card of its own instead, with its content directly under it: full
 * width, standing apart at a group's gap. Nesting the content inside the heading's card is what would show a
 * step - a full-width bar over narrower cards over a full-width bar - and unlike the inset it replaced, there
 * is nothing here that has to be read as a deliberate indent to be tolerated.
 *
 * The row says what the section is called and nothing else. Its state was here for a while - the accent, the
 * payload mode, the manager's flavour - and it was the wrong job for the row: half the sections have no single
 * word for what they hold, so the index read as though some sections were in a state and others were not,
 * while the cards below state themselves properly and in full.
 */
@Composable
private fun SettingsSectionHeader(
    section: SettingsSection,
    openSections: Set<SettingsSection>,
    indexRows: Map<SettingsSection, SettingsIndexRow>,
    onToggle: (SettingsSection) -> Unit,
    navigationMode: Boolean = false,
) {
    val view = LocalView.current
    val open = section in openSections
    val row = if (navigationMode) {
        SettingsIndexRow(SettingsCardPosition.GroupedSingle, endsBlock = true)
    } else {
        indexRows.getValue(section)
    }
    val interactionSource = remember { MutableInteractionSource() }
    Card(
        onClick = {
            clickHaptic(view, sound = null)
            onToggle(section)
        },
        modifier = Modifier
            .fillMaxWidth()
            // The gap belongs to the row above the card, and the seam to the rows inside one - which is why
            // this is a bottom padding and not a top one: a heading that is stuck to the top of the list has
            // nothing above it to have paid, and a gap it paid for itself would be page above the pinned
            // card every time it pinned.
            .padding(
                bottom = if (navigationMode) 10.dp
                else if (row.endsBlock) SETTINGS_BLOCK_GAP
                else 0.dp,
            ),
        shape = expressiveClickableCardShape(interactionSource, row.position),
        interactionSource = interactionSource,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                // Dense on purpose: eight of these are the page's first screen, and a row that grows taller
                // to breathe turns the index into something that has to be scrolled before anything is
                // found in it.
                .heightIn(min = 56.dp)
                .padding(start = 16.dp, end = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Icon(
                imageVector = section.icon,
                // Decorative: the row's own text names the section, and the glyph is only how it is found
                // faster. Six of these are also a card's icon further down the page.
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp),
            )
            Text(
                text = stringResource(section.title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = if (navigationMode) {
                    Icons.Rounded.ChevronRight
                } else if (open) {
                    Icons.Rounded.ExpandLess
                } else {
                    Icons.Rounded.ExpandMore
                },
                contentDescription = stringResource(
                    if (navigationMode) {
                        R.string.settings_section_open
                    } else if (open) {
                        R.string.settings_section_collapse
                    } else {
                        R.string.settings_section_expand
                    },
                ),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/**
 * The cards of one section that is open, drawn as the group they are.
 *
 * Full width and directly under the heading that opened it, which is what makes it that heading's content:
 * the pair read as one accordion - a heading card, then the cards - where an inset would say the same thing
 * with a step in the card's silhouette.
 *
 * The gap above is deliberately **smaller** than the one below. Both are between rounded cards and nothing
 * else distinguishes them, so at equal gaps the page would read as a column of unrelated cards: the near pair
 * is the heading and what it opened, and the far pair is the start of the next section.
 *
 * Both gaps are paid here rather than by the rows they separate, because this item scrolls away with its own
 * content: the gap below it is gone by the time the heading after it pins, which is what lets that heading sit
 * flush against the top of the screen.
 */
@Composable
private fun SettingsSectionBody(content: @Composable ColumnScope.() -> Unit) {
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }
    AnimatedVisibility(
        visible = entered,
        enter = fadeIn(tween(180)) + expandVertically(
            animationSpec = tween(240),
            expandFrom = Alignment.Top,
        ),
        exit = fadeOut(tween(120)),
    ) {
        Column(
            modifier = Modifier.padding(top = SETTINGS_BODY_GAP, bottom = SETTINGS_BLOCK_GAP),
            verticalArrangement = Arrangement.spacedBy(SETTINGS_CARD_SEAM),
            content = content,
        )
    }
}

/**
 * Where a card sits in the list it is drawn in.
 *
 * The corners are the whole difference between one group and several: a card that names the wrong
 * one starts a new container on screen or leaves a group of one. Internal, because the Recovery
 * actions are a group too and belong to the same list as everything else on the screen.
 */
internal enum class SettingsCardPosition {
    Single,
    GroupedSingle,
    Top,
    Middle,
    Bottom,
}

/**
 * The corner radius a card of this position rests at, given which end of it is being asked about.
 *
 * One rule, read by the card's own shape and by anything drawn *on* a card. The outline a settings jump
 * draws is the reason it is not inlined into the shape: an outline that guessed the radius would sit
 * off the card's own curve wherever it guessed wrong, and the row would look like it had grown a second
 * border rather than like it was being pointed at.
 */
internal fun settingsCardRestingRadius(position: SettingsCardPosition, top: Boolean): Dp = when {
    position == SettingsCardPosition.Single -> 16.dp
    position in setOf(SettingsCardPosition.GroupedSingle, SettingsCardPosition.Top) && top -> 24.dp
    position in setOf(SettingsCardPosition.GroupedSingle, SettingsCardPosition.Bottom) && !top -> 24.dp
    else -> 6.dp
}

/** The shape a card rests at, for something that has to be drawn around one. */
internal fun settingsCardRestingShape(position: SettingsCardPosition): RoundedCornerShape =
    RoundedCornerShape(
        topStart = settingsCardRestingRadius(position, top = true),
        topEnd = settingsCardRestingRadius(position, top = true),
        bottomStart = settingsCardRestingRadius(position, top = false),
        bottomEnd = settingsCardRestingRadius(position, top = false),
    )

/**
 * How much of a card a trailing value may take.
 *
 * Every band here is short - a state, a count, a mode - so a value that wants more than this is
 * either a sentence that belongs below the description or one that needs shortening. The cap exists
 * because the value is measured before the text column beside it, so an uncapped one silently
 * claimed the row.
 */
private val SETTINGS_VALUE_MAX_WIDTH = 140.dp

/**
 * Where a card's own header starts its text, which is where any row below it has to start too.
 *
 * A card's title and description begin past its icon - 28 dp of icon and the 12 dp gap beside it - so a
 * row that began at the card's own padding instead would sit further left than everything above it, and
 * read as belonging to the card's edge rather than to the text it continues. Measured from the two
 * numbers the header is built from, so changing either one carries here as well.
 */
private val SETTINGS_CARD_TEXT_INDENT = 40.dp

/** How thick the outline a settings jump draws is. Thin enough to read as a pointer, not a control. */
private val SETTINGS_HIGHLIGHT_WIDTH = 2.dp

/**
 * The seam between two rows of one card.
 *
 * Two places read it: the page's own arrangement, because consecutive headings are one card of flush rows,
 * and the body of an open section, whose cards are one group. It is the same number the app's groups have
 * always been built with - what changed is who pays for the gaps around them.
 */
private val SETTINGS_CARD_SEAM = 2.dp

/**
 * The gap between two cards, which is what a card's first row is padded by.
 *
 * Together with the seam this is the 14dp the app has always left between one group and the next; the page's
 * arrangement is the seam, so a gap is paid by whichever row starts a card.
 */
private val SETTINGS_BLOCK_GAP = 12.dp

/**
 * The space above the page's title, which is the settings page's whole top margin.
 *
 * It lives on the title rather than in the list's content padding because of the sticky heading: a sticky
 * heading is placed at the top of the list's *padded* area, so a top content padding is a band the rows below
 * the pinned card scroll through on their way past it - content appearing above a heading that is supposed to
 * be at the top. On the title, the same space scrolls away with the title and a pinned heading meets the top
 * of the screen with nothing above it but the page.
 */
private val SETTINGS_TOP_SPACE = 40.dp

/**
 * The gap between a heading and the content it opened, which is shorter than [SETTINGS_BLOCK_GAP] on purpose.
 *
 * Both separate two cards and neither can say anything else by itself, so the difference in distance is the
 * whole of what pairs a heading with its own rows.
 */
private val SETTINGS_BODY_GAP = 6.dp

/**
 * How much of a failed Shizuku start is worth showing.
 *
 * The tail, because the lines that matter are the last route's: an attempt tries the routes in order and
 * says what each one found, so the end of the list is the reason the whole thing stopped. Bounded on
 * both axes for the same reason the run log is - a dialog is not a log viewer.
 */
private const val SHIZUKU_START_LOG_LINES = 14
private val SHIZUKU_START_LOG_MAX_HEIGHT = 220.dp

/** An action offered beside a card's notice: what it says it does, and what it does. */
internal data class NoticeAction(val label: String, val onClick: () -> Unit)

/**
 * The flag for a payload's KernelSU standing beside the boot's, or null when they agree or nothing is
 * known.
 *
 * One wording for both places it appears, so the row and the dialog cannot describe the same pair of
 * numbers differently - and so the two states keep their different sentences rather than collapsing
 * into a generic warning: a payload ahead of the boot lands on the next run, while one behind it is the
 * app offering a release this phone has already passed.
 */
@Composable
private fun payloadDriftNotice(reading: PayloadKernelReading): String? = when (reading.state) {
    PayloadKernelState.Ahead -> stringResource(
        R.string.settings_manager_version_drift_ahead,
        reading.running.orEmpty(),
        reading.declared.orEmpty(),
    )

    PayloadKernelState.Behind -> stringResource(
        R.string.settings_manager_version_drift_behind,
        reading.running.orEmpty(),
        reading.declared.orEmpty(),
    )

    else -> null
}

/**
 * One row of the settings list: an icon, a title, a description, and an optional trailing value.
 *
 * [busy] is for a row that starts something the app has to wait on. It keeps the row in place and
 * stops it being tapped again while the work runs, instead of the row changing shape or a second
 * tap starting the same thing twice.
 */
@Composable
internal fun SettingsCard(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    title: String,
    description: String,
    value: String = "",
    /**
     * One short line of state that is not a setting: why this card is not doing what it says.
     *
     * Kept to a single line on purpose. It used to be a sentence written like body copy - the same
     * size and colour as the description above it - which made a row that carried one into a paragraph
     * six lines tall, and made state read as more description. A small icon and a line beside it is
     * what tells the two apart at a glance, and what keeps this row the height of its neighbours.
     * Centred, because it belongs to the whole row rather than to the text column it would otherwise
     * hang off, aligned under an icon it has nothing to do with.
     */
    notice: String? = null,
    noticeIcon: ImageVector = Icons.Rounded.RestartAlt,
    /**
     * The fix for what [notice] just said, offered where the notice is.
     *
     * A warning that only says what is wrong makes the reader go and find the control that fixes it,
     * and on this screen that control may be a sheet, a field and a remembered number away. This puts
     * the one action that resolves the state directly under the line naming it, and it is deliberately
     * a *separate* control from the row: the row's own tap does what the row is for, and a warning that
     * hijacked it would make the card do something different depending on a state nobody can see.
     */
    noticeAction: NoticeAction? = null,
    position: SettingsCardPosition = SettingsCardPosition.Single,
    busy: Boolean = false,
    /**
     * False when the row's action cannot do anything right now, so the card dims and takes no tap.
     *
     * It is for actions that are *already done* rather than merely likely to fail: a row whose only
     * remaining outcome is a dialog saying "this was already the case" reads as a button that does
     * not work, where a dimmed row reads as a state.
     */
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val view = LocalView.current
    Card(
        enabled = enabled && !busy,
        onClick = {
            clickHaptic(view)
            onClick()
        },
        modifier = modifier.fillMaxWidth(),
        shape = expressiveClickableCardShape(interactionSource, position),
        interactionSource = interactionSource,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        ),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 15.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(28.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    // Wraps like SettingsSwitchCard rather than ellipsising: a description that
                    // needs a second line is still worth reading.
                    Text(
                        description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (value.isNotBlank()) {
                    Text(
                        value,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        // Capped, because an unweighted Row child is measured before the weighted one
                        // beside it: a long value took the whole row and left the title and the
                        // description one character per line. Two lines and end alignment keeps the
                        // text column readable without cutting the value off at one line.
                        modifier = Modifier.widthIn(max = SETTINGS_VALUE_MAX_WIDTH),
                        textAlign = TextAlign.End,
                        maxLines = 2,
                    )
                }
            }
            if (notice != null) {
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        noticeIcon,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        notice,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                // Under the notice and centred with it, because it answers that line rather than the
                // row: it takes its own tap without the card's, so the two do not both fire.
                noticeAction?.let { action ->
                    TextButton(
                        onClick = {
                            clickHaptic(view)
                            action.onClick()
                        },
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    ) {
                        Text(action.label)
                    }
                }
            }
            if (busy) {
                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp),
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun SettingsSwitchCard(
    icon: ImageVector,
    title: String,
    description: String,
    checked: Boolean,
    position: SettingsCardPosition = SettingsCardPosition.Single,
    /**
     * False when the setting cannot take effect right now, so the switch dims and refuses to move.
     *
     * The stored value is left alone: a setting that is inert because something it depends on is off
     * comes back as it was when that is turned back on, and silently clearing it would lose a choice
     * the user made deliberately.
     */
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val view = LocalView.current
    Card(
        enabled = enabled,
        onClick = {
            clickHaptic(view)
            onCheckedChange(!checked)
        },
        modifier = Modifier.fillMaxWidth(),
        shape = expressiveClickableCardShape(interactionSource, position),
        interactionSource = interactionSource,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 15.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(28.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // `onCheckedChange = null` already, so the switch is a reading of the card rather than a
            // second control; a disabled card simply stops the whole row taking a tap.
            Switch(checked = checked, enabled = enabled, onCheckedChange = null)
        }
    }
}

/**
 * One line of a [SettingsReadingsCard]: what was read, what it said, and any mark it carries.
 *
 * [mark] is a word rather than a colour, and it is deliberately not the whole message: a mark that was
 * only a colour would say nothing to a reader who cannot see the difference, and a mark that was a
 * sentence would be the notice this card exists to replace.
 */
/**
 * The card that gives a kernel the signing key of the manager it has not been built to accept.
 *
 * Shown only for a flavour whose module carries the runtime path, because the row's whole meaning is
 * that a key can be handed to this kernel afterwards - and on a kernel where that is not true, the
 * most useful thing the row could do is not exist.
 *
 * Three things are on it and each answers a different question: what the kernel holds now (the value
 * band, and empty when it holds nothing), whether there is anything to register (the notice, when no
 * manager is installed), and the tap, which re-reads the installed manager's own key, gives it to the
 * kernel and then asks the kernel what it holds. The last step is the one that makes the tap honest:
 * the write's exit code says an ioctl finished, not that the setting is the one intended.
 */
@Composable
private fun DynamicManagerCard(
    installedManager: InstalledManager?,
    kernelsuFlavor: KernelSuFlavor,
    context: Context,
    view: View,
    scope: CoroutineScope,
    /** Bumped by every return to the foreground, so the reading follows a kernel that was changed. */
    resumeTick: Int,
    /**
     * Where the card sits in the group, which belongs to the caller rather than to this.
     *
     * A helper in this file decides nothing about its own place in a list: the group it is drawn in is
     * the caller's, and a position written here would be a second card claiming a shape outside the
     * group that owns it.
     */
    position: SettingsCardPosition,
) {
    var reading by remember { mutableStateOf<DynamicManagerReading?>(null) }
    var registering by remember { mutableStateOf(false) }
    // Bumped by a finished attempt, so the reading is made again rather than kept: the row's whole
    // job is to say what the kernel holds, and a registration changes exactly that.
    var reads by remember { mutableStateOf(0) }
    // The resume is in the key for the same reason: a run, or `ksud` itself, can change what the
    // kernel holds while this screen is in the background.
    LaunchedEffect(kernelsuFlavor, reads, resumeTick) {
        reading = withContext(Dispatchers.IO) { DynamicManager.read(context) }
    }
    val held = reading
    SettingsCard(
        icon = Icons.Rounded.VerifiedUser,
        title = stringResource(R.string.settings_manager_registration),
        description = stringResource(R.string.settings_manager_registration_summary),
        // The key in the band, and nothing when the kernel holds none: an empty band is the state,
        // and filling it with the key the app would register would put a claim on the row that the
        // kernel has not made.
        value = held?.signature?.shortLabel().orEmpty(),
        notice = when {
            installedManager == null ->
                stringResource(R.string.settings_manager_registration_no_manager)
            held == null -> null
            held.state == DynamicManagerState.Unreadable ->
                stringResource(R.string.settings_manager_registration_unreadable)
            held.state == DynamicManagerState.Unset ->
                stringResource(R.string.settings_manager_registration_unset)
            else -> null
        },
        noticeIcon = Icons.Rounded.Key,
        position = position,
        busy = registering,
        // Dimmed rather than removed when there is no manager: the row still has to say that the
        // kernel holds no key, and a card that vanished would take that reading with it.
        enabled = installedManager != null,
        onClick = {
            clickHaptic(view)
            registering = true
            scope.launch {
                val report = withContext(Dispatchers.IO) {
                    DynamicManager.register(context, kernelsuFlavor)
                }
                registering = false
                reads++
                Toast.makeText(
                    context,
                    managerRegistrationMessage(context, report),
                    Toast.LENGTH_LONG,
                ).show()
            }
        },
    )
}

/**
 * What a finished attempt says, in the outcome's own words.
 *
 * A refusal carries the daemon's line rather than a sentence written here, because the daemon is the
 * thing that knows: it distinguishes an APK with no v2 signature from a kernel that would not take
 * the command, and a message of this app's would collapse those into one.
 */
private fun managerRegistrationMessage(context: Context, report: RegistrationReport): String =
    when (report.outcome) {
        RegistrationOutcome.Registered -> context.getString(
            R.string.settings_manager_registration_done,
            report.signature?.shortLabel().orEmpty(),
        )

        RegistrationOutcome.AlreadyRegistered -> context.getString(
            R.string.settings_manager_registration_already,
            report.signature?.shortLabel().orEmpty(),
        )

        RegistrationOutcome.NoManager ->
            context.getString(R.string.settings_manager_registration_no_manager)

        RegistrationOutcome.NoShell ->
            context.getString(R.string.settings_manager_registration_no_shell)

        RegistrationOutcome.Refused -> context.getString(
            R.string.settings_manager_registration_refused,
            report.detail.ifBlank { context.getString(R.string.settings_manager_registration_unreadable) },
        )

        RegistrationOutcome.NotHeld ->
            context.getString(R.string.settings_manager_registration_not_held)
    }

internal data class SettingsReading(
    val label: String,
    val value: String,
    val mark: String? = null,
)

/**
 * A card that reports readings instead of offering a setting: what it says it read, and one line each.
 *
 * Non-clickable, and that is the whole reason it is not a [SettingsCard]: a row that takes a tap is a
 * control, and a card whose content is three answers from the device has nothing to do when it is
 * tapped. Giving it a tap anyway would teach the reader that these rows are buttons, which is exactly
 * the lesson the fix button below them has to unteach.
 *
 * Values are laid out one per line with their labels, rather than as the trailing band a [SettingsCard]
 * puts its single value in: two versions in one band read as one sentence about one thing, and the
 * point of this card is that they are two answers that may or may not agree.
 */
@Composable
private fun SettingsReadingsCard(
    icon: ImageVector,
    title: String,
    description: String,
    readings: List<SettingsReading>,
    position: SettingsCardPosition = SettingsCardPosition.Single,
    /**
     * The fix for what the readings just said, offered under them.
     *
     * Unlike a [SettingsCard]'s, it does not wait for a notice: the readings above *are* the notice here,
     * and a card that restated them in a sentence before offering the button would be the same facts
     * twice in one card.
     */
    action: NoticeAction? = null,
) {
    val view = LocalView.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = settingsCardRestingShape(position),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        ),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 15.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(28.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    Text(
                        description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            readings.forEach { reading ->
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = SETTINGS_CARD_TEXT_INDENT),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        reading.label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        reading.value,
                        style = MaterialTheme.typography.labelLarge,
                        // A marked value is the one the action below replaces, so it is the one that
                        // takes the warning colour; an unmarked one stays in the band every other
                        // value on this screen sits in.
                        color = if (reading.mark != null) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                        textAlign = TextAlign.End,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    reading.mark?.let { mark ->
                        Text(
                            mark,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
            action?.let { offered ->
                Spacer(Modifier.height(4.dp))
                TextButton(
                    onClick = {
                        clickHaptic(view)
                        offered.onClick()
                    },
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                ) {
                    Text(offered.label)
                }
            }
        }
    }
}

private data class VsprAppInfo(
    val packageName: String,
    val label: String,
    val isSystem: Boolean,
)

private fun vsprCardPosition(index: Int, size: Int): SettingsCardPosition = when {
    size <= 1 -> SettingsCardPosition.GroupedSingle
    index == 0 -> SettingsCardPosition.Top
    index == size - 1 -> SettingsCardPosition.Bottom
    else -> SettingsCardPosition.Middle
}

@Composable
private fun vsprPrivilegeLabel(privilege: VsprPrivilege): String = when (privilege) {
    VsprPrivilege.SuperSu -> stringResource(R.string.vspr_supersu)
    VsprPrivilege.Adb -> stringResource(R.string.vspr_adb)
    VsprPrivilege.Shizuku -> stringResource(R.string.vspr_shizuku)
    VsprPrivilege.AdVeyra -> stringResource(R.string.vspr_adveyra)
    VsprPrivilege.CVeyra -> stringResource(R.string.vspr_cveyra)
    VsprPrivilege.System -> stringResource(R.string.vspr_system)
}

private fun vsprPrivilegeColor(privilege: VsprPrivilege): Color = when (privilege) {
    VsprPrivilege.SuperSu -> Color(0xFFA855F7)
    VsprPrivilege.Adb -> Color(0xFF2EAD66)
    VsprPrivilege.Shizuku -> Color(0xFF3B82F6)
    VsprPrivilege.AdVeyra -> Color(0xFFE45454)
    VsprPrivilege.CVeyra -> Color(0xFFEC4899)
    VsprPrivilege.System -> Color(0xFFF59E0B)
}
@Composable
private fun CVeyraAccessPage(
    padding: PaddingValues,
    openManagementInitially: Boolean = false,
    onAccessStateChanged: (Boolean) -> Unit = {},
    onOpenVeyraKsu: () -> Unit = {},
    onOpenRootMigration: () -> Unit = {},
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val listState = rememberLazyListState()

    var providerExpanded by rememberSaveable { mutableStateOf(true) }
    var showInfo by rememberSaveable { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    var refreshTick by remember { mutableStateOf(0) }
    var grantRevision by remember { mutableStateOf(0) }
    var apps by remember { mutableStateOf<List<VsprAppInfo>>(emptyList()) }
    var hiddenDirectRootCount by remember { mutableStateOf(0) }
    var permissionMode by remember {
        mutableStateOf(VsprStore.permissionMode(context))
    }
    var selfApps by remember {
        mutableStateOf(VsprStore.selfApps(context))
    }
    var showAddAppDialog by rememberSaveable { mutableStateOf(false) }
    var showShizukuModuleDialog by rememberSaveable { mutableStateOf(false) }
    var showAdVeyraOwnerDialog by rememberSaveable { mutableStateOf(false) }
    var showSystemApps by remember {
        mutableStateOf(VsprStore.showSystemApps(context))
    }
    var accessActive by remember {
        mutableStateOf(CVeyraAccessStore.isActive(context))
    }
    var showPermissionManager by rememberSaveable { mutableStateOf(false) }
    var showCVeyraManagement by rememberSaveable {
        mutableStateOf(openManagementInitially && CVeyraAccessStore.isActive(context))
    }

    var superSuProvider by remember {
        mutableStateOf(VsprStore.providerEnabled(context, VsprPrivilege.SuperSu))
    }
    var adbProvider by remember {
        mutableStateOf(VsprStore.providerEnabled(context, VsprPrivilege.Adb))
    }
    var shizukuProvider by remember {
        mutableStateOf(VsprStore.providerEnabled(context, VsprPrivilege.Shizuku))
    }
    var adVeyraProvider by remember {
        mutableStateOf(VsprStore.providerEnabled(context, VsprPrivilege.AdVeyra))
    }
    var cveyraProvider by remember {
        mutableStateOf(VsprStore.providerEnabled(context, VsprPrivilege.CVeyra))
    }
    var systemProvider by remember {
        mutableStateOf(VsprStore.providerEnabled(context, VsprPrivilege.System))
    }

    fun providerEnabled(privilege: VsprPrivilege): Boolean = when (privilege) {
        VsprPrivilege.SuperSu -> superSuProvider
        VsprPrivilege.Adb -> adbProvider
        VsprPrivilege.Shizuku -> shizukuProvider
        VsprPrivilege.AdVeyra -> adVeyraProvider
        VsprPrivilege.CVeyra -> cveyraProvider
        VsprPrivilege.System -> systemProvider
    }

    fun setProviderEnabled(privilege: VsprPrivilege, enabled: Boolean) {
        VsprStore.setProviderEnabled(context, privilege, enabled)
        when (privilege) {
            VsprPrivilege.SuperSu -> superSuProvider = enabled
            VsprPrivilege.Adb -> adbProvider = enabled
            VsprPrivilege.Shizuku -> shizukuProvider = enabled
            VsprPrivilege.AdVeyra -> adVeyraProvider = enabled
            VsprPrivilege.CVeyra -> cveyraProvider = enabled
            VsprPrivilege.System -> systemProvider = enabled
        }
    }

    if (showInfo) {
        VsprInfoPage(
            padding = padding,
            onBack = { showInfo = false },
            onRead = {
                CVeyraAccessStore.markInfoRead(context)
                showInfo = false
            },
        )
        return
    }

    if (!accessActive) {
        CVeyraAccessSetupPage(
            padding = padding,
            onBack = onBack,
            onOpenInfo = { showInfo = true },
            onActivated = {
                accessActive = true
                onAccessStateChanged(true)
                permissionMode = VsprStore.permissionMode(context)
                selfApps = VsprStore.selfApps(context)
                showCVeyraManagement = true
                refreshTick++
            },
        )
        return
    }

    if (showPermissionManager) {
        CVeyraPermissionManagerPage(
            padding = padding,
            onBack = {
                showPermissionManager = false
                permissionMode = VsprStore.permissionMode(context)
                selfApps = VsprStore.selfApps(context)
                refreshTick++
            },
        )
        return
    }

    if (showCVeyraManagement) {
        CVeyraManagementPage(
            padding = padding,
            onBack = { showCVeyraManagement = false },
            onOpenPermissionManager = {
                showCVeyraManagement = false
                showPermissionManager = true
            },
            onOpenVeyraKsu = onOpenVeyraKsu,
            onOpenMigration = onOpenRootMigration,
        )
        return
    }

    BackHandler(onBack = onBack)
    LaunchedEffect(refreshTick) {
        loading = true
        val result = withContext(Dispatchers.IO) {
            val pm = context.packageManager
            val installedApplications = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getInstalledApplications(0)
            }
            val installed = installedApplications.asSequence()
                .filter { appInfo -> appInfo.enabled && appInfo.packageName != context.packageName }
                .map { appInfo ->
                    VsprAppInfo(
                        packageName = appInfo.packageName,
                        label = pm.getApplicationLabel(appInfo).toString()
                            .ifBlank { appInfo.packageName },
                        isSystem = (
                            appInfo.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM
                        ) != 0 || (
                            appInfo.flags and android.content.pm.ApplicationInfo.FLAG_UPDATED_SYSTEM_APP
                        ) != 0,
                    )
                }
                .distinctBy { appInfo -> appInfo.packageName }
                .sortedWith(
                    compareBy<VsprAppInfo> { appInfo ->
                        appInfo.label.lowercase(Locale.getDefault())
                    }.thenBy { appInfo -> appInfo.packageName },
                )
                .toList()

            val directRoot = KernelSuDirectGrantProbe.grantedPackages(
                installed.map(VsprAppInfo::packageName),
            )
            directRoot.forEach { packageName ->
                VsprStore.clearPrivilege(context, packageName, VsprPrivilege.SuperSu)
            }
            installed.filterNot { appInfo -> appInfo.packageName in directRoot } to directRoot.size
        }
        apps = result.first
        hiddenDirectRootCount = result.second
        loading = false
    }

    val visibleApps = remember(
        apps,
        showSystemApps,
        grantRevision,
        permissionMode,
        selfApps,
    ) {
        val base = apps.filter { appInfo ->
            !appInfo.isSystem ||
                showSystemApps ||
                appInfo.packageName in selfApps ||
                VsprStore.grants(context, appInfo.packageName).isNotEmpty()
        }
        when (permissionMode) {
            VsprPermissionMode.Offline -> emptyList()
            VsprPermissionMode.Auto -> base
            VsprPermissionMode.Self -> base.filter { it.packageName in selfApps }
        }
    }
    val addableApps = remember(apps, showSystemApps, selfApps) {
        apps.filter { appInfo ->
            appInfo.packageName !in selfApps &&
                (!appInfo.isSystem || showSystemApps)
        }
    }

    if (showShizukuModuleDialog) {
        AlertDialog(
            onDismissRequest = { showShizukuModuleDialog = false },
            icon = { Icon(Icons.Rounded.Terminal, contentDescription = null) },
            title = { Text(stringResource(R.string.vspr_shizuku_module_title)) },
            text = { Text(stringResource(R.string.vspr_shizuku_module_body)) },
            confirmButton = {
                FilledTonalButton(onClick = {
                    clickHaptic(view)
                    val availability = ShizukuController.availability()
                    if (availability == ShizukuAvailability.Ready) {
                        setProviderEnabled(VsprPrivilege.Shizuku, true)
                        Toast.makeText(
                            context,
                            "Shizuku provider active",
                            Toast.LENGTH_SHORT,
                        ).show()
                        showShizukuModuleDialog = false
                    } else {
                        Toast.makeText(
                            context,
                            "Shizuku permission is not active yet.",
                            Toast.LENGTH_LONG,
                        ).show()
                    }
                }) {
                    Text("Activate")
                }
            },
            dismissButton = {
                TextButton(onClick = { showShizukuModuleDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    if (showAdVeyraOwnerDialog) {
        AlertDialog(
            onDismissRequest = { showAdVeyraOwnerDialog = false },
            icon = { Icon(Icons.Rounded.VerifiedUser, contentDescription = null) },
            title = { Text(stringResource(R.string.vspr_adveyra_owner_title)) },
            text = { Text(stringResource(R.string.vspr_adveyra_owner_body)) },
            confirmButton = {
                FilledTonalButton(onClick = {
                    clickHaptic(view)
                    val dpm = context.getSystemService(android.app.admin.DevicePolicyManager::class.java)
                    if (dpm?.isDeviceOwnerApp(context.packageName) == true) {
                        setProviderEnabled(VsprPrivilege.AdVeyra, true)
                        Toast.makeText(
                            context,
                            "ADVeyra Device Owner provider active",
                            Toast.LENGTH_SHORT,
                        ).show()
                        showAdVeyraOwnerDialog = false
                    } else {
                        Toast.makeText(
                            context,
                            "Veyra is not the Android Device Owner on this device.",
                            Toast.LENGTH_LONG,
                        ).show()
                    }
                }) {
                    Text("Activate")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAdVeyraOwnerDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    if (showAddAppDialog) {
        AlertDialog(
            onDismissRequest = { showAddAppDialog = false },
            icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
            title = { Text(stringResource(R.string.vspr_add_app_title)) },
            text = {
                if (addableApps.isEmpty()) {
                    Text(stringResource(R.string.vspr_add_app_empty))
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 460.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        items(addableApps, key = { it.packageName }) { appInfo ->
                            Card(
                                onClick = {
                                    clickHaptic(view)
                                    VsprStore.addSelfApp(context, appInfo.packageName)
                                    selfApps = VsprStore.selfApps(context)
                                    showAddAppDialog = false
                                },
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                ),
                            ) {
                                Column(Modifier.fillMaxWidth().padding(12.dp)) {
                                    Text(appInfo.label, style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        appInfo.packageName,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showAddAppDialog = false }) {
                    Text(stringResource(R.string.action_close))
                }
            },
        )
    }

    PageList(
        padding = padding,
        listState = listState,
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                IconButton(onClick = {
                    clickHaptic(view)
                    onBack()
                }) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null)
                }
                Text(
                    stringResource(R.string.vspr_title),
                    style = MaterialTheme.typography.headlineLarge,
                )
            }
        }

        item {
            val interactionSource = remember { MutableInteractionSource() }
            Card(
                onClick = {
                    clickHaptic(view)
                    providerExpanded = !providerExpanded
                },
                modifier = Modifier.fillMaxWidth(),
                shape = expressiveClickableCardShape(
                    interactionSource,
                    SettingsCardPosition.GroupedSingle,
                ),
                interactionSource = interactionSource,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                ),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 15.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(
                        Icons.Rounded.Security,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp),
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.vspr_provider_title),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            stringResource(R.string.vspr_provider_subtitle),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Icon(
                        if (providerExpanded) Icons.Rounded.ExpandLess
                        else Icons.Rounded.ExpandMore,
                        contentDescription = null,
                    )
                }
            }
        }
        if (providerExpanded) {
            val privileges = VsprPrivilege.entries
            items(privileges, key = { privilege -> "provider:" + privilege.storedValue }) { privilege ->
                VsprProviderSwitchCard(
                    privilege = privilege,
                    checked = providerEnabled(privilege),
                    position = vsprCardPosition(
                        privileges.indexOf(privilege),
                        privileges.size,
                    ),
                    onCheckedChange = { enabled ->
                        when {
                            enabled && privilege == VsprPrivilege.Shizuku -> {
                                showShizukuModuleDialog = true
                            }
                            enabled && privilege == VsprPrivilege.AdVeyra -> {
                                showAdVeyraOwnerDialog = true
                            }
                            else -> {
                                setProviderEnabled(privilege, enabled)
                            }
                        }
                    },
                )
            }
        }

        item {
            Card(
                onClick = {
                    clickHaptic(view)
                    showInfo = true
                },
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
                        Text(
                            stringResource(R.string.vspr_provider_info),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            stringResource(R.string.vspr_provider_info_summary),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = {
                        clickHaptic(view)
                        showInfo = true
                    }) {
                        Icon(Icons.Rounded.Info, contentDescription = null)
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth()
                    .padding(top = 8.dp, start = 4.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Rounded.Apps,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.vspr_apps_title),
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(
                        stringResource(R.string.vspr_apps_summary),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (permissionMode == VsprPermissionMode.Self) {
                    IconButton(onClick = {
                        clickHaptic(view)
                        showAddAppDialog = true
                    }) {
                        Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.vspr_add_app_title))
                    }
                }
                IconButton(onClick = {
                    clickHaptic(view)
                    refreshTick++
                }) {
                    Icon(Icons.Rounded.Refresh, contentDescription = null)
                }
            }
        }

        item {
            Card(
                onClick = {
                    clickHaptic(view)
                    showPermissionManager = true
                },
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
                        Icons.Rounded.Apps,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Open Permission Management",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            when (permissionMode) {
                                VsprPermissionMode.Offline ->
                                    "Offline · no apps are currently managed"
                                VsprPermissionMode.Self ->
                                    "Self · choose managed apps and keep detailed grants here"
                                VsprPermissionMode.Auto ->
                                    "Auto · Soon"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        item {
            Card(
                onClick = {
                    clickHaptic(view)
                    showCVeyraManagement = true
                },
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                ),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Image(
                        painter = painterResource(R.drawable.ic_veyra_mark),
                        contentDescription = null,
                        modifier = Modifier.size(28.dp),
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "CVeyra Management",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            "VeyraKSU 2.0.0 · modules · firewall · permission broker",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.78f),
                        )
                    }
                }
            }
        }

        item {
            SettingsSwitchCard(
                icon = Icons.Rounded.Apps,
                title = stringResource(R.string.vspr_show_system_apps),
                description = stringResource(R.string.vspr_show_system_apps_summary),
                checked = showSystemApps,
                position = SettingsCardPosition.GroupedSingle,
                onCheckedChange = { enabled ->
                    clickHaptic(view)
                    showSystemApps = enabled
                    VsprStore.setShowSystemApps(context, enabled)
                },
            )
        }

        if (hiddenDirectRootCount > 0) {
            item {
                Text(
                    stringResource(
                        R.string.vspr_direct_root_hidden,
                        hiddenDirectRootCount,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        }

        if (permissionMode == VsprPermissionMode.Offline) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().alpha(0.72f),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    ),
                ) {
                    Text(
                        stringResource(R.string.vspr_offline_apps_inactive),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(18.dp),
                    )
                }
            }
        } else if (loading) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp))
                    Text(
                        stringResource(R.string.vspr_loading),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        } else if (visibleApps.isEmpty()) {
            item {
                Text(
                    stringResource(
                        if (permissionMode == VsprPermissionMode.Self) {
                            R.string.vspr_self_no_apps
                        } else {
                            R.string.vspr_no_apps
                        },
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(18.dp),
                )
            }
        } else {
            items(visibleApps, key = { appInfo -> appInfo.packageName }) { appInfo ->
                VsprAppCard(
                    app = appInfo,
                    providerEnabled = ::providerEnabled,
                    interactive = permissionMode == VsprPermissionMode.Self,
                    onRemove = if (permissionMode == VsprPermissionMode.Self) {
                        {
                            VsprStore.removeSelfApp(context, appInfo.packageName)
                            selfApps = VsprStore.selfApps(context)
                            grantRevision++
                        }
                    } else {
                        null
                    },
                    onGrantChanged = { grantRevision++ },
                )
            }
        }
    }
}

@Composable
private fun VsprProviderSwitchCard(
    privilege: VsprPrivilege,
    checked: Boolean,
    position: SettingsCardPosition,
    onCheckedChange: (Boolean) -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val view = LocalView.current
    Card(
        onClick = {
            clickHaptic(view)
            onCheckedChange(!checked)
        },
        modifier = Modifier.fillMaxWidth(),
        shape = expressiveClickableCardShape(interactionSource, position),
        interactionSource = interactionSource,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier.size(14.dp)
                    .clip(CircleShape)
                    .background(vsprPrivilegeColor(privilege)),
            )
            Text(
                vsprPrivilegeLabel(privilege),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            Switch(
                checked = checked,
                onCheckedChange = null,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = vsprPrivilegeColor(privilege),
                    checkedBorderColor = vsprPrivilegeColor(privilege),
                ),
            )
        }
    }
}

@Composable
private fun VsprAppCard(
    app: VsprAppInfo,
    providerEnabled: (VsprPrivilege) -> Boolean,
    interactive: Boolean,
    onRemove: (() -> Unit)?,
    onGrantChanged: () -> Unit,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    var expanded by rememberSaveable(app.packageName) { mutableStateOf(false) }
    var applying by remember(app.packageName) { mutableStateOf<VsprPrivilege?>(null) }
    var grants by remember(app.packageName) {
        mutableStateOf(VsprStore.grants(context, app.packageName))
    }

    Card(
        onClick = {
            clickHaptic(view)
            if (interactive) expanded = !expanded
        },
        modifier = Modifier.fillMaxWidth().alpha(if (interactive) 1f else 0.52f),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        ),
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    Icons.Rounded.Apps,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(app.label, style = MaterialTheme.typography.titleMedium)
                    Text(
                        app.packageName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (onRemove != null) {
                    IconButton(onClick = {
                        clickHaptic(view)
                        onRemove()
                    }) {
                        Icon(Icons.Rounded.Delete, contentDescription = stringResource(R.string.history_delete))
                    }
                }
                Icon(
                    if (expanded && interactive) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = null,
                    tint = if (interactive) {
                        LocalContentColor.current
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                    },
                )
            }

            AnimatedVisibility(visible = expanded && interactive) {
                Column(
                    modifier = Modifier.fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                ) {
                    HorizontalDivider()
                    VsprPrivilege.entries.forEach { privilege ->
                        val providerOn = providerEnabled(privilege)
                        val granted = privilege in grants
                        VsprAppPrivilegeRow(
                            privilege = privilege,
                            checked = granted,
                            enabled = providerOn && applying == null,
                            onCheckedChange = { enabled ->
                                if (applying != null) return@VsprAppPrivilegeRow
                                applying = privilege
                                scope.launch {
                                    val result = withContext(Dispatchers.IO) {
                                        VsprPolicyEngine.apply(
                                            context,
                                            app.packageName,
                                            privilege,
                                            enabled,
                                        )
                                    }
                                    if (result.success) {
                                        VsprStore.setGrantRaw(
                                            context,
                                            app.packageName,
                                            privilege,
                                            enabled,
                                        )
                                        grants = VsprStore.grants(context, app.packageName)
                                        onGrantChanged()
                                    }
                                    Toast.makeText(
                                        context,
                                        result.detail,
                                        if (result.success) Toast.LENGTH_SHORT else Toast.LENGTH_LONG,
                                    ).show()
                                    applying = null
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun VsprAppPrivilegeRow(
    privilege: VsprPrivilege,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier.size(12.dp)
                .clip(CircleShape)
                .background(vsprPrivilegeColor(privilege)),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                vsprPrivilegeLabel(privilege),
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!enabled) {
                Text(
                    stringResource(R.string.vspr_disabled_by_provider),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Switch(
            checked = checked,
            enabled = enabled,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = vsprPrivilegeColor(privilege),
                checkedBorderColor = vsprPrivilegeColor(privilege),
            ),
        )
    }
}

private fun webColor(color: Color): String =
    "#%06X".format(color.toArgb() and 0xFFFFFF)

@Composable
private fun VsprInfoPage(
    padding: PaddingValues,
    onBack: () -> Unit,
    onRead: () -> Unit,
) {
    BackHandler(onBack = onBack)

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
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.vspr_info_title),
                        style = MaterialTheme.typography.headlineMedium,
                    )
                    Text(
                        stringResource(R.string.vspr_info_version),
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
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                ),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(17.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    Text(
                        stringResource(R.string.vspr_provider_info),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(stringResource(R.string.vspr_info_provider_body_v2))
                }
            }
        }

        item {
            VsprInfoNativeCard(
                title = stringResource(R.string.vspr_info_activation_title),
                body = stringResource(R.string.vspr_info_activation_body),
            )
        }
        item {
            VsprInfoNativeCard(
                title = stringResource(R.string.vspr_info_permissions_title),
                body = stringResource(R.string.vspr_info_permissions_body),
            )
        }
        item {
            VsprInfoNativeCard(
                title = stringResource(R.string.vspr_info_ksu_title),
                body = stringResource(R.string.vspr_info_ksu_body_v2),
            )
        }
        item {
            VsprInfoNativeCard(
                title = stringResource(R.string.vspr_info_modules_title),
                body = stringResource(R.string.vspr_info_modules_body),
            )
        }
        item {
            VsprInfoNativeCard(
                title = stringResource(R.string.vspr_info_firewall_title),
                body = stringResource(R.string.vspr_info_firewall_body),
            )
        }

        item {
            FilledTonalButton(
                onClick = onRead,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Rounded.CheckCircle, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.vspr_info_read_action))
            }
        }
    }
}

@Composable
private fun VsprInfoNativeCard(
    title: String,
    body: String,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        ),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(17.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                body,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}


@Composable
private fun SoundSettingsDialog(
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val view = LocalView.current
    var enabled by remember { mutableStateOf(AppPreferences.uiSoundsEnabled(context)) }
    var legacyV1 by remember { mutableStateOf(AppPreferences.uiSoundLegacyV1(context)) }
    var profile by remember { mutableStateOf(AppPreferences.uiSoundProfile(context)) }
    var withoutRinger by remember {
        mutableStateOf(AppPreferences.uiSoundWithoutRinger(context))
    }
    var pages by remember { mutableStateOf(AppPreferences.uiSoundPages(context)) }
    var settings by remember { mutableStateOf(AppPreferences.uiSoundSettings(context)) }
    var buttons by remember { mutableStateOf(AppPreferences.uiSoundButtons(context)) }
    var expand by remember { mutableStateOf(AppPreferences.uiSoundExpand(context)) }
    var myHomePage by remember {
        mutableStateOf(AppPreferences.uiSoundProfileForKind(context, UiSoundKind.Page))
    }
    var myHomeSettings by remember {
        mutableStateOf(AppPreferences.uiSoundProfileForKind(context, UiSoundKind.Settings))
    }
    var myHomeButton by remember {
        mutableStateOf(AppPreferences.uiSoundProfileForKind(context, UiSoundKind.Button))
    }
    var myHomeExpand by remember {
        mutableStateOf(AppPreferences.uiSoundProfileForKind(context, UiSoundKind.Expand))
    }
    var editingKind by remember { mutableStateOf<UiSoundKind?>(null) }

    if (editingKind != null) {
        val kind = editingKind!!
        val selected = when (kind) {
            UiSoundKind.Page -> myHomePage
            UiSoundKind.Settings -> myHomeSettings
            UiSoundKind.Button -> myHomeButton
            UiSoundKind.Expand -> myHomeExpand
        }
        AlertDialog(
            onDismissRequest = { editingKind = null },
            title = {
                Text(
                    when (kind) {
                        UiSoundKind.Page -> stringResource(R.string.sound_pages)
                        UiSoundKind.Settings -> stringResource(R.string.sound_settings)
                        UiSoundKind.Button -> stringResource(R.string.sound_buttons)
                        UiSoundKind.Expand -> stringResource(R.string.sound_expand)
                    },
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 480.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    UiSoundProfile.presets.forEach { candidate ->
                        Card(
                            onClick = {
                                clickHaptic(view, sound = null)
                                when (kind) {
                                    UiSoundKind.Page -> myHomePage = candidate
                                    UiSoundKind.Settings -> myHomeSettings = candidate
                                    UiSoundKind.Button -> myHomeButton = candidate
                                    UiSoundKind.Expand -> myHomeExpand = candidate
                                }
                                AppPreferences.setUiSoundProfileForKind(context, kind, candidate)
                                UiSoundEngine.preview(context, candidate, kind)
                            },
                            colors = CardDefaults.cardColors(
                                containerColor = if (selected == candidate) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainerHighest
                                },
                            ),
                        ) {
                            Text(
                                candidate.displayLabel(legacyV1),
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { editingKind = null }) {
                    Text(stringResource(R.string.sound_done))
                }
            },
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.sound_menu_title)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                SoundToggleRow(
                    title = stringResource(R.string.sound_activate),
                    summary = stringResource(R.string.sound_activate_summary),
                    checked = enabled,
                    onCheckedChange = { next ->
                        clickHaptic(view, sound = null)
                        enabled = next
                        AppPreferences.setUiSoundsEnabled(context, next)
                        if (next) UiSoundEngine.preload(context)
                    },
                )

                AnimatedVisibility(visible = enabled) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        HorizontalDivider()
                        SoundToggleRow(
                            title = stringResource(R.string.sound_old_activate),
                            summary = stringResource(R.string.sound_old_activate_summary),
                            checked = legacyV1,
                            onCheckedChange = { next ->
                                clickHaptic(view, sound = null)
                                legacyV1 = next
                                AppPreferences.setUiSoundLegacyV1(context, next)
                                UiSoundEngine.preload(context)
                                UiSoundEngine.preview(context, profile)
                            },
                        )
                        AnimatedVisibility(visible = legacyV1) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                HorizontalDivider()
                                Text(
                                    stringResource(R.string.sound_profile),
                                    style = MaterialTheme.typography.titleSmall,
                                )
                        Text(
                            stringResource(R.string.sound_profile_summary),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            UiSoundProfile.entries.forEach { candidate ->
                                FilterChip(
                                    selected = profile == candidate,
                                    onClick = {
                                        clickHaptic(view, sound = null)
                                        profile = candidate
                                        AppPreferences.setUiSoundProfile(context, candidate)
                                        UiSoundEngine.preload(context)
                                        UiSoundEngine.preview(context, candidate)
                                    },
                                    label = { Text(candidate.displayLabel(legacyV1)) },
                                )
                            }
                        }

                        AnimatedVisibility(visible = profile == UiSoundProfile.MyHome) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                MyHomeSoundRow(
                                    title = stringResource(R.string.sound_pages),
                                    value = myHomePage.displayLabel(legacyV1),
                                    onClick = { editingKind = UiSoundKind.Page },
                                )
                                MyHomeSoundRow(
                                    title = stringResource(R.string.sound_settings),
                                    value = myHomeSettings.displayLabel(legacyV1),
                                    onClick = { editingKind = UiSoundKind.Settings },
                                )
                                MyHomeSoundRow(
                                    title = stringResource(R.string.sound_buttons),
                                    value = myHomeButton.displayLabel(legacyV1),
                                    onClick = { editingKind = UiSoundKind.Button },
                                )
                                MyHomeSoundRow(
                                    title = stringResource(R.string.sound_expand),
                                    value = myHomeExpand.displayLabel(legacyV1),
                                    onClick = { editingKind = UiSoundKind.Expand },
                                )
                            }
                        }

                        HorizontalDivider()

                        SoundToggleRow(
                            title = stringResource(R.string.sound_pages),
                            summary = stringResource(R.string.sound_pages_summary),
                            checked = pages,
                            onCheckedChange = { next ->
                                clickHaptic(view, sound = null)
                                pages = next
                                AppPreferences.setUiSoundPages(context, next)
                            },
                        )
                        SoundToggleRow(
                            title = stringResource(R.string.sound_settings),
                            summary = stringResource(R.string.sound_settings_summary),
                            checked = settings,
                            onCheckedChange = { next ->
                                clickHaptic(view, sound = null)
                                settings = next
                                AppPreferences.setUiSoundSettings(context, next)
                            },
                        )
                        SoundToggleRow(
                            title = stringResource(R.string.sound_buttons),
                            summary = stringResource(R.string.sound_buttons_summary),
                            checked = buttons,
                            onCheckedChange = { next ->
                                clickHaptic(view, sound = null)
                                buttons = next
                                AppPreferences.setUiSoundButtons(context, next)
                            },
                        )
                        SoundToggleRow(
                            title = stringResource(R.string.sound_expand),
                            summary = stringResource(R.string.sound_expand_summary),
                            checked = expand,
                            onCheckedChange = { next ->
                                clickHaptic(view, sound = null)
                                expand = next
                                AppPreferences.setUiSoundExpand(context, next)
                            },
                        )

                        HorizontalDivider()

                        SoundToggleRow(
                            title = stringResource(R.string.sound_without_ringer),
                            summary = stringResource(R.string.sound_without_ringer_summary),
                            checked = withoutRinger,
                            onCheckedChange = { next ->
                                clickHaptic(view, sound = null)
                                withoutRinger = next
                                AppPreferences.setUiSoundWithoutRinger(context, next)
                            },
                        )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.sound_done))
            }
        },
    )
}

@Composable
private fun MyHomeSoundRow(
    title: String,
    value: String,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                title,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                value,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun SoundToggleRow(
    title: String,
    summary: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    }
}

@Composable
private fun ThemeModeSelector(
    themeMode: AppThemeMode,
    onThemeModeChanged: (AppThemeMode) -> Unit,
) {
    val view = LocalView.current

    @Composable
    fun ThemeRow(modes: List<AppThemeMode>) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
        ) {
            modes.forEachIndexed { index, mode ->
                ToggleButton(
                    checked = themeMode == mode,
                    onCheckedChange = {
                        clickHaptic(view)
                        onThemeModeChanged(mode)
                    },
                    modifier = Modifier.weight(1f).semantics { role = Role.RadioButton },
                    colors = ToggleButtonDefaults.toggleButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    ),
                    shapes = when (index) {
                        0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                        modes.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                        else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                    },
                    contentPadding = PaddingValues(horizontal = 8.dp),
                ) {
                    Icon(
                        imageVector = when (mode) {
                            AppThemeMode.System -> Icons.Rounded.BrightnessAuto
                            AppThemeMode.Light -> Icons.Rounded.LightMode
                            AppThemeMode.Dark -> Icons.Rounded.DarkMode
                            AppThemeMode.WhiteDark -> Icons.Rounded.Contrast
                            AppThemeMode.WhitePurple -> Icons.Rounded.Palette
                            AppThemeMode.DarkPurple -> Icons.Rounded.DarkMode
                            AppThemeMode.Oled -> Icons.Rounded.Contrast
                            AppThemeMode.OledPurple -> Icons.Rounded.Palette
                            AppThemeMode.Green -> Icons.Rounded.Palette
                            AppThemeMode.Violet -> Icons.Rounded.Palette
                            AppThemeMode.Orange -> Icons.Rounded.Palette
                            AppThemeMode.Aurora -> Icons.Rounded.Palette
                            AppThemeMode.Graphite -> Icons.Rounded.Contrast
                            AppThemeMode.Midnight -> Icons.Rounded.DarkMode
                        },
                        contentDescription = null,
                    )
                    Spacer(Modifier.size(ToggleButtonDefaults.IconSpacing))
                    Text(themeModeLabel(mode), maxLines = 1)
                }
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ThemeRow(listOf(AppThemeMode.System, AppThemeMode.WhitePurple, AppThemeMode.OledPurple))
        ThemeRow(listOf(AppThemeMode.Light, AppThemeMode.Dark, AppThemeMode.WhiteDark))
        ThemeRow(listOf(AppThemeMode.DarkPurple, AppThemeMode.Oled))
        ThemeRow(listOf(AppThemeMode.Green, AppThemeMode.Violet, AppThemeMode.Orange))
        ThemeRow(listOf(AppThemeMode.Aurora, AppThemeMode.Graphite, AppThemeMode.Midnight))
    }
}

@Composable
private fun CVeyraRootAccessDialog(
    backend: CVeyraBackend,
    busy: Boolean,
    message: String?,
    onRequestRoot: () -> Unit,
    onOpenManager: () -> Unit,
    onUseAdb: () -> Unit,
    onDismiss: () -> Unit,
) {
    val view = LocalView.current
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        icon = {
            Icon(
                Icons.Rounded.Security,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        },
        title = {
            DialogDimAmount(0.34f)
            Text(stringResource(R.string.cveyra_root_menu_title))
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    stringResource(R.string.cveyra_root_menu_body),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    ),
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            stringResource(R.string.cveyra_root_menu_status),
                            style = MaterialTheme.typography.labelLarge,
                        )
                        Text(
                            stringResource(
                                when (backend) {
                                    CVeyraBackend.Root -> R.string.cveyra_backend_root
                                    CVeyraBackend.WirelessAdb -> R.string.cveyra_backend_adb
                                    CVeyraBackend.None -> R.string.cveyra_backend_none
                                },
                            ),
                            color = if (backend == CVeyraBackend.Root) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                }
                Text(
                    stringResource(R.string.cveyra_root_menu_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (busy) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp))
                        Text(stringResource(R.string.cveyra_root_menu_requesting))
                    }
                }
                message?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (backend == CVeyraBackend.Root) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                    )
                }
            }
        },
        confirmButton = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Button(
                    enabled = !busy,
                    onClick = onRequestRoot,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Rounded.Security, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.cveyra_root_menu_request))
                }
                FilledTonalButton(
                    enabled = !busy,
                    onClick = onOpenManager,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Rounded.Apps, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.cveyra_root_menu_open_manager))
                }
                FilledTonalButton(
                    enabled = !busy,
                    onClick = onUseAdb,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Rounded.Terminal, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.cveyra_root_menu_use_adb))
                }
            }
        },
        dismissButton = {
            TextButton(
                enabled = !busy,
                onClick = {
                    clickHaptic(view)
                    onDismiss()
                },
            ) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

@Composable
private fun CVeyraSetupDialog(
    backend: CVeyraBackend,
    busy: Boolean,
    message: String?,
    onGrantRoot: () -> Unit,
    onPairWireless: () -> Unit,
    onOpenWireless: () -> Unit,
    onDismiss: () -> Unit,
) {
    val view = LocalView.current
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                Icons.Rounded.Security,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        },
        title = {
            DialogDimAmount(0.34f)
            Text(stringResource(R.string.cveyra_setup_title))
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    stringResource(R.string.cveyra_setup_body),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    ),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(
                            imageVector = when (backend) {
                                CVeyraBackend.Root -> Icons.Rounded.Security
                                CVeyraBackend.WirelessAdb -> Icons.Rounded.Link
                                CVeyraBackend.None -> Icons.Rounded.Info
                            },
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            stringResource(
                                when (backend) {
                                    CVeyraBackend.Root -> R.string.cveyra_backend_root
                                    CVeyraBackend.WirelessAdb -> R.string.cveyra_backend_adb
                                    CVeyraBackend.None -> R.string.cveyra_backend_none
                                },
                            ),
                            style = MaterialTheme.typography.titleSmall,
                        )
                    }
                }
                if (busy) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp))
                        Text(
                            stringResource(R.string.adb_pair_working),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
                message?.let {
                    Text(
                        it,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                FilledTonalButton(
                    enabled = !busy,
                    onClick = onGrantRoot,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Rounded.Security, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.cveyra_setup_root))
                }
                FilledTonalButton(
                    enabled = !busy,
                    onClick = onPairWireless,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Rounded.Link, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.cveyra_setup_wireless))
                }
                TextButton(
                    enabled = !busy,
                    onClick = onOpenWireless,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.cveyra_setup_open_wireless))
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    clickHaptic(view)
                    onDismiss()
                },
            ) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

@Composable
private fun AboutDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val view = LocalView.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            DialogDimAmount(0.34f)
            Text(stringResource(R.string.about_title))
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(
                    onClick = {
                        clickHaptic(view)
                        uriHandler.openUri(KERNEL_SU_HOME_URL)
                    },
                    color = Color.Transparent,
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(painterResource(R.drawable.ic_kernelsu), contentDescription = null)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.kernelsu_card_title),
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Text(
                                stringResource(R.string.kernelsu_card_description),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Icon(Icons.Rounded.Link, contentDescription = null)
                    }
                }
                HorizontalDivider()
                Surface(
                    onClick = {
                        clickHaptic(view)
                        Toast.makeText(
                            context,
                            context.getString(R.string.veyra_link_unavailable),
                            Toast.LENGTH_SHORT,
                        ).show()
                    },
                    color = Color.Transparent,
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(painterResource(R.drawable.ic_github), contentDescription = null)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.github_card_title),
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Text(
                                stringResource(R.string.github_card_description),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Icon(
                            Icons.Rounded.Link,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                clickHaptic(view)
                onDismiss()
            }) {
                Text(stringResource(R.string.action_close))
            }
        },
    )
}

@Composable
internal fun expressiveClickableCardShape(
    interactionSource: MutableInteractionSource,
    position: SettingsCardPosition = SettingsCardPosition.Single,
): RoundedCornerShape {
    val pressed by interactionSource.collectIsPressedAsState()
    val topRadius by animateDpAsState(
        // Pressed is the one radius that is not a resting one: the card swells to show it took the tap.
        targetValue = if (pressed) 28.dp else settingsCardRestingRadius(position, top = true),
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "clickable-card-top-corner",
    )
    val bottomRadius by animateDpAsState(
        targetValue = if (pressed) 28.dp else settingsCardRestingRadius(position, top = false),
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "clickable-card-bottom-corner",
    )
    return RoundedCornerShape(
        topStart = topRadius,
        topEnd = topRadius,
        bottomStart = bottomRadius,
        bottomEnd = bottomRadius,
    )
}

@Composable
private fun SideChoiceMenu(
    choices: List<String>,
    selectedIndex: Int,
    topOffset: Dp,
    onSelected: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var visible by remember { mutableStateOf(false) }
    var closing by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val view = LocalView.current
    val scrimAlpha by animateFloatAsState(
        targetValue = if (visible) 0.34f else 0f,
        animationSpec = tween(durationMillis = if (visible) 160 else 180),
        label = "menu-scrim",
    )

    fun closeMenu(afterAnimation: () -> Unit) {
        if (closing) return
        closing = true
        visible = false
        coroutineScope.launch {
            delay(MENU_EXIT_WAIT_MILLIS)
            afterAnimation()
        }
    }

    LaunchedEffect(Unit) {
        visible = true
    }

    Popup(
        onDismissRequest = { closeMenu(onDismiss) },
        properties = PopupProperties(
            focusable = true,
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            clippingEnabled = false,
        ),
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val estimatedHeight = 16.dp + 56.dp * choices.size
            val constrainedTop = minOf(
                topOffset,
                maxHeight - estimatedHeight - 24.dp,
            ).coerceAtLeast(16.dp)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = scrimAlpha))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { closeMenu(onDismiss) },
                    ),
            )
            AnimatedVisibility(
                visible = visible,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = constrainedTop, end = 18.dp),
                enter = scaleIn(
                    animationSpec = keyframes {
                        durationMillis = 200
                        1.025f at 95
                        0.995f at 155
                    },
                    initialScale = 0.94f,
                    transformOrigin = TransformOrigin(1f, 0f),
                ),
                exit = scaleOut(
                    animationSpec = tween(durationMillis = MENU_EXIT_ANIMATION_MILLIS),
                    targetScale = 0.86f,
                    transformOrigin = TransformOrigin(1f, 0.5f),
                ) + fadeOut(
                    animationSpec = tween(
                        durationMillis = 160,
                        delayMillis = 20,
                    ),
                ),
            ) {
                Surface(
                    modifier = Modifier
                        .width(196.dp)
                        .heightIn(max = 620.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {},
                        ),
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    tonalElevation = 0.dp,
                    shadowElevation = 0.dp,
                ) {
                    LazyColumn(
                        contentPadding = PaddingValues(8.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        itemsIndexed(choices) { index, choice ->
                            val selected = index == selectedIndex
                            Surface(
                                onClick = {
                                    clickHaptic(view)
                                    closeMenu { onSelected(index) }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = if (selected) {
                                    MaterialTheme.shapes.extraLarge
                                } else {
                                    MaterialTheme.shapes.medium
                                },
                                color = if (selected) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    Color.Transparent
                                },
                                contentColor = if (selected) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    if (selected) {
                                        Icon(
                                            Icons.Rounded.Check,
                                            contentDescription = null,
                                            modifier = Modifier.size(22.dp),
                                        )
                                    }
                                    Text(
                                        text = choice,
                                        modifier = Modifier.weight(1f),
                                        style = MaterialTheme.typography.titleSmall,
                                        maxLines = 1,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private const val MENU_EXIT_ANIMATION_MILLIS = 180
private const val MENU_EXIT_WAIT_MILLIS = 200L

@Composable
private fun languageLabel(tag: String): String =
    languageOptions.firstOrNull { languageMatches(it, tag) }
        ?.let { stringResource(it.label) }
        ?: stringResource(R.string.language_system)

private fun languageMatches(option: LanguageOption, currentTag: String): Boolean {
    if (option.tag.isEmpty()) return currentTag.isEmpty()
    return currentTag == option.tag || currentTag.startsWith("$option.tag-")
}

@Composable
private fun accentLabel(color: AccentColor): String = when (color) {
    AccentColor.Dynamic -> stringResource(R.string.color_dynamic)
    AccentColor.Blue -> stringResource(R.string.color_blue)
    AccentColor.Violet -> stringResource(R.string.color_violet)
    AccentColor.Green -> stringResource(R.string.color_green)
    AccentColor.Orange -> stringResource(R.string.color_orange)
}

@Composable
private fun themeModeLabel(themeMode: AppThemeMode): String = when (themeMode) {
    AppThemeMode.System -> stringResource(R.string.theme_system)
    AppThemeMode.Light -> stringResource(R.string.theme_light)
    AppThemeMode.Dark -> stringResource(R.string.theme_dark)
    AppThemeMode.WhiteDark -> stringResource(R.string.theme_white_dark)
    AppThemeMode.WhitePurple -> stringResource(R.string.theme_white_purple)
    AppThemeMode.DarkPurple -> stringResource(R.string.theme_dark_purple)
    AppThemeMode.Oled -> stringResource(R.string.theme_oled)
    AppThemeMode.OledPurple -> stringResource(R.string.theme_oled_purple)
    AppThemeMode.Green -> stringResource(R.string.theme_green)
    AppThemeMode.Violet -> stringResource(R.string.theme_violet)
    AppThemeMode.Orange -> stringResource(R.string.theme_orange)
    AppThemeMode.Aurora -> stringResource(R.string.theme_aurora)
    AppThemeMode.Graphite -> stringResource(R.string.theme_graphite)
    AppThemeMode.Midnight -> stringResource(R.string.theme_midnight)
}
