package id.homebase.soundhouse.live

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import id.homebase.api.client.HttpClientProvider
import id.homebase.api.client.drives.query.DriveQueryProvider
import id.homebase.api.client.eventbus.EventBus
import id.homebase.api.sync.DriveSyncManager
import id.homebase.api.sync.database.DatabaseManager
import id.homebase.soundhouse.data.AudioTrackContent
import id.homebase.soundhouse.data.TrackStore
import id.homebase.core.config.mandatorySyncDrives
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.uuid.Uuid

/** The copied drive sync, pointed at the Audio drive, lands uploads in the index the Library reads. */
class LiveLibrarySyncTest {
    private val workDir: File = Files.createTempDirectory("hba-live-sync").toFile()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val db = DatabaseManager({ JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY) })

    @AfterTest
    fun tearDown() {
        scope.cancel()
        db.close()
        workDir.deleteRecursively()
    }

    @Test
    fun `drive sync brings an upload into the library`() = runBlocking {
        val session = LiveSession.requireOrSkip()
        val credentials = session.credentialsManager()
        val api = session.audioDriveApi(File(workDir, "cache"), credentials)
        purgeTagged(api)
        val eventBus = EventBus()
        val sync = DriveSyncManager(
            DriveQueryProvider(HttpClientProvider.create(), credentials), credentials, eventBus, scope, db,
            mandatoryDrives = mandatorySyncDrives.associate { it.drive.alias to it.label },
        )
        val store = TrackStore(db, credentials, eventBus, scope)
        val title = "liveSync ${Uuid.random().toString().take(8)}"
        try {
            val source = File(workDir, "tone.mp3").apply { writeBytes(fixtureBytes("tone.mp3")) }
            val uploaded = api.uploadTrack(
                source.absolutePath,
                AudioTrackContent(title = title, sizeBytes = source.length(), mimeType = "audio/mpeg"),
                tags = listOf(LIVE_TEST_TAG),
            )
            sync.ensureMandatoryMounted()
            sync.start()
            sync.syncAll()
            withTimeout(30_000) {
                store.tracks.first { tracks -> tracks.any { it.fileId == uploaded.fileId && it.title == title } }
            }

            api.deleteTrack(uploaded.fileId)
            sync.syncAll()
            withTimeout(30_000) { store.tracks.first { tracks -> tracks.none { it.fileId == uploaded.fileId } } }
            Unit
        } finally {
            sync.stop()
            purgeTagged(api)
        }
    }

    @Test
    fun `a track hard-deleted elsewhere disappears on reconcile`() = runBlocking {
        val session = LiveSession.requireOrSkip()
        val credentials = session.credentialsManager()
        val api = session.audioDriveApi(File(workDir, "cache"), credentials)
        purgeTagged(api)
        val eventBus = EventBus()
        val sync = DriveSyncManager(
            DriveQueryProvider(HttpClientProvider.create(), credentials), credentials, eventBus, scope, db,
            mandatoryDrives = mandatorySyncDrives.associate { it.drive.alias to it.label },
        )
        val store = TrackStore(db, credentials, eventBus, scope)
        val reconciler = id.homebase.soundhouse.data.LibraryReconciler(
            store, { api.queryTrackFiles().mapTo(HashSet()) { it.fileId } }, credentials, scope,
        )
        try {
            val source = File(workDir, "tone.mp3").apply { writeBytes(fixtureBytes("tone.mp3")) }
            val uploaded = api.uploadTrack(
                source.absolutePath,
                AudioTrackContent("liveGhost ${Uuid.random().toString().take(8)}", source.length(), "audio/mpeg"),
                tags = listOf(LIVE_TEST_TAG),
            )
            sync.ensureMandatoryMounted()
            sync.start()
            sync.syncAll()
            withTimeout(30_000) { store.tracks.first { tracks -> tracks.any { it.fileId == uploaded.fileId } } }
            val before = store.tracks.value.size

            api.hardDeleteTrack(uploaded.fileId)
            sync.syncAll()
            kotlin.test.assertTrue(store.tracks.value.any { it.fileId == uploaded.fileId }, "sync alone should not see a hard delete")

            kotlin.test.assertEquals(1, reconciler.reconcile())
            withTimeout(10_000) { store.tracks.first { tracks -> tracks.none { it.fileId == uploaded.fileId } } }
            kotlin.test.assertEquals(before - 1, store.tracks.value.size, "reconcile removed more than the ghost")
        } finally {
            sync.stop()
            purgeTagged(api)
        }
    }
}
