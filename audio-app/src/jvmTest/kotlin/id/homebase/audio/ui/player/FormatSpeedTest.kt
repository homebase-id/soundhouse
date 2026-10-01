package id.homebase.audio.ui.player

import kotlin.test.Test
import kotlin.test.assertEquals

class FormatSpeedTest {
    @Test
    fun `speeds drop trailing zeros`() {
        assertEquals(listOf("0.5", "0.75", "1", "1.25", "1.5", "2"), listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f).map(::formatSpeed))
    }
}
