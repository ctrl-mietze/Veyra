package ctrl.mietze.veyraroot

import android.app.LocaleManager
import android.content.Context
import android.os.Build
import android.os.LocaleList
import java.util.Locale
import com.kernelpack.policy.SeriesOverride
import org.json.JSONArray
import org.json.JSONObject

enum class AccentColor(val storedValue: String) {
    Dynamic("dynamic"),
    Blue("blue"),
    Violet("violet"),
    Green("green"),
    Orange("orange");

    companion object {
        fun fromStoredValue(value: String?): AccentColor =
            entries.firstOrNull { it.storedValue == value } ?: Dynamic
    }
}

enum class AppThemeMode(val storedValue: String) {
    System("system"),
    Light("light"),
    Dark("dark"),
    WhiteDark("white_dark"),
    WhitePurple("white_purple"),
    DarkPurple("dark_purple"),
    Oled("oled"),
    OledPurple("oled_purple"),
    Green("green"),
    Violet("violet"),
    Orange("orange"),
    Aurora("aurora"),
    Graphite("graphite"),
    Midnight("midnight");

    companion object {
        fun fromStoredValue(value: String?): AppThemeMode =
            entries.firstOrNull { it.storedValue == value } ?: System
    }
}

enum class UiSoundProfile(
    val storedValue: String,
    val label: String,
    val v1Label: String,
) {
    Veyra("veyra", "Veyra", "Veyra"),
    Soft("soft", "Soft", "Soft"),
    Glass("glass", "Glass", "Glass"),
    Air("air", "Air", "Air"),
    Velvet("velvet", "Velvet", "Velvet"),
    Halo("halo", "Halo", "Halo"),
    Pulse("pulse", "Pulse", "Pulse"),
    Crystal("crystal", "Crystal", "Crystal"),
    Nova("nova", "Nova", "Nova"),
    Minimal("minimal", "Minimal", "Minimal"),
    Aero("aero", "Aero", "Aero"),
    Obsidian("obsidian", "Obsidian", "Obsidian"),
    Silk("silk", "Silk", "Silk"),
    MyHome("my_home", "My Home", "My Home");

    fun displayLabel(useV1: Boolean): String = if (useV1) v1Label else label

    companion object {
        val presets: List<UiSoundProfile>
            get() = entries.filterNot { it == MyHome }

        fun fromStoredValue(value: String?): UiSoundProfile =
            entries.firstOrNull { it.storedValue == value } ?: Veyra

        fun presetFromStoredValue(value: String?): UiSoundProfile =
            presets.firstOrNull { it.storedValue == value } ?: Veyra
    }
}

enum class SettingsLayoutMode(val storedValue: String) {
    Pages("pages"),
    Folders("folders");

    companion object {
        fun fromStoredValue(value: String?): SettingsLayoutMode =
            entries.firstOrNull { it.storedValue == value } ?: Pages
    }
}

enum class NavBarStyle(val storedValue: String) {
    Standard("standard"),
    Glass("glass");

    companion object {
        fun fromStoredValue(value: String?): NavBarStyle =
            entries.firstOrNull { it.storedValue == value } ?: Standard
    }
}

object AppPreferences {
    private const val PREFERENCES = "appearance"
    private const val ACCENT_COLOR = "accent_color"
    private const val THEME_MODE = "theme_mode"
    private const val ADVANCED_MODE = "advanced_mode"
    private const val DISABLE_KSU_MODULES = "disable_ksu_modules"
    private const val LOAD_KERNEL_SU = "load_kernel_su"
    private const val KERNEL_SU_FLAVOR = "kernel_su_flavor"
    private const val MANAGER_VERSION_PREFIX = "manager_version_"
    private const val PAYLOAD_KERNEL_SU_VERSION_PREFIX = "payload_ksu_version_"
    private const val MANAGER_PACKAGE_PREFIX = "manager_package_"
    private const val LOADED_FLAVOR = "loaded_flavor"
    private const val LOADED_FLAVOR_BOOT = "loaded_flavor_boot"
    private const val SHIZUKU_MODE = "shizuku_mode"
    private const val BOOT_ROOT_MODE = "boot_root_mode"
    private const val RESTART_AFTER_ROOT = "restart_after_root"
    private const val RETRY_AFTER_REBOOT = "retry_after_reboot_boot"
    private const val SHIZUKU_BOOT_MODE = "shizuku_boot_mode"
    private const val BOOT_SETTLE_SECONDS = "boot_settle_seconds"
    private const val RUN_STALL_SECONDS = "run_stall_seconds"
    private const val RUN_TOTAL_SECONDS = "run_total_seconds"
    private const val RUN_HELPER_SECONDS = "run_helper_seconds"
    private const val EXPLOIT_OVERRIDE_ENABLED = "exploit_override_enabled"
    private const val EXPLOIT_OVERRIDE_ATTEMPTS = "exploit_override_attempts"
    private const val EXPLOIT_OVERRIDE_ATTEMPT_TIMEOUT = "exploit_override_attempt_timeout"
    private const val EXPLOIT_OVERRIDE_SLIDE_ROUTE = "exploit_override_slide_route"
    private const val AUTO_ROOT_SETTLE_SECONDS = "auto_root_settle_seconds"
    private const val SHIZUKU_AUTOMATION_TOKEN = "shizuku_automation_token"
    private const val PARTITION_READ_ONLY_MODE = "partition_read_only_mode"
    private const val READ_ONLY_PROTECTED_BOOT = "partition_read_only_protected_boot"
    private const val READ_ONLY_PROTECTED_DEVICES = "partition_read_only_protected_devices"
    private const val ADB_PAIRED = "adb_paired"
    private const val WIRELESS_ADB_OURS = "wireless_adb_owned"
    private const val PAYLOAD_MODE = "payload_mode"
    private const val ROOT_METHOD = "root_method"
    private const val BATTERY_PROMPT_SHOWN = "battery_prompt_shown"
    private const val LOCAL_PAYLOAD_NAME = "local_payload_name"
    private const val USE_LOCAL_PAYLOAD = "use_local_payload"
    private const val MAGIC_UNLOCK_REPORT = "magic_unlock_report"
    private const val PAYLOAD_SOURCES = "payload_sources"
    private const val PAYLOAD_SOURCES_MANUAL_SELECTION = "payload_sources_manual_selection"
    private const val PAYLOAD_SOURCES_POLICY_VERSION = "payload_sources_policy_version"
    private const val CURRENT_PAYLOAD_SOURCES_POLICY_VERSION = 1
    private const val PAYLOAD_SOURCES_BUILTIN_VERSION = "payload_sources_builtin_version"
    private const val CURRENT_PAYLOAD_SOURCES_BUILTIN_VERSION = 3
    // Superseded by the source list; read once so an existing selection survives the upgrade.
    private const val LEGACY_PAYLOAD_REPOSITORY = "payload_repository"
    private const val LEGACY_PAYLOAD_BRANCH = "payload_branch"
    private const val CONSUMED_INSTALL_REQUEST = "consumed_install_request"
    // Named for the collapsed set rather than the open one: this replaced a key that stored which sections
    // were open, and the two are opposite readings of the same names, so the old one is left unread rather
    // than migrated - a page that comes up with everything open is the page this change is for.
    private const val CLOSED_SETTINGS_SECTIONS = "closed_settings_sections"
    private const val SETTINGS_LAYOUT_MODE = "settings_layout_mode"
    private const val FOLDER_LAYOUT_INITIALIZED = "folder_layout_initialized"
    private const val NAV_BAR_GLASS = "nav_bar_glass"
    private const val PAGE_TRANSITION_SOUND = "page_transition_sound"
    private const val PAGE_TRANSITION_SOUND_WITHOUT_RINGER = "page_transition_sound_without_ringer"
    private const val UI_SOUNDS_ENABLED = "ui_sounds_enabled"
    private const val UI_SOUND_LEGACY_V1 = "ui_sound_legacy_v1"
    private const val UI_SOUND_PROFILE = "ui_sound_profile"
    private const val UI_SOUND_WITHOUT_RINGER = "ui_sound_without_ringer"
    private const val UI_SOUND_PAGES = "ui_sound_pages"
    private const val UI_SOUND_SETTINGS = "ui_sound_settings"
    private const val UI_SOUND_BUTTONS = "ui_sound_buttons"
    private const val UI_SOUND_EXPAND = "ui_sound_expand"
    private const val UI_SOUND_MY_HOME_PAGE = "ui_sound_my_home_page"
    private const val UI_SOUND_MY_HOME_SETTINGS = "ui_sound_my_home_settings"
    private const val UI_SOUND_MY_HOME_BUTTON = "ui_sound_my_home_button"
    private const val UI_SOUND_MY_HOME_EXPAND = "ui_sound_my_home_expand"
    private const val HOME_STATUS_EXPANDED = "home_status_expanded"
    private const val HOME_DEVICE_EXPANDED = "home_device_expanded"
    private const val HOME_KERNEL_EXPANDED = "home_kernel_expanded"
    private const val TARGET_FITS_DEVICE = "target_fits_device"
    private const val KERNEL_SERIES_OVERRIDE = "kernel_series_override"
    private const val ALLOW_ABI_MISMATCH = "allow_abi_mismatch"
    private const val ALLOW_TEST_KERNEL = "allow_test_kernel"
    private const val BUILDER_ALLOW_UNSTABLE_4X = "builder_allow_unstable_4x"
    private const val BUILDER_NEAREST_FAMILY = "builder_nearest_family"
    private const val MAGIC_BUILDER_ALLOW_UNSTABLE_4X = "magic_builder_allow_unstable_4x"
    private const val MAGIC_BUILDER_NEAREST_FAMILY = "magic_builder_nearest_family"
    private const val MAGIC_BUILDER_VIVO_LEGACY_LOCAL = "magic_builder_vivo_legacy_local"
    private const val MAGIC_BUILDER_AUTO_EXPORT = "magic_builder_auto_export"
    private const val MAGIC_BUILDER_STRICT_KERNEL = "magic_builder_strict_kernel"
    private const val MAGIC_BUILDER_AUTO_APPLY = "magic_builder_auto_apply"
    private const val NAV_BAR_STYLE = "nav_bar_style"

    /**
     * The settings sections the user has collapsed, by name.
     *
     * Stored rather than kept on the page because the page is rebuilt every time the tab is left and
     * returned to, and a section that reopened itself on the way back would undo what was asked for.
     *
     * The collapsed set and not the open one, because an empty preference has to mean the page is whole:
     * every section shows what it holds unless it was closed, so nothing stored is the full page and
     * collapsing all eight is still a state that can be stored and come back.
     */
    internal fun closedSettingsSections(context: Context): Set<SettingsSection> {
        val preferences = prefs(context)
        if (!preferences.getBoolean(FOLDER_LAYOUT_INITIALIZED, false)) {
            return SettingsSection.entries.toSet()
        }
        return SettingsSection.named(
            preferences.getStringSet(CLOSED_SETTINGS_SECTIONS, emptySet()).orEmpty().toSet(),
        )
    }

    internal fun setClosedSettingsSections(context: Context, sections: Set<SettingsSection>) {
        prefs(context).edit()
            .putBoolean(FOLDER_LAYOUT_INITIALIZED, true)
            .putStringSet(CLOSED_SETTINGS_SECTIONS, sections.map { it.name }.toSet())
            .apply()
    }

    fun navBarGlass(context: Context): Boolean =
        prefs(context).getBoolean(NAV_BAR_GLASS, false)

    fun setNavBarGlass(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(NAV_BAR_GLASS, enabled).apply()
    }

    fun pageTransitionSound(context: Context): Boolean =
        prefs(context).getBoolean(PAGE_TRANSITION_SOUND, true)

    fun setPageTransitionSound(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(PAGE_TRANSITION_SOUND, enabled).apply()
    }

    fun pageTransitionSoundWithoutRinger(context: Context): Boolean =
        prefs(context).getBoolean(PAGE_TRANSITION_SOUND_WITHOUT_RINGER, false)

    fun setPageTransitionSoundWithoutRinger(context: Context, enabled: Boolean) {
        prefs(context).edit()
            .putBoolean(PAGE_TRANSITION_SOUND_WITHOUT_RINGER, enabled)
            .apply()
    }

    fun uiSoundsEnabled(context: Context): Boolean =
        prefs(context).getBoolean(UI_SOUNDS_ENABLED, false)

    fun setUiSoundsEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(UI_SOUNDS_ENABLED, enabled).apply()
    }

    fun uiSoundLegacyV1(context: Context): Boolean =
        prefs(context).getBoolean(UI_SOUND_LEGACY_V1, false)

    fun setUiSoundLegacyV1(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(UI_SOUND_LEGACY_V1, enabled).apply()
    }

    fun uiSoundProfile(context: Context): UiSoundProfile =
        UiSoundProfile.fromStoredValue(prefs(context).getString(UI_SOUND_PROFILE, null))

    fun setUiSoundProfile(context: Context, profile: UiSoundProfile) {
        prefs(context).edit().putString(UI_SOUND_PROFILE, profile.storedValue).apply()
    }

    fun uiSoundWithoutRinger(context: Context): Boolean =
        prefs(context).getBoolean(UI_SOUND_WITHOUT_RINGER, false)

    fun setUiSoundWithoutRinger(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(UI_SOUND_WITHOUT_RINGER, enabled).apply()
    }

    fun uiSoundPages(context: Context): Boolean =
        prefs(context).getBoolean(UI_SOUND_PAGES, true)

    fun setUiSoundPages(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(UI_SOUND_PAGES, enabled).apply()
    }

    fun uiSoundSettings(context: Context): Boolean =
        prefs(context).getBoolean(UI_SOUND_SETTINGS, true)

    fun setUiSoundSettings(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(UI_SOUND_SETTINGS, enabled).apply()
    }

    fun uiSoundButtons(context: Context): Boolean =
        prefs(context).getBoolean(UI_SOUND_BUTTONS, true)

    fun setUiSoundButtons(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(UI_SOUND_BUTTONS, enabled).apply()
    }

    fun uiSoundExpand(context: Context): Boolean =
        prefs(context).getBoolean(UI_SOUND_EXPAND, true)

    fun setUiSoundExpand(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(UI_SOUND_EXPAND, enabled).apply()
    }

    internal fun uiSoundProfileForKind(context: Context, kind: UiSoundKind): UiSoundProfile {
        val key = when (kind) {
            UiSoundKind.Page -> UI_SOUND_MY_HOME_PAGE
            UiSoundKind.Settings -> UI_SOUND_MY_HOME_SETTINGS
            UiSoundKind.Button -> UI_SOUND_MY_HOME_BUTTON
            UiSoundKind.Expand -> UI_SOUND_MY_HOME_EXPAND
        }
        return UiSoundProfile.presetFromStoredValue(prefs(context).getString(key, null))
    }

    internal fun setUiSoundProfileForKind(
        context: Context,
        kind: UiSoundKind,
        profile: UiSoundProfile,
    ) {
        val key = when (kind) {
            UiSoundKind.Page -> UI_SOUND_MY_HOME_PAGE
            UiSoundKind.Settings -> UI_SOUND_MY_HOME_SETTINGS
            UiSoundKind.Button -> UI_SOUND_MY_HOME_BUTTON
            UiSoundKind.Expand -> UI_SOUND_MY_HOME_EXPAND
        }
        val preset = profile.takeUnless { it == UiSoundProfile.MyHome } ?: UiSoundProfile.Veyra
        prefs(context).edit().putString(key, preset.storedValue).apply()
    }

    fun homeStatusExpanded(context: Context): Boolean =
        prefs(context).getBoolean(HOME_STATUS_EXPANDED, true)

    fun setHomeStatusExpanded(context: Context, expanded: Boolean) {
        prefs(context).edit().putBoolean(HOME_STATUS_EXPANDED, expanded).apply()
    }

    fun homeDeviceExpanded(context: Context): Boolean =
        prefs(context).getBoolean(HOME_DEVICE_EXPANDED, true)

    fun setHomeDeviceExpanded(context: Context, expanded: Boolean) {
        prefs(context).edit().putBoolean(HOME_DEVICE_EXPANDED, expanded).apply()
    }

    fun homeKernelExpanded(context: Context): Boolean =
        prefs(context).getBoolean(HOME_KERNEL_EXPANDED, false)

    fun setHomeKernelExpanded(context: Context, expanded: Boolean) {
        prefs(context).edit().putBoolean(HOME_KERNEL_EXPANDED, expanded).apply()
    }

    fun settingsLayoutMode(context: Context): SettingsLayoutMode =
        SettingsLayoutMode.fromStoredValue(prefs(context).getString(SETTINGS_LAYOUT_MODE, null))

    fun setSettingsLayoutMode(context: Context, mode: SettingsLayoutMode) {
        val preferences = prefs(context)
        val editor = preferences.edit().putString(SETTINGS_LAYOUT_MODE, mode.storedValue)
        if (mode == SettingsLayoutMode.Folders &&
            !preferences.getBoolean(FOLDER_LAYOUT_INITIALIZED, false)
        ) {
            editor
                .putBoolean(FOLDER_LAYOUT_INITIALIZED, true)
                .putStringSet(
                    CLOSED_SETTINGS_SECTIONS,
                    SettingsSection.entries.map { it.name }.toSet(),
                )
        }
        editor.apply()
    }

    /**
     * Whether the target sheet offers only what fits this phone.
     *
     * Stored rather than remembered on the sheet, because it is a standing preference and not a
     * question asked once: whoever turns it off is testing other devices' payloads, and having to
     * turn it off again at every run is the sort of errand that ends in picking the wrong target.
     */
    internal fun targetFitsDeviceOnly(context: Context): Boolean =
        prefs(context).getBoolean(TARGET_FITS_DEVICE, true)

    internal fun setTargetFitsDeviceOnly(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(TARGET_FITS_DEVICE, enabled).apply()
    }

    fun payloadSources(context: Context): List<PayloadSource> {
        val preferences = prefs(context)
        val stored = preferences.getString(PAYLOAD_SOURCES, null)
        val current = if (stored == null) {
            listOf(legacyPayloadSource(context))
        } else {
            decodePayloadSources(stored).ifEmpty { listOf(PayloadSource.DEFAULT) }
        }

        // Builds before the exact-firmware scanner marked the source sheet as "manual" even when the
        // saved rows were just the untouched built-ins. That stale bit bypasses compatibility scanning
        // forever and can pin an S938B/CZG3 device to a generic S938N/CZF1 payload. Migrate that state
        // exactly once. Custom repositories and pinned revisions are preserved as intentional manual
        // policy; after this version, a real checkbox change writes the current policy version itself.
        if (preferences.getInt(PAYLOAD_SOURCES_POLICY_VERSION, 0) <
            CURRENT_PAYLOAD_SOURCES_POLICY_VERSION
        ) {
            val builtInRepositories = PayloadSource.BUILT_IN_SOURCES
                .map { it.repository.lowercase() }
                .toSet()
            val onlyPlainBuiltIns = current.all { source ->
                source.repository.lowercase() in builtInRepositories && !source.isPinned
            }
            preferences.edit()
                .putInt(
                    PAYLOAD_SOURCES_POLICY_VERSION,
                    CURRENT_PAYLOAD_SOURCES_POLICY_VERSION,
                )
                .apply {
                    if (onlyPlainBuiltIns) {
                        putBoolean(PAYLOAD_SOURCES_MANUAL_SELECTION, false)
                    }
                }
                .apply()
        }

        if (preferences.getInt(PAYLOAD_SOURCES_BUILTIN_VERSION, 0) >=
            CURRENT_PAYLOAD_SOURCES_BUILTIN_VERSION
        ) {
            return current
        }

        var migrated = current
        PayloadSource.BUILT_IN_SOURCES.forEach { builtIn ->
            if (migrated.none {
                    it.repository.equals(builtIn.repository, ignoreCase = true) &&
                        it.branch.equals(builtIn.branch, ignoreCase = true)
                }
            ) {
                migrated = migrated + builtIn
            }
        }

        preferences.edit()
            .putString(PAYLOAD_SOURCES, encodePayloadSources(migrated))
            .putInt(
                PAYLOAD_SOURCES_BUILTIN_VERSION,
                CURRENT_PAYLOAD_SOURCES_BUILTIN_VERSION,
            )
            .remove(LEGACY_PAYLOAD_REPOSITORY)
            .remove(LEGACY_PAYLOAD_BRANCH)
            .apply()

        return migrated
    }

    /**
     * Saves a source selection made by the person in the source sheet.
     *
     * Once this flag is set Veyra stops choosing another built-in behind their back: the checked
     * sources are the policy until the user explicitly asks to return to automatic selection.
     */
    fun setPayloadSources(context: Context, sources: List<PayloadSource>) {
        prefs(context).edit()
            .putString(PAYLOAD_SOURCES, encodePayloadSources(sources))
            .putBoolean(PAYLOAD_SOURCES_MANUAL_SELECTION, true)
            .putInt(PAYLOAD_SOURCES_POLICY_VERSION, CURRENT_PAYLOAD_SOURCES_POLICY_VERSION)
            .remove(LEGACY_PAYLOAD_REPOSITORY)
            .remove(LEGACY_PAYLOAD_BRANCH)
            .apply()
    }

    /** Saves the app's compatibility-scanner choice without turning it into a manual override. */
    fun setPayloadSourcesAutomatically(context: Context, sources: List<PayloadSource>) {
        prefs(context).edit()
            .putString(PAYLOAD_SOURCES, encodePayloadSources(sources))
            .putBoolean(PAYLOAD_SOURCES_MANUAL_SELECTION, false)
            .putInt(PAYLOAD_SOURCES_POLICY_VERSION, CURRENT_PAYLOAD_SOURCES_POLICY_VERSION)
            .remove(LEGACY_PAYLOAD_REPOSITORY)
            .remove(LEGACY_PAYLOAD_BRANCH)
            .apply()
    }

    /** Whether the source checkboxes are a user decision rather than the built-in scanner's choice. */
    fun payloadSourcesManuallySelected(context: Context): Boolean =
        prefs(context).getBoolean(PAYLOAD_SOURCES_MANUAL_SELECTION, false)

    /** Returns source selection to the built-in compatibility scanner. */
    fun useAutomaticPayloadSources(context: Context) {
        prefs(context).edit()
            .putBoolean(PAYLOAD_SOURCES_MANUAL_SELECTION, false)
            .putInt(PAYLOAD_SOURCES_POLICY_VERSION, CURRENT_PAYLOAD_SOURCES_POLICY_VERSION)
            .apply()
    }

    private fun legacyPayloadSource(context: Context): PayloadSource {
        val preferences = prefs(context)
        val repository = preferences.getString(LEGACY_PAYLOAD_REPOSITORY, null)
        val branch = preferences.getString(LEGACY_PAYLOAD_BRANCH, null)
        return PayloadSource.create(
            repository = repository ?: PayloadSource.DEFAULT_REPOSITORY,
            branch = branch ?: PayloadSource.DEFAULT_BRANCH,
        ) ?: PayloadSource.DEFAULT
    }

    private fun encodePayloadSources(sources: List<PayloadSource>): String {
        val array = JSONArray()
        sources.forEach { source ->
            array.put(
                JSONObject()
                    .put("repository", source.repository)
                    .put("branch", source.branch)
                    .put("enabled", source.enabled)
                    .put("pinnedCommit", source.pinnedCommit),
            )
        }
        return array.toString()
    }

    private fun decodePayloadSources(stored: String): List<PayloadSource> = try {
        val array = JSONArray(stored)
        buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val source = PayloadSource.create(
                    repository = item.optString("repository"),
                    branch = item.optString("branch"),
                    enabled = item.optBoolean("enabled", true),
                ) ?: continue
                // A stored pin is trusted only if it is a full commit; anything else would either
                // fail later or silently read the wrong revision.
                val stored = item.optString("pinnedCommit")
                val pinned = if (PayloadSource.isCommitValid(stored)) {
                    source.copy(pinnedCommit = stored.trim())
                } else {
                    source
                }
                if (none { it.id == pinned.id }) add(pinned)
            }
        }
    } catch (error: Throwable) {
        emptyList()
    }

    fun kernelSeriesOverride(context: Context): SeriesOverride = SeriesOverride.fromWire(
        prefs(context).getString(KERNEL_SERIES_OVERRIDE, null),
    )

    fun setKernelSeriesOverride(context: Context, value: SeriesOverride) {
        prefs(context).edit().putString(KERNEL_SERIES_OVERRIDE, value.wireValue).apply()
    }

    fun allowAbiMismatch(context: Context): Boolean =
        prefs(context).getBoolean(ALLOW_ABI_MISMATCH, false)

    fun setAllowAbiMismatch(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(ALLOW_ABI_MISMATCH, enabled).apply()
    }

    fun allowTestKernel(context: Context): Boolean =
        prefs(context).getBoolean(ALLOW_TEST_KERNEL, false)

    fun setAllowTestKernel(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(ALLOW_TEST_KERNEL, enabled).apply()
    }

    fun builderAllowUnstable4x(context: Context): Boolean =
        prefs(context).getBoolean(BUILDER_ALLOW_UNSTABLE_4X, false)

    fun setBuilderAllowUnstable4x(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(BUILDER_ALLOW_UNSTABLE_4X, enabled).apply()
    }

    fun builderNearestFamily(context: Context): Boolean =
        prefs(context).getBoolean(BUILDER_NEAREST_FAMILY, false)

    fun setBuilderNearestFamily(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(BUILDER_NEAREST_FAMILY, enabled).apply()
    }

    fun magicBuilderAllowUnstable4x(context: Context): Boolean =
        prefs(context).getBoolean(MAGIC_BUILDER_ALLOW_UNSTABLE_4X, true)

    fun setMagicBuilderAllowUnstable4x(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(MAGIC_BUILDER_ALLOW_UNSTABLE_4X, enabled).apply()
    }

    fun magicBuilderNearestFamily(context: Context): Boolean =
        prefs(context).getBoolean(MAGIC_BUILDER_NEAREST_FAMILY, false)

    fun setMagicBuilderNearestFamily(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(MAGIC_BUILDER_NEAREST_FAMILY, enabled).apply()
    }

    fun magicBuilderVivoLegacyLocal(context: Context): Boolean =
        prefs(context).getBoolean(MAGIC_BUILDER_VIVO_LEGACY_LOCAL, true)

    fun setMagicBuilderVivoLegacyLocal(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(MAGIC_BUILDER_VIVO_LEGACY_LOCAL, enabled).apply()
    }

    fun magicBuilderAutoExport(context: Context): Boolean =
        prefs(context).getBoolean(MAGIC_BUILDER_AUTO_EXPORT, true)

    fun setMagicBuilderAutoExport(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(MAGIC_BUILDER_AUTO_EXPORT, enabled).apply()
    }

    fun magicBuilderStrictKernel(context: Context): Boolean =
        prefs(context).getBoolean(MAGIC_BUILDER_STRICT_KERNEL, true)

    fun setMagicBuilderStrictKernel(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(MAGIC_BUILDER_STRICT_KERNEL, enabled).apply()
    }

    fun magicBuilderAutoApply(context: Context): Boolean =
        prefs(context).getBoolean(MAGIC_BUILDER_AUTO_APPLY, true)

    fun setMagicBuilderAutoApply(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(MAGIC_BUILDER_AUTO_APPLY, enabled).apply()
    }

    fun accentColor(context: Context): AccentColor = AccentColor.fromStoredValue(
        prefs(context).getString(ACCENT_COLOR, null),
    )

    fun setAccentColor(context: Context, color: AccentColor) {
        prefs(context).edit()
            .putString(ACCENT_COLOR, color.storedValue)
            .apply()
    }

    fun navBarStyle(context: Context): NavBarStyle =
        NavBarStyle.fromStoredValue(prefs(context).getString(NAV_BAR_STYLE, null))

    fun setNavBarStyle(context: Context, style: NavBarStyle) {
        prefs(context).edit().putString(NAV_BAR_STYLE, style.storedValue).apply()
    }

    fun themeMode(context: Context): AppThemeMode = AppThemeMode.fromStoredValue(
        prefs(context).getString(THEME_MODE, null),
    )

    fun setThemeMode(context: Context, themeMode: AppThemeMode) {
        prefs(context).edit()
            .putString(THEME_MODE, themeMode.storedValue)
            .apply()
    }

    fun advancedMode(context: Context): Boolean =
        prefs(context).getBoolean(ADVANCED_MODE, false)

    fun setAdvancedMode(context: Context, enabled: Boolean) {
        prefs(context).edit()
            .putBoolean(ADVANCED_MODE, enabled)
            .apply()
    }

    fun disableKsuModules(context: Context): Boolean =
        prefs(context).getBoolean(DISABLE_KSU_MODULES, false)

    fun setDisableKsuModules(context: Context, enabled: Boolean) {
        prefs(context).edit()
            .putBoolean(DISABLE_KSU_MODULES, enabled)
            .apply()
    }

    /**
     * Whether a run loads KernelSU once the exploit has root.
     *
     * On by default, because loading it is what this app is for. Off is for a device where the module
     * is deliberately not wanted - another root solution is doing that job, or the load itself is the
     * thing that misbehaves - and the run then ends at the root the exploit won and says so rather
     * than reporting an install it did not make.
     *
     * A preference rather than a per-run choice: everything that depends on the load (root on boot,
     * the recovery actions, keeping the modules out of the way) reads the same answer, so it has to be
     * the same answer for all of them. A run freezes it at its start, like the transport.
     */
    fun loadKernelSu(context: Context): Boolean = prefs(context).getBoolean(LOAD_KERNEL_SU, true)

    fun setLoadKernelSu(context: Context, enabled: Boolean) {
        prefs(context).edit()
            .putBoolean(LOAD_KERNEL_SU, enabled)
            .apply()
    }

    /**
     * Which KernelSU a run installs.
     *
     * An id this build no longer knows falls back to the default rather than refusing: the only way
     * that happens is a downgrade, and a phone that had the other flavour selected is not a reason to
     * show an empty list.
     */
    fun kernelsuFlavor(context: Context): KernelSuFlavor {
        val stored = prefs(context).getString(KERNEL_SU_FLAVOR, null)
        return KernelSuFlavor.fromId(stored) ?: KernelSuFlavor.Default
    }

    fun setKernelsuFlavor(context: Context, flavor: KernelSuFlavor) {
        prefs(context).edit()
            .putString(KERNEL_SU_FLAVOR, flavor.id)
            .apply()
    }

    /**
     * The manager version to offer for [flavor], when the user has named one.
     *
     * Null means the app's own offer, which is what keeps this from being a second place a version is
     * written down: the app stores only the deliberate choice, so an offer that changes - because the
     * payload changed, or because a later build knows a newer release - changes for everyone who never
     * made one.
     */
    fun managerVersion(context: Context, flavor: KernelSuFlavor): String? =
        prefs(context).getString(MANAGER_VERSION_PREFIX + flavor.id, null)
            ?.trim()
            ?.takeIf(String::isNotEmpty)

    fun setManagerVersion(context: Context, flavor: KernelSuFlavor, version: String?) {
        val editor = prefs(context).edit()
        val key = MANAGER_VERSION_PREFIX + flavor.id
        if (version.isNullOrBlank()) editor.remove(key) else editor.putString(key, version.trim())
        editor.apply()
    }

    /**
     * The KernelSU version the payload the app last resolved for [flavor] declares.
     *
     * Written whenever a run resolves its payload, whenever a payload is picked by hand, and whenever
     * one is cached - the three moments the app learns which payload this device will run. It is a
     * record of a decision rather than of the catalog: nothing here re-reads the sources, so a phone
     * with no network still offers the manager that matches the daemon it is holding.
     *
     * Null means no payload has declared one, which is every entry written before the feed carried the
     * field. The manager offer falls back to the flavour's own release there rather than guessing.
     */
    fun payloadKernelSuVersion(context: Context, flavor: KernelSuFlavor): String? =
        prefs(context).getString(PAYLOAD_KERNEL_SU_VERSION_PREFIX + flavor.id, null)
            ?.trim()
            ?.takeIf(String::isNotEmpty)

    fun setPayloadKernelSuVersion(context: Context, flavor: KernelSuFlavor, version: String?) {
        val editor = prefs(context).edit()
        val key = PAYLOAD_KERNEL_SU_VERSION_PREFIX + flavor.id
        if (version.isNullOrBlank()) editor.remove(key) else editor.putString(key, version.trim())
        editor.apply()
    }

    /**
     * The package the app should open for [flavor], when the user named one.
     *
     * Stored because a manager's package cannot always be known ahead of time: KernelSU-Next's
     * spoofed build rewrites its own to three random words on every release, so the only thing that
     * can address it is a choice made on the phone that has it installed.
     */
    fun managerPackage(context: Context, flavor: KernelSuFlavor): String? =
        prefs(context).getString(MANAGER_PACKAGE_PREFIX + flavor.id, null)
            ?.trim()
            ?.takeIf(String::isNotEmpty)

    fun setManagerPackage(context: Context, flavor: KernelSuFlavor, packageName: String?) {
        val editor = prefs(context).edit()
        val key = MANAGER_PACKAGE_PREFIX + flavor.id
        if (packageName.isNullOrBlank()) editor.remove(key) else editor.putString(key, packageName.trim())
        editor.apply()
    }

    /**
     * The flavour this boot has already loaded, or null when nothing was loaded into it.
     *
     * Stored with the boot it happened in, because the promise it feeds is about *this* boot: a
     * late-loaded module is gone after a restart, so a record that outlived one would refuse a run on
     * a phone that is perfectly able to make it.
     */
    fun loadedFlavor(context: Context): KernelSuFlavor? {
        val preferences = prefs(context)
        val boot = preferences.getString(LOADED_FLAVOR_BOOT, null) ?: return null
        if (boot != AutoRootSupport.currentBootToken()) return null
        return KernelSuFlavor.fromId(preferences.getString(LOADED_FLAVOR, null))
    }

    fun setLoadedFlavor(context: Context, flavor: KernelSuFlavor?, bootToken: String?) {
        val editor = prefs(context).edit()
        if (flavor == null || bootToken == null) {
            editor.remove(LOADED_FLAVOR).remove(LOADED_FLAVOR_BOOT)
        } else {
            editor.putString(LOADED_FLAVOR, flavor.id).putString(LOADED_FLAVOR_BOOT, bootToken)
        }
        editor.commit()
    }

    fun shizukuMode(context: Context): Boolean =
        prefs(context).getBoolean(SHIZUKU_MODE, false)

    fun setShizukuMode(context: Context, enabled: Boolean) {
        prefs(context).edit()
            .putBoolean(SHIZUKU_MODE, enabled)
            .apply()
    }

    fun bootRootMode(context: Context): Boolean =
        prefs(context).getBoolean(BOOT_ROOT_MODE, false)

    fun setBootRootMode(context: Context, enabled: Boolean) {
        prefs(context).edit()
            .putBoolean(BOOT_ROOT_MODE, enabled)
            .apply()
    }

    /**
     * Whether a run that loaded KernelSU should hand the userspace over before it reports done.
     *
     * Off by default, because what it does is close everything that is open, and a setting that did
     * that unannounced would cost more than the tap it saves. What it buys when it is on: KernelSU's
     * own soft reboot walks the module lifecycle in its normal order, so a run that started from a
     * phone whose modules were inert ends with them loaded rather than with an instruction to restart.
     */
    fun restartAfterRoot(context: Context): Boolean =
        prefs(context).getBoolean(RESTART_AFTER_ROOT, false)

    fun setRestartAfterRoot(context: Context, enabled: Boolean) {
        prefs(context).edit()
            .putBoolean(RESTART_AFTER_ROOT, enabled)
            .apply()
    }

    /**
     * Arms one retry of the install for the *next* boot, or forgets one.
     *
     * Stored as the boot it was armed in rather than as a flag, because the whole promise is "after a
     * reboot": a run that was armed while the phone was up must not be started by the same kernel boot,
     * or the user would get the same clean-window attempt they declined when they chose to reboot.
     * Passing null is what consumes it.
     */
    fun setRetryAfterReboot(context: Context, armedForBoot: String?) {
        val editor = prefs(context).edit()
        if (armedForBoot == null) {
            editor.remove(RETRY_AFTER_REBOOT)
        } else {
            editor.putString(RETRY_AFTER_REBOOT, armedForBoot)
        }
        // Committed rather than applied: the retry is armed before a reboot is asked for, and an
        // asynchronous write that had not landed would lose the whole decision.
        editor.commit()
    }

    fun retryArmedInBoot(context: Context): String? =
        prefs(context).getString(RETRY_AFTER_REBOOT, null)?.takeIf(String::isNotBlank)

    /** Whether a retry is armed and this is not the boot it was armed in. */
    fun retryPendingForBoot(context: Context, bootToken: String?): Boolean {
        val armed = retryArmedInBoot(context) ?: return false
        return armed != bootToken
    }

    /** Whether a retry is armed at all, whatever boot armed it. */
    fun retryArmed(context: Context): Boolean = retryArmedInBoot(context) != null

    /**
     * How many image partitions a run set read-only in this boot, and zero when this boot set none.
     *
     * The setting says what a *run* would do; this says what one actually did, and the two are not the
     * same question: `blockdev --setro` is cleared by a reboot, so a read-only refusal in a boot that
     * never ran anything cannot be this app's doing, and naming the switch for it would send someone to
     * turn off a protection that is not there. Scoped to the boot it was recorded in for that reason,
     * the same way an armed retry is.
     */
    fun readOnlyProtectedDevices(context: Context, bootToken: String?): Int {
        val storedBoot = prefs(context).getString(READ_ONLY_PROTECTED_BOOT, null) ?: return 0
        if (bootToken == null || storedBoot != bootToken) return 0
        return prefs(context).getInt(READ_ONLY_PROTECTED_DEVICES, 0)
    }

    fun setReadOnlyProtectedDevices(context: Context, bootToken: String?, devices: Int) {
        val editor = prefs(context).edit()
        if (bootToken == null || devices <= 0) {
            editor.remove(READ_ONLY_PROTECTED_BOOT).remove(READ_ONLY_PROTECTED_DEVICES)
        } else {
            editor.putString(READ_ONLY_PROTECTED_BOOT, bootToken)
                .putInt(READ_ONLY_PROTECTED_DEVICES, devices)
        }
        // Committed, not applied: what this records is used to decide whether a failure gets blamed on
        // the protection, and a write that had not landed would answer "not this boot" about a boot that
        // did set devices.
        editor.commit()
    }

    /**
     * Where a run takes its payload from. Online is the default because it is the mode that follows
     * the sources the user configured; Offline is what makes a run possible with no network.
     */
    fun payloadMode(context: Context): PayloadMode {
        val stored = prefs(context).getString(PAYLOAD_MODE, PayloadMode.Online.name)
        return PayloadMode.entries.firstOrNull { it.name == stored } ?: PayloadMode.Online
    }

    fun setPayloadMode(context: Context, mode: PayloadMode) {
        prefs(context).edit()
            .putString(PAYLOAD_MODE, mode.name)
            .apply()
    }

    fun rootMethod(context: Context): RootMethod =
        RootMethod.fromStoredValue(prefs(context).getString(ROOT_METHOD, null))

    fun setRootMethod(context: Context, method: RootMethod) {
        prefs(context).edit()
            .putString(ROOT_METHOD, method.storedValue)
            .apply()
    }

    /**
     * How long a run waits after a boot before the exploit starts. See [BootSettle]: the wait is
     * measured from the boot, so it is a floor on the device's uptime and not a delay per run.
     */
    fun bootSettleSeconds(context: Context): Int = BootSettle.normalize(
        prefs(context).getInt(BOOT_SETTLE_SECONDS, BootSettle.DEFAULT_SECONDS),
    )

    fun setBootSettleSeconds(context: Context, seconds: Int) {
        prefs(context).edit()
            .putInt(BOOT_SETTLE_SECONDS, BootSettle.normalize(seconds))
            .apply()
    }

    /**
     * The three ceilings a run is given unless the user chose otherwise.
     *
     * Normalized on the way in as well as on the way out, so a value that is not one of the offered ones
     * is never stored in the first place - the settings only offer the values, and everything downstream
     * is entitled to assume it.
     */
    fun runStallSeconds(context: Context): Int = RunLimits.normalizeStallSeconds(
        prefs(context).getInt(RUN_STALL_SECONDS, RunLimits.DEFAULT_STALL_SECONDS),
    )

    fun setRunStallSeconds(context: Context, seconds: Int) {
        prefs(context).edit()
            .putInt(RUN_STALL_SECONDS, RunLimits.normalizeStallSeconds(seconds))
            .apply()
    }

    fun runTotalSeconds(context: Context): Int = RunLimits.normalizeTotalSeconds(
        prefs(context).getInt(RUN_TOTAL_SECONDS, RunLimits.DEFAULT_TOTAL_SECONDS),
    )

    fun setRunTotalSeconds(context: Context, seconds: Int) {
        prefs(context).edit()
            .putInt(RUN_TOTAL_SECONDS, RunLimits.normalizeTotalSeconds(seconds))
            .apply()
    }

    fun runHelperSeconds(context: Context): Int = RunLimits.normalizeHelperSeconds(
        prefs(context).getInt(RUN_HELPER_SECONDS, RunLimits.DEFAULT_HELPER_SECONDS),
    )

    fun setRunHelperSeconds(context: Context, seconds: Int) {
        prefs(context).edit()
            .putInt(RUN_HELPER_SECONDS, RunLimits.normalizeHelperSeconds(seconds))
            .apply()
    }

    /** The three ceilings as one value, which is how every reader wants them. */
    internal fun runLimits(context: Context): RunLimitsSettings = RunLimitsSettings(
        totalSeconds = runTotalSeconds(context),
        stallSeconds = runStallSeconds(context),
        helperSeconds = runHelperSeconds(context),
    )

    internal fun setRunLimit(context: Context, limit: RunLimit, seconds: Int) {
        when (limit) {
            RunLimit.Total -> setRunTotalSeconds(context, seconds)
            RunLimit.Stall -> setRunStallSeconds(context, seconds)
            RunLimit.Helper -> setRunHelperSeconds(context, seconds)
        }
    }

    /**
     * The app's own numbers for the payload's exploit, off by default.
     *
     * Normalized on the way in and on the way out, like the three ceilings and for the same reason: the
     * settings only offer the values, and the run and the run plan are entitled to assume that a stored
     * number is one a user could have picked.
     */
    internal fun exploitOverride(context: Context): ExploitOverrideSettings {
        val stored = prefs(context)
        return ExploitOverrideSettings(
            enabled = stored.getBoolean(EXPLOIT_OVERRIDE_ENABLED, false),
            attempts = ExploitOverride.normalizeAttempts(
                stored.getInt(EXPLOIT_OVERRIDE_ATTEMPTS, ExploitRoutePolicy.DEFAULT_ATTEMPTS),
            ),
            attemptTimeoutSec = ExploitOverride.normalizeTimeout(
                stored.getInt(
                    EXPLOIT_OVERRIDE_ATTEMPT_TIMEOUT,
                    ExploitRoutePolicy.DEFAULT_ATTEMPT_TIMEOUT_SEC,
                ),
            ),
            slideRoute = ExploitOverride.normalizeRoute(
                SlideRoute.parse(stored.getString(EXPLOIT_OVERRIDE_SLIDE_ROUTE, null)),
            ),
        )
    }

    internal fun setExploitOverride(context: Context, override: ExploitOverrideSettings) {
        prefs(context).edit()
            .putBoolean(EXPLOIT_OVERRIDE_ENABLED, override.enabled)
            .putInt(EXPLOIT_OVERRIDE_ATTEMPTS, ExploitOverride.normalizeAttempts(override.attempts))
            .putInt(
                EXPLOIT_OVERRIDE_ATTEMPT_TIMEOUT,
                ExploitOverride.normalizeTimeout(override.attemptTimeoutSec),
            )
            .putString(
                EXPLOIT_OVERRIDE_SLIDE_ROUTE,
                ExploitOverride.normalizeRoute(override.slideRoute).name,
            )
            .apply()
    }

    /**
     * The same floor for the automatic install, stored separately on purpose.
     *
     * See [BootSettle.AUTO_ROOT_DEFAULT_SECONDS]: an automatic run has already waited out the boot
     * before it can act, so it needs a shorter floor than a manual one - and someone tuning it must not
     * be changing the wait a manual run does.
     */
    fun autoRootSettleSeconds(context: Context): Int = BootSettle.normalize(
        prefs(context).getInt(AUTO_ROOT_SETTLE_SECONDS, BootSettle.AUTO_ROOT_DEFAULT_SECONDS),
    )

    fun setAutoRootSettleSeconds(context: Context, seconds: Int) {
        prefs(context).edit()
            .putInt(AUTO_ROOT_SETTLE_SECONDS, BootSettle.normalize(seconds))
            .apply()
    }

    /**
     * The token a Shizuku build may require before it honours an authenticated start request.
     *
     * Stored only so the app can include it in that one broadcast, and never written to the log or to
     * the run history: it is the credential that lets this app ask for a privileged process to be
     * started, so it is treated like one.
     */
    fun shizukuAutomationToken(context: Context): String =
        prefs(context).getString(SHIZUKU_AUTOMATION_TOKEN, "").orEmpty()

    fun setShizukuAutomationToken(context: Context, token: String) {
        prefs(context).edit()
            .putString(SHIZUKU_AUTOMATION_TOKEN, token.trim())
            .apply()
    }

    /**
     * Whether a run marks the image partitions read-only once bootstrap root is in hand.
     *
     * On unless turned off: see [PartitionReadOnly] - it guards the window between bootstrap root
     * and the first verified boot, which is where a mistaken write leaves a device that has to be
     * recovered from download mode. Turning it off is a decision about the device, so an existing
     * choice is never overwritten: a stored value wins over this default.
     */
    fun partitionReadOnlyMode(context: Context): Boolean =
        prefs(context).getBoolean(PARTITION_READ_ONLY_MODE, true)

    fun setPartitionReadOnlyMode(context: Context, enabled: Boolean) {
        prefs(context).edit()
            .putBoolean(PARTITION_READ_ONLY_MODE, enabled)
            .apply()
    }

    /**
     * Whether a wireless-debugging pairing has ever succeeded.
     *
     * A record of an event, not a statement about now: adbd can forget this app's key without
     * anything here changing, so anything that depends on the transport asks the device instead of
     * reading this.
     */
    fun adbPaired(context: Context): Boolean = prefs(context).getBoolean(ADB_PAIRED, false)

    fun setAdbPaired(context: Context, paired: Boolean) {
        prefs(context).edit().putBoolean(ADB_PAIRED, paired).apply()
    }

    /**
     * Whether the wireless-debugging switch is on because this app turned it on.
     *
     * Persisted, not held in memory: the process that flipped it is often gone by the time the failsafe
     * alarm turns it back off, and a flag that died with that process would leave the switch on with
     * nobody left to restore it. It is also what keeps the app off a session the user started
     * themselves - the same setting serves a cable-free adb session this app knows nothing about.
     */
    fun wirelessAdbOwnedByApp(context: Context): Boolean =
        prefs(context).getBoolean(WIRELESS_ADB_OURS, false)

    fun setWirelessAdbOwnedByApp(context: Context, owned: Boolean) {
        prefs(context).edit()
            .putBoolean(WIRELESS_ADB_OURS, owned)
            .commit()
    }

    /** Whether Shizuku is started at boot through KernelSU, once the device already has root. */
    fun shizukuBootMode(context: Context): Boolean =
        prefs(context).getBoolean(SHIZUKU_BOOT_MODE, false)

    fun setShizukuBootMode(context: Context, enabled: Boolean) {
        prefs(context).edit()
            .putBoolean(SHIZUKU_BOOT_MODE, enabled)
            .apply()
    }

    /**
     * Whether the one-shot battery-optimisation prompt has been shown. Persisted so declining it
     * is a decision rather than something the app re-asks on every launch.
     */
    fun batteryPromptShown(context: Context): Boolean =
        prefs(context).getBoolean(BATTERY_PROMPT_SHOWN, false)

    fun setBatteryPromptShown(context: Context, shown: Boolean) {
        prefs(context).edit()
            .putBoolean(BATTERY_PROMPT_SHOWN, shown)
            .apply()
    }

    /** Name of the imported payload, kept for display only; the file itself is in app storage. */
    fun localPayloadName(context: Context): String? =
        prefs(context).getString(LOCAL_PAYLOAD_NAME, null)

    fun useLocalPayload(context: Context): Boolean =
        prefs(context).getBoolean(USE_LOCAL_PAYLOAD, LocalPayload.file(context) != null)

    fun setUseLocalPayload(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(USE_LOCAL_PAYLOAD, enabled).apply()
    }

    fun magicUnlockReport(context: Context): String? =
        prefs(context).getString(MAGIC_UNLOCK_REPORT, null)

    fun setMagicUnlockReport(context: Context, path: String?) {
        if (path.isNullOrBlank()) {
            prefs(context).edit().remove(MAGIC_UNLOCK_REPORT).apply()
        } else {
            prefs(context).edit().putString(MAGIC_UNLOCK_REPORT, path).apply()
        }
    }

    fun setLocalPayloadName(context: Context, name: String?) {
        val editor = prefs(context).edit()
        if (name == null) editor.remove(LOCAL_PAYLOAD_NAME) else editor.putString(LOCAL_PAYLOAD_NAME, name)
        editor.apply()
    }

    @Synchronized
    fun consumeInstallRequest(context: Context, requestId: String?): Boolean {
        if (requestId.isNullOrBlank()) return false
        val preferences = prefs(context)
        if (preferences.getString(CONSUMED_INSTALL_REQUEST, null) == requestId) return false
        return preferences.edit()
            .putString(CONSUMED_INSTALL_REQUEST, requestId)
            .commit()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    fun languageTag(context: Context): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val locales = context.getSystemService(LocaleManager::class.java).applicationLocales
            return if (locales.isEmpty) "" else locales[0].toLanguageTag()
        }
        val locales = context.resources.configuration.locales
        return if (locales.isEmpty) "" else locales[0].toLanguageTag()
    }

    fun setLanguage(context: Context, languageTag: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.getSystemService(LocaleManager::class.java).applicationLocales =
                LocaleList.forLanguageTags(languageTag)
            return
        }

        val locale = languageTag
            .takeIf { it.isNotBlank() }
            ?.let(Locale::forLanguageTag)
            ?: Locale.getDefault()
        Locale.setDefault(locale)
        val resources = context.resources
        val configuration = resources.configuration
        configuration.setLocale(locale)
        @Suppress("DEPRECATION")
        resources.updateConfiguration(configuration, resources.displayMetrics)
    }
}
