package id.homebase.audio.data

import id.homebase.api.client.KeyHeader
import id.homebase.api.client.drives.HomebaseFile
import id.homebase.api.serialization.OdinSystemSerializer
import id.homebase.core.config.audioLabeledDrive
import kotlinx.serialization.Serializable
import kotlin.io.encoding.Base64
import kotlin.uuid.Uuid

val audioDriveId: Uuid get() = audioLabeledDrive.drive.alias

const val AUDIO_TRACK_FILE_TYPE = 4410

// Server rule: ^[a-z0-9_]{8,10}$
const val AUDIO_PAYLOAD_KEY = "audiotrack"

@Serializable
enum class TrackOrigin { Imported, Recorded }

/** Encrypted into the file's appData.content. */
@Serializable
data class AudioTrackContent(
    val title: String,
    val sizeBytes: Long,
    val mimeType: String,
    val durationMs: Long? = null,
    val fileName: String? = null,
    val origin: TrackOrigin = TrackOrigin.Imported,
)

class AudioTrack(
    val fileId: Uuid,
    val uniqueId: Uuid?,
    val content: AudioTrackContent,
    val dateAddedMs: Long,
    val versionTag: Uuid?,
    val tags: List<Uuid>,
    val keyHeader: KeyHeader,
    /** File key with the payload's own IV; the header IV rotates on every update, the payload's doesn't. */
    val payloadKeyHeader: KeyHeader,
) {
    val title: String get() = content.title
    val durationMs: Long? get() = content.durationMs
    val sizeBytes: Long get() = content.sizeBytes
    val mimeType: String get() = content.mimeType

    override fun equals(other: Any?): Boolean =
        other is AudioTrack && fileId == other.fileId && versionTag == other.versionTag && content == other.content

    override fun hashCode(): Int = 31 * fileId.hashCode() + (versionTag?.hashCode() ?: 0)

    override fun toString(): String = "AudioTrack($fileId, ${content.title})"
}

fun HomebaseFile.toAudioTrackOrNull(): AudioTrack? {
    if (isSoftDeleted()) return null
    val appData = fileMetadata.appData
    if (appData.fileType != AUDIO_TRACK_FILE_TYPE) return null
    val payload = fileMetadata.payloads.orEmpty().firstOrNull { it.key == AUDIO_PAYLOAD_KEY } ?: return null
    val payloadIv = payload.iv?.let { runCatching { Base64.decode(it) }.getOrNull() } ?: keyHeader.iv
    val json = appData.content ?: return null
    val content = runCatching { OdinSystemSerializer.deserialize<AudioTrackContent>(json) }.getOrNull() ?: return null
    return AudioTrack(
        fileId = fileId,
        uniqueId = appData.uniqueId,
        content = content,
        dateAddedMs = fileMetadata.created.milliseconds,
        versionTag = fileMetadata.versionTag,
        tags = appData.tags.orEmpty(),
        keyHeader = keyHeader,
        payloadKeyHeader = KeyHeader(iv = payloadIv, aesKey = keyHeader.aesKey),
    )
}
