package id.homebase.soundhouse.ui.settings

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import id.homebase.soundhouse.data.TrackStore
import id.homebase.soundhouse.download.DownloadStore
import id.homebase.soundhouse.download.OfflineKeeper
import id.homebase.soundhouse.settings.AudioPreferences
import id.homebase.soundhouse.settings.AudioSettings
import id.homebase.soundhouse.settings.Skin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val settings: AudioSettings,
    trackStore: TrackStore,
    downloads: DownloadStore,
    offline: OfflineKeeper,
) : ViewModel() {
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(settings.preferences, trackStore.tracks, downloads.downloaded, offline.autoKept) { prefs, tracks, done, auto ->
                val onDevice = tracks.filter { it.fileId in done }
                SettingsUiState(
                    preferences = prefs,
                    automaticBytes = onDevice.filter { it.fileId in auto }.sumOf { it.sizeBytes },
                    ownBytes = onDevice.filterNot { it.fileId in auto }.sumOf { it.sizeBytes },
                )
            }.collect { state -> _uiState.update { state } }
        }
    }

    fun setKeepRecent(value: Boolean) = settings.update { it.copy(keepRecentOffline = value) }
    fun setLimit(bytes: Long) = settings.update { it.copy(offlineLimitBytes = bytes) }
    fun setWifiOnly(value: Boolean) = settings.update { it.copy(offlineOnWifiOnly = value) }
    fun setUploadsAtOnce(value: Int) = settings.update { it.copy(uploadsAtOnce = value) }
    fun setSkin(value: Skin) = settings.update { it.copy(skin = value) }

    companion object {
        val LIMITS_GB = listOf(1, 2, 5, 10)
        val UPLOADS_AT_ONCE = listOf(1, 2, 3, 5)
        const val GB = 1024L * 1024 * 1024
    }
}

@Immutable
data class SettingsUiState(
    val preferences: AudioPreferences = AudioPreferences(),
    val automaticBytes: Long = 0,
    val ownBytes: Long = 0,
)
