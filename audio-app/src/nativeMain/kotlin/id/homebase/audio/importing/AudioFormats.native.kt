package id.homebase.audio.importing

import kotlinx.cinterop.ExperimentalForeignApi
import platform.AVFoundation.AVURLAsset
import platform.CoreMedia.CMTimeGetSeconds
import platform.Foundation.NSURL

@OptIn(ExperimentalForeignApi::class)
actual suspend fun readAudioMetadata(path: String): AudioFileMetadata {
    val asset = AVURLAsset(uRL = NSURL.fileURLWithPath(path), options = null)
    val seconds = CMTimeGetSeconds(asset.duration)
    val durationMs = if (seconds.isNaN() || seconds <= 0.0) null else (seconds * 1000).toLong()
    return AudioFileMetadata(title = null, durationMs = durationMs)
}

// AVFoundation has no Ogg/Opus decoder.
actual val playableExtensions: Set<String> = allAudioExtensions - setOf("ogg", "oga", "opus")
