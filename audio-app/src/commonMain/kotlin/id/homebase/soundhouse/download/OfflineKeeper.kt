package id.homebase.soundhouse.download

import co.touchlab.kermit.Logger
import id.homebase.soundhouse.data.AudioTrack
import id.homebase.soundhouse.history.ListenEntry
import id.homebase.soundhouse.settings.AudioPreferences
import id.homebase.soundhouse.settings.AudioSettings
import id.homebase.core.util.NetworkMonitor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.IO
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.merge
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

/**
 * The tracks worth having offline: unfinished ones first, then the most recently played, as many as fit
 * in [limitBytes]. A track the user removed stays out until it's played again after [skipped] time.
 */
fun offlineSelection(
    tracks: List<AudioTrack>,
    history: Collection<ListenEntry>,
    limitBytes: Long,
    skipped: Map<String, Long> = emptyMap(),
): List<AudioTrack> {
    val byId = tracks.associateBy { it.fileId.toString() }
    val ordered = history
        .filter { it.fileId in byId }
        .filterNot { entry -> skipped[entry.fileId]?.let { entry.lastPlayedMs <= it } == true }
        .sortedWith(compareByDescending<ListenEntry> { it.resumable }.thenByDescending { it.lastPlayedMs })
    var total = 0L
    return ordered.mapNotNull { entry ->
        val track = byId.getValue(entry.fileId)
        if (total + track.sizeBytes > limitBytes) return@mapNotNull null
        total += track.sizeBytes
        track
    }
}

@Serializable
private data class KeeperState(val auto: Set<String> = emptySet(), val skipped: Map<String, Long> = emptyMap())

/**
 * Keeps recent listening on the device. Copies it makes itself are tracked separately from the user's own
 * downloads, which it never removes and which don't count towards the limit.
 */
@OptIn(FlowPreview::class)
class OfflineKeeper(
    private val downloads: DownloadStore,
    private val tracks: StateFlow<List<AudioTrack>>,
    private val tracksLoaded: StateFlow<Boolean>,
    private val history: StateFlow<Map<String, ListenEntry>>,
    private val historyLoaded: StateFlow<Boolean>,
    private val settings: AudioSettings,
    private val network: NetworkMonitor,
    private val stateFile: String,
    private val fileSystem: FileSystem,
    private val scope: CoroutineScope,
    private val settleMs: Long = 3_000,
    private val recheckMs: Long = 15 * 60_000L,
    private val now: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val lock = Mutex()
    private val writeLock = Mutex()
    private val state = MutableStateFlow(KeeperState())

    private val _autoKept = MutableStateFlow<Set<Uuid>>(emptySet())
    /** Downloads this keeper made, as opposed to the user's. */
    val autoKept: StateFlow<Set<Uuid>> = _autoKept.asStateFlow()

    init {
        scope.launch {
            load()
            downloads.awaitStartupScan()
            tracksLoaded.first { it }
            historyLoaded.first { it }
            val changes = combine(tracks, history, settings.preferences, downloads.downloaded, downloads.inProgress) {
                    library, listened, prefs, done, running ->
                Snapshot(offlineSelection(library, listened.values, prefs.offlineLimitBytes, state.value.skipped).map { it.fileId }, prefs, done, running.keys)
            }.distinctUntilChanged().debounce(settleMs)
            // Wi-Fi coming back changes nothing above, so look again now and then.
            val ticks = flow { while (true) { delay(recheckMs); emit(Unit) } }
            merge(changes, ticks).collect { reconcile() }
        }
    }

    private data class Snapshot(val wanted: List<Uuid>, val prefs: AudioPreferences, val done: Set<Uuid>, val running: Set<Uuid>)

    /** The user asked for this one: it becomes theirs and is never evicted. */
    fun keep(track: AudioTrack) {
        scope.launch {
            lock.withLock { change { it.copy(auto = it.auto - track.fileId.toString(), skipped = it.skipped - track.fileId.toString()) } }
            downloads.download(track)
        }
    }

    /** The user removed it: don't fetch it again until it's played again. */
    fun release(track: AudioTrack) {
        scope.launch {
            lock.withLock {
                change {
                    it.copy(auto = it.auto - track.fileId.toString(), skipped = it.skipped + (track.fileId.toString() to now()))
                }
            }
            downloads.remove(track.fileId)
        }
    }

    private suspend fun reconcile() = lock.withLock {
        val prefs = settings.preferences.value
        val wanted = if (prefs.keepRecentOffline) {
            offlineSelection(tracks.value, history.value.values, prefs.offlineLimitBytes, state.value.skipped)
        } else emptyList()
        val wantedIds = wanted.mapTo(HashSet()) { it.fileId.toString() }
        val done = downloads.downloaded.value
        val running = downloads.inProgress.value.keys

        val evict = state.value.auto.filter { it !in wantedIds }
        evict.forEach { id ->
            val fileId = Uuid.parse(id)
            if (fileId !in running) downloads.remove(fileId)
        }
        if (evict.isNotEmpty()) change { it.copy(auto = it.auto - evict.toSet()) }

        if (prefs.offlineOnWifiOnly && !network.isUnmetered()) return@withLock
        // One at a time: the next starts when this one lands and the downloaded set changes.
        if (state.value.auto.any { Uuid.parse(it) in running }) return@withLock
        val next = wanted.firstOrNull { it.fileId !in done && it.fileId !in running && it.fileId !in downloads.failures.value }
            ?: return@withLock
        change { it.copy(auto = it.auto + next.fileId.toString()) }
        downloads.download(next)
    }

    private fun change(transform: (KeeperState) -> KeeperState) {
        state.update(transform)
        _autoKept.value = state.value.auto.mapTo(HashSet(), Uuid::parse)
        scope.launch(Dispatchers.IO) {
            writeLock.withLock { write(state.value) }
        }
    }

    private fun write(snapshot: KeeperState) {
        try {
            fileSystem.writeTextAtomically(stateFile, json.encodeToString(snapshot))
        } catch (e: Exception) {
            Logger.w(e, TAG) { "Could not save offline state" }
        }
    }

    private suspend fun load() {
        val loaded = kotlinx.coroutines.withContext(Dispatchers.IO) {
            val path = stateFile.toPath()
            if (!fileSystem.exists(path)) return@withContext null
            try {
                json.decodeFromString<KeeperState>(fileSystem.read(path) { readUtf8() })
            } catch (e: Exception) {
                Logger.w(e, TAG) { "Offline state unreadable; starting fresh" }
                null
            }
        } ?: return
        state.value = loaded
        _autoKept.value = loaded.auto.mapTo(HashSet(), Uuid::parse)
    }

    private companion object {
        const val TAG = "OfflineKeeper"
    }
}
