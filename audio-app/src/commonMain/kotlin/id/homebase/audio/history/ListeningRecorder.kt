package id.homebase.audio.history

import id.homebase.audio.playback.PlaybackController
import id.homebase.audio.playback.PlaybackState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.uuid.Uuid

/** Feeds playback progress into [ListeningHistory]: every tick in memory, written through on pause and track change. */
class ListeningRecorder(
    private val controller: PlaybackController,
    private val history: ListeningHistory,
    scope: CoroutineScope,
) {
    private var lastTrack: Uuid? = null
    private var lastPosition = 0L
    private var lastDuration = 0L
    private var wasPlaying = false

    init {
        scope.launch { controller.state.collect(::onState) }
    }

    private fun onState(state: PlaybackState) {
        val track = state.current
        val previous = lastTrack
        if (previous != null && previous != track?.fileId) {
            history.record(previous, lastPosition, lastDuration, force = true)
        }
        if (track == null || state.isLoading) {
            lastTrack = track?.fileId
            wasPlaying = false
            return
        }
        val duration = state.durationMs.takeIf { it > 0 } ?: track.durationMs ?: 0
        val paused = wasPlaying && !state.isPlaying
        if (state.isPlaying || paused) {
            history.record(track.fileId, state.positionMs, duration, force = paused)
        }
        lastTrack = track.fileId
        lastPosition = state.positionMs
        lastDuration = duration
        wasPlaying = state.isPlaying
    }
}
