package id.homebase.audio.importing

import android.media.AudioFormat
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.os.Build
import co.touchlab.kermit.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private fun MediaMetadataRetriever.open(path: String) {
    // The plain overload treats its argument as a file path; the loopback stream URL needs this one.
    if (path.startsWith("http://")) setDataSource(path, HashMap()) else setDataSource(path)
}

actual suspend fun readAudioMetadata(path: String): AudioFileMetadata = withContext(Dispatchers.IO) {
    val retriever = MediaMetadataRetriever()
    try {
        retriever.open(path)
        AudioFileMetadata(
            title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE),
            durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull(),
            quality = readQuality(path, retriever),
        )
    } catch (_: RuntimeException) {
        // setDataSource throws for containers the platform can't parse; the filename is the fallback.
        AudioFileMetadata(null, null)
    } finally {
        retriever.release()
    }
}

private fun readQuality(path: String, retriever: MediaMetadataRetriever): AudioQuality? {
    val extractor = MediaExtractor()
    try {
        extractor.setDataSource(path)
        val format = (0 until extractor.trackCount).map(extractor::getTrackFormat)
            .firstOrNull { it.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true }
            ?: return null
        val codec = format.getString(MediaFormat.KEY_MIME)?.let(::codecForMime)
        return AudioQuality(
            codec = codec,
            sampleRateHz = format.intOrNull(MediaFormat.KEY_SAMPLE_RATE),
            bitDepth = bitDepthOf(format, retriever),
            channels = format.intOrNull(MediaFormat.KEY_CHANNEL_COUNT),
            bitrateBps = format.intOrNull(MediaFormat.KEY_BIT_RATE)?.toLong()
                ?: retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toLongOrNull(),
        )
    } catch (e: Exception) {
        Logger.w(e, "AudioMetadata") { "MediaExtractor could not read the stream format" }
        return null
    } finally {
        extractor.release()
    }
}

private fun bitDepthOf(format: MediaFormat, retriever: MediaMetadataRetriever): Int? {
    when (format.intOrNull(MediaFormat.KEY_PCM_ENCODING)) {
        AudioFormat.ENCODING_PCM_8BIT -> return 8
        AudioFormat.ENCODING_PCM_16BIT -> return 16
        AudioFormat.ENCODING_PCM_24BIT_PACKED -> return 24
        AudioFormat.ENCODING_PCM_32BIT, AudioFormat.ENCODING_PCM_FLOAT -> return 32
    }
    format.intOrNull("bits-per-sample")?.takeIf { it > 0 }?.let { return it }
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
    return retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITS_PER_SAMPLE)?.toIntOrNull()?.takeIf { it > 0 }
}

private fun MediaFormat.intOrNull(key: String): Int? =
    if (containsKey(key)) runCatching { getInteger(key) }.getOrNull() else null

actual suspend fun readCoverArt(path: String): ByteArray? = withContext(Dispatchers.IO) {
    val retriever = MediaMetadataRetriever()
    try {
        retriever.open(path)
        retriever.embeddedPicture
    } catch (_: RuntimeException) {
        null
    } finally {
        retriever.release()
    }
}

actual val playableExtensions: Set<String> = allAudioExtensions
