package id.homebase.soundhouse.download

import id.homebase.api.client.KeyHeader
import id.homebase.soundhouse.data.AudioTrack
import id.homebase.soundhouse.data.AudioTrackContent
import kotlinx.coroutines.CompletableDeferred
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

class DownloadStoreTest {
    private val dir: File = Files.createTempDirectory("hba-downloads").toFile()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @AfterTest
    fun tearDown() {
        scope.cancel()
        dir.deleteRecursively()
    }

    private fun track(size: Long, fileId: Uuid = Uuid.random()) = AudioTrack(
        fileId, null, AudioTrackContent("t", size, "audio/mpeg"), 0, null, emptyList(), KeyHeader.empty(), KeyHeader.empty(),
    )

    private fun store(writeBytes: Int?, gate: CompletableDeferred<Unit>? = null) = DownloadStore(
        directory = dir.absolutePath,
        downloader = { _, path, onProgress ->
            gate?.await()
            onProgress(0.5f)
            if (writeBytes == null) error("network gone")
            File(path).writeBytes(ByteArray(writeBytes))
            true
        },
        scope = scope,
        fileSystem = FileSystem.SYSTEM,
    )

    @Test
    fun `a complete download becomes the local copy`() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        val store = store(writeBytes = 100, gate = gate)
        val track = track(100)
        store.download(track)
        assertTrue(track.fileId in store.inProgress.value)
        gate.complete(Unit)
        withTimeout(5_000) { store.downloaded.first { track.fileId in it } }
        val path = assertNotNull(store.localPathFor(track))
        assertTrue(path.endsWith("${track.fileId}.mp3"))
        assertEquals(100, File(path).length())
        assertTrue(store.inProgress.value.isEmpty())
        assertTrue(dir.listFiles()!!.none { it.name.endsWith(".part") })
    }

    @Test
    fun `a short or failed download leaves nothing behind`() = runBlocking {
        val short = store(writeBytes = 40)
        val track = track(100)
        short.download(track)
        withTimeout(5_000) { short.failures.first { track.fileId in it } }
        assertNull(short.localPathFor(track))
        assertTrue(dir.listFiles()!!.isEmpty())

        val broken = store(writeBytes = null)
        val other = track(10)
        broken.download(other)
        withTimeout(5_000) { broken.failures.first { other.fileId in it } }
        assertTrue(dir.listFiles()!!.isEmpty())
    }

    @Test
    fun `remove deletes the copy and a restart finds existing copies`() = runBlocking {
        val track = track(10)
        val first = store(writeBytes = 10)
        first.download(track)
        withTimeout(5_000) { first.downloaded.first { track.fileId in it } }
        File(dir, "stale.part").writeText("x")

        val restarted = store(writeBytes = 10)
        withTimeout(5_000) { restarted.downloaded.first { track.fileId in it } }
        assertNotNull(restarted.localPathFor(track))
        assertTrue(!File(dir, "stale.part").exists())

        restarted.remove(track.fileId)
        assertNull(restarted.localPathFor(track))
        assertTrue(dir.listFiles()!!.isEmpty())
    }

    @Test
    fun `a copy whose size no longer matches the track is not used`() = runBlocking {
        val store = store(writeBytes = 10)
        val track = track(10)
        store.download(track)
        withTimeout(5_000) { store.downloaded.first { track.fileId in it } }
        assertNull(store.localPathFor(track(size = 11, fileId = track.fileId)))
    }

    @Test
    fun `the startup rescan never deletes a download that is in flight`() = runBlocking {
        val listGate = java.util.concurrent.CountDownLatch(1)
        val slowListing = object : okio.ForwardingFileSystem(FileSystem.SYSTEM) {
            override fun list(dir: okio.Path): List<okio.Path> {
                listGate.await()
                return super.list(dir)
            }
        }
        val partWritten = CompletableDeferred<Unit>()
        val finishDownload = CompletableDeferred<Unit>()
        dir.mkdirs()
        val store = DownloadStore(
            directory = dir.absolutePath,
            downloader = { _, path, _ ->
                File(path).writeBytes(ByteArray(10))
                partWritten.complete(Unit)
                finishDownload.await()
                true
            },
            scope = scope,
            fileSystem = slowListing,
        )
        val track = track(10)
        store.download(track)
        // Let the rescan list the directory while the download may be holding a .part file.
        kotlinx.coroutines.withTimeoutOrNull(1_000) { partWritten.await() }
        listGate.countDown()
        kotlinx.coroutines.delay(200)
        finishDownload.complete(Unit)
        withTimeout(5_000) { store.downloaded.first { track.fileId in it } }
        assertNotNull(store.localPathFor(track))
        Unit
    }
}
