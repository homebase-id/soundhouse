package id.homebase.audio.ui.home

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import id.homebase.api.file.FileOperationsProvider
import id.homebase.audio.data.AudioTrack
import id.homebase.audio.data.TrackStore
import id.homebase.audio.history.ListenEntry
import id.homebase.audio.history.ListeningHistory
import id.homebase.audio.importing.ImportJob
import id.homebase.audio.data.CollectionStore
import id.homebase.audio.ui.collections.CollectionSummary
import id.homebase.audio.ui.collections.summarize
import id.homebase.audio.importing.TrackImporter
import id.homebase.audio.importing.enqueuePicked
import id.homebase.audio.playback.PlaybackController
import io.github.vinceglb.filekit.PlatformFile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.uuid.Uuid

data class ListenedTrack(val track: AudioTrack, val entry: ListenEntry)

/** Joins listening history with the library; entries for tracks no longer in the library drop out. */
fun dashboardSections(
    tracks: List<AudioTrack>,
    history: Map<String, ListenEntry>,
): Triple<List<ListenedTrack>, List<ListenedTrack>, List<AudioTrack>> {
    val byId = tracks.associateBy { it.fileId.toString() }
    val listened = history.values
        .mapNotNull { entry -> byId[entry.fileId]?.let { ListenedTrack(it, entry) } }
        .sortedByDescending { it.entry.lastPlayedMs }
    val continueListening = listened.filter { it.entry.resumable }.take(SECTION_SIZE)
    val recentlyPlayed = listened.take(RECENT_SIZE)
    val recentlyAdded = tracks.sortedByDescending { it.dateAddedMs }.take(SECTION_SIZE)
    return Triple(continueListening, recentlyPlayed, recentlyAdded)
}

private const val SECTION_SIZE = 10
private const val RECENT_SIZE = 6

class HomeViewModel(
    private val trackStore: TrackStore,
    private val history: ListeningHistory,
    private val playback: PlaybackController,
    private val importer: TrackImporter,
    private val fileOps: FileOperationsProvider,
    private val collectionStore: CollectionStore,
) : ViewModel() {
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val dashboard = combine(trackStore.tracks, trackStore.isLoaded, history.entries, playback.state, importer.jobs) { tracks, loaded, entries, playing, jobs ->
                val (continueListening, recentlyPlayed, recentlyAdded) = dashboardSections(tracks, entries)
                HomeUiState(
                    isLoaded = loaded,
                    totalTracks = tracks.size,
                    allTracks = tracks,
                    continueListening = continueListening,
                    recentlyPlayed = recentlyPlayed,
                    recentlyAdded = recentlyAdded,
                    nowPlayingId = playing.current?.fileId,
                    isPlaying = playing.isPlaying || playing.isLoading,
                    imports = jobs,
                )
            }
            combine(dashboard, collectionStore.collections) { state, collections ->
                state.copy(collections = summarize(collections, state.allTracks).filter { it.trackCount > 0 })
            }.collect { state -> _uiState.update { state } }
        }
    }

    fun resume(item: ListenedTrack) {
        val queue = _uiState.value.continueListening
        playback.playQueue(queue.map { it.track }, queue.indexOf(item).coerceAtLeast(0), startAtMs = item.entry.positionMs)
    }

    fun playRecent(item: ListenedTrack) {
        val queue = _uiState.value.recentlyPlayed.map { it.track }
        playback.playQueue(queue, queue.indexOf(item.track).coerceAtLeast(0))
    }

    fun playAdded(track: AudioTrack) {
        val queue = _uiState.value.recentlyAdded
        playback.playQueue(queue, queue.indexOf(track).coerceAtLeast(0))
    }

    fun playAll(shuffle: Boolean) {
        val tracks = _uiState.value.allTracks.sortedByDescending { it.dateAddedMs }
        if (tracks.isEmpty()) return
        playback.playQueue(if (shuffle) tracks.shuffled() else tracks, 0)
    }

    fun onFilesPicked(files: List<PlatformFile>) {
        viewModelScope.launch { enqueuePicked(files, importer, fileOps) }
    }

    fun dismissImport(id: Uuid) = importer.dismiss(id)

    fun retryImport(id: Uuid) = importer.retry(id)

    fun clearFinishedImports() = importer.clearFinished()
}

@Immutable
data class HomeUiState(
    val isLoaded: Boolean = false,
    val totalTracks: Int = 0,
    val allTracks: List<AudioTrack> = emptyList(),
    val continueListening: List<ListenedTrack> = emptyList(),
    val recentlyPlayed: List<ListenedTrack> = emptyList(),
    val recentlyAdded: List<AudioTrack> = emptyList(),
    val nowPlayingId: Uuid? = null,
    val isPlaying: Boolean = false,
    val imports: List<ImportJob> = emptyList(),
    val collections: List<CollectionSummary> = emptyList(),
)
