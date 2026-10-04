package id.homebase.soundhouse.ui

import id.homebase.api.client.KeyHeader
import id.homebase.soundhouse.data.AudioTrack
import id.homebase.soundhouse.data.AudioTrackContent
import id.homebase.soundhouse.importing.TrackDetails
import id.homebase.soundhouse.importing.numberPair
import id.homebase.soundhouse.importing.trackDetailsFromTags
import id.homebase.soundhouse.ui.library.LibrarySort
import id.homebase.soundhouse.ui.library.arrangeTracks
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.uuid.Uuid

class TrackDetailsTest {
    @Test
    fun `tags map across id3 vorbis and mp4 spellings`() {
        val id3 = trackDetailsFromTags(
            mapOf("ARTIST" to " Nina Simone ", "album" to "Pastel Blues", "album_artist" to "Nina", "track" to "3/9",
                "disc" to "1/1", "date" to "1965", "genre" to "Jazz", "composer" to "Trad.", "comment" to "Remaster")
        )
        assertEquals(
            TrackDetails("Nina Simone", "Pastel Blues", "Nina", 3, 9, 1, 1, "1965", "Jazz", "Trad.", "Remaster"),
            id3,
        )
        val vorbis = trackDetailsFromTags(mapOf("ALBUMARTIST" to "V", "TRACKNUMBER" to "4", "TRACKTOTAL" to "10", "DISCNUMBER" to "2"))
        assertEquals(TrackDetails(albumArtist = "V", trackNumber = 4, trackTotal = 10, discNumber = 2), vorbis)
    }

    @Test
    fun `blank and odd values are dropped`() {
        assertEquals(TrackDetails(), trackDetailsFromTags(mapOf("artist" to "  ", "track" to "x/y", "genre" to "")))
        assertEquals(3 to null, numberPair("3"))
        assertEquals(null to null, numberPair(null))
    }

    @Test
    fun `credit line prefers the track artist and falls back to the album artist`() {
        assertEquals("A — LP", TrackDetails(artist = "A", album = "LP").creditLine)
        assertEquals("Band — LP", TrackDetails(albumArtist = "Band", album = "LP").creditLine)
        assertNull(TrackDetails(genre = "Jazz").creditLine)
    }

    @Test
    fun `search matches artist album genre and composer as well as title`() {
        fun track(title: String, details: TrackDetails?) = AudioTrack(
            Uuid.random(), null, AudioTrackContent(title, 1, "audio/mpeg", details = details), 0, null, emptyList(),
            KeyHeader.empty(), KeyHeader.empty(),
        )
        val tracks = listOf(
            track("Sinnerman", TrackDetails(artist = "Nina Simone", genre = "Jazz")),
            track("Clair de lune", TrackDetails(composer = "Debussy", album = "Suite bergamasque")),
            track("Voice memo", null),
        )
        assertEquals(listOf("Sinnerman"), arrangeTracks(tracks, "simone", LibrarySort.TitleAscending).map { it.title })
        assertEquals(listOf("Clair de lune"), arrangeTracks(tracks, "debussy", LibrarySort.TitleAscending).map { it.title })
        assertEquals(listOf("Clair de lune"), arrangeTracks(tracks, "bergamasque", LibrarySort.TitleAscending).map { it.title })
        assertEquals(listOf("Voice memo"), arrangeTracks(tracks, "memo", LibrarySort.TitleAscending).map { it.title })
    }
}
