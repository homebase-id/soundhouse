package id.homebase.soundhouse.data

import id.homebase.api.client.KeyHeader
import id.homebase.api.serialization.OdinSystemSerializer
import id.homebase.soundhouse.importing.TrackDetails
import kotlinx.coroutines.runBlocking
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HeaderBudgetTest {
    @Test
    fun `the size formula matches real encryption and base64`() = runBlocking {
        val key = KeyHeader.newRandom16()
        for (size in listOf(1, 15, 16, 17, 31, 32, 100, 1_000, 7_663, 7_664, 7_680)) {
            val sent = Base64.encode(key.encryptDataAes(ByteArray(size)))
            assertEquals(sent.length, encryptedContentLength(size), "plaintext $size")
        }
    }

    @Test
    fun `ordinary content is left as it is`() {
        val content = AudioTrackContent("Sinnerman", 1, "audio/mpeg", details = TrackDetails(artist = "Nina Simone", comment = "Live"))
        assertEquals(content, content.fittedToHeader())
    }

    @Test
    fun `everything at its cap in four-byte characters still fits, title kept`() {
        val emoji = "🎵"
        fun many(n: Int) = emoji.repeat(n)
        val content = AudioTrackContent(
            title = many(500),
            sizeBytes = 1,
            mimeType = "audio/mpeg",
            fileName = many(500),
            details = TrackDetails(
                artist = many(200), album = many(200), albumArtist = many(200), date = many(200),
                genre = many(200), composer = many(200), comment = many(1_000),
            ),
        )
        assertTrue(!fitsAppDataContent(OdinSystemSerializer.serialize(content)), "the case should start over the limit")
        val fitted = content.fittedToHeader()
        assertTrue(fitsAppDataContent(OdinSystemSerializer.serialize(fitted)))
        assertTrue(fitted.title.isNotEmpty())
        // The comment is the first thing to go.
        assertTrue((fitted.details?.comment?.length ?: 0) < content.details!!.comment!!.length)
        assertEquals(content.details!!.artist, fitted.details?.artist)
    }
}
