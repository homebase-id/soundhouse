package id.homebase.audio.ui.player

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import id.homebase.audio.playback.PlaybackController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class PlayerViewModel(private val controller: PlaybackController) : ViewModel() {
    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            controller.state.collect { state ->
                _uiState.update {
                    PlayerUiState(
                        title = state.current?.title,
                        isPlaying = state.isPlaying,
                        isLoading = state.isLoading,
                        failed = state.failed,
                        positionMs = state.positionMs,
                        durationMs = state.durationMs,
                        hasNext = state.hasNext,
                        hasPrevious = state.current != null,
                        trackNumber = state.index + 1,
                        trackCount = state.queue.size,
                    )
                }
            }
        }
    }

    fun togglePlayPause() = controller.togglePlayPause()
    fun seekTo(positionMs: Long) = controller.seekTo(positionMs)
    fun next() = controller.next()
    fun previous() = controller.previous()
}

@Immutable
data class PlayerUiState(
    val title: String? = null,
    val isPlaying: Boolean = false,
    val isLoading: Boolean = false,
    val failed: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val hasNext: Boolean = false,
    val hasPrevious: Boolean = false,
    val trackNumber: Int = 0,
    val trackCount: Int = 0,
)
