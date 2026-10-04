package id.homebase.soundhouse.data

import id.homebase.api.client.drives.HomebaseFile
import id.homebase.soundhouse.importing.AudioQuality
import id.homebase.soundhouse.importing.TrackDetails
import kotlin.uuid.Uuid

/**
 * Edits, deletes and metadata backfill, applied to the server first and then to everything local that holds the track:
 * the drive index (so the list updates without waiting for sync), offline copies and the play queue.
 */
class TrackManager(
    private val editor: TrackEditor,
    private val writeLocal: suspend (HomebaseFile) -> Unit,
    private val removeDownload: suspend (Uuid) -> Unit,
    private val onChanged: (AudioTrack) -> Unit,
    private val onDeleted: (Uuid) -> Unit,
) {
    /**
     * The user's edit: title is required, details replace what was there. [notes] null means the
     * notes weren't loaded, so they (and any comment an older version left in the header) stay as
     * they are; empty removes them.
     */
    suspend fun editDetails(track: AudioTrack, title: String, details: TrackDetails, notes: String?) {
        val cleanTitle = title.trim()
        require(cleanTitle.isNotEmpty()) { "title must not be blank" }
        val change = if (notes == null) NotesChange.Keep else notesChange(track, cleanNotes(notes))
        val headerComment = if (notes == null) track.details?.comment else null
        update(track, track.content.copy(title = cleanTitle, details = details.copy(comment = headerComment).normalized()), change)
    }

    /**
     * Fills only what the track doesn't have yet, so a read never overwrites an edit. Notes come
     * with the first read of the tags only: after that, no notes means the user removed them.
     */
    suspend fun fillMissing(track: AudioTrack, quality: AudioQuality?, details: TrackDetails?, notes: String? = null) {
        val firstRead = track.details == null && details != null
        val newNotes = cleanNotes(notes)?.takeIf { firstRead && !track.hasNotesPayload }
        update(
            track,
            track.content.copy(quality = track.quality ?: quality, details = track.details ?: details?.copy(comment = null)),
            newNotes?.let { NotesChange.Set(it) } ?: NotesChange.Keep,
        )
    }

    suspend fun readNotes(track: AudioTrack): String? = editor.readNotes(track)

    private suspend fun notesChange(track: AudioTrack, desired: String?): NotesChange = when {
        desired == null -> if (track.hasNotesPayload) NotesChange.Remove else NotesChange.Keep
        !track.hasNotesPayload -> NotesChange.Set(desired)
        desired == editor.readNotes(track) -> NotesChange.Keep
        else -> NotesChange.Set(desired)
    }

    private suspend fun update(track: AudioTrack, content: AudioTrackContent, notes: NotesChange = NotesChange.Keep) {
        if (content == track.content && notes == NotesChange.Keep) return
        editor.updateTrackContent(track, content, notes)
        val header = editor.getTrackFile(track.fileId) ?: return
        writeLocal(header)
        header.toAudioTrackOrNull()?.let(onChanged)
    }

    suspend fun delete(track: AudioTrack) {
        editor.deleteTrack(track.fileId)
        onDeleted(track.fileId)
        removeDownload(track.fileId)
        editor.getTrackFile(track.fileId)?.let { writeLocal(it) }
    }
}
