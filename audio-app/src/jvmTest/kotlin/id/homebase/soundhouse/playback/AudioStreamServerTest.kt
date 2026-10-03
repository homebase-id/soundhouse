package id.homebase.soundhouse.playback

import id.homebase.api.video.FFmpegBinaryManager
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URI
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AudioStreamServerTest {
    private val server = AudioStreamServer(chunkSize = 1024)

    @AfterTest
    fun tearDown() = runBlocking { server.stop() }

    private class MemorySource(private val bytes: ByteArray, override val mimeType: String = "audio/mpeg") : TrackByteSource {
        val reads = AtomicInteger()
        override val size: Long get() = bytes.size.toLong()
        override suspend fun read(start: Long, length: Long): ByteArray {
            reads.incrementAndGet()
            return bytes.copyOfRange(start.toInt(), (start + length).toInt())
        }
    }

    private class Response(val status: Int, val headers: Map<String, String?>, val body: ByteArray)

    private suspend fun get(url: String, range: String? = null, method: String = "GET") = withContext(Dispatchers.IO) {
        val connection = URI(url).toURL().openConnection() as HttpURLConnection
        connection.requestMethod = method
        range?.let { connection.setRequestProperty("Range", it) }
        val status = connection.responseCode
        val body = if (status < 400 && method == "GET") connection.inputStream.use { it.readBytes() } else ByteArray(0)
        Response(
            status,
            listOf("Accept-Ranges", "Content-Range", "Content-Length", "Content-Type").associateWith { connection.getHeaderField(it) },
            body,
        )
    }

    private val payload = Random(7).nextBytes(10_000)

    @Test
    fun `whole and ranged reads return the right bytes`() = runBlocking {
        val url = server.urlFor("t1", MemorySource(payload))

        val whole = get(url)
        assertEquals(200, whole.status)
        assertEquals("bytes", whole.headers["Accept-Ranges"])
        assertEquals("audio/mpeg", whole.headers["Content-Type"])
        assertContentEquals(payload, whole.body)

        val part = get(url, "bytes=2500-6999")
        assertEquals(206, part.status)
        assertEquals("bytes 2500-6999/10000", part.headers["Content-Range"])
        assertContentEquals(payload.copyOfRange(2500, 7000), part.body)

        val tail = get(url, "bytes=9000-")
        assertContentEquals(payload.copyOfRange(9000, 10_000), tail.body)

        val head = get(url, "bytes=0-", method = "HEAD")
        assertEquals(206, head.status)
        assertEquals("10000", head.headers["Content-Length"])
    }

    @Test
    fun `unknown paths and impossible ranges are refused`() = runBlocking {
        val url = server.urlFor("t1", MemorySource(payload))
        assertEquals(404, get(url.replaceAfterLast('/', "nope")).status)
        assertEquals(404, get(url.replace(Regex("/[0-9a-f]{32}/"), "/${"0".repeat(32)}/")).status)
        val refused = get(url, "bytes=20000-")
        assertEquals(416, refused.status)
        server.unregister("t1")
        assertEquals(404, get(url).status)
    }

    @Test
    fun `first bytes arrive before later chunks have been fetched`() = runBlocking {
        val release = CompletableDeferred<Unit>()
        val source = object : TrackByteSource {
            override val size: Long = payload.size.toLong()
            override val mimeType: String = "audio/mpeg"
            override suspend fun read(start: Long, length: Long): ByteArray {
                if (start >= 2048) release.await()
                return payload.copyOfRange(start.toInt(), (start + length).toInt())
            }
        }
        val url = server.urlFor("slow", source)
        val firstBytes = withContext(Dispatchers.IO) {
            val connection = URI(url).toURL().openConnection() as HttpURLConnection
            connection.readTimeout = 5_000
            val buffer = ByteArray(2048)
            val input = connection.inputStream
            var read = 0
            while (read < buffer.size) read += input.read(buffer, read, buffer.size - read)
            release.complete(Unit)
            input.readBytes()
            buffer
        }
        assertContentEquals(payload.copyOfRange(0, 2048), firstBytes)
    }

    @Test
    fun `ffmpeg can probe and seek the stream`() = runBlocking {
        val mp3 = javaClass.getResourceAsStream("/fixtures/tone.mp3")!!.use { it.readBytes() }
        val source = MemorySource(mp3)
        val url = AudioStreamServer().let { real ->
            try {
                val streamUrl = real.urlFor("tone", source)
                val duration = run(FFmpegBinaryManager.ffprobePath(), "-v", "error", "-show_entries", "format=duration", "-of", "csv=p=0", streamUrl)
                assertTrue(duration.trim().toDouble() in 5.9..6.2, "ffprobe duration '$duration'")
                val seekDecode = run(FFmpegBinaryManager.ffmpegPath(), "-v", "error", "-ss", "4", "-i", streamUrl, "-f", "null", "-")
                assertEquals("", seekDecode.trim(), "ffmpeg reported errors decoding from a seek")
                streamUrl
            } finally {
                real.stop()
            }
        }
        assertTrue(url.startsWith("http://127.0.0.1:"))
    }

    private suspend fun run(vararg command: String): String = withContext(Dispatchers.IO) {
        val process = ProcessBuilder(*command).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText()
        check(process.waitFor(30, TimeUnit.SECONDS)) { "${command.first()} timed out" }
        check(process.exitValue() == 0) { "${command.first()} exited ${process.exitValue()}: $output" }
        output
    }
}
