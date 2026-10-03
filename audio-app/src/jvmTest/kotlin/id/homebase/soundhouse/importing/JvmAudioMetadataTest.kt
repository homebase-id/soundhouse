package id.homebase.soundhouse.importing

import id.homebase.api.video.FFmpegBinaryManager
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class JvmAudioMetadataTest {
    @Test
    fun `ffprobe reads title and duration from the fixture`() = runBlocking {
        val file = Files.createTempFile("tone", ".mp3").toFile()
        try {
            javaClass.getResourceAsStream("/fixtures/tone.mp3")!!.use { input -> file.outputStream().use { input.copyTo(it) } }
            val metadata = readAudioMetadata(file.absolutePath)
            assertEquals("Live Fixture Tone", metadata.title)
            val duration = assertNotNull(metadata.durationMs)
            assertTrue(duration in 5_900..6_200, "duration $duration")
        } finally {
            file.delete()
        }
    }

    @Test
    fun `ffprobe reads the stream format of a lossy file`() = runBlocking {
        val file = fixture("tone.mp3")
        try {
            val quality = assertNotNull(readAudioMetadata(file.absolutePath).quality)
            assertEquals(Codecs.MP3, quality.codec)
            assertEquals(QualityTier.Lossy, quality.tier)
            assertTrue((quality.sampleRateHz ?: 0) > 0 && (quality.bitrateBps ?: 0) > 0, "$quality")
            assertEquals(null, quality.bitDepth)
        } finally {
            file.delete()
        }
    }

    @Test
    fun `ffprobe reads a 24-bit 96 kHz FLAC as hi-res`() = runBlocking {
        val file = Files.createTempFile("hires", ".flac").toFile()
        try {
            val encode = ProcessBuilder(
                FFmpegBinaryManager.ffmpegPath(), "-v", "error", "-y",
                "-f", "lavfi", "-i", "sine=frequency=440:sample_rate=96000:duration=1",
                "-ac", "2", "-sample_fmt", "s32", "-bits_per_raw_sample", "24", file.absolutePath,
            ).redirectErrorStream(true).start()
            assertEquals(0, encode.waitFor(), encode.inputStream.bufferedReader().readText())
            val quality = assertNotNull(readAudioMetadata(file.absolutePath).quality)
            assertEquals(AudioQuality(Codecs.FLAC, 96_000, 24, 2, quality.bitrateBps), quality)
            assertEquals(QualityTier.HiRes, quality.tier)
        } finally {
            file.delete()
        }
    }

    @Test
    fun `parses the first audio stream and falls back to the container bitrate`() {
        val parsed = parseFfprobeFormat(
            """{"streams":[{"codec_name":"pcm_s16le","sample_rate":"44100","channels":2,"bits_per_sample":16,"bits_per_raw_sample":"N/A"}],
               "format":{"duration":"1.0","bit_rate":"1411200"}}"""
        )
        assertEquals(AudioQuality(Codecs.PCM, 44_100, 16, 2, 1_411_200), parsed.quality)
    }

    @Test
    fun `unreadable files yield empty metadata`() = runBlocking {
        val file = File.createTempFile("junk", ".mp3").apply { writeText("not audio") }
        try {
            val metadata = readAudioMetadata(file.absolutePath)
            assertEquals(null, metadata.durationMs)
        } finally {
            file.delete()
        }
    }

    @Test
    fun `parses ffprobe json with any title key case`() {
        val parsed = parseFfprobeFormat("""{"format":{"duration":"12.5","tags":{"TITLE":"Upper"}}}""")
        assertEquals(AudioFileMetadata("Upper", 12_500), parsed)
        assertEquals(AudioFileMetadata(null, null), parseFfprobeFormat("garbage"))
    }

    @Test
    fun `cover art is extracted when present and absent otherwise`() = runBlocking {
        val withCover = fixture("tone-with-cover.mp3")
        val plain = fixture("tone.mp3")
        try {
            val cover = assertNotNull(readCoverArt(withCover.absolutePath))
            assertTrue(cover.size > 100, "cover too small: ${cover.size}")
            assertEquals(null, readCoverArt(plain.absolutePath))
        } finally {
            withCover.delete()
            plain.delete()
        }
    }

    private fun fixture(name: String): File = Files.createTempFile("fx", ".mp3").toFile().also { out ->
        javaClass.getResourceAsStream("/fixtures/$name")!!.use { input -> out.outputStream().use { input.copyTo(it) } }
    }
}
