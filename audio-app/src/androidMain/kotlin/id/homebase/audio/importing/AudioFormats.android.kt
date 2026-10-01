package id.homebase.audio.importing

import android.media.MediaMetadataRetriever
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

actual suspend fun readAudioMetadata(path: String): AudioFileMetadata = withContext(Dispatchers.IO) {
    val retriever = MediaMetadataRetriever()
    try {
        retriever.setDataSource(path)
        AudioFileMetadata(
            title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE),
            durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull(),
        )
    } catch (_: RuntimeException) {
        // setDataSource throws for containers the platform can't parse; the filename is the fallback.
        AudioFileMetadata(null, null)
    } finally {
        retriever.release()
    }
}

actual val playableExtensions: Set<String> = allAudioExtensions
