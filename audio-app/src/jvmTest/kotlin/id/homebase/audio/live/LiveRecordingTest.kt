package id.homebase.audio.live

import id.homebase.api.client.drives.HomebaseFile
import id.homebase.audio.data.TestFileOps
import id.homebase.audio.data.TrackOrigin
import id.homebase.audio.data.toAudioTrackOrNull
import id.homebase.audio.importing.ImportStatus
import id.homebase.audio.importing.TrackImporter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.file.Files
import javax.sound.sampled.AudioFileFormat
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioInputStream
import javax.sound.sampled.AudioSystem
import kotlin.math.PI
import kotlin.math.sin
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * A recorded clip goes through the save path the Record screen uses. The clip is synthesized in the
 * desktop recorder's exact output format (44.1 kHz 16-bit mono WAV), because a test run has no
 * microphone to record from.
 */
class LiveRecordingTest {
    private val workDir: File = Files.createTempDirectory("hba-live-rec").toFile()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @AfterTest
    fun tearDown() {
        scope.cancel()
        workDir.deleteRecursively()
    }

    @Test
    fun `recorded clip round trip`() = runBlocking {
        val session = LiveSession.requireOrSkip()
        val api = session.audioDriveApi(File(workDir, "cache"))
        purgeTagged(api)
        try {
            val clip = File(workDir, "recording-test.wav").apply { writeBytes(synthesizedRecording(seconds = 2)) }
            val original = clip.readBytes()
            val landed = mutableListOf<HomebaseFile>()
            val importer = TrackImporter(api, TestFileOps(workDir), scope, onUploaded = { landed += it })
            importer.enqueue(
                path = clip.absolutePath,
                fileName = "Live memo.wav",
                title = "Live memo",
                origin = TrackOrigin.Recorded,
                deleteSourceAfter = true,
                tags = listOf(LIVE_TEST_TAG),
            )
            val job = withTimeout(60_000) {
                importer.jobs.first { it.single().status.let { s -> s == ImportStatus.Done || s == ImportStatus.Failed } }
            }.single()
            assertEquals(ImportStatus.Done, job.status)
            assertFalse(clip.exists(), "the recording temp should be deleted after saving")

            val track = assertNotNull(landed.single().toAudioTrackOrNull())
            assertEquals("Live memo", track.title)
            assertEquals(TrackOrigin.Recorded, track.content.origin)
            assertEquals("audio/wav", track.mimeType)
            assertTrue(track.durationMs!! in 1_950..2_050, "duration ${track.durationMs}")

            val downloaded = File(workDir, "back.wav")
            assertTrue(api.downloadTo(track, downloaded.absolutePath))
            assertContentEquals(original, downloaded.readBytes())
            assertContentEquals(original.copyOfRange(44, 4_044), api.readRange(track, 44, 4_000))
        } finally {
            purgeTagged(api)
        }
    }

    private fun synthesizedRecording(seconds: Int): ByteArray {
        val format = AudioFormat(44_100f, 16, 1, true, false)
        val samples = 44_100 * seconds
        val pcm = ByteArray(samples * 2)
        for (i in 0 until samples) {
            val value = (sin(2 * PI * 330 * i / 44_100) * 8_000).toInt()
            pcm[2 * i] = value.toByte()
            pcm[2 * i + 1] = (value shr 8).toByte()
        }
        val out = File(workDir, "synth.wav")
        AudioSystem.write(AudioInputStream(ByteArrayInputStream(pcm), format, samples.toLong()), AudioFileFormat.Type.WAVE, out)
        return out.readBytes().also { out.delete() }
    }
}
