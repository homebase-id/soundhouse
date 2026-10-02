package id.homebase.audio.importing

import id.homebase.api.client.NetworkException
import id.homebase.api.client.drives.HomebaseFile
import id.homebase.api.file.SourceUnavailableException
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
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.io.File
import java.net.SocketException
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

    private class FakeTarget(
        private val failFor: String? = null,
        private var connectionFailures: Int = 0,
        private val hang: Boolean = false,
    ) : TrackUploadTarget {
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
            if (hang) awaitCancellation()
            if (content.fileName == failFor) error("server said no")
            if (connectionFailures > 0) {
                connectionFailures--
                throw NetworkException(SocketException("Software caused connection abort"))
            }
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
            parallelism = kotlinx.coroutines.flow.MutableStateFlow(1),
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
    fun `failures are classified by walking the cause chain`() {
        val dropped = NetworkException(SocketException("Software caused connection abort"))
        assertEquals(ImportFailure.Connection, importFailureOf(RuntimeException("upload failed", dropped)))
        assertEquals(ImportFailure.Unreadable, importFailureOf(SourceUnavailableException("/gone.mp3")))
        assertEquals(ImportFailure.Unknown, importFailureOf(IllegalStateException("boom")))
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

    private val queueFile get() = File(dir, "state/imports.json").path
    private val stagingDir get() = File(dir, "state/imports").path

    private suspend fun TrackImporter.settled() = withTimeout(5_000) {
        jobs.first { jobs -> jobs.isNotEmpty() && jobs.none { it.isActive } }
    }

    @Test
    fun `a dropped connection is retried until the upload lands`() = runBlocking {
        val target = FakeTarget(connectionFailures = 2)
        val importer = TrackImporter(
            target, TestFileOps(dir), scope, onUploaded = {},
            readMetadata = { AudioFileMetadata(null, null) }, retryDelaysMs = listOf(1, 1, 1),
        )
        importer.enqueue(file("big.mp3", 1).path, "big.mp3")
        assertEquals(ImportStatus.Done, importer.settled().single().status)
        assertEquals(1, target.uploaded.size)
    }

    @Test
    fun `after the last retry the job fails with its reason and keeps its copy for a manual retry`() = runBlocking {
        val target = FakeTarget(connectionFailures = 3)
        val importer = TrackImporter(
            target, TestFileOps(dir), scope, onUploaded = {},
            readMetadata = { AudioFileMetadata(null, null) }, retryDelaysMs = listOf(1, 1),
            queueFile = queueFile, stagingDir = stagingDir,
        )
        val source = file("copy.mp3", 1)
        importer.enqueue(source.path, "copy.mp3", deleteSourceAfter = true)
        val failed = importer.settled().single()
        assertEquals(ImportStatus.Failed, failed.status)
        assertEquals(ImportFailure.Connection, failed.failure)
        assertFalse(source.exists())
        assertEquals(1, File(stagingDir).listFiles()!!.size)

        importer.retry(failed.id)
        withTimeout(5_000) { importer.jobs.first { it.single().status == ImportStatus.Done } }
        assertEquals(1, target.uploaded.size)
        assertTrue(File(stagingDir).listFiles()!!.isEmpty())
    }

    @Test
    fun `dismissing a failed import deletes its app-owned copy`() = runBlocking {
        val importer = TrackImporter(
            FakeTarget(failFor = "bad.mp3"), TestFileOps(dir), scope, onUploaded = {},
            readMetadata = { AudioFileMetadata(null, null) },
            queueFile = queueFile, stagingDir = stagingDir,
        )
        importer.enqueue(file("bad.mp3", 1).path, "bad.mp3", deleteSourceAfter = true)
        val failed = importer.settled().single()
        assertEquals(ImportFailure.Unknown, failed.failure)
        importer.dismiss(failed.id)
        assertTrue(importer.jobs.value.isEmpty())
        assertTrue(File(stagingDir).listFiles()!!.isEmpty())
    }

    @Test
    fun `the queue survives a restart and picks up where it stopped`() = runBlocking {
        val firstRun = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val interrupted = TrackImporter(
            FakeTarget(hang = true), TestFileOps(dir), firstRun, onUploaded = {},
            readMetadata = { AudioFileMetadata(null, null) },
            queueFile = queueFile, stagingDir = stagingDir,
        )
        interrupted.enqueue(file("long.mp3", 1).path, "long.mp3", deleteSourceAfter = true)
        withTimeout(5_000) { interrupted.jobs.first { it.single().status == ImportStatus.Uploading } }
        withTimeout(5_000) { while (!File(queueFile).exists()) kotlinx.coroutines.delay(10) }
        firstRun.cancel()

        val target = FakeTarget()
        val resumed = TrackImporter(
            target, TestFileOps(dir), scope, onUploaded = {},
            readMetadata = { AudioFileMetadata(null, null) },
            queueFile = queueFile, stagingDir = stagingDir,
        )
        assertEquals(ImportStatus.Done, resumed.settled().single().status)
        assertEquals("long.mp3", target.uploaded.single().fileName)
        assertTrue(File(stagingDir).listFiles()!!.isEmpty())
    }

    @Test
    fun `a failed import is still listed as failed after a restart`() = runBlocking {
        val firstRun = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val first = TrackImporter(
            FakeTarget(failFor = "bad.mp3"), TestFileOps(dir), firstRun, onUploaded = {},
            readMetadata = { AudioFileMetadata(null, null) },
            queueFile = queueFile, stagingDir = stagingDir,
        )
        first.enqueue(file("bad.mp3", 1).path, "bad.mp3", deleteSourceAfter = true)
        first.settled()
        withTimeout(5_000) { while (!File(queueFile).readText().contains("Unknown")) kotlinx.coroutines.delay(10) }
        firstRun.cancel()

        val target = FakeTarget()
        val second = TrackImporter(
            target, TestFileOps(dir), scope, onUploaded = {},
            readMetadata = { AudioFileMetadata(null, null) },
            queueFile = queueFile, stagingDir = stagingDir,
        )
        val restored = withTimeout(5_000) { second.jobs.first { it.isNotEmpty() } }.single()
        assertEquals(ImportStatus.Failed, restored.status)
        second.retry(restored.id)
        withTimeout(5_000) { second.jobs.first { it.single().status == ImportStatus.Done } }
        assertEquals(1, target.uploaded.size)
    }

    /** Records how many uploads overlap; each upload takes [holdMs]. */
    private class OverlapTarget(private val holdMs: Long = 150) : TrackUploadTarget {
        private val running = java.util.concurrent.atomic.AtomicInteger()
        val maxOverlap = java.util.concurrent.atomic.AtomicInteger()
        val overlapWhileUploading = java.util.concurrent.ConcurrentHashMap<String, Int>()
        val uploaded = java.util.Collections.synchronizedList(mutableListOf<AudioTrackContent>())
        val starts = java.util.Collections.synchronizedList(mutableListOf<String>())
        override suspend fun uploadTrack(
            sourcePath: String, content: AudioTrackContent, tags: List<Uuid>, uniqueId: Uuid,
            coverArt: ByteArray?, onProgress: (Float) -> Unit,
        ): UploadedTrack {
            starts += content.fileName ?: ""
            val now = running.incrementAndGet()
            maxOverlap.accumulateAndGet(now) { a, b -> maxOf(a, b) }
            delay(holdMs)
            overlapWhileUploading.merge(content.fileName ?: "", running.get()) { a, b -> maxOf(a, b) }
            running.decrementAndGet()
            uploaded += content
            return UploadedTrack(Uuid.random(), uniqueId, Uuid.random())
        }
        override suspend fun getTrackFile(fileId: Uuid): HomebaseFile = buildTrackFile(trackContentJson(uploaded.last()), fileId = fileId)
    }

    @Test
    fun `small files upload three at a time and never more`() = runBlocking<Unit> {
        val target = OverlapTarget()
        val importer = TrackImporter(
            target, TestFileOps(dir), scope, onUploaded = {},
            readMetadata = { AudioFileMetadata(null, null) }, readCover = { null },
        )
        repeat(7) { importer.enqueue(file("s$it.mp3", 1).path, "s$it.mp3") }
        val jobs = withTimeout(10_000) { importer.jobs.first { jobs -> jobs.size == 7 && jobs.all { it.status == ImportStatus.Done } } }
        assertEquals(7, jobs.size)
        assertEquals(3, target.maxOverlap.get())
    }

    @Test
    fun `a large file uploads alone`() = runBlocking<Unit> {
        val target = OverlapTarget()
        val importer = TrackImporter(
            target, TestFileOps(dir), scope, onUploaded = {},
            readMetadata = { AudioFileMetadata(null, null) }, readCover = { null },
            largeFileBytes = 10,
        )
        importer.enqueue(file("small1.mp3", 1).path, "small1.mp3")
        importer.enqueue(file("big.mp3", 20).path, "big.mp3")
        importer.enqueue(file("small2.mp3", 1).path, "small2.mp3")
        importer.enqueue(file("small3.mp3", 1).path, "small3.mp3")
        withTimeout(10_000) { importer.jobs.first { jobs -> jobs.size == 4 && jobs.all { it.status == ImportStatus.Done } } }
        assertEquals(1, target.overlapWhileUploading["big.mp3"])
        assertEquals(4, target.uploaded.size)
    }

    @Test
    fun `files waiting for a slot stay queued`() = runBlocking<Unit> {
        val target = OverlapTarget(holdMs = 400)
        val importer = TrackImporter(
            target, TestFileOps(dir), scope, onUploaded = {},
            readMetadata = { AudioFileMetadata(null, null) }, readCover = { null },
            parallelism = kotlinx.coroutines.flow.MutableStateFlow(2),
        )
        repeat(4) { importer.enqueue(file("q$it.mp3", 1).path, "q$it.mp3") }
        val busy = withTimeout(5_000) { importer.jobs.first { jobs -> jobs.count { it.status == ImportStatus.Uploading } == 2 } }
        assertEquals(2, busy.count { it.status == ImportStatus.Queued })
    }

    @Test
    fun `raising the limit lets waiting files start`() = runBlocking<Unit> {
        val target = OverlapTarget(holdMs = 300)
        val limit = kotlinx.coroutines.flow.MutableStateFlow(1)
        val importer = TrackImporter(
            target, TestFileOps(dir), scope, onUploaded = {},
            readMetadata = { AudioFileMetadata(null, null) }, readCover = { null },
            parallelism = limit,
        )
        repeat(6) { importer.enqueue(file("r$it.mp3", 1).path, "r$it.mp3") }
        withTimeout(5_000) { importer.jobs.first { jobs -> jobs.count { it.status == ImportStatus.Uploading } == 1 } }
        limit.value = 5
        withTimeout(10_000) { importer.jobs.first { jobs -> jobs.size == 6 && jobs.all { it.status == ImportStatus.Done } } }
        assertTrue(target.maxOverlap.get() >= 3, "overlap after raising was ${target.maxOverlap.get()}")
    }

    @Test
    fun `uploads start in the order they were queued at any limit`() = runBlocking<Unit> {
        for (limit in listOf(1, 3)) {
            val target = OverlapTarget(holdMs = 40)
            val importer = TrackImporter(
                target, TestFileOps(dir), scope, onUploaded = {},
                readMetadata = { AudioFileMetadata(null, null) }, readCover = { null },
                parallelism = kotlinx.coroutines.flow.MutableStateFlow(limit),
            )
            val names = List(if (limit == 1) 8 else 9) { "o$limit-$it.mp3" }
            names.forEach { importer.enqueue(file(it, 1).path, it) }
            withTimeout(10_000) { importer.jobs.first { jobs -> jobs.size == names.size && jobs.all { it.status == ImportStatus.Done } } }
            assertEquals(names, target.starts.toList(), "start order at limit $limit")
        }
    }
}
