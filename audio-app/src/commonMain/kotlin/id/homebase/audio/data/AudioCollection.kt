package id.homebase.audio.data

import id.homebase.api.client.KeyHeader
import id.homebase.api.client.drives.HomebaseFile
import id.homebase.api.serialization.OdinSystemSerializer
import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

const val AUDIO_COLLECTION_FILE_TYPE = 4411

/** Encrypted into the collection file's appData.content. Membership lives on the tracks, as a tag equal to [AudioCollection.id]. */
@Serializable
data class AudioCollectionContent(val name: String)

data class AudioCollection(
    val fileId: Uuid,
    /** The file's uniqueId, used as the tag on member tracks. */
    val id: Uuid,
    val name: String,
    val createdMs: Long,
    val versionTag: Uuid?,
    val keyHeader: KeyHeader,
)

fun HomebaseFile.toAudioCollectionOrNull(): AudioCollection? {
    if (isSoftDeleted()) return null
    val appData = fileMetadata.appData
    if (appData.fileType != AUDIO_COLLECTION_FILE_TYPE) return null
    val id = appData.uniqueId ?: return null
    val json = appData.content ?: return null
    val content = runCatching { OdinSystemSerializer.deserialize<AudioCollectionContent>(json) }.getOrNull() ?: return null
    return AudioCollection(fileId, id, content.name, fileMetadata.created.milliseconds, fileMetadata.versionTag, keyHeader)
}

fun AudioTrack.isIn(collection: AudioCollection): Boolean = collection.id in tags

/** [track]'s tags with its collection memberships replaced by [selected]; tags that aren't collections are kept. */
fun retagged(track: AudioTrack, known: Collection<AudioCollection>, selected: Set<Uuid>): List<Uuid> {
    val collectionIds = known.mapTo(HashSet()) { it.id }
    return track.tags.filterNot { it in collectionIds } + selected.filter { it in collectionIds }.sorted()
}
