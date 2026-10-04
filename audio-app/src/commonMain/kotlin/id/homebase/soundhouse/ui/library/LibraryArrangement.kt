package id.homebase.soundhouse.ui.library

import id.homebase.soundhouse.data.AudioTrack

enum class LibrarySort { Newest, TitleAscending, TitleDescending }

fun arrangeTracks(tracks: List<AudioTrack>, query: String, sort: LibrarySort): List<AudioTrack> {
    val needle = query.trim()
    val matching = if (needle.isEmpty()) tracks else tracks.filter { track ->
        track.title.contains(needle, ignoreCase = true) ||
            track.details?.searchable.orEmpty().any { it.contains(needle, ignoreCase = true) }
    }
    return when (sort) {
        LibrarySort.Newest -> matching.sortedByDescending { it.dateAddedMs }
        LibrarySort.TitleAscending -> matching.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })
        LibrarySort.TitleDescending -> matching.sortedWith(compareByDescending(String.CASE_INSENSITIVE_ORDER) { it.title })
    }
}
