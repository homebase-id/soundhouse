package id.homebase.soundhouse.ui.library

import id.homebase.soundhouse.importing.TrackDetails
import id.homebase.soundhouse.playback.MetadataBackfill
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import id.homebase.soundhouse.data.AudioTrack
import id.homebase.soundhouse.data.TrackStore
import id.homebase.api.file.FileOperationsProvider
import id.homebase.soundhouse.importing.ImportJob
import co.touchlab.kermit.Logger
import id.homebase.soundhouse.data.TrackManager
import id.homebase.soundhouse.data.CollectionManager
import id.homebase.soundhouse.data.CollectionStore
import id.homebase.soundhouse.ui.collections.CollectionSummary
import id.homebase.soundhouse.ui.collections.summarize
import id.homebase.soundhouse.download.DownloadStore
import id.homebase.soundhouse.download.OfflineKeeper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import id.homebase.soundhouse.importing.TrackImporter
import id.homebase.soundhouse.playback.PlaybackController
import id.homebase.soundhouse.importing.enqueuePicked
import io.github.vinceglb.filekit.PlatformFile
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
    private val offline: OfflineKeeper,
    private val manager: TrackManager,
    private val fileOps: FileOperationsProvider,
    private val collectionStore: CollectionStore,
    private val collectionManager: CollectionManager,
    private val backfill: MetadataBackfill,
) : ViewModel() {
    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<LibraryEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<LibraryEvent> = _events.asSharedFlow()

    private val query = MutableStateFlow("")
    private val sort = MutableStateFlow(LibrarySort.Newest)
    private val downloadedOnly = MutableStateFlow(false)

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
            val withDownloads = combine(library, downloads.downloaded, downloads.inProgress, downloads.failures, downloadedOnly) {
                    state, done, progress, failed, onlyDownloaded ->
                state.copy(
                    tracks = if (onlyDownloaded) state.tracks.filter { it.fileId in done } else state.tracks,
                    downloadedOnly = onlyDownloaded,
                    downloaded = done,
                    downloadProgress = progress,
                    downloadFailures = failed,
                )
            }
            val withPlayback = combine(withDownloads, playback.state) { state, playing ->
                state.copy(nowPlayingId = playing.current?.fileId, isPlaying = playing.isPlaying || playing.isLoading)
            }
            combine(withPlayback, collectionStore.collections, trackStore.tracks) { state, collections, tracks ->
                state.copy(collections = summarize(collections, tracks))
            }.collect { state -> _uiState.update { state } }
        }
        viewModelScope.launch { trackStore.reload() }
    }

    /** Plays [track] with the list as shown (filter and sort applied) as the queue. */
    fun play(track: AudioTrack) {
        playback.playFrom(_uiState.value.tracks, track)
    }

    fun download(track: AudioTrack) = offline.keep(track)

    fun removeDownload(track: AudioTrack) = offline.release(track)

    fun readMetadata(track: AudioTrack) = backfill.request(track)

    fun editDetails(track: AudioTrack, title: String, details: TrackDetails) {
        viewModelScope.launch {
            runCatching { manager.editDetails(track, title, details) }
                .onFailure {
                    if (it is CancellationException) throw it
                    Logger.e(it, TAG) { "Editing ${track.fileId} failed" }
                    _events.emit(LibraryEvent.EditFailed)
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

    fun setCollections(track: AudioTrack, selected: Set<Uuid>) = collectionEdit { collectionManager.setMembership(track, selected) }

    fun createCollection(name: String, withTrack: AudioTrack? = null) = collectionEdit {
        val id = collectionManager.create(name)
        if (withTrack != null) {
            val created = collectionStore.collections.value.firstOrNull { it.id == id } ?: return@collectionEdit
            collectionManager.add(listOf(withTrack), created)
        }
    }

    private fun collectionEdit(block: suspend () -> Unit) {
        viewModelScope.launch {
            runCatching { block() }.onFailure {
                if (it is CancellationException) throw it
                Logger.e(it, TAG) { "Collection update failed" }
                _events.emit(LibraryEvent.CollectionFailed)
            }
        }
    }

    fun onQueryChange(value: String) {
        query.value = value
    }

    fun onDownloadedOnlyChange(value: Boolean) {
        downloadedOnly.value = value
    }

    fun onSortChange(value: LibrarySort) {
        sort.value = value
    }

    fun onFilesPicked(files: List<PlatformFile>) {
        viewModelScope.launch { enqueuePicked(files, importer, fileOps) }
    }

    fun dismissImport(id: Uuid) = importer.dismiss(id)

    fun retryImport(id: Uuid) = importer.retry(id)

    fun clearFinishedImports() = importer.clearFinished()
}

sealed interface LibraryEvent {
    data object EditFailed : LibraryEvent
    data object DeleteFailed : LibraryEvent
    data object CollectionFailed : LibraryEvent
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
    val downloadedOnly: Boolean = false,
    val nowPlayingId: Uuid? = null,
    val isPlaying: Boolean = false,
    val collections: List<CollectionSummary> = emptyList(),
)
