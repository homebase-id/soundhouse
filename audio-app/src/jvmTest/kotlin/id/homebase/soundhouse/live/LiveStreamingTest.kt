package id.homebase.soundhouse.live

import id.homebase.api.video.FFmpegBinaryManager
import id.homebase.soundhouse.data.AudioTrackContent
import id.homebase.soundhouse.data.toAudioTrackOrNull
import id.homebase.soundhouse.playback.AudioStreamServer
import id.homebase.soundhouse.playback.RemoteTrackSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/** Real drive → loopback stream server → what the desktop player (ffmpeg) does with it. */
class LiveStreamingTest {
    private val workDir: File = Files.createTempDirectory("hba-live-stream").toFile()
    private val server = AudioStreamServer()

    @AfterTest
    fun tearDown() {
        runBlocking { server.stop() }
        workDir.deleteRecursively()
    }

    @Test
    fun `streams and seeks a drive track over the loopback server`() = runBlocking {
        val session = LiveSession.requireOrSkip()
        val api = session.audioDriveApi(File(workDir, "cache"))
        purgeTagged(api)
        val original = fixtureBytes("tone.mp3")
        try {
            val source = File(workDir, "tone.mp3").apply { writeBytes(original) }
            val uploaded = api.uploadTrack(
                source.absolutePath,
                AudioTrackContent("liveStream ${Uuid.random().toString().take(8)}", original.size.toLong(), "audio/mpeg"),
                tags = listOf(LIVE_TEST_TAG),
            )
            val track = assertNotNull(api.getTrackFile(uploaded.fileId)?.toAudioTrackOrNull())
            val url = server.urlFor(track.fileId.toString(), RemoteTrackSource(api, track))

            val head = httpGet(url, "bytes=0-32767")
            assertEquals(206, head.first)
            assertContentEquals(original.copyOfRange(0, 32_768), head.second)
            val mid = original.size / 2 + 3
            val tail = httpGet(url, "bytes=$mid-")
            assertContentEquals(original.copyOfRange(mid, original.size), tail.second)

            val duration = run(FFmpegBinaryManager.ffprobePath(), "-v", "error", "-show_entries", "format=duration", "-of", "csv=p=0", url)
            assertTrue(duration.trim().toDouble() in 5.9..6.2, "ffprobe duration '$duration'")
            assertEquals("", run(FFmpegBinaryManager.ffmpegPath(), "-v", "error", "-ss", "4", "-i", url, "-f", "null", "-").trim())
        } finally {
            purgeTagged(api)
        }
    }

    private suspend fun httpGet(url: String, range: String): Pair<Int, ByteArray> = withContext(Dispatchers.IO) {
        val connection = URI(url).toURL().openConnection() as HttpURLConnection
        connection.setRequestProperty("Range", range)
        connection.responseCode to connection.inputStream.use { it.readBytes() }
    }

    private suspend fun run(vararg command: String): String = withContext(Dispatchers.IO) {
        val process = ProcessBuilder(*command).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText()
        check(process.waitFor(60, TimeUnit.SECONDS)) { "${command.first()} timed out" }
        check(process.exitValue() == 0) { "${command.first()} exited ${process.exitValue()}: $output" }
        output
    }
}
