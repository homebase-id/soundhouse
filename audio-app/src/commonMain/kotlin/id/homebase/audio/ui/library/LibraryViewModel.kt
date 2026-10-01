package id.homebase.audio.ui.library

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import id.homebase.api.youauth.YouAuthFlowManager
import id.homebase.audio.data.AudioTrack
import id.homebase.audio.data.TrackStore
import id.homebase.api.file.FileOperationsProvider
import id.homebase.audio.importing.ImportJob
import co.touchlab.kermit.Logger
import id.homebase.audio.data.TrackManager
import id.homebase.audio.download.DownloadStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
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
    private val downloads: DownloadStore,
    private val manager: TrackManager,
    private val fileOps: FileOperationsProvider,
    private val youAuthFlowManager: YouAuthFlowManager,
) : ViewModel() {
    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<LibraryEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<LibraryEvent> = _events.asSharedFlow()

    private val query = MutableStateFlow("")
    private val sort = MutableStateFlow(LibrarySort.Newest)

    init {
        viewModelScope.launch {
            val library = combine(trackStore.tracks, trackStore.isLoaded, query, sort, importer.jobs) { tracks, loaded, q, s, jobs ->
                LibraryUiState(
                    tracks = arrangeTracks(tracks, q, s),
                    totalTracks = tracks.size,
                    query = q,
                    sort = s,
                    isLoaded = loaded,
                    imports = jobs,
                )
            }
            combine(library, downloads.downloaded, downloads.inProgress, downloads.failures) { state, done, progress, failed ->
                state.copy(downloaded = done, downloadProgress = progress, downloadFailures = failed)
            }.collect { state -> _uiState.update { state } }
        }
        viewModelScope.launch { trackStore.reload() }
    }

    /** Plays [track] with the list as shown (filter and sort applied) as the queue. */
    fun play(track: AudioTrack) {
        val queue = _uiState.value.tracks
        playback.playQueue(queue, queue.indexOf(track).coerceAtLeast(0))
    }

    fun download(track: AudioTrack) = downloads.download(track)

    fun removeDownload(track: AudioTrack) {
        viewModelScope.launch { downloads.remove(track.fileId) }
    }

    fun rename(track: AudioTrack, newTitle: String) {
        viewModelScope.launch {
            runCatching { manager.rename(track, newTitle) }
                .onFailure {
                    if (it is CancellationException) throw it
                    Logger.e(it, TAG) { "Rename of ${track.fileId} failed" }
                    _events.emit(LibraryEvent.RenameFailed)
                }
        }
    }

    fun delete(track: AudioTrack) {
        viewModelScope.launch {
            runCatching { manager.delete(track) }
                .onFailure {
                    if (it is CancellationException) throw it
                    Logger.e(it, TAG) { "Delete of ${track.fileId} failed" }
                    _events.emit(LibraryEvent.DeleteFailed)
                }
        }
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

sealed interface LibraryEvent {
    data object RenameFailed : LibraryEvent
    data object DeleteFailed : LibraryEvent
}

private const val TAG = "LibraryViewModel"

@Immutable
data class LibraryUiState(
    val tracks: List<AudioTrack> = emptyList(),
    val totalTracks: Int = 0,
    val query: String = "",
    val sort: LibrarySort = LibrarySort.Newest,
    val isLoaded: Boolean = false,
    val imports: List<ImportJob> = emptyList(),
    val downloaded: Set<Uuid> = emptySet(),
    val downloadProgress: Map<Uuid, Float> = emptyMap(),
    val downloadFailures: Set<Uuid> = emptySet(),
)
