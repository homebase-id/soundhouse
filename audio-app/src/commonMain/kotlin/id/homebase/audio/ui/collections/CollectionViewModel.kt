package id.homebase.audio.ui.collections

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import id.homebase.audio.data.AudioCollection
import id.homebase.audio.data.AudioTrack
import id.homebase.audio.data.CollectionManager
import id.homebase.audio.data.CollectionStore
import id.homebase.audio.data.TrackStore
import id.homebase.audio.data.TrackManager
import id.homebase.audio.data.isIn
import id.homebase.audio.download.DownloadStore
import id.homebase.audio.download.OfflineKeeper
import id.homebase.audio.playback.PlaybackController
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.uuid.Uuid

class CollectionViewModel(
    private val collectionId: Uuid,
    private val collectionStore: CollectionStore,
    trackStore: TrackStore,
    private val manager: CollectionManager,
    private val playback: PlaybackController,
    downloads: DownloadStore,
    private val offline: OfflineKeeper,
    private val trackManager: TrackManager,
) : ViewModel() {
    private val _uiState = MutableStateFlow(CollectionUiState())
    val uiState: StateFlow<CollectionUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<CollectionEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<CollectionEvent> = _events.asSharedFlow()

    init {
        viewModelScope.launch {
            val base = combine(collectionStore.collections, trackStore.tracks, trackStore.isLoaded) { collections, tracks, loaded ->
                val collection = collections.firstOrNull { it.id == collectionId }
                CollectionUiState(
                    isLoaded = loaded,
                    collection = collection,
                    tracks = if (collection == null) emptyList() else tracks.filter { it.isIn(collection) },
                    allCollections = collections,
                )
            }
            combine(base, playback.state, downloads.downloaded, downloads.inProgress, downloads.failures) { state, playing, done, progress, failed ->
                state.copy(
                    nowPlayingId = playing.current?.fileId,
                    isPlaying = playing.isPlaying || playing.isLoading,
                    downloaded = done,
                    downloadProgress = progress,
                    downloadFailures = failed,
                )
            }.collect { state -> _uiState.update { state } }
        }
    }

    fun play(track: AudioTrack) {
        playback.playFrom(_uiState.value.tracks, track)
    }

    fun playAll(shuffle: Boolean) {
        playback.playAll(_uiState.value.tracks, shuffle)
    }

    fun download(track: AudioTrack) = offline.keep(track)
    fun removeDownload(track: AudioTrack) = offline.release(track)

    fun rename(name: String) = edit { _uiState.value.collection?.let { manager.rename(it, name) } }

    fun delete() = edit {
        val collection = _uiState.value.collection ?: return@edit
        manager.delete(collection)
        _events.emit(CollectionEvent.Deleted)
    }

    fun renameTrack(track: AudioTrack, title: String) = edit { trackManager.rename(track, title) }
    fun deleteTrack(track: AudioTrack) = edit { trackManager.delete(track) }

    fun remove(track: AudioTrack) = edit { _uiState.value.collection?.let { manager.remove(track, it) } }

    fun setCollections(track: AudioTrack, selected: Set<Uuid>) = edit { manager.setMembership(track, selected) }

    fun createCollection(name: String, withTrack: AudioTrack) = edit {
        val id = manager.create(name)
        collectionStore.collections.value.firstOrNull { it.id == id }?.let { manager.add(listOf(withTrack), it) }
    }

    private fun edit(block: suspend () -> Unit) {
        viewModelScope.launch {
            runCatching { block() }.onFailure {
                if (it is CancellationException) throw it
                Logger.e(it, TAG) { "Collection update failed" }
                _events.emit(CollectionEvent.Failed)
            }
        }
    }

    private companion object {
        const val TAG = "CollectionViewModel"
    }
}

sealed interface CollectionEvent {
    data object Deleted : CollectionEvent
    data object Failed : CollectionEvent
}

@Immutable
data class CollectionUiState(
    val isLoaded: Boolean = false,
    val collection: AudioCollection? = null,
    val tracks: List<AudioTrack> = emptyList(),
    val allCollections: List<AudioCollection> = emptyList(),
    val nowPlayingId: Uuid? = null,
    val isPlaying: Boolean = false,
    val downloaded: Set<Uuid> = emptySet(),
    val downloadProgress: Map<Uuid, Float> = emptyMap(),
    val downloadFailures: Set<Uuid> = emptySet(),
)
