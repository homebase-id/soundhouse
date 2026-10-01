package id.homebase.audio.data

import co.touchlab.kermit.Logger
import id.homebase.api.client.auth.CredentialsManager
import id.homebase.api.client.drives.HomebaseFile
import id.homebase.api.client.eventbus.BackendEvent
import id.homebase.api.client.eventbus.EventBus
import id.homebase.api.sync.database.DatabaseManager
import id.homebase.api.sync.database.MainIndexMetaHelpers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.uuid.Uuid

/** Collections as synced into the local drive index, sorted by name. */
class CollectionStore(
    private val databaseManager: DatabaseManager,
    private val credentialsManager: CredentialsManager,
    private val eventBus: EventBus,
    private val scope: CoroutineScope,
    private val driveId: Uuid = audioDriveId,
) {
    private val _collections = MutableStateFlow<List<AudioCollection>>(emptyList())
    val collections: StateFlow<List<AudioCollection>> = _collections.asStateFlow()

    private val reloadMutex = Mutex()

    init {
        scope.launch {
            eventBus.events.collect { event ->
                when (event) {
                    is BackendEvent.SessionEnded -> _collections.value = emptyList()
                    is BackendEvent.DataEvent.BatchReceived -> if (event.driveId == driveId) reload()
                    is BackendEvent.DriveEvent.Stopped -> if (event.driveId == driveId && event.totalCount > 0) reload()
                    else -> Unit
                }
            }
        }
        scope.launch { credentialsManager.credentialsFlow.filterNotNull().collect { reload() } }
    }

    suspend fun reload() = reloadMutex.withLock {
        val creds = credentialsManager.getActiveCredentials() ?: return@withLock
        try {
            _collections.value = queryLocalIndex(databaseManager, creds.getIdentityId(), driveId, AUDIO_COLLECTION_FILE_TYPE)
                .mapNotNull { it.toAudioCollectionOrNull() }
                .sortedBy { it.name.lowercase() }
        } catch (e: Exception) {
            Logger.e(e, TAG) { "Failed to load collections from the local index" }
        }
    }

    suspend fun upsert(file: HomebaseFile) {
        val creds = credentialsManager.getActiveCredentials() ?: return
        MainIndexMetaHelpers.HomebaseFileProcessor(databaseManager)
            .baseUpsertEntryZapZap(creds.getIdentityId(), driveId, file, cursor = null)
        reload()
    }

    private companion object {
        const val TAG = "CollectionStore"
    }
}
