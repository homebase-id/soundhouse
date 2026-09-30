package id.homebase.audio.ui.library

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import id.homebase.api.youauth.YouAuthFlowManager
import id.homebase.audio.data.AudioTrack
import id.homebase.audio.data.TrackStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class LibraryViewModel(
    private val trackStore: TrackStore,
    private val youAuthFlowManager: YouAuthFlowManager,
) : ViewModel() {
    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    private val query = MutableStateFlow("")
    private val sort = MutableStateFlow(LibrarySort.Newest)

    init {
        viewModelScope.launch {
            combine(trackStore.tracks, trackStore.isLoaded, query, sort) { tracks, loaded, q, s ->
                LibraryUiState(
                    tracks = arrangeTracks(tracks, q, s),
                    totalTracks = tracks.size,
                    query = q,
                    sort = s,
                    isLoaded = loaded,
                )
            }.collect { state -> _uiState.update { state } }
        }
        viewModelScope.launch { trackStore.reload() }
    }

    fun onQueryChange(value: String) {
        query.value = value
    }

    fun onSortChange(value: LibrarySort) {
        sort.value = value
    }

    fun signOut() {
        viewModelScope.launch { youAuthFlowManager.logout() }
    }
}

@Immutable
data class LibraryUiState(
    val tracks: List<AudioTrack> = emptyList(),
    val totalTracks: Int = 0,
    val query: String = "",
    val sort: LibrarySort = LibrarySort.Newest,
    val isLoaded: Boolean = false,
)
