package id.homebase.soundhouse.ui.player

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import id.homebase.soundhouse.data.AudioTrack
import id.homebase.soundhouse.data.TrackOrigin
import id.homebase.soundhouse.importing.AudioQuality
import id.homebase.soundhouse.playback.PlaybackController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import id.homebase.soundhouse.playback.SleepTimer
import id.homebase.soundhouse.settings.AudioSettings
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlin.time.Clock

class PlayerViewModel(
    private val controller: PlaybackController,
    private val settings: AudioSettings,
    private val now: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) : ViewModel() {
    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            controller.state.collect { state ->
                _uiState.update {
                    PlayerUiState(
                        track = state.current,
                        title = state.current?.title,
                        artworkSeed = state.current?.fileId?.toString(),
                        quality = state.current?.displayQuality,
                        dateAddedMs = state.current?.dateAddedMs,
                        credits = state.current?.details?.creditLine,
                        recorded = state.current?.content?.origin == TrackOrigin.Recorded,
                        isPlaying = state.isPlaying,
                        isLoading = state.isLoading,
                        failed = state.failed,
                        positionMs = state.positionMs,
                        durationMs = state.durationMs,
                        hasNext = state.hasNext,
                        hasPrevious = state.current != null,
                        trackNumber = state.index + 1,
                        trackCount = state.queue.size,
                        speed = state.speed,
                        sleepAtEndOfTrack = state.sleepTimer == SleepTimer.EndOfTrack,
                        sleepRemainingMs = it.sleepRemainingMs,
                    )
                }
            }
        }
        // A once-a-second countdown, only while a timed sleep is set.
        viewModelScope.launch {
            controller.state.map { it.sleepTimer }.distinctUntilChanged().collectLatest { timer ->
                if (timer !is SleepTimer.At) {
                    _uiState.update { it.copy(sleepRemainingMs = null) }
                    return@collectLatest
                }
                while (true) {
                    _uiState.update { it.copy(sleepRemainingMs = (timer.endsAtMs - now()).coerceAtLeast(0)) }
                    delay(1_000)
                }
            }
        }
    }

    fun togglePlayPause() = controller.togglePlayPause()
    fun seekTo(positionMs: Long) = controller.seekTo(positionMs)
    fun next() = controller.next()
    fun previous() = controller.previous()
    fun skipBack() = controller.skipBy(-SKIP_BACK_MS)
    fun skipForward() = controller.skipBy(SKIP_FORWARD_MS)

    fun setSpeed(speed: Float) {
        controller.setSpeed(speed)
        settings.update { it.copy(playbackSpeed = speed) }
    }

    fun sleepAfterMinutes(minutes: Int?) = controller.sleepAfter(minutes?.let { it * 60_000L })
    fun sleepAtEndOfTrack() = controller.sleepAtEndOfTrack()

    companion object {
        const val SKIP_BACK_MS = 10_000L
        const val SKIP_FORWARD_MS = 30_000L
        val SPEEDS = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)
        val SLEEP_MINUTES = listOf(15, 30, 45, 60)
    }
}

@Immutable
data class PlayerUiState(
    val track: AudioTrack? = null,
    val title: String? = null,
    val artworkSeed: String? = null,
    val quality: AudioQuality? = null,
    val dateAddedMs: Long? = null,
    val credits: String? = null,
    val recorded: Boolean = false,
    val isPlaying: Boolean = false,
    val isLoading: Boolean = false,
    val failed: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val hasNext: Boolean = false,
    val hasPrevious: Boolean = false,
    val trackNumber: Int = 0,
    val trackCount: Int = 0,
    val speed: Float = 1f,
    val sleepRemainingMs: Long? = null,
    val sleepAtEndOfTrack: Boolean = false,
)
