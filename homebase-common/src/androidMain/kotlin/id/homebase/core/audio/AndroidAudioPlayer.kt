package id.homebase.core.audio

import android.media.AudioAttributes
import android.media.MediaPlayer
import co.touchlab.kermit.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class AndroidAudioPlayer: AudioPlayer {
    private var mediaPlayer: MediaPlayer? = null
    private var observer: AudioPlaybackObserver? = null
    private var positionJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)
    private var speed = 1f
    // A seek waits for its data before onSeekComplete; a running stream can also stall on its own.
    private var seeking = false
    private var stalled = false
    private var reportedBuffering = false

    override fun play(filePath: String) {
        release()
        mediaPlayer = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            setDataSource(filePath)
            setOnCompletionListener { observer?.onComplete() }
            setOnSeekCompleteListener {
                seeking = false
                reportBuffering()
            }
            setOnInfoListener { _, what, _ ->
                when (what) {
                    MediaPlayer.MEDIA_INFO_BUFFERING_START -> stalled = true
                    MediaPlayer.MEDIA_INFO_BUFFERING_END -> stalled = false
                    else -> return@setOnInfoListener false
                }
                reportBuffering()
                true
            }
            prepare()
            start()
        }
        applySpeed()
        startPositionPolling()
    }

    override fun jumpTo(positionMs: Long) {
        mediaPlayer?.let {
            seeking = true
            reportBuffering()
            it.seekTo(positionMs.toInt().coerceIn(0, it.duration))
        }
    }

    override fun resume() {
        mediaPlayer?.start()
        applySpeed()
    }

    override fun setSpeed(speed: Float) {
        this.speed = speed.coerceToPlaybackSpeed()
        applySpeed()
    }

    override fun pause() {
        mediaPlayer?.pause()
    }

    override fun stop() {
        positionJob?.cancel()
        mediaPlayer?.stop()
    }

    override fun release() {
        seeking = false
        stalled = false
        reportBuffering()
        positionJob?.cancel()
        mediaPlayer?.release()
        mediaPlayer = null
    }

    override fun setPlaybackObserver(observer: AudioPlaybackObserver) {
        this.observer = observer
    }

    // MediaPlayer.playbackParams resumes a paused player as a side effect, so only touch it
    // while it is already running.
    private fun applySpeed() {
        val player = mediaPlayer ?: return
        if (!player.isPlaying) return
        runCatching { player.playbackParams = player.playbackParams.setSpeed(speed) }
            .onFailure { Logger.w(it) { "setPlaybackParams($speed) rejected" } }
    }

    @Synchronized
    private fun reportBuffering() {
        val buffering = seeking || stalled
        if (buffering == reportedBuffering) return
        reportedBuffering = buffering
        observer?.onBufferingChanged(buffering)
    }

    private fun startPositionPolling() {
        positionJob = scope.launch {
            while (isActive) {
                val position = mediaPlayer?.currentPosition ?: 0
                val duration = mediaPlayer?.duration ?: 0
                observer?.onProgressUpdate(position.toLong(), duration.toLong())
                delay(PROGRESS_INTERVAL_MS)
            }
        }
    }

    private companion object {
        const val PROGRESS_INTERVAL_MS = 80L
    }
}