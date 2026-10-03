package id.homebase.soundhouse.ui

import androidx.compose.material3.lightColorScheme
import id.homebase.soundhouse.ui.common.soundprint
import id.homebase.soundhouse.ui.common.artworkPalette
import id.homebase.soundhouse.ui.common.artworkVariant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TrackArtworkTest {
    @Test
    fun `soundprints are stable per track bounded and differ between tracks`() {
        val a = soundprint("track-a", 19)
        assertEquals(a, soundprint("track-a", 19))
        assertEquals(19, a.size)
        assertTrue(a.all { it in 0.12f..1f })
        assertTrue(a != soundprint("track-b", 19))
        assertTrue(a.max() - a.min() > 0.2f, "a soundprint should have visible shape: $a")
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
