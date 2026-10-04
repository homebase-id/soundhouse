package id.homebase.soundhouse.importing

import id.homebase.api.util.truncateToCodePoints
import kotlinx.serialization.Serializable

/**
 * Descriptive tags for a track. Stored with the track's encrypted metadata, never written back into
 * the file. A track that has been read (or edited) has a non-null [TrackDetails] even when every
 * field is empty; null means it hasn't been read yet.
 */
@Serializable
data class TrackDetails(
    val artist: String? = null,
    val album: String? = null,
    val albumArtist: String? = null,
    val trackNumber: Int? = null,
    val trackTotal: Int? = null,
    val discNumber: Int? = null,
    val discTotal: Int? = null,
    val date: String? = null,
    val genre: String? = null,
    val composer: String? = null,
    val comment: String? = null,
) {
    /** The artist to show for the track: its own, else the album's. */
    val displayArtist: String? get() = artist ?: albumArtist

    /** "Artist — Album" for lists and the player; null when neither is known. */
    val creditLine: String? get() = listOfNotNull(displayArtist, album).joinToString(" — ").ifEmpty { null }

    /** Every text field, for search. */
    val searchable: List<String> get() = listOfNotNull(artist, albumArtist, album, genre, composer)

    /** Blank fields become null and long ones are cut, so edits and tags store the same way. */
    fun normalized(): TrackDetails = TrackDetails(
        artist = artist.clean(),
        album = album.clean(),
        albumArtist = albumArtist.clean(),
        trackNumber = trackNumber?.takeIf { it > 0 },
        trackTotal = trackTotal?.takeIf { it > 0 },
        discNumber = discNumber?.takeIf { it > 0 },
        discTotal = discTotal?.takeIf { it > 0 },
        date = date.clean(),
        genre = genre.clean(),
        composer = composer.clean(),
        comment = comment.clean(COMMENT_LIMIT),
    )
}

// Per-field caps keep ordinary tags well inside the header; AudioDriveApi still fits the total.
private const val FIELD_LIMIT = 200
private const val COMMENT_LIMIT = 1_000

private fun String?.clean(limit: Int = FIELD_LIMIT): String? =
    this?.trim()?.takeIf { it.isNotEmpty() }?.truncateToCodePoints(limit)

/**
 * Builds details from container tags. Keys are matched case-insensitively across the spellings
 * ID3 (via ffprobe), Vorbis comments and MP4 atoms use; "3/12" style numbers carry their total.
 */
fun trackDetailsFromTags(tags: Map<String, String>): TrackDetails {
    val byKey = tags.entries.associate { it.key.lowercase().replace(' ', '_') to it.value }
    fun first(vararg keys: String): String? = keys.firstNotNullOfOrNull { byKey[it]?.trim()?.takeIf(String::isNotEmpty) }
    val (track, trackOf) = numberPair(first("track", "tracknumber", "trck"))
    val (disc, discOf) = numberPair(first("disc", "discnumber", "disk", "tpos"))
    return TrackDetails(
        artist = first("artist", "tpe1"),
        album = first("album", "talb"),
        albumArtist = first("album_artist", "albumartist", "tpe2"),
        trackNumber = track,
        trackTotal = trackOf ?: first("tracktotal", "totaltracks")?.toIntOrNull(),
        discNumber = disc,
        discTotal = discOf ?: first("disctotal", "totaldiscs")?.toIntOrNull(),
        date = first("date", "year", "tdrc", "tyer", "originaldate"),
        genre = first("genre", "tcon"),
        composer = first("composer", "tcom"),
        comment = first("comment", "description", "comm"),
    ).normalized()
}

/** "3/12" → (3, 12); "3" → (3, null); anything else → (null, null). */
fun numberPair(value: String?): Pair<Int?, Int?> {
    val parts = value?.split('/')?.map { it.trim() } ?: return null to null
    return parts.getOrNull(0)?.toIntOrNull() to parts.getOrNull(1)?.toIntOrNull()
}
