package id.homebase.soundhouse.data

import co.touchlab.kermit.Logger
import id.homebase.api.client.auth.CredentialsManager
import id.homebase.api.client.drives.FileSystemType
import id.homebase.api.client.drives.HomebaseFile
import id.homebase.api.client.drives.QueryBatchSortField
import id.homebase.api.client.drives.QueryBatchSortOrder
import id.homebase.api.client.drives.query.QueryBatchCursor
import id.homebase.api.client.eventbus.BackendEvent
import id.homebase.api.client.eventbus.EventBus
import id.homebase.api.sync.database.DatabaseManager
import id.homebase.api.sync.database.MainIndexMetaHelpers
import id.homebase.api.sync.database.QueryBatch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.uuid.Uuid

/**
 * The library as synced into the local drive index. Reloads when the drive sync or the notify
 * websocket lands files on the Audio drive, and when this app writes through [upsert].
 */
class TrackStore(
    private val databaseManager: DatabaseManager,
    private val credentialsManager: CredentialsManager,
    private val eventBus: EventBus,
    private val scope: CoroutineScope,
    private val driveId: Uuid = audioDriveId,
    private val query: suspend (identityId: Uuid) -> List<HomebaseFile> = { identityId ->
        queryLocalIndex(databaseManager, identityId, driveId, AUDIO_TRACK_FILE_TYPE)
    },
) {
    private val _tracks = MutableStateFlow<List<AudioTrack>>(emptyList())
    val tracks: StateFlow<List<AudioTrack>> = _tracks.asStateFlow()

    private val _isLoaded = MutableStateFlow(false)
    val isLoaded: StateFlow<Boolean> = _isLoaded.asStateFlow()

    private val reloadMutex = Mutex()
    // A reload that was still reading when the session ended must not publish what it read; the
    // check and the write share stateLock with reset() so neither can land between the other's steps.
    private val stateLock = Mutex()
    private var session = 0

    init {
        scope.launch {
            eventBus.events.collect { event ->
                when (event) {
                    is BackendEvent.SessionEnded -> stateLock.withLock {
                        session++
                        _tracks.value = emptyList()
                        _isLoaded.value = false
                    }
                    is BackendEvent.DataEvent.BatchReceived -> if (event.driveId == driveId) reload()
                    is BackendEvent.DriveEvent.Stopped ->
                        if (event.driveId == driveId && event.totalCount > 0) reload()
                    else -> Unit
                }
            }
        }
        scope.launch {
            credentialsManager.credentialsFlow.filterNotNull().collect { reload() }
        }
    }

    suspend fun reload() = reloadMutex.withLock {
        val startedIn = stateLock.withLock { session }
        val creds = credentialsManager.getActiveCredentials() ?: return@withLock
        val loaded = try {
            query(creds.getIdentityId()).mapNotNull { it.toAudioTrackOrNull() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Logger.e(e, TAG) { "Failed to load tracks from the local index" }
            null
        }
        stateLock.withLock {
            if (session != startedIn) return@withLock
            if (loaded != null) _tracks.value = loaded
            _isLoaded.value = true
        }
    }

    /** Writes a header this app just fetched from the server, so the list updates without waiting for sync. */
    suspend fun upsert(file: HomebaseFile) {
        val creds = credentialsManager.getActiveCredentials() ?: return
        MainIndexMetaHelpers.HomebaseFileProcessor(databaseManager)
            .baseUpsertEntryZapZap(creds.getIdentityId(), driveId, file, cursor = null)
        reload()
    }

    /**
     * Drops local tracks the server no longer has. Sync only reports changes, and a hard-deleted file
     * never shows up as one, so without this its row would stay in the library forever.
     * [serverFileIds] must be the complete set from a successful query.
     */
    suspend fun removeMissing(serverFileIds: Set<Uuid>): Int {
        val creds = credentialsManager.getActiveCredentials() ?: return 0
        val stale = _tracks.value.filter { it.fileId !in serverFileIds }
        if (stale.isEmpty()) return 0
        val processor = MainIndexMetaHelpers.HomebaseFileProcessor(databaseManager)
        stale.forEach { processor.deleteEntryDriveMainIndex(creds.getIdentityId(), driveId, it.fileId) }
        Logger.i(tag = TAG) { "Removed ${stale.size} track(s) deleted on the server" }
        reload()
        return stale.size
    }

    private companion object {
        const val TAG = "TrackStore"
    }
}

internal suspend fun queryLocalIndex(databaseManager: DatabaseManager, identityId: Uuid, driveId: Uuid, fileType: Int): List<HomebaseFile> {
    val records = mutableListOf<HomebaseFile>()
    var cursor: QueryBatchCursor? = null
    do {
        val page = QueryBatch(identityId).queryBatchAsync(
            dbm = databaseManager,
            driveId = driveId,
            noOfItems = 500,
            cursor = cursor,
            sortOrder = QueryBatchSortOrder.NewestFirst,
            sortField = QueryBatchSortField.CreatedDate,
            fileSystemType = FileSystemType.Standard.value,
            filetypesAnyOf = listOf(fileType),
        )
        records += page.records
        cursor = page.cursor
    } while (page.hasMoreRows && page.records.isNotEmpty())
    return records
}
