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
    /** The user's edit: title is required, details replace what was there. */
    suspend fun editDetails(track: AudioTrack, title: String, details: TrackDetails) {
        val cleanTitle = title.trim()
        require(cleanTitle.isNotEmpty()) { "title must not be blank" }
        update(track, track.content.copy(title = cleanTitle, details = details.normalized()))
    }

    /** Fills only what the track doesn't have yet, so a read never overwrites an edit. */
    suspend fun fillMissing(track: AudioTrack, quality: AudioQuality?, details: TrackDetails?) {
        update(track, track.content.copy(quality = track.quality ?: quality, details = track.details ?: details))
    }

    private suspend fun update(track: AudioTrack, content: AudioTrackContent) {
        if (content == track.content) return
        editor.updateTrackContent(track, content)
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
