package ctrl.mietze.veyraroot

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import android.os.SystemClock

internal enum class UiSoundKind {
    Page,
    Settings,
    Button,
    Expand,
}

internal object UiSoundEngine {
    private data class SoundKey(
        val profile: UiSoundProfile,
        val kind: UiSoundKind,
        val legacyV1: Boolean,
    )

    private var pool: SoundPool? = null
    private val soundIds = mutableMapOf<SoundKey, Int>()
    private val loadedIds = mutableSetOf<Int>()
    private val activeStreams = mutableMapOf<UiSoundKind, Int>()
    private val lastPlayedAt = mutableMapOf<UiSoundKind, Long>()
    private var lastGlobalPlayAt = 0L

    @Synchronized
    fun preload(context: Context) {
        ensure(context.applicationContext)
    }

    @Synchronized
    fun play(context: Context, kind: UiSoundKind) {
        val app = context.applicationContext
        if (!AppPreferences.uiSoundsEnabled(app)) return
        if (!categoryEnabled(app, kind)) return

        if (!AppPreferences.uiSoundWithoutRinger(app)) {
            val audio = app.getSystemService(AudioManager::class.java)
            val mode = audio?.ringerMode ?: AudioManager.RINGER_MODE_NORMAL
            if (mode != AudioManager.RINGER_MODE_NORMAL) return
        }

        val now = SystemClock.elapsedRealtime()
        val cooldown = when (kind) {
            UiSoundKind.Page -> 190L
            UiSoundKind.Settings -> 115L
            UiSoundKind.Button -> 48L
            UiSoundKind.Expand -> 90L
        }
        val last = lastPlayedAt[kind] ?: 0L
        if (now - last < cooldown || now - lastGlobalPlayAt < 28L) return

        val soundPool = ensure(app)
        val selectedProfile = AppPreferences.uiSoundProfile(app)
        val profile = if (selectedProfile == UiSoundProfile.MyHome) {
            AppPreferences.uiSoundProfileForKind(app, kind)
        } else {
            selectedProfile
        }
        val legacyV1 = AppPreferences.uiSoundLegacyV1(app)
        val id = soundIds[SoundKey(profile, kind, legacyV1)] ?: return
        if (id !in loadedIds) return

        if (kind != UiSoundKind.Button) {
            activeStreams.remove(kind)?.takeIf { it != 0 }?.let(soundPool::stop)
        }

        val volume = when (kind) {
            UiSoundKind.Page -> 0.58f
            UiSoundKind.Settings -> 0.42f
            UiSoundKind.Button -> 0.30f
            UiSoundKind.Expand -> 0.34f
        }
        val stream = soundPool.play(id, volume, volume, 1, 0, 1.0f)
        if (stream != 0) activeStreams[kind] = stream
        lastPlayedAt[kind] = now
        lastGlobalPlayAt = now
    }

    @Synchronized
    fun preview(
        context: Context,
        profile: UiSoundProfile,
        kind: UiSoundKind = UiSoundKind.Settings,
    ) {
        val app = context.applicationContext
        val soundPool = ensure(app)
        val effective = if (profile == UiSoundProfile.MyHome) {
            AppPreferences.uiSoundProfileForKind(app, kind)
        } else {
            profile
        }
        val legacyV1 = AppPreferences.uiSoundLegacyV1(app)
        val id = soundIds[SoundKey(effective, kind, legacyV1)] ?: return
        if (id !in loadedIds) return
        activeStreams.remove(kind)?.takeIf { it != 0 }?.let(soundPool::stop)
        val stream = soundPool.play(id, 0.46f, 0.46f, 1, 0, 1.0f)
        if (stream != 0) activeStreams[kind] = stream
    }

    @Synchronized
    fun release() {
        pool?.release()
        pool = null
        soundIds.clear()
        loadedIds.clear()
        activeStreams.clear()
        lastPlayedAt.clear()
        lastGlobalPlayAt = 0L
    }

    private fun categoryEnabled(context: Context, kind: UiSoundKind): Boolean = when (kind) {
        UiSoundKind.Page -> AppPreferences.uiSoundPages(context)
        UiSoundKind.Settings -> AppPreferences.uiSoundSettings(context)
        UiSoundKind.Button -> AppPreferences.uiSoundButtons(context)
        UiSoundKind.Expand -> AppPreferences.uiSoundExpand(context)
    }

    private fun ensure(context: Context): SoundPool {
        pool?.let { return it }
        val created = SoundPool.Builder()
            .setMaxStreams(3)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            .build()
        created.setOnLoadCompleteListener { _, sampleId, status ->
            if (status == 0) synchronized(this) { loadedIds += sampleId }
        }
        fun add(
            profile: UiSoundProfile,
            kind: UiSoundKind,
            res: Int,
            legacyV1: Boolean,
        ) {
            soundIds[SoundKey(profile, kind, legacyV1)] = created.load(context, res, 1)
        }
        add(UiSoundProfile.Veyra, UiSoundKind.Page, R.raw.veyra_page, legacyV1 = true)
        add(UiSoundProfile.Veyra, UiSoundKind.Settings, R.raw.veyra_settings, legacyV1 = true)
        add(UiSoundProfile.Veyra, UiSoundKind.Button, R.raw.veyra_button, legacyV1 = true)
        add(UiSoundProfile.Veyra, UiSoundKind.Expand, R.raw.veyra_expand, legacyV1 = true)
        add(UiSoundProfile.Soft, UiSoundKind.Page, R.raw.soft_page, legacyV1 = true)
        add(UiSoundProfile.Soft, UiSoundKind.Settings, R.raw.soft_settings, legacyV1 = true)
        add(UiSoundProfile.Soft, UiSoundKind.Button, R.raw.soft_button, legacyV1 = true)
        add(UiSoundProfile.Soft, UiSoundKind.Expand, R.raw.soft_expand, legacyV1 = true)
        add(UiSoundProfile.Glass, UiSoundKind.Page, R.raw.glass_page, legacyV1 = true)
        add(UiSoundProfile.Glass, UiSoundKind.Settings, R.raw.glass_settings, legacyV1 = true)
        add(UiSoundProfile.Glass, UiSoundKind.Button, R.raw.glass_button, legacyV1 = true)
        add(UiSoundProfile.Glass, UiSoundKind.Expand, R.raw.glass_expand, legacyV1 = true)

        add(UiSoundProfile.Air, UiSoundKind.Page, R.raw.air_page, legacyV1 = true)
        add(UiSoundProfile.Air, UiSoundKind.Settings, R.raw.air_settings, legacyV1 = true)
        add(UiSoundProfile.Air, UiSoundKind.Button, R.raw.air_button, legacyV1 = true)
        add(UiSoundProfile.Air, UiSoundKind.Expand, R.raw.air_expand, legacyV1 = true)

        add(UiSoundProfile.Velvet, UiSoundKind.Page, R.raw.velvet_page, legacyV1 = true)
        add(UiSoundProfile.Velvet, UiSoundKind.Settings, R.raw.velvet_settings, legacyV1 = true)
        add(UiSoundProfile.Velvet, UiSoundKind.Button, R.raw.velvet_button, legacyV1 = true)
        add(UiSoundProfile.Velvet, UiSoundKind.Expand, R.raw.velvet_expand, legacyV1 = true)

        add(UiSoundProfile.Halo, UiSoundKind.Page, R.raw.halo_page, legacyV1 = true)
        add(UiSoundProfile.Halo, UiSoundKind.Settings, R.raw.halo_settings, legacyV1 = true)
        add(UiSoundProfile.Halo, UiSoundKind.Button, R.raw.halo_button, legacyV1 = true)
        add(UiSoundProfile.Halo, UiSoundKind.Expand, R.raw.halo_expand, legacyV1 = true)

        add(UiSoundProfile.Pulse, UiSoundKind.Page, R.raw.pulse_page, legacyV1 = true)
        add(UiSoundProfile.Pulse, UiSoundKind.Settings, R.raw.pulse_settings, legacyV1 = true)
        add(UiSoundProfile.Pulse, UiSoundKind.Button, R.raw.pulse_button, legacyV1 = true)
        add(UiSoundProfile.Pulse, UiSoundKind.Expand, R.raw.pulse_expand, legacyV1 = true)

        add(UiSoundProfile.Crystal, UiSoundKind.Page, R.raw.crystal_page, legacyV1 = true)
        add(UiSoundProfile.Crystal, UiSoundKind.Settings, R.raw.crystal_settings, legacyV1 = true)
        add(UiSoundProfile.Crystal, UiSoundKind.Button, R.raw.crystal_button, legacyV1 = true)
        add(UiSoundProfile.Crystal, UiSoundKind.Expand, R.raw.crystal_expand, legacyV1 = true)

        add(UiSoundProfile.Nova, UiSoundKind.Page, R.raw.nova_page, legacyV1 = true)
        add(UiSoundProfile.Nova, UiSoundKind.Settings, R.raw.nova_settings, legacyV1 = true)
        add(UiSoundProfile.Nova, UiSoundKind.Button, R.raw.nova_button, legacyV1 = true)
        add(UiSoundProfile.Nova, UiSoundKind.Expand, R.raw.nova_expand, legacyV1 = true)

        add(UiSoundProfile.Minimal, UiSoundKind.Page, R.raw.minimal_page, legacyV1 = true)
        add(UiSoundProfile.Minimal, UiSoundKind.Settings, R.raw.minimal_settings, legacyV1 = true)
        add(UiSoundProfile.Minimal, UiSoundKind.Button, R.raw.minimal_button, legacyV1 = true)
        add(UiSoundProfile.Minimal, UiSoundKind.Expand, R.raw.minimal_expand, legacyV1 = true)

        add(UiSoundProfile.Aero, UiSoundKind.Page, R.raw.aero_page, legacyV1 = true)
        add(UiSoundProfile.Aero, UiSoundKind.Settings, R.raw.aero_settings, legacyV1 = true)
        add(UiSoundProfile.Aero, UiSoundKind.Button, R.raw.aero_button, legacyV1 = true)
        add(UiSoundProfile.Aero, UiSoundKind.Expand, R.raw.aero_expand, legacyV1 = true)

        add(UiSoundProfile.Obsidian, UiSoundKind.Page, R.raw.obsidian_page, legacyV1 = true)
        add(UiSoundProfile.Obsidian, UiSoundKind.Settings, R.raw.obsidian_settings, legacyV1 = true)
        add(UiSoundProfile.Obsidian, UiSoundKind.Button, R.raw.obsidian_button, legacyV1 = true)
        add(UiSoundProfile.Obsidian, UiSoundKind.Expand, R.raw.obsidian_expand, legacyV1 = true)

        add(UiSoundProfile.Silk, UiSoundKind.Page, R.raw.silk_page, legacyV1 = true)
        add(UiSoundProfile.Silk, UiSoundKind.Settings, R.raw.silk_settings, legacyV1 = true)
        add(UiSoundProfile.Silk, UiSoundKind.Button, R.raw.silk_button, legacyV1 = true)
        add(UiSoundProfile.Silk, UiSoundKind.Expand, R.raw.silk_expand, legacyV1 = true)


        // No default V2 audio bank. V1 is opt-in through "Switch to V1".

        pool = created
        return created
    }
}
