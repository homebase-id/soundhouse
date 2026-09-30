package id.homebase.audio.ui.library

import id.homebase.audio.data.AudioTrack

enum class LibrarySort { Newest, TitleAscending, TitleDescending }

fun arrangeTracks(tracks: List<AudioTrack>, query: String, sort: LibrarySort): List<AudioTrack> {
    val needle = query.trim()
    val matching = if (needle.isEmpty()) tracks else tracks.filter { it.title.contains(needle, ignoreCase = true) }
    return when (sort) {
        LibrarySort.Newest -> matching.sortedByDescending { it.dateAddedMs }
        LibrarySort.TitleAscending -> matching.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })
        LibrarySort.TitleDescending -> matching.sortedWith(compareByDescending(String.CASE_INSENSITIVE_ORDER) { it.title })
    }
}
