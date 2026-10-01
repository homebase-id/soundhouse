package id.homebase.audio.data

import id.homebase.api.client.drives.HomebaseFile
import kotlin.uuid.Uuid

/** Collection edits, written to the server first and then to the local index so the UI updates without waiting for sync. */
class CollectionManager(
    private val editor: CollectionEditor,
    private val collections: () -> List<AudioCollection>,
    private val tracks: () -> List<AudioTrack>,
    private val writeCollection: suspend (HomebaseFile) -> Unit,
    private val writeTrack: suspend (HomebaseFile) -> Unit,
    private val onTrackChanged: (AudioTrack) -> Unit,
) {
    suspend fun create(name: String): Uuid {
        val trimmed = name.trim()
        require(trimmed.isNotEmpty()) { "name must not be blank" }
        val id = Uuid.random()
        val fileId = editor.createCollection(trimmed, id)
        editor.getFile(fileId)?.let { writeCollection(it) }
        return id
    }

    suspend fun rename(collection: AudioCollection, newName: String) {
        val trimmed = newName.trim()
        require(trimmed.isNotEmpty()) { "name must not be blank" }
        if (trimmed == collection.name) return
        editor.renameCollection(collection, trimmed)
        editor.getFile(collection.fileId)?.let { writeCollection(it) }
    }

    /** Takes the collection's tag off its tracks first, so nothing points at a deleted collection. */
    suspend fun delete(collection: AudioCollection) {
        tracks().filter { it.isIn(collection) }.forEach { track ->
            writeTags(track, track.tags - collection.id)
        }
        editor.deleteCollection(collection.fileId)
        editor.getFile(collection.fileId)?.let { writeCollection(it) }
    }

    /** Makes [track] a member of exactly [selected] among the known collections. */
    suspend fun setMembership(track: AudioTrack, selected: Set<Uuid>) {
        val tags = retagged(track, collections(), selected)
        if (tags.toSet() == track.tags.toSet()) return
        writeTags(track, tags)
    }

    suspend fun add(tracks: List<AudioTrack>, collection: AudioCollection) {
        tracks.filterNot { it.isIn(collection) }.forEach { writeTags(it, it.tags + collection.id) }
    }

    suspend fun remove(track: AudioTrack, collection: AudioCollection) {
        if (track.isIn(collection)) writeTags(track, track.tags - collection.id)
    }

    private suspend fun writeTags(track: AudioTrack, tags: List<Uuid>) {
        editor.setTrackTags(track, tags)
        val header = editor.getFile(track.fileId) ?: return
        writeTrack(header)
        header.toAudioTrackOrNull()?.let(onTrackChanged)
    }
}
