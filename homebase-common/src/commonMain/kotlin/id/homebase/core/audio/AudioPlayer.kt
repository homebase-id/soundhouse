package id.homebase.core.audio

interface AudioPlayer {
    fun play(filePath: String)
    fun jumpTo(positionMs: Long)
    fun resume()
    fun pause()
    fun stop()
    fun release()
    fun setSpeed(speed: Float)

    fun setPlaybackObserver(observer: AudioPlaybackObserver)
}

interface AudioPlaybackObserver {
    fun onComplete()
    fun onProgressUpdate(positionMs: Long, durationMs: Long)

    /** Playback is waiting on data: after a seek, or when a stream runs dry. */
    fun onBufferingChanged(buffering: Boolean) {}
}

const val MIN_PLAYBACK_SPEED = 0.5f
const val MAX_PLAYBACK_SPEED = 2.0f

fun Float.coerceToPlaybackSpeed(): Float = coerceIn(MIN_PLAYBACK_SPEED, MAX_PLAYBACK_SPEED)
