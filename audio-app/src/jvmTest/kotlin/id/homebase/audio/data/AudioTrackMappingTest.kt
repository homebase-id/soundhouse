package id.homebase.audio.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class AudioTrackMappingTest {
    private val content = AudioTrackContent(
        title = "Morning walk",
        sizeBytes = 123_456,
        mimeType = "audio/mpeg",
        durationMs = 61_000,
        fileName = "walk.mp3",
    )

    @Test
    fun `maps an active track file`() {
        val file = buildTrackFile(trackContentJson(content), createdMs = 1_700_000_000_000)
        val track = assertNotNull(file.toAudioTrackOrNull())
        assertEquals("Morning walk", track.title)
        assertEquals(61_000, track.durationMs)
        assertEquals(123_456, track.sizeBytes)
        assertEquals(1_700_000_000_000, track.dateAddedMs)
        assertEquals(file.fileId, track.fileId)
    }

    @Test
    fun `skips other file types`() {
        assertNull(buildTrackFile(trackContentJson(content), fileType = 1).toAudioTrackOrNull())
    }

    @Test
    fun `skips deleted files`() {
        assertNull(buildTrackFile(trackContentJson(content), fileState = "deleted").toAudioTrackOrNull())
    }

    @Test
    fun `skips files without the audio payload`() {
        assertNull(buildTrackFile(trackContentJson(content), payloadKeys = emptyList()).toAudioTrackOrNull())
    }

    @Test
    fun `skips unreadable content`() {
        assertNull(buildTrackFile("not json").toAudioTrackOrNull())
        assertNull(buildTrackFile(null).toAudioTrackOrNull())
    }

    @Test
    fun `ignores unknown content fields from newer versions`() {
        val json = """{"title":"x","sizeBytes":1,"mimeType":"audio/wav","futureField":true}"""
        assertEquals("x", assertNotNull(buildTrackFile(json).toAudioTrackOrNull()).title)
    }

    @Test
    fun `payload key header uses the payload's own IV`() {
        val iv = ByteArray(16) { (it + 1).toByte() }
        val file = buildTrackFile(trackContentJson(content), payloadIvBase64 = kotlin.io.encoding.Base64.encode(iv))
        val track = assertNotNull(file.toAudioTrackOrNull())
        kotlin.test.assertContentEquals(iv, track.payloadKeyHeader.iv)
        kotlin.test.assertContentEquals(ByteArray(16), track.keyHeader.iv)
    }

    @Test
    fun `payload key header falls back to the file IV`() {
        val track = assertNotNull(buildTrackFile(trackContentJson(content)).toAudioTrackOrNull())
        kotlin.test.assertContentEquals(track.keyHeader.iv, track.payloadKeyHeader.iv)
    }
}
