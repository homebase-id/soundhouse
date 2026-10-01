package id.homebase.audio.importing

import id.homebase.api.client.drives.HomebaseFile
import id.homebase.audio.data.AudioTrackContent
import id.homebase.audio.data.TestFileOps
import id.homebase.audio.data.TrackOrigin
import id.homebase.audio.data.TrackUploadTarget
import id.homebase.audio.data.UploadedTrack
import id.homebase.audio.data.buildTrackFile
import id.homebase.audio.data.trackContentJson
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
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class TrackImporterTest {
    private val dir: File = Files.createTempDirectory("hba-import").toFile()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @AfterTest
    fun tearDown() {
        scope.cancel()
        dir.deleteRecursively()
    }

    private class FakeTarget(private val failFor: String? = null) : TrackUploadTarget {
        val uploaded = mutableListOf<AudioTrackContent>()
        val covers = mutableListOf<ByteArray?>()
        override suspend fun uploadTrack(
            sourcePath: String,
            content: AudioTrackContent,
            tags: List<Uuid>,
            uniqueId: Uuid,
            coverArt: ByteArray?,
            onProgress: (Float) -> Unit,
        ): UploadedTrack {
            covers += coverArt
            if (content.fileName == failFor) error("server said no")
            onProgress(0.5f)
            onProgress(1f)
            uploaded += content
            return UploadedTrack(Uuid.random(), uniqueId, Uuid.random())
        }

        override suspend fun getTrackFile(fileId: Uuid): HomebaseFile =
            buildTrackFile(trackContentJson(uploaded.last()), fileId = fileId)
    }

    private fun file(name: String, size: Int) = File(dir, name).apply { writeBytes(ByteArray(size)) }

    @Test
    fun `uploads in order with metadata and hands the header to the store`() = runBlocking {
        val target = FakeTarget()
        val stored = mutableListOf<HomebaseFile>()
        val importer = TrackImporter(
            target, TestFileOps(dir), scope,
            onUploaded = { stored += it },
            readMetadata = { path -> if (path.endsWith("a.mp3")) AudioFileMetadata("Tagged", 1234) else AudioFileMetadata(null, null) },
            readCover = { path -> if (path.endsWith("a.mp3")) byteArrayOf(1, 2, 3) else null },
        )
        importer.enqueue(file("a.mp3", 10).path, "a.mp3")
        importer.enqueue(file("b side.flac", 20).path, "b side.flac")
        withTimeout(5_000) { importer.jobs.first { jobs -> jobs.size == 2 && jobs.all { it.status == ImportStatus.Done } } }

        assertEquals(listOf("Tagged", "b side"), target.uploaded.map { it.title })
        assertEquals(listOf(1234L, null), target.uploaded.map { it.durationMs })
        assertEquals(listOf(10L, 20L), target.uploaded.map { it.sizeBytes })
        assertEquals(listOf("audio/mpeg", "audio/flac"), target.uploaded.map { it.mimeType })
        assertEquals(2, stored.size)
        assertEquals(listOf(listOf<Byte>(1, 2, 3), null), target.covers.map { it?.toList() })
        assertTrue(importer.jobs.value.all { it.progress == 1f })
    }

    @Test
    fun `a failure marks only that job and the queue keeps going`() = runBlocking {
        val importer = TrackImporter(
            FakeTarget(failFor = "bad.mp3"), TestFileOps(dir), scope,
            onUploaded = {}, readMetadata = { AudioFileMetadata(null, null) },
        )
        importer.enqueue(file("bad.mp3", 1).path, "bad.mp3")
        importer.enqueue(file("good.mp3", 1).path, "good.mp3")
        val jobs = withTimeout(5_000) {
            importer.jobs.first { jobs -> jobs.none { it.status == ImportStatus.Queued || it.status == ImportStatus.Uploading } }
        }
        assertEquals(listOf(ImportStatus.Failed, ImportStatus.Done), jobs.map { it.status })
        importer.clearFinished()
        assertEquals(listOf("bad.mp3"), importer.jobs.value.map { it.fileName })
        importer.dismiss(importer.jobs.value.single().id)
        assertTrue(importer.jobs.value.isEmpty())
    }

    @Test
    fun `recordings keep their given title and the app-owned source is deleted`() = runBlocking {
        val target = FakeTarget()
        val importer = TrackImporter(
            target, TestFileOps(dir), scope, onUploaded = {},
            readMetadata = { AudioFileMetadata("ignored", 5) },
            readCover = { error("recordings have no cover to read") },
        )
        val source = file("rec.m4a", 3)
        importer.enqueue(source.path, "rec.m4a", title = "Voice memo", origin = TrackOrigin.Recorded, deleteSourceAfter = true)
        withTimeout(5_000) { importer.jobs.first { it.single().status == ImportStatus.Done } }
        assertEquals("Voice memo", target.uploaded.single().title)
        assertEquals(TrackOrigin.Recorded, target.uploaded.single().origin)
        assertEquals(listOf<ByteArray?>(null), target.covers)
        assertFalse(source.exists())
    }
}
