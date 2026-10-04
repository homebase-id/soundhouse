package id.homebase.soundhouse.importing

data class AudioFileMetadata(
    val title: String?,
    val durationMs: Long?,
    val quality: AudioQuality? = null,
    val details: TrackDetails? = null,
    val notes: String? = null,
)

/** Title, duration, stream format and tags straight from the container; any may be null when the format doesn't say. */
expect suspend fun readAudioMetadata(path: String): AudioFileMetadata

/** The embedded cover image (JPEG/PNG bytes) if the file carries one. */
expect suspend fun readCoverArt(path: String): ByteArray?

/** Extensions this platform's player can decode. */
expect val playableExtensions: Set<String>

private val mimeByExtension = mapOf(
    "mp3" to "audio/mpeg",
    "m4a" to "audio/mp4",
    "mp4" to "audio/mp4",
    "aac" to "audio/aac",
    "wav" to "audio/wav",
    "ogg" to "audio/ogg",
    "oga" to "audio/ogg",
    "opus" to "audio/ogg",
    "flac" to "audio/flac",
)

val allAudioExtensions: Set<String> = mimeByExtension.keys

fun extensionOf(fileName: String): String = fileName.substringAfterLast('.', "").lowercase()

fun mimeTypeForFileName(fileName: String): String = mimeByExtension[extensionOf(fileName)] ?: "application/octet-stream"

fun extensionForMimeType(mimeType: String): String? =
    mimeByExtension.entries.firstOrNull { it.value == mimeType }?.key

/** The embedded title when there is a real one, otherwise the file name without its extension. */
fun trackTitle(metadata: AudioFileMetadata, fileName: String): String =
    metadata.title?.trim()?.takeIf { it.isNotEmpty() }
        ?: fileName.substringBeforeLast('.').ifBlank { fileName }
