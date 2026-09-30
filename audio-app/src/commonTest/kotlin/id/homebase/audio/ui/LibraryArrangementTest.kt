package id.homebase.audio.ui

import id.homebase.api.client.KeyHeader
import id.homebase.audio.data.AudioTrack
import id.homebase.audio.data.AudioTrackContent
import id.homebase.audio.ui.library.LibrarySort
import id.homebase.audio.ui.library.arrangeTracks
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.uuid.Uuid

class LibraryArrangementTest {
    private fun track(title: String, added: Long) = AudioTrack(
        fileId = Uuid.random(),
        uniqueId = null,
        content = AudioTrackContent(title = title, sizeBytes = 1, mimeType = "audio/mpeg"),
        dateAddedMs = added,
        versionTag = null,
        tags = emptyList(),
        keyHeader = KeyHeader.empty(),
        payloadKeyHeader = KeyHeader.empty(),
    )

    private val tracks = listOf(track("banana", 2), track("Apple", 3), track("cherry pie", 1))

    @Test
    fun `newest first by date added`() {
        assertEquals(listOf("Apple", "banana", "cherry pie"), arrangeTracks(tracks, "", LibrarySort.Newest).map { it.title })
    }

    @Test
    fun `title sort ignores case`() {
        assertEquals(listOf("Apple", "banana", "cherry pie"), arrangeTracks(tracks, "", LibrarySort.TitleAscending).map { it.title })
        assertEquals(listOf("cherry pie", "banana", "Apple"), arrangeTracks(tracks, "", LibrarySort.TitleDescending).map { it.title })
    }

    @Test
    fun `search matches any part of the title ignoring case and padding`() {
        assertEquals(listOf("cherry pie"), arrangeTracks(tracks, "  PIE ", LibrarySort.Newest).map { it.title })
        assertEquals(listOf("banana", "Apple"), arrangeTracks(tracks, "a", LibrarySort.TitleDescending).map { it.title })
        assertEquals(emptyList(), arrangeTracks(tracks, "zzz", LibrarySort.Newest))
    }
}
