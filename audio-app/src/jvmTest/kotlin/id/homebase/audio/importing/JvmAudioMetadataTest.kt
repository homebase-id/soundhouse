package id.homebase.audio.importing

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
