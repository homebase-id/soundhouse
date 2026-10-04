package id.homebase.soundhouse.live

import id.homebase.soundhouse.importing.TrackDetails
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import id.homebase.api.client.eventbus.EventBus
import id.homebase.api.sync.database.DatabaseManager
import id.homebase.soundhouse.data.AudioTrackContent
import id.homebase.soundhouse.data.TrackManager
import id.homebase.soundhouse.data.TrackStore
import id.homebase.soundhouse.data.toAudioTrackOrNull
import id.homebase.soundhouse.download.DownloadStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okio.FileSystem
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/** Rename and delete through the same TrackManager wiring the Library uses. */
class LiveManageTest {
    private val workDir: File = Files.createTempDirectory("hba-live-manage").toFile()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val db = DatabaseManager({ JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY) })

    @AfterTest
    fun tearDown() {
        scope.cancel()
        db.close()
        workDir.deleteRecursively()
    }

    @Test
    fun `rename and delete reach server index downloads and queue`() = runBlocking {
        val session = LiveSession.requireOrSkip()
        val credentials = session.credentialsManager()
        val api = session.audioDriveApi(File(workDir, "cache"), credentials)
        purgeTagged(api)
        val store = TrackStore(db, credentials, EventBus(), scope)
        val downloads = DownloadStore(
            File(workDir, "downloads").absolutePath,
            { t, path, onProgress -> api.downloadTo(t, path, onProgress) },
            scope,
            FileSystem.SYSTEM,
        )
        val renamedInQueue = mutableListOf<String>()
        val dequeued = mutableListOf<Uuid>()
        val manager = TrackManager(
            editor = api,
            writeLocal = store::upsert,
            removeDownload = downloads::remove,
            onChanged = { renamedInQueue += it.title },
            onDeleted = { dequeued += it },
        )
        try {
            val bytes = fixtureBytes("tone.mp3")
            val source = File(workDir, "tone.mp3").apply { writeBytes(bytes) }
            val title = "liveManage ${Uuid.random().toString().take(8)}"
            val uploaded = api.uploadTrack(
                source.absolutePath,
                AudioTrackContent(title, bytes.size.toLong(), "audio/mpeg"),
                tags = listOf(LIVE_TEST_TAG),
            )
            store.upsert(assertNotNull(api.getTrackFile(uploaded.fileId)))
            val track = withTimeout(10_000) { store.tracks.first { list -> list.any { it.fileId == uploaded.fileId } } }
                .single { it.fileId == uploaded.fileId }
            downloads.download(track)
            withTimeout(60_000) { downloads.downloaded.first { track.fileId in it } }

            manager.editDetails(track, "$title renamed", track.details ?: TrackDetails())
            val renamed = withTimeout(10_000) { store.tracks.first { list -> list.any { it.title == "$title renamed" } } }
                .single { it.fileId == track.fileId }
            assertEquals(listOf("$title renamed"), renamedInQueue)
            assertEquals("$title renamed", api.getTrackFile(track.fileId)?.toAudioTrackOrNull()?.title)
            assertNotNull(downloads.localPathFor(renamed), "the offline copy survives a rename")

            manager.delete(renamed)
            withTimeout(10_000) { store.tracks.first { list -> list.none { it.fileId == track.fileId } } }
            assertEquals(listOf(track.fileId), dequeued)
            assertNull(downloads.localPathFor(renamed))
            assertNull(api.getTrackFile(track.fileId)?.toAudioTrackOrNull())
            assertTrue(api.queryTracks(listOf(LIVE_TEST_TAG)).none { it.fileId == track.fileId })
        } finally {
            purgeTagged(api)
        }
    }
}
