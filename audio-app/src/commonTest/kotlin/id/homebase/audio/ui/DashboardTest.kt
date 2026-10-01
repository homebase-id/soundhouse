package id.homebase.audio.ui

import id.homebase.api.client.KeyHeader
import id.homebase.audio.data.AudioTrack
import id.homebase.audio.data.AudioTrackContent
import id.homebase.audio.history.ListenEntry
import id.homebase.audio.ui.common.RelativeTime
import id.homebase.audio.ui.common.relativeTime
import id.homebase.audio.ui.home.dashboardSections
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.uuid.Uuid

class DashboardTest {
    private fun track(title: String, added: Long) = AudioTrack(
        Uuid.random(), null, AudioTrackContent(title, 1, "audio/mpeg", 600_000), added, null, emptyList(),
        KeyHeader.empty(), KeyHeader.empty(),
    )

    private fun entry(track: AudioTrack, played: Long, position: Long, finished: Boolean = false) =
        track.fileId.toString() to ListenEntry(track.fileId.toString(), played, position, 600_000, finished)

    @Test
    fun `sections come from history joined with the library`() {
        val a = track("a", 1)
        val b = track("b", 2)
        val c = track("c", 3)
        val gone = track("deleted", 4)
        val history = mapOf(
            entry(a, played = 300, position = 120_000),
            entry(b, played = 200, position = 599_000, finished = true),
            entry(c, played = 100, position = 2_000),
            entry(gone, played = 400, position = 100_000),
        )
        val (continueListening, recent, added) = dashboardSections(listOf(a, b, c), history)
        assertEquals(listOf("a"), continueListening.map { it.track.title })
        assertEquals(listOf("a", "b", "c"), recent.map { it.track.title })
        assertEquals(listOf("c", "b", "a"), added.map { it.title })
    }

    @Test
    fun `relative times`() {
        val now = 10L * 24 * 3_600_000
        assertEquals(RelativeTime.JustNow, relativeTime(now - 30_000, now))
        assertEquals(RelativeTime.Minutes(5), relativeTime(now - 5 * 60_000, now))
        assertEquals(RelativeTime.Hours(3), relativeTime(now - 3 * 3_600_000, now))
        assertEquals(RelativeTime.Yesterday, relativeTime(now - 30 * 3_600_000, now))
        assertEquals(RelativeTime.Days(4), relativeTime(now - 4 * 24 * 3_600_000, now))
        assertEquals(RelativeTime.On(0), relativeTime(0, now))
    }
}
