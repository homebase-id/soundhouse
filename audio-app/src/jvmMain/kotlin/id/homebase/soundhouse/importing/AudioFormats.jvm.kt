package id.homebase.soundhouse.importing

import co.touchlab.kermit.Logger
import id.homebase.api.video.FFmpegBinaryManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.concurrent.TimeUnit

actual suspend fun readAudioMetadata(path: String): AudioFileMetadata = withContext(Dispatchers.IO) {
    if (!FFmpegBinaryManager.isAvailable()) return@withContext AudioFileMetadata(null, null)
    try {
        val process = ProcessBuilder(
            FFmpegBinaryManager.ffprobePath(),
            "-v", "error",
            "-select_streams", "a:0",
            "-show_entries",
            "format=duration,bit_rate:format_tags:stream=codec_name,sample_rate,channels,bits_per_raw_sample,bits_per_sample,bit_rate:stream_tags",
            "-of", "json",
            path,
        ).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText()
        if (!process.waitFor(10, TimeUnit.SECONDS)) process.destroy()
        parseFfprobeFormat(output)
    } catch (e: Exception) {
        Logger.w(e, "AudioMetadata") { "ffprobe failed" }
        AudioFileMetadata(null, null)
    }
}

internal fun parseFfprobeFormat(json: String): AudioFileMetadata {
    val format = runCatching { Json.parseToJsonElement(json).jsonObject["format"]?.jsonObject }.getOrNull()
        ?: return AudioFileMetadata(null, null)
    val durationMs = format["duration"]?.jsonPrimitive?.content?.toDoubleOrNull()?.let { (it * 1000).toLong() }
    val stream = runCatching { Json.parseToJsonElement(json).jsonObject["streams"]?.jsonArray?.firstOrNull()?.jsonObject }.getOrNull()
    // Ogg/Opus keep their comments on the stream, MP3/MP4/FLAC on the container; the container wins.
    val tags = stream.tagMap() + format.tagMap()
    val title = tags.entries.firstOrNull { it.key.equals("title", ignoreCase = true) }?.value
    return AudioFileMetadata(title, durationMs, stream?.let { qualityOf(it, format) }, trackDetailsFromTags(tags))
}

private fun JsonObject?.tagMap(): Map<String, String> =
    this?.get("tags")?.jsonObject?.mapValues { it.value.jsonPrimitive.content }.orEmpty()

// ffprobe reports "N/A" or 0 for fields a codec doesn't have, e.g. bit depth for MP3.
private fun JsonObject.positive(key: String): Long? =
    this[key]?.jsonPrimitive?.content?.toLongOrNull()?.takeIf { it > 0 }

private fun qualityOf(stream: JsonObject, format: JsonObject): AudioQuality? {
    val codec = stream["codec_name"]?.jsonPrimitive?.content?.let(::canonicalCodec) ?: return null
    return AudioQuality(
        codec = codec,
        sampleRateHz = stream.positive("sample_rate")?.toInt(),
        bitDepth = (stream.positive("bits_per_raw_sample") ?: stream.positive("bits_per_sample"))?.toInt(),
        channels = stream.positive("channels")?.toInt(),
        bitrateBps = stream.positive("bit_rate") ?: format.positive("bit_rate"),
    )
}

actual suspend fun readCoverArt(path: String): ByteArray? = withContext(Dispatchers.IO) {
    if (!FFmpegBinaryManager.isAvailable()) return@withContext null
    try {
        // Copies the attached-picture stream as-is; files without one make ffmpeg exit non-zero.
        val process = ProcessBuilder(
            FFmpegBinaryManager.ffmpegPath(),
            "-v", "error",
            "-i", path,
            "-an", "-map", "0:v:0", "-frames:v", "1", "-c:v", "copy",
            "-f", "image2pipe", "pipe:1",
        ).redirectError(ProcessBuilder.Redirect.DISCARD).start()
        val bytes = process.inputStream.readBytes()
        if (!process.waitFor(10, TimeUnit.SECONDS)) process.destroy()
        bytes.takeIf { process.exitValue() == 0 && it.isNotEmpty() }
    } catch (e: Exception) {
        Logger.w(e, "AudioMetadata") { "cover art extraction failed" }
        null
    }
}

actual val playableExtensions: Set<String> = allAudioExtensions
