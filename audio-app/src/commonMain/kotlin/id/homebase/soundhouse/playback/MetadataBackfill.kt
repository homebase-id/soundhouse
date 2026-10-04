package id.homebase.soundhouse.playback

import co.touchlab.kermit.Logger
import id.homebase.soundhouse.data.AudioTrack
import id.homebase.soundhouse.data.TrackManager
import id.homebase.soundhouse.importing.AudioFileMetadata
import id.homebase.soundhouse.importing.readAudioMetadata
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.uuid.Uuid

/**
 * Tracks imported before the stream format or tags were recorded get them the first time they play
 * or their details open: read from the same source the player uses, then written back to the drive.
 * Only missing parts are filled, so a user's edits are never replaced.
 */
class MetadataBackfill(
    controller: PlaybackController,
    private val locator: TrackLocator,
    private val manager: TrackManager,
    private val scope: CoroutineScope,
    private val readMetadata: suspend (String) -> AudioFileMetadata = ::readAudioMetadata,
) {
    private val lock = Mutex()
    private val attempted = mutableSetOf<Uuid>()

    init {
        scope.launch {
            controller.state.mapNotNull { it.current }.distinctUntilChanged { a, b -> a.fileId == b.fileId }.collect(::fill)
        }
    }

    fun request(track: AudioTrack) {
        scope.launch { fill(track) }
    }

    suspend fun fill(track: AudioTrack) {
        if (track.quality != null && track.details != null) return
        // Once per track per run: a file the platform can't parse would otherwise be re-probed on every play.
        if (!lock.withLock { attempted.add(track.fileId) }) return
        try {
            val read = readMetadata(locator.locate(track))
            if (read.quality == null && read.details == null) return
            manager.fillMissing(track, read.quality, read.details, read.notes)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Logger.w(e, TAG) { "Could not backfill the metadata of ${track.fileId}" }
        }
    }

    private companion object {
        const val TAG = "MetadataBackfill"
    }
}
