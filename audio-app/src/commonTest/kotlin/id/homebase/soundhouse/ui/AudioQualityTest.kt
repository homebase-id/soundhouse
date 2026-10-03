package id.homebase.soundhouse.ui

import id.homebase.soundhouse.importing.AudioQuality
import id.homebase.soundhouse.importing.Codecs
import id.homebase.soundhouse.importing.QualityTier
import id.homebase.soundhouse.importing.canonicalCodec
import id.homebase.soundhouse.importing.codecForMime
import id.homebase.soundhouse.importing.formatKilohertz
import id.homebase.soundhouse.importing.formatMegabytes
import id.homebase.soundhouse.importing.qualityFromMimeType
import id.homebase.soundhouse.importing.tier
import kotlin.test.Test
import kotlin.test.assertEquals

class AudioQualityTest {
    @Test
    fun `tier follows codec then depth and rate`() {
        assertEquals(QualityTier.Lossy, AudioQuality(Codecs.MP3, 48_000, null, 2, 320_000).tier)
        assertEquals(QualityTier.Lossless, AudioQuality(Codecs.FLAC, 44_100, 16).tier)
        assertEquals(QualityTier.Lossless, AudioQuality(Codecs.ALAC, 48_000, 16).tier)
        assertEquals(QualityTier.HiRes, AudioQuality(Codecs.FLAC, 44_100, 24).tier)
        assertEquals(QualityTier.HiRes, AudioQuality(Codecs.PCM, 96_000, 16).tier)
        assertEquals(QualityTier.Lossless, AudioQuality(Codecs.FLAC).tier)
        assertEquals(null, AudioQuality().tier)
    }

    @Test
    fun `platform codec names collapse to canonical ones`() {
        assertEquals(Codecs.PCM, canonicalCodec("pcm_s24le"))
        assertEquals(Codecs.FLAC, canonicalCodec("FLAC"))
        assertEquals(Codecs.AAC, codecForMime("audio/mp4a-latm"))
        assertEquals(Codecs.ALAC, codecForMime("audio/alac"))
        assertEquals(Codecs.PCM, codecForMime("audio/raw"))
    }

    @Test
    fun `mime type alone only answers when the container is unambiguous`() {
        assertEquals(QualityTier.Lossless, qualityFromMimeType("audio/flac")?.tier)
        assertEquals(QualityTier.Lossy, qualityFromMimeType("audio/mpeg")?.tier)
        assertEquals(null, qualityFromMimeType("audio/mp4"))
        assertEquals(null, qualityFromMimeType("audio/ogg"))
    }

    @Test
    fun `numbers format without trailing zeros`() {
        assertEquals("44.1", formatKilohertz(44_100))
        assertEquals("96", formatKilohertz(96_000))
        assertEquals("22.05", formatKilohertz(22_050))
        assertEquals("4.3", formatMegabytes(4_250_000))
        assertEquals("0.1", formatMegabytes(120_000))
    }
}
