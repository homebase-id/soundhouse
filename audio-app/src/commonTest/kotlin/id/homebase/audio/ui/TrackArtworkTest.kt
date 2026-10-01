package id.homebase.audio.ui

import androidx.compose.material3.lightColorScheme
import id.homebase.audio.ui.common.artworkGlyph
import id.homebase.audio.ui.common.artworkPalette
import id.homebase.audio.ui.common.artworkVariant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TrackArtworkTest {
    @Test
    fun `glyph is the first character uppercased and never half an emoji`() {
        assertEquals("M", artworkGlyph("  morning walk"))
        assertEquals("🎵", artworkGlyph("🎵 tune"))
        assertEquals("♪", artworkGlyph("   "))
        assertEquals("Ü", artworkGlyph("über"))
    }

    @Test
    fun `the same seed always picks the same artwork and seeds spread out`() {
        val seeds = List(200) { "track-$it" }
        assertEquals(seeds.map(::artworkVariant), seeds.map(::artworkVariant))
        assertTrue(seeds.all { artworkVariant(it) in 0 until 32 })
        assertTrue(seeds.map(::artworkVariant).toSet().size > 16, "variants should spread across palettes")
        val scheme = lightColorScheme()
        assertEquals(scheme.artworkPalette("a"), scheme.artworkPalette("a"))
    }
}
