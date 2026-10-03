package id.homebase.soundhouse.download

import co.touchlab.kermit.Logger
import id.homebase.soundhouse.data.AudioTrack
import id.homebase.soundhouse.importing.extensionForMimeType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import kotlin.uuid.Uuid

/** Writes decrypted plaintext of a track to [outputPath]; true on success. */
fun interface TrackDownloader {
    suspend fun download(track: AudioTrack, outputPath: String, onProgress: (Float) -> Unit): Boolean
}

/**
 * Offline copies, one decrypted file per track under [directory] (app data, not cache: the OS
 * mustn't reclaim them). A copy only counts when its size matches the track's plaintext size, so a
 * crashed download never plays as a truncated file.
 */
class DownloadStore(
    directory: String,
    private val downloader: TrackDownloader,
    private val scope: CoroutineScope,
    private val fileSystem: FileSystem,
) {
    private val dir: Path = directory.toPath()

    private val _downloaded = MutableStateFlow<Set<Uuid>>(emptySet())
    val downloaded: StateFlow<Set<Uuid>> = _downloaded.asStateFlow()

    private val _inProgress = MutableStateFlow<Map<Uuid, Float>>(emptyMap())
    val inProgress: StateFlow<Map<Uuid, Float>> = _inProgress.asStateFlow()

    private val _failures = MutableStateFlow<Set<Uuid>>(emptySet())
    val failures: StateFlow<Set<Uuid>> = _failures.asStateFlow()

    // Downloads wait for this: the scan deletes stray .part files, which would include an in-flight one.
    private val startupScan = scope.launch(Dispatchers.IO) { rescan() }

    suspend fun awaitStartupScan() = startupScan.join()

    fun download(track: AudioTrack) {
        if (track.fileId in _inProgress.value || localPathFor(track) != null) return
        _inProgress.update { it + (track.fileId to 0f) }
        _failures.update { it - track.fileId }
        scope.launch(Dispatchers.IO) {
            startupScan.join()
            val finalPath = pathFor(track)
            val partPath = "$finalPath.part".toPath()
            val stored = try {
                fileSystem.createDirectories(dir)
                val complete = downloader.download(track, partPath.toString()) { progress ->
                    _inProgress.update { if (track.fileId in it) it + (track.fileId to progress) else it }
                } && fileSystem.metadataOrNull(partPath)?.size == track.sizeBytes
                if (complete) {
                    deleteMatching(track.fileId, keep = partPath)
                    fileSystem.atomicMove(partPath, finalPath)
                }
                complete
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Logger.e(e, TAG) { "Download of ${track.fileId} failed" }
                false
            } finally {
                _inProgress.update { it - track.fileId }
            }
            if (stored) {
                _downloaded.update { it + track.fileId }
            } else {
                fileSystem.delete(partPath, mustExist = false)
                _failures.update { it + track.fileId }
            }
        }
    }

    suspend fun remove(fileId: Uuid) = withContext(Dispatchers.IO) {
        deleteMatching(fileId)
        _downloaded.update { it - fileId }
    }

    /** The local plaintext for [track], or null when there is no complete copy. */
    fun localPathFor(track: AudioTrack): String? {
        if (track.fileId !in _downloaded.value) return null
        val path = pathFor(track)
        return path.toString().takeIf { fileSystem.metadataOrNull(path)?.size == track.sizeBytes }
    }

    private fun pathFor(track: AudioTrack): Path =
        dir / "${track.fileId}.${extensionForMimeType(track.mimeType) ?: "bin"}"

    private fun deleteMatching(fileId: Uuid, keep: Path? = null) {
        if (!fileSystem.exists(dir)) return
        fileSystem.list(dir)
            .filter { it.name.startsWith(fileId.toString()) && it != keep }
            .forEach { fileSystem.delete(it, mustExist = false) }
    }

    private fun rescan() {
        if (!fileSystem.exists(dir)) return
        val entries = fileSystem.list(dir)
        entries.filter { it.name.endsWith(".part") }.forEach { fileSystem.delete(it, mustExist = false) }
        _downloaded.value = entries
            .filterNot { it.name.endsWith(".part") }
            .mapNotNull { runCatching { Uuid.parse(it.name.substringBefore('.')) }.getOrNull() }
            .toSet()
    }

    private companion object {
        const val TAG = "DownloadStore"
    }
}
