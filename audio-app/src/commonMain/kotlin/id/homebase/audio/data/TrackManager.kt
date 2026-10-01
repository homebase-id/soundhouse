package id.homebase.audio.data

import id.homebase.api.client.drives.HomebaseFile
import kotlin.uuid.Uuid

/**
 * Rename and delete, applied to the server first and then to everything local that holds the track:
 * the drive index (so the list updates without waiting for sync), offline copies and the play queue.
 */
class TrackManager(
    private val editor: TrackEditor,
    private val writeLocal: suspend (HomebaseFile) -> Unit,
    private val removeDownload: suspend (Uuid) -> Unit,
    private val onRenamed: (AudioTrack) -> Unit,
    private val onDeleted: (Uuid) -> Unit,
) {
    suspend fun rename(track: AudioTrack, newTitle: String) {
        val title = newTitle.trim()
        require(title.isNotEmpty()) { "title must not be blank" }
        if (title == track.title) return
        editor.renameTrack(track, title)
        val header = editor.getTrackFile(track.fileId) ?: return
        writeLocal(header)
        header.toAudioTrackOrNull()?.let(onRenamed)
    }

    suspend fun delete(track: AudioTrack) {
        editor.deleteTrack(track.fileId)
        onDeleted(track.fileId)
        removeDownload(track.fileId)
        editor.getTrackFile(track.fileId)?.let { writeLocal(it) }
    }
}
