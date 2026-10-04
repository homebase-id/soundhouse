package id.homebase.core.audio

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.mp3.Mp3Extractor
import co.touchlab.kermit.Logger
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * ExoPlayer behind the blocking [AudioPlayer] contract. ExoPlayer lives on the main looper, so
 * every call is posted there in order; [play] waits (off the main thread) until the source is
 * ready or fails, as MediaPlayer.prepare did, so callers still learn about a bad source.
 */
@OptIn(UnstableApi::class)
class AndroidAudioPlayer(private val context: Context) : AudioPlayer {
    private val main = Handler(Looper.getMainLooper())
    private var observer: AudioPlaybackObserver? = null
    private var reportedBuffering = false
    // Built on first use and dropped by release(), so a released player can still play again.
    private var player: ExoPlayer? = null

    private val progress = object : Runnable {
        override fun run() {
            val exo = player ?: return
            observer?.onProgressUpdate(exo.currentPosition, exo.duration.takeIf { it != C.TIME_UNSET } ?: 0)
            main.postDelayed(this, PROGRESS_INTERVAL_MS)
        }
    }

    private val exo: ExoPlayer get() = player ?: build().also { player = it }

    private fun build(): ExoPlayer {
        val extractors = DefaultExtractorsFactory()
            // MP3s without a seek table would otherwise be unseekable or seek by reading from the
            // start; estimating from the bitrate jumps straight to the right part of the stream.
            .setMp3ExtractorFlags(Mp3Extractor.FLAG_ENABLE_CONSTANT_BITRATE_SEEKING_ALWAYS)
            .setConstantBitrateSeekingEnabled(true)
        return ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(context, extractors))
            .setLoadControl(
                DefaultLoadControl.Builder()
                    // Start after half a second of audio, and keep at most a minute ahead: each
                    // ranged read is a full round trip, and on cellular data a seek shouldn't pull
                    // tens of MB that may never be heard.
                    .setBufferDurationsMs(MIN_BUFFER_MS, MAX_BUFFER_MS, START_BUFFER_MS, REBUFFER_MS)
                    // Otherwise the ~13 MB default byte target for audio wins over MAX_BUFFER_MS
                    // (observed: 14 MB read ahead on start, ~4 MB after every seek).
                    .setPrioritizeTimeOverSizeThresholds(true)
                    .build(),
            )
            .setLooper(Looper.getMainLooper())
            .build()
            .apply {
                // PlaybackService owns audio focus.
                setAudioAttributes(
                    AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(),
                    false,
                )
                addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(state: Int) {
                        if (state == Player.STATE_ENDED) observer?.onComplete()
                        reportBuffering()
                    }

                    override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) = reportBuffering()

                    override fun onPlayerError(error: PlaybackException) {
                        Logger.e(error, tag = TAG) { "Playback failed: ${error.errorCodeName}" }
                    }
                })
            }
    }

    override fun play(filePath: String) {
        val ready = CountDownLatch(1)
        val failure = AtomicReference<PlaybackException?>()
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY || state == Player.STATE_ENDED) ready.countDown()
            }

            override fun onPlayerError(error: PlaybackException) {
                failure.set(error)
                ready.countDown()
            }
        }
        onMain {
            exo.addListener(listener)
            exo.setMediaItem(MediaItem.fromUri(filePath))
            exo.prepare()
            exo.playWhenReady = true
            main.removeCallbacks(progress)
            main.post(progress)
        }
        if (Looper.myLooper() == Looper.getMainLooper()) return
        val settled = ready.await(PREPARE_TIMEOUT_S, TimeUnit.SECONDS)
        main.post { player?.removeListener(listener) }
        failure.get()?.let { throw IllegalStateException("Could not play: ${it.errorCodeName}", it) }
        if (!settled) throw IllegalStateException("Timed out preparing $filePath")
    }

    override fun jumpTo(positionMs: Long) = onMain { player?.seekTo(positionMs.coerceAtLeast(0)) }

    override fun resume() = onMain { player?.playWhenReady = true }

    override fun pause() = onMain { player?.playWhenReady = false }

    override fun stop() = onMain {
        main.removeCallbacks(progress)
        player?.stop()
        player?.clearMediaItems()
    }

    override fun release() = onMain {
        main.removeCallbacks(progress)
        player?.release()
        player = null
        reportBuffering()
    }

    override fun setSpeed(speed: Float) = onMain { exo.setPlaybackSpeed(speed.coerceToPlaybackSpeed()) }

    override fun setPlaybackObserver(observer: AudioPlaybackObserver) {
        this.observer = observer
    }

    // Waiting on data only counts while playback is wanted: a paused seek isn't something to show.
    private fun reportBuffering() {
        val exo = player
        val buffering = exo != null && exo.playbackState == Player.STATE_BUFFERING && exo.playWhenReady
        if (buffering == reportedBuffering) return
        reportedBuffering = buffering
        observer?.onBufferingChanged(buffering)
    }

    private fun onMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else main.post(block)
    }

    private companion object {
        const val TAG = "AndroidAudioPlayer"
        const val PROGRESS_INTERVAL_MS = 80L
        const val START_BUFFER_MS = 500
        const val REBUFFER_MS = 1_000
        const val MIN_BUFFER_MS = 15_000
        const val MAX_BUFFER_MS = 60_000
        const val PREPARE_TIMEOUT_S = 30L
    }
}
