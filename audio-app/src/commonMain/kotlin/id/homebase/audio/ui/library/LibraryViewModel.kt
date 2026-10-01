package id.homebase.audio.ui.library

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import id.homebase.api.youauth.YouAuthFlowManager
import id.homebase.audio.data.AudioTrack
import id.homebase.audio.data.TrackStore
import id.homebase.api.file.FileOperationsProvider
import id.homebase.audio.importing.ImportJob
import id.homebase.audio.importing.TrackImporter
import id.homebase.audio.playback.PlaybackController
import id.homebase.core.files.materializeForUpload
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.name
import io.github.vinceglb.filekit.path
import kotlin.uuid.Uuid
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class LibraryViewModel(
    private val trackStore: TrackStore,
    private val importer: TrackImporter,
    private val playback: PlaybackController,
    private val fileOps: FileOperationsProvider,
    private val youAuthFlowManager: YouAuthFlowManager,
) : ViewModel() {
    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    private val query = MutableStateFlow("")
    private val sort = MutableStateFlow(LibrarySort.Newest)

    init {
        viewModelScope.launch {
            combine(trackStore.tracks, trackStore.isLoaded, query, sort, importer.jobs) { tracks, loaded, q, s, jobs ->
                LibraryUiState(
                    tracks = arrangeTracks(tracks, q, s),
                    totalTracks = tracks.size,
                    query = q,
                    sort = s,
                    isLoaded = loaded,
                    imports = jobs,
                )
            }.collect { state -> _uiState.update { state } }
        }
        viewModelScope.launch { trackStore.reload() }
    }

    /** Plays [track] with the list as shown (filter and sort applied) as the queue. */
    fun play(track: AudioTrack) {
        val queue = _uiState.value.tracks
        playback.playQueue(queue, queue.indexOf(track).coerceAtLeast(0))
    }

    fun onQueryChange(value: String) {
        query.value = value
    }

    fun onSortChange(value: LibrarySort) {
        sort.value = value
    }

    fun onFilesPicked(files: List<PlatformFile>) {
        viewModelScope.launch {
            files.forEach { picked ->
                // Copy while the picker's read grant is live (Android URI grant, iOS security scope).
                val copy = picked.materializeForUpload(fileOps)
                importer.enqueue(copy.path, picked.name, deleteSourceAfter = copy.path != picked.path)
            }
        }
    }

    fun dismissImport(id: Uuid) = importer.dismiss(id)

    fun clearFinishedImports() = importer.clearFinished()

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
    val imports: List<ImportJob> = emptyList(),
)
