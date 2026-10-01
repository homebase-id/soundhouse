package id.homebase.audio.playback

import co.touchlab.kermit.Logger
import id.homebase.audio.data.AudioTrack
import id.homebase.core.audio.AudioPlaybackObserver
import id.homebase.core.audio.AudioPlayer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import id.homebase.core.audio.coerceToPlaybackSpeed
import kotlin.time.Clock

data class PlaybackState(
    val queue: List<AudioTrack> = emptyList(),
    val index: Int = -1,
    val isPlaying: Boolean = false,
    val isLoading: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val failed: Boolean = false,
    val speed: Float = 1f,
    val sleepTimer: SleepTimer? = null,
) {
    val current: AudioTrack? get() = queue.getOrNull(index)
    val hasNext: Boolean get() = index in 0 until queue.lastIndex
    val hasPrevious: Boolean get() = index > 0
}

sealed interface SleepTimer {
    data class At(val endsAtMs: Long) : SleepTimer
    data object EndOfTrack : SleepTimer
}

/** Turns a track into something [AudioPlayer.play] accepts: a loopback stream URL or a local file path. */
fun interface TrackLocator {
    suspend fun locate(track: AudioTrack): String
}

/**
 * One player for the whole app. Every player call runs on a single serial lane, because the
 * platform players block while they open a source (MediaPlayer.prepare, ffprobe) and must never see
 * two calls at once.
 */
class PlaybackController(
    private val player: AudioPlayer,
    private val locator: TrackLocator,
    private val scope: CoroutineScope,
    private val playerLane: CoroutineDispatcher = Dispatchers.IO.limitedParallelism(1),
    initialSpeed: Float = 1f,
    private val now: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) {
    private val _state = MutableStateFlow(PlaybackState(speed = initialSpeed.coerceToPlaybackSpeed()))
    val state: StateFlow<PlaybackState> = _state.asStateFlow()

    private var generation = 0
    private var sleepJob: Job? = null

    init {
        if (_state.value.speed != 1f) scope.launch(playerLane) { player.setSpeed(_state.value.speed) }
        player.setPlaybackObserver(object : AudioPlaybackObserver {
            override fun onComplete() {
                scope.launch(playerLane) { advanceAfterCompletion() }
            }

            override fun onProgressUpdate(positionMs: Long, durationMs: Long) {
                _state.update { state ->
                    if (!state.isPlaying || state.isLoading) state
                    else state.copy(
                        positionMs = positionMs,
                        durationMs = if (durationMs > 0) durationMs else state.durationMs,
                    )
                }
            }
        })
    }

    /** [startAtMs] resumes the first track part-way; later tracks always start from the top. */
    fun playQueue(tracks: List<AudioTrack>, startIndex: Int, startAtMs: Long = 0) {
        if (startIndex !in tracks.indices) return
        _state.update { PlaybackState(queue = tracks, index = startIndex, speed = it.speed, sleepTimer = it.sleepTimer) }
        startCurrent(startAtMs)
    }

    fun togglePlayPause() {
        val state = _state.value
        if (state.current == null || state.isLoading) return
        if (state.failed) {
            startCurrent()
            return
        }
        scope.launch(playerLane) {
            if (_state.value.isPlaying) {
                player.pause()
                _state.update { it.copy(isPlaying = false) }
            } else {
                player.resume()
                _state.update { it.copy(isPlaying = true) }
            }
        }
    }

    fun pause() {
        if (!_state.value.isPlaying) return
        scope.launch(playerLane) {
            player.pause()
            _state.update { it.copy(isPlaying = false) }
        }
    }

    fun seekTo(positionMs: Long) {
        val state = _state.value
        if (state.current == null || state.isLoading) return
        val target = positionMs.coerceIn(0, state.durationMs.takeIf { it > 0 } ?: Long.MAX_VALUE)
        _state.update { it.copy(positionMs = target) }
        scope.launch(playerLane) { player.jumpTo(target) }
    }

    fun skipBy(deltaMs: Long) = seekTo(_state.value.positionMs + deltaMs)

    fun setSpeed(speed: Float) {
        val clamped = speed.coerceToPlaybackSpeed()
        _state.update { it.copy(speed = clamped) }
        scope.launch(playerLane) { player.setSpeed(clamped) }
    }

    /** Pauses after [durationMs]; null cancels. */
    fun sleepAfter(durationMs: Long?) {
        sleepJob?.cancel()
        if (durationMs == null) {
            _state.update { it.copy(sleepTimer = null) }
            return
        }
        val timer = SleepTimer.At(now() + durationMs)
        _state.update { it.copy(sleepTimer = timer) }
        sleepJob = scope.launch {
            delay(durationMs)
            if (_state.value.sleepTimer == timer) {
                _state.update { it.copy(sleepTimer = null) }
                pause()
            }
        }
    }

    fun sleepAtEndOfTrack() {
        sleepJob?.cancel()
        _state.update { it.copy(sleepTimer = SleepTimer.EndOfTrack) }
    }

    fun next() {
        val state = _state.value
        if (!state.hasNext) return
        _state.update { it.copy(index = it.index + 1) }
        startCurrent()
    }

    /** Restarts the current track when it's a few seconds in, like every other player. */
    fun previous() {
        val state = _state.value
        if (state.current == null) return
        if (state.positionMs > RESTART_THRESHOLD_MS || !state.hasPrevious) {
            seekTo(0)
            return
        }
        _state.update { it.copy(index = it.index - 1) }
        startCurrent()
    }

    fun stop() {
        generation++
        sleepJob?.cancel()
        _state.update { PlaybackState(speed = it.speed) }
        scope.launch(playerLane) { player.stop() }
    }

    /** Swaps a track in the queue for its updated version (rename, download) without interrupting it. */
    fun replaceTrack(track: AudioTrack) {
        _state.update { state -> state.copy(queue = state.queue.map { if (it.fileId == track.fileId) track else it }) }
    }

    /** Drops a deleted track from the queue; stops if it was playing. */
    fun removeTrack(fileId: kotlin.uuid.Uuid) {
        val state = _state.value
        val removed = state.queue.indexOfFirst { it.fileId == fileId }
        if (removed < 0) return
        if (removed == state.index) {
            stop()
            return
        }
        _state.update {
            it.copy(queue = it.queue - it.queue[removed], index = if (removed < it.index) it.index - 1 else it.index)
        }
    }

    private fun startCurrent(startAtMs: Long = 0) {
        val track = _state.value.current ?: return
        val myGeneration = ++generation
        _state.update {
            it.copy(isLoading = true, isPlaying = false, failed = false, positionMs = startAtMs, durationMs = track.durationMs ?: 0)
        }
        scope.launch(playerLane) {
            try {
                val source = locator.locate(track)
                if (myGeneration != generation) return@launch
                player.stop()
                player.play(source)
                if (startAtMs > 0) player.jumpTo(startAtMs)
                if (myGeneration != generation) return@launch
                _state.update { it.copy(isLoading = false, isPlaying = true) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Logger.e(e, TAG) { "Could not start ${track.fileId}" }
                if (myGeneration == generation) _state.update { it.copy(isLoading = false, isPlaying = false, failed = true) }
            }
        }
    }

    private fun advanceAfterCompletion() {
        val state = _state.value
        if (state.sleepTimer == SleepTimer.EndOfTrack) {
            _state.update { it.copy(isPlaying = false, positionMs = it.durationMs, sleepTimer = null) }
            return
        }
        if (state.hasNext) {
            _state.update { it.copy(index = it.index + 1) }
            startCurrent()
        } else {
            _state.update { it.copy(isPlaying = false, positionMs = it.durationMs) }
        }
    }

    private companion object {
        const val TAG = "PlaybackController"
        const val RESTART_THRESHOLD_MS = 3_000L
    }
}
