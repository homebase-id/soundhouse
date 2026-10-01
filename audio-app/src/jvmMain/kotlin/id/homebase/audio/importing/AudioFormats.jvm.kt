package id.homebase.audio.importing

import co.touchlab.kermit.Logger
import id.homebase.api.video.FFmpegBinaryManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.concurrent.TimeUnit

actual suspend fun readAudioMetadata(path: String): AudioFileMetadata = withContext(Dispatchers.IO) {
    if (!FFmpegBinaryManager.isAvailable()) return@withContext AudioFileMetadata(null, null)
    try {
        val process = ProcessBuilder(
            FFmpegBinaryManager.ffprobePath(),
            "-v", "error",
            "-show_entries", "format=duration:format_tags=title",
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
    val tags = format["tags"]?.jsonObject
    val title = tags?.entries?.firstOrNull { it.key.equals("title", ignoreCase = true) }?.value?.jsonPrimitive?.content
    return AudioFileMetadata(title, durationMs)
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
