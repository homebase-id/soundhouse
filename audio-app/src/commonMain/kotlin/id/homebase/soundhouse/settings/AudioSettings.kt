package id.homebase.soundhouse.settings

import id.homebase.api.storage.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class Skin { Standard, Aluminium }

data class AudioPreferences(
    val playbackSpeed: Float = 1f,
    val keepRecentOffline: Boolean = true,
    val offlineLimitBytes: Long = 2L * 1024 * 1024 * 1024,
    val offlineOnWifiOnly: Boolean = true,
    val uploadsAtOnce: Int = 3,
    val skin: Skin = Skin.Standard,
)

/** Device-local settings. */
interface AudioSettings {
    val preferences: StateFlow<AudioPreferences>
    fun update(change: (AudioPreferences) -> AudioPreferences)
}

class InMemoryAudioSettings(initial: AudioPreferences = AudioPreferences()) : AudioSettings {
    private val state = MutableStateFlow(initial)
    override val preferences: StateFlow<AudioPreferences> = state.asStateFlow()
    override fun update(change: (AudioPreferences) -> AudioPreferences) = state.update(change)
}

class StoredAudioSettings : AudioSettings {
    private val state = MutableStateFlow(load())
    override val preferences: StateFlow<AudioPreferences> = state.asStateFlow()

    override fun update(change: (AudioPreferences) -> AudioPreferences) {
        state.update(change)
        val saved = state.value
        SharedPreferences.putFloat(SPEED, saved.playbackSpeed)
        SharedPreferences.putBoolean(KEEP_RECENT, saved.keepRecentOffline)
        SharedPreferences.putLong(LIMIT, saved.offlineLimitBytes)
        SharedPreferences.putBoolean(WIFI_ONLY, saved.offlineOnWifiOnly)
        SharedPreferences.putLong(UPLOADS_AT_ONCE, saved.uploadsAtOnce.toLong())
        SharedPreferences.putString(SKIN, saved.skin.name)
    }

    private fun load(): AudioPreferences {
        val defaults = AudioPreferences()
        return AudioPreferences(
            playbackSpeed = SharedPreferences.getFloat(SPEED, defaults.playbackSpeed),
            keepRecentOffline = SharedPreferences.getBoolean(KEEP_RECENT, defaults.keepRecentOffline),
            offlineLimitBytes = SharedPreferences.getLong(LIMIT, defaults.offlineLimitBytes),
            offlineOnWifiOnly = SharedPreferences.getBoolean(WIFI_ONLY, defaults.offlineOnWifiOnly),
            uploadsAtOnce = SharedPreferences.getLong(UPLOADS_AT_ONCE, defaults.uploadsAtOnce.toLong()).toInt(),
            skin = Skin.entries.firstOrNull { it.name == SharedPreferences.getString(SKIN) } ?: defaults.skin,
        )
    }

    private companion object {
        const val SPEED = "audio.playback_speed"
        const val KEEP_RECENT = "audio.offline_keep_recent"
        const val LIMIT = "audio.offline_limit_bytes"
        const val WIFI_ONLY = "audio.offline_wifi_only"
        const val UPLOADS_AT_ONCE = "audio.uploads_at_once"
        const val SKIN = "audio.skin"
    }
}
