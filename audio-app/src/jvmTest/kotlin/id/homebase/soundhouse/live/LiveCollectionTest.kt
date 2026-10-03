package id.homebase.soundhouse.live

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import id.homebase.api.client.eventbus.EventBus
import id.homebase.api.sync.database.DatabaseManager
import id.homebase.soundhouse.data.AudioTrackContent
import id.homebase.soundhouse.data.CollectionManager
import id.homebase.soundhouse.data.CollectionStore
import id.homebase.soundhouse.data.TrackStore
import id.homebase.soundhouse.data.isIn
import id.homebase.soundhouse.data.toAudioCollectionOrNull
import id.homebase.soundhouse.data.toAudioTrackOrNull
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
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/** Collections are header-only files; membership is a tag on the track. Both must round-trip through the server. */
class LiveCollectionTest {
    private val workDir: File = Files.createTempDirectory("hba-live-collection").toFile()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val db = DatabaseManager({ JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY) })

    @AfterTest
    fun tearDown() {
        scope.cancel()
        db.close()
        workDir.deleteRecursively()
    }

    @Test
    fun `create tag rename and delete a collection`() = runBlocking<Unit> {
        val session = LiveSession.requireOrSkip()
        val credentials = session.credentialsManager()
        val api = session.audioDriveApi(File(workDir, "cache"), credentials)
        purgeTagged(api)
        val tracks = TrackStore(db, credentials, EventBus(), scope)
        val collections = CollectionStore(db, credentials, EventBus(), scope)
        val manager = CollectionManager(
            editor = api,
            collections = { collections.collections.value },
            tracks = { tracks.tracks.value },
            writeCollection = collections::upsert,
            writeTrack = tracks::upsert,
            onTrackChanged = {},
        )
        var collectionFileId: Uuid? = null
        try {
            val bytes = fixtureBytes("tone.mp3")
            val source = File(workDir, "tone.mp3").apply { writeBytes(bytes) }
            val uploaded = api.uploadTrack(source.absolutePath, AudioTrackContent("liveCollection track", bytes.size.toLong(), "audio/mpeg"), tags = listOf(LIVE_TEST_TAG))
            tracks.upsert(assertNotNull(api.getTrackFile(uploaded.fileId)))
            val track = tracks.tracks.value.single { it.fileId == uploaded.fileId }

            val name = "liveCollection ${Uuid.random().toString().take(8)}"
            val id = manager.create(name)
            val created = withTimeout(10_000) { collections.collections.first { list -> list.any { it.id == id } } }.single { it.id == id }
            collectionFileId = created.fileId
            assertEquals(name, api.getFile(created.fileId)?.toAudioCollectionOrNull()?.name)

            manager.add(listOf(track), created)
            val serverTrack = assertNotNull(api.getTrackFile(track.fileId)?.toAudioTrackOrNull())
            assertTrue(serverTrack.isIn(created))
            assertTrue(LIVE_TEST_TAG in serverTrack.tags, "non-collection tags survive")
            assertTrue(api.queryTracks(listOf(created.id)).any { it.fileId == track.fileId }, "the server can query by collection")

            manager.rename(created, "$name renamed")
            assertEquals("$name renamed", api.getFile(created.fileId)?.toAudioCollectionOrNull()?.name)
            val renamed = collections.collections.value.single { it.id == id }

            manager.delete(renamed)
            assertNull(api.getFile(created.fileId)?.toAudioCollectionOrNull())
            val untagged = assertNotNull(api.getTrackFile(track.fileId)?.toAudioTrackOrNull())
            assertTrue(!untagged.isIn(created))
            assertTrue(LIVE_TEST_TAG in untagged.tags)
            withTimeout(10_000) { collections.collections.first { list -> list.none { it.id == id } } }
        } finally {
            collectionFileId?.let {
                runCatching { api.deleteCollection(it) }
                api.hardDeleteTrack(it)
            }
            purgeTagged(api)
        }
    }
}
