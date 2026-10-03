package id.homebase.soundhouse.live

import id.homebase.api.video.FFmpegBinaryManager
import id.homebase.soundhouse.data.AudioTrackContent
import id.homebase.soundhouse.data.toAudioTrackOrNull
import id.homebase.soundhouse.download.DownloadStore
import id.homebase.soundhouse.playback.AudioStreamServer
import id.homebase.soundhouse.playback.DefaultTrackLocator
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
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/** Download for offline, play from the local copy, then remove it and fall back to streaming. */
class LiveDownloadTest {
    private val workDir: File = Files.createTempDirectory("hba-live-dl").toFile()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val server = AudioStreamServer()

    @AfterTest
    fun tearDown() {
        runBlocking { server.stop() }
        scope.cancel()
        workDir.deleteRecursively()
    }

    @Test
    fun `offline copy round trip`() = runBlocking {
        val session = LiveSession.requireOrSkip()
        val api = session.audioDriveApi(File(workDir, "cache"))
        purgeTagged(api)
        val original = fixtureBytes("tone.mp3")
        try {
            val source = File(workDir, "tone.mp3").apply { writeBytes(original) }
            val uploaded = api.uploadTrack(
                source.absolutePath,
                AudioTrackContent("liveDownload ${Uuid.random().toString().take(8)}", original.size.toLong(), "audio/mpeg"),
                tags = listOf(LIVE_TEST_TAG),
            )
            val track = assertNotNull(api.getTrackFile(uploaded.fileId)?.toAudioTrackOrNull())
            val downloads = DownloadStore(
                directory = File(workDir, "downloads").absolutePath,
                downloader = { t, path, onProgress -> api.downloadTo(t, path, onProgress) },
                scope = scope,
                fileSystem = FileSystem.SYSTEM,
            )
            val locator = DefaultTrackLocator(downloads, server, api)
            assertTrue(locator.locate(track).startsWith("http://127.0.0.1:"), "streams before downloading")

            downloads.download(track)
            withTimeout(60_000) { downloads.downloaded.first { track.fileId in it } }
            val local = locator.locate(track)
            assertTrue(File(local).isFile, "a downloaded track should play from a local file")
            assertContentEquals(original, File(local).readBytes())
            val process = ProcessBuilder(FFmpegBinaryManager.ffmpegPath(), "-v", "error", "-ss", "2", "-i", local, "-f", "null", "-")
                .redirectErrorStream(true).start()
            val output = process.inputStream.bufferedReader().readText()
            assertTrue(process.waitFor(30, TimeUnit.SECONDS) && process.exitValue() == 0 && output.isBlank(), "ffmpeg: $output")

            downloads.remove(track.fileId)
            assertTrue(!File(local).exists())
            assertTrue(locator.locate(track).startsWith("http://127.0.0.1:"), "streams again after the copy is removed")
        } finally {
            purgeTagged(api)
        }
    }
}
