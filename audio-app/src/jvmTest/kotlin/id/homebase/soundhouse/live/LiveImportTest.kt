package id.homebase.soundhouse.live

import id.homebase.soundhouse.data.TestFileOps
import id.homebase.soundhouse.data.toAudioTrackOrNull
import id.homebase.soundhouse.importing.ImportStatus
import id.homebase.soundhouse.importing.TrackImporter
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
import kotlin.test.assertTrue

/** A picked mp3 goes through the real importer: ffprobe metadata, encrypted upload, header back. */
class LiveImportTest {
    private val workDir: File = Files.createTempDirectory("hba-live-import").toFile()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @AfterTest
    fun tearDown() {
        scope.cancel()
        workDir.deleteRecursively()
    }

    @Test
    fun `import reads metadata and uploads`() = runBlocking {
        val session = LiveSession.requireOrSkip()
        val api = session.audioDriveApi(File(workDir, "cache"))
        purgeTagged(api)
        try {
            val picked = File(workDir, "picked tone.mp3").apply { writeBytes(fixtureBytes("tone.mp3")) }
            val landed = mutableListOf<id.homebase.api.client.drives.HomebaseFile>()
            val importer = TrackImporter(api, TestFileOps(workDir), scope, onUploaded = { landed += it })
            importer.enqueue(picked.absolutePath, picked.name, tags = listOf(LIVE_TEST_TAG))
            val job = withTimeout(60_000) {
                importer.jobs.first { jobs -> jobs.single().status.let { it == ImportStatus.Done || it == ImportStatus.Failed } }
            }.single()
            assertEquals(ImportStatus.Done, job.status, "import failed; see log above")

            val track = assertNotNull(landed.single().toAudioTrackOrNull())
            assertEquals("Live Fixture Tone", track.title)
            assertEquals("audio/mpeg", track.mimeType)
            assertEquals(picked.length(), track.sizeBytes)
            assertTrue(track.durationMs!! in 5_900..6_200, "duration ${track.durationMs}")
            assertTrue(picked.exists(), "a user-picked original must not be deleted")
        } finally {
            purgeTagged(api)
        }
    }

    @Test
    fun `embedded cover art becomes encrypted thumbnails that survive a rename`() = runBlocking {
        val session = LiveSession.requireOrSkip()
        val api = session.audioDriveApi(File(workDir, "cache"))
        purgeTagged(api)
        try {
            val picked = File(workDir, "with cover.mp3").apply { writeBytes(fixtureBytes("tone-with-cover.mp3")) }
            val landed = mutableListOf<id.homebase.api.client.drives.HomebaseFile>()
            val importer = TrackImporter(api, TestFileOps(workDir), scope, onUploaded = { landed += it })
            importer.enqueue(picked.absolutePath, picked.name, tags = listOf(LIVE_TEST_TAG))
            val job = withTimeout(60_000) {
                importer.jobs.first { jobs -> jobs.single().status.let { it == ImportStatus.Done || it == ImportStatus.Failed } }
            }.single()
            assertEquals(ImportStatus.Done, job.status)

            val track = assertNotNull(landed.single().toAudioTrackOrNull())
            assertTrue(track.hasCover, "uploaded track lists no cover thumbnails")
            assertNotNull(track.coverPreview, "tiny preview missing from the header")
            val cover = assertNotNull(api.readCover(track, 320))
            val image = org.jetbrains.skia.Image.makeFromEncoded(cover)
            assertTrue(image.width in 200..320, "cover width ${image.width}")

            api.renameTrack(track, "cover renamed")
            val renamed = assertNotNull(api.getTrackFile(track.fileId)?.toAudioTrackOrNull())
            assertTrue(renamed.hasCover)
            assertNotNull(renamed.coverPreview, "rename dropped the preview")
            val afterRename = assertNotNull(api.readCover(renamed, 320))
            assertTrue(org.jetbrains.skia.Image.makeFromEncoded(afterRename).width > 0, "cover unreadable after rename")
        } finally {
            purgeTagged(api)
        }
    }
}
