package id.homebase.audio.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import id.homebase.api.client.auth.ApiCredentials
import id.homebase.api.client.auth.CredentialsManager
import id.homebase.api.client.eventbus.BackendEvent
import id.homebase.api.client.eventbus.EventBus
import id.homebase.api.common.OdinId
import id.homebase.api.common.SecureByteArray
import id.homebase.api.sync.database.DatabaseManager
import id.homebase.api.sync.database.MainIndexMetaHelpers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.uuid.Uuid

// Real dispatchers, not runTest: DatabaseManager runs on its own threads, so virtual time can't
// see its work. Each assertion waits for the flow to reach the expected state instead.
class TrackStoreTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val db = DatabaseManager({ JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY) })
    private val eventBus = EventBus()
    private val credentials = CredentialsManager()

    @AfterTest
    fun tearDown() {
        scope.cancel()
        db.close()
    }

    private fun track(
        title: String,
        createdMs: Long,
        fileId: Uuid = Uuid.random(),
        uniqueId: Uuid = Uuid.random(),
        state: String = "active",
        updatedMs: Long = createdMs,
    ) = buildTrackFile(
        trackContentJson(AudioTrackContent(title = title, sizeBytes = 10, mimeType = "audio/mpeg")),
        createdMs = createdMs,
        updatedMs = updatedMs,
        fileId = fileId,
        uniqueId = uniqueId,
        fileState = state,
    )

    private suspend fun seed(vararg files: id.homebase.api.client.drives.HomebaseFile) {
        val identity = credentials.requireActiveCredentials().getIdentityId()
        files.forEach {
            MainIndexMetaHelpers.HomebaseFileProcessor(db).baseUpsertEntryZapZap(identity, audioDriveId, it, null)
        }
    }

    private suspend fun signIn() {
        credentials.setActiveCredentials(
            ApiCredentials.create(OdinId("store.example"), "token", SecureByteArray(ByteArray(16)))
        )
    }

    private suspend fun TrackStore.awaitTitles(expected: List<String>) = withTimeout(5_000) {
        tracks.first { list -> list.map { it.title } == expected }
    }

    @Test
    fun `loads synced tracks newest first once signed in`() = runBlocking {
        signIn()
        seed(track("old", 1_000), track("new", 2_000))
        val store = TrackStore(db, credentials, eventBus, scope)
        store.awaitTitles(listOf("new", "old"))
        Unit
    }

    @Test
    fun `reloads when the websocket lands a batch on the audio drive`() = runBlocking {
        signIn()
        val store = TrackStore(db, credentials, eventBus, scope)
        withTimeout(5_000) { store.isLoaded.first { it } }
        val added = track("arrived", 3_000)
        seed(added)
        eventBus.emit(BackendEvent.DataEvent.BatchReceived(driveId = audioDriveId, batchData = listOf(added)))
        store.awaitTitles(listOf("arrived"))
        Unit
    }

    @Test
    fun `upsert of a deleted header removes the track`() = runBlocking {
        signIn()
        val id = Uuid.random()
        val uid = Uuid.random()
        seed(track("doomed", 1_000, fileId = id, uniqueId = uid), track("kept", 500))
        val store = TrackStore(db, credentials, eventBus, scope)
        store.awaitTitles(listOf("doomed", "kept"))
        store.upsert(track("doomed", 1_000, fileId = id, uniqueId = uid, state = "deleted", updatedMs = 2_000))
        store.awaitTitles(listOf("kept"))
        Unit
    }

    @Test
    fun `session end clears the list`() = runBlocking {
        signIn()
        seed(track("mine", 1_000))
        val store = TrackStore(db, credentials, eventBus, scope)
        store.awaitTitles(listOf("mine"))
        eventBus.emit(BackendEvent.SessionEnded)
        store.awaitTitles(emptyList())
        assertEquals(false, store.isLoaded.value)
    }

    @Test
    fun `tracks missing on the server are removed and a failed listing removes nothing`() = runBlocking {
        signIn()
        val kept = track("kept", 2_000)
        val ghost = track("ghost", 1_000)
        seed(kept, ghost)
        val store = TrackStore(db, credentials, eventBus, scope)
        store.awaitTitles(listOf("kept", "ghost"))

        val failing = LibraryReconciler(store, { error("network down") }, credentials, scope)
        assertEquals(0, failing.reconcile())
        assertEquals(listOf("kept", "ghost"), store.tracks.value.map { it.title })

        val reconciler = LibraryReconciler(store, { setOf(kept.fileId) }, credentials, scope)
        assertEquals(1, reconciler.reconcile())
        store.awaitTitles(listOf("kept"))
        Unit
    }
}
