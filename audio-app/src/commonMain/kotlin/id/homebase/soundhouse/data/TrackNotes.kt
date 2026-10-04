package id.homebase.soundhouse.data

import id.homebase.api.util.truncateToCodePoints

// Free text too big for the header (MAX_APP_DATA_CONTENT_LENGTH): liner notes, a set's tracklist.
// Server rule for payload keys: ^[a-z0-9_]{8,10}$
const val NOTES_PAYLOAD_KEY = "tracknote"
const val NOTES_CONTENT_TYPE = "text/plain"
const val MAX_NOTES_CODE_POINTS = 100_000

/** What an update does to the notes payload. */
sealed interface NotesChange {
    data object Keep : NotesChange
    data class Set(val text: String) : NotesChange
    data object Remove : NotesChange
}

/** Trimmed and capped; null when there's nothing to keep. */
fun cleanNotes(text: String?): String? = text?.trim()?.takeIf { it.isNotEmpty() }?.truncateToCodePoints(MAX_NOTES_CODE_POINTS)
