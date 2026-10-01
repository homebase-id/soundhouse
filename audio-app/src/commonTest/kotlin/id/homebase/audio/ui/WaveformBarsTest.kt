package id.homebase.audio.ui

import id.homebase.audio.ui.record.waveformBars
import kotlin.test.Test
import kotlin.test.assertEquals

class WaveformBarsTest {
    @Test
    fun `averages long recordings into buckets`() {
        assertEquals(listOf(0.5f, 1f), waveformBars(listOf(0f, 1f, 1f, 1f), 2))
    }

    @Test
    fun `stretches short recordings and handles empty`() {
        assertEquals(listOf(0.2f, 0.2f, 0.8f, 0.8f), waveformBars(listOf(0.2f, 0.8f), 4))
        assertEquals(emptyList(), waveformBars(emptyList(), 10))
    }
}
