package id.homebase.soundhouse.history

import id.homebase.soundhouse.download.writeTextAtomically
import co.touchlab.kermit.Logger
import id.homebase.api.client.eventbus.BackendEvent
import id.homebase.api.client.eventbus.EventBus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okio.FileSystem
import okio.Path.Companion.toPath
import kotlin.time.Clock
import kotlin.uuid.Uuid

@Serializable
data class ListenEntry(
    val fileId: String,
    val lastPlayedMs: Long,
    val positionMs: Long,
    val durationMs: Long,
    val finished: Boolean = false,
) {
    /** Worth offering under "Continue listening": started, not finished, with something left. */
    val resumable: Boolean get() = !finished && positionMs >= MIN_RESUME_MS && durationMs - positionMs > MIN_REMAINING_MS

    val remainingMs: Long get() = (durationMs - positionMs).coerceAtLeast(0)

    val progress: Float get() = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f

    companion object {
        const val MIN_RESUME_MS = 10_000L
        const val MIN_REMAINING_MS = 15_000L
    }
}

/**
 * Where each track was last left, kept on this device only (a JSON file in app data) and cleared on
 * sign-out. Positions are updated on every progress tick but written to disk at most every
 * [saveIntervalMs], and immediately on pause and track changes.
 */
class ListeningHistory(
    private val file: String,
    private val fileSystem: FileSystem,
    private val scope: CoroutineScope,
    eventBus: EventBus? = null,
    private val now: () -> Long = { Clock.System.now().toEpochMilliseconds() },
    private val saveIntervalMs: Long = 5_000,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val writeLock = Mutex()
    private var lastSaveMs = 0L

    private val _entries = MutableStateFlow<Map<String, ListenEntry>>(emptyMap())
    val entries: StateFlow<Map<String, ListenEntry>> = _entries.asStateFlow()

    private val _isLoaded = MutableStateFlow(false)
    val isLoaded: StateFlow<Boolean> = _isLoaded.asStateFlow()

    // Removals made while the stored file is still loading, so the merge doesn't bring them back; null = cleared.
    private val removedBeforeLoad = MutableStateFlow<Set<String>?>(emptySet())

    init {
        scope.launch(Dispatchers.IO) {
            load()
            _isLoaded.value = true
        }
        if (eventBus != null) {
            scope.launch { eventBus.events.collect { if (it is BackendEvent.SessionEnded) clear() } }
        }
    }

    fun entry(fileId: Uuid): ListenEntry? = _entries.value[fileId.toString()]

    /** Records progress on [fileId]; [force] writes through, e.g. on pause or when the track changes. */
    fun record(fileId: Uuid, positionMs: Long, durationMs: Long, force: Boolean = false) {
        if (durationMs <= 0) return
        val finished = positionMs >= durationMs - FINISHED_SLACK_MS || positionMs >= durationMs * 0.97
        _entries.update {
            it + (fileId.toString() to ListenEntry(fileId.toString(), now(), positionMs.coerceIn(0, durationMs), durationMs, finished))
        }
        val due = now() - lastSaveMs >= saveIntervalMs
        if (force || due) save()
    }

    fun forget(fileId: Uuid) {
        if (!_isLoaded.value) removedBeforeLoad.update { it?.plus(fileId.toString()) }
        _entries.update { it - fileId.toString() }
        save()
    }

    fun clear() {
        if (!_isLoaded.value) removedBeforeLoad.value = null
        _entries.value = emptyMap()
        save()
    }

    private fun save() {
        lastSaveMs = now()
        scope.launch(Dispatchers.IO) {
            // Until the stored file is merged in, the map holds only this session's changes; writing it
            // would replace the whole stored history.
            _isLoaded.first { it }
            writeLock.withLock {
                // Read under the lock: saves can start out of order, and the last write must be the latest state.
                val snapshot = _entries.value.values.sortedByDescending { it.lastPlayedMs }.take(MAX_ENTRIES)
                try {
                    fileSystem.writeTextAtomically(file, json.encodeToString(snapshot))
                } catch (e: Exception) {
                    Logger.w(e, TAG) { "Could not save listening history" }
                }
            }
        }
    }

    private fun load() {
        val path = file.toPath()
        if (!fileSystem.exists(path)) return
        val loaded = try {
            json.decodeFromString<List<ListenEntry>>(fileSystem.read(path) { readUtf8() })
        } catch (e: Exception) {
            Logger.w(e, TAG) { "Listening history unreadable; starting fresh" }
            emptyList()
        }
        // Progress recorded before the file finished loading wins over the stored copy. Removals are set
        // before entries change, so a removal racing this update makes it retry and see them.
        _entries.update { current ->
            val removed = removedBeforeLoad.value ?: return@update current
            loaded.filterNot { it.fileId in removed }.associateBy { it.fileId } + current
        }
    }

    private companion object {
        const val TAG = "ListeningHistory"
        const val FINISHED_SLACK_MS = 5_000L
        const val MAX_ENTRIES = 500
    }
}
