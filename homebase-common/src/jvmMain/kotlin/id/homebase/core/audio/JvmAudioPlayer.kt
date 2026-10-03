package id.homebase.core.audio

import co.touchlab.kermit.Logger
import id.homebase.api.video.FFmpegBinaryManager
import java.io.File
import java.io.InputStream
import java.util.concurrent.TimeUnit
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.LineUnavailableException
import javax.sound.sampled.SourceDataLine

/** Signed little-endian PCM, the only layout this player hands Java Sound. */
internal data class PcmFormat(val sampleRate: Int, val bits: Int, val channels: Int) {
    val frameBytes: Int get() = bits / 8 * channels

    fun toAudioFormat() = AudioFormat(sampleRate.toFloat(), bits, channels, true, false)
}

internal data class SourceProbe(val durationMs: Long, val format: PcmFormat?)

open class JvmAudioPlayer : AudioPlayer {
    private var process: Process? = null
    private var sourceLine: SourceDataLine? = null
    private var playbackThread: Thread? = null
    private var progressThread: Thread? = null
    private var observer: AudioPlaybackObserver? = null

    private var currentFilePath: String? = null
    private var sourceFormat: PcmFormat? = null

    @Volatile
    internal var totalDurationMs: Long = 0
        private set

    @Volatile
    internal var seekOffsetMs: Long = 0
        private set

    @Volatile
    internal var speed: Float = 1f
        private set

    @Volatile
    private var lastReportedMs: Long = 0

    @Volatile
    private var backwardsCount = 0

    @Volatile
    private var isPaused = false

    @Volatile
    private var isStopped = true

    override fun play(filePath: String) {
        stopPlayback()
        currentFilePath = filePath
        seekOffsetMs = 0
        isStopped = false
        isPaused = false
        val probe = probe(filePath)
        totalDurationMs = probe.durationMs
        sourceFormat = probe.format
        startPlayback(filePath, seekMs = 0)
    }

    override fun pause() {
        isPaused = true
        sourceLine?.stop()
    }

    override fun resume() {
        sourceLine?.start()
        isPaused = false
    }

    override fun jumpTo(positionMs: Long) {
        val path = currentFilePath ?: return
        val clamped = positionMs.coerceIn(0, totalDurationMs)
        stopPlayback()
        seekOffsetMs = clamped
        isStopped = false
        isPaused = false
        startPlayback(path, seekMs = clamped)
    }

    override fun stop() {
        isStopped = true
        stopPlayback()
        seekOffsetMs = 0
    }

    // ffmpeg resamples the whole stream, so a speed change has to restart the decoder from
    // wherever the line had reached.
    override fun setSpeed(speed: Float) {
        val clamped = speed.coerceToPlaybackSpeed()
        if (clamped == this.speed) return
        val path = currentFilePath
        val resumeAt = lastReportedMs
        this.speed = clamped
        if (path == null || isStopped) return
        stopPlayback()
        seekOffsetMs = resumeAt.coerceIn(0, totalDurationMs)
        isStopped = false
        startPlayback(path, seekMs = seekOffsetMs)
    }

    override fun release() {
        stop()
        observer = null
        currentFilePath = null
        sourceFormat = null
    }

    override fun setPlaybackObserver(observer: AudioPlaybackObserver) {
        this.observer = observer
    }

    internal fun buildFfmpegCommand(
        filePath: String,
        seekMs: Long,
        speed: Float = 1f,
        output: PcmFormat = DEFAULT_FORMAT,
    ): List<String> = buildList {
        add(ffmpegExecutable())
        add("-v"); add("error")
        if (seekMs > 0) {
            add("-ss"); add((seekMs / 1000.0).toString())
        }
        add("-i"); add(filePath)
        // A single atempo stage covers the clamped 0.5-2.0 range; beyond it ffmpeg needs a chain.
        val tempo = speed.coerceToPlaybackSpeed()
        if (tempo != 1f) {
            add("-filter:a"); add("atempo=$tempo")
        }
        val sampleFormat = "s${output.bits}le"
        add("-f"); add(sampleFormat)
        add("-acodec"); add("pcm_$sampleFormat")
        add("-ar"); add(output.sampleRate.toString())
        add("-ac"); add(output.channels.toString())
        add("pipe:1")
    }

    protected open fun ffmpegExecutable(): String = FFmpegBinaryManager.ffmpegPath()

    internal open fun startDecoder(filePath: String, seekMs: Long, output: PcmFormat): InputStream? {
        if (!FFmpegBinaryManager.isAvailable()) {
            Logger.e(tag = TAG) { "FFmpeg binaries not available — cannot decode audio" }
            return null
        }
        val command = buildFfmpegCommand(filePath, seekMs, speed, output)
        process = ProcessBuilder(command)
            .redirectError(ProcessBuilder.Redirect.PIPE)
            .start()
        drainStderr(process!!)
        return process?.inputStream
    }

    protected open fun openAudioLine(format: AudioFormat): SourceDataLine {
        val line = AudioSystem.getSourceDataLine(format)
        line.open(format, bufferBytes(format))
        line.start()
        return line
    }

    // Java Sound refuses some formats outright (32-bit, more than two channels on macOS) and the
    // device may refuse others, so walk down the candidates until a line opens.
    private fun openBestLine(): Pair<SourceDataLine, PcmFormat>? {
        for (candidate in outputCandidates(sourceFormat)) {
            try {
                return openAudioLine(candidate.toAudioFormat()) to candidate
            } catch (e: LineUnavailableException) {
                Logger.w(tag = TAG) { "No line for $candidate: ${e.message}" }
            } catch (e: IllegalArgumentException) {
                Logger.w(tag = TAG) { "No line for $candidate: ${e.message}" }
            }
        }
        return null
    }

    private fun startPlayback(filePath: String, seekMs: Long) {
        lastReportedMs = seekMs
        backwardsCount = 0

        try {
            val (line, output) = openBestLine() ?: run {
                Logger.e(tag = TAG) { "No audio line accepts any output format" }
                return
            }
            sourceLine = line
            val input = startDecoder(filePath, seekMs, output) ?: run {
                stopPlayback()
                return
            }

            playbackThread = Thread({
                val buffer = ByteArray(bufferBytes(output.toAudioFormat()))
                try {
                    while (!isStopped) {
                        if (isPaused) {
                            Thread.sleep(50)
                            continue
                        }
                        // A pipe read can end mid-frame, and the line rejects partial frames; the
                        // buffer is whole frames, so a full read always is.
                        val bytesRead = input.readNBytes(buffer, 0, buffer.size)
                        val whole = bytesRead - bytesRead % output.frameBytes
                        if (whole > 0) line.write(buffer, 0, whole)
                        if (bytesRead < buffer.size) break
                    }
                    if (!isStopped) {
                        line.drain()
                        observer?.onComplete()
                    }
                } catch (_: InterruptedException) {
                } catch (e: Exception) {
                    Logger.e(e, tag = TAG) { "Playback error" }
                }
            }, "JvmAudioPlayback").apply {
                isDaemon = true
                start()
            }

            progressThread = Thread({
                try {
                    while (!isStopped && playbackThread?.isAlive == true) {
                        if (!isPaused) {
                            // The line advances in output time, so atempo-stretched media moves
                            // `speed` times faster than the bytes it has played.
                            val playedMs = (line.microsecondPosition / 1000.0 * speed).toLong()
                            observer?.onProgressUpdate(
                                monotonicPositionMs(seekOffsetMs + playedMs),
                                totalDurationMs
                            )
                        }
                        Thread.sleep(PROGRESS_INTERVAL_MS)
                    }
                } catch (_: InterruptedException) {
                }
            }, "JvmAudioProgress").apply {
                isDaemon = true
                start()
            }
        } catch (e: Exception) {
            Logger.e(e, tag = TAG) { "Failed to start audio playback" }
            stopPlayback()
        }
    }

    private fun stopPlayback() {
        isStopped = true
        process?.destroy()
        process = null
        playbackThread?.interrupt()
        playbackThread = null
        progressThread?.interrupt()
        progressThread = null
        try {
            sourceLine?.stop()
            sourceLine?.close()
        } catch (_: Exception) {
        }
        sourceLine = null
    }

    private fun drainStderr(proc: Process) {
        Thread({
            try {
                proc.errorStream.bufferedReader().forEachLine { line ->
                    Logger.w(tag = TAG) { "ffmpeg: $line" }
                }
            } catch (_: Exception) {
            }
        }, "JvmAudioStderr").apply {
            isDaemon = true
            start()
        }
    }

    // `line.microsecondPosition` restarts at zero across a pause or a seek, so a single
    // backwards sample is jitter; only a sustained run of them is a real rewind.
    internal fun monotonicPositionMs(rawMs: Long): Long {
        val capped = if (totalDurationMs > 0) rawMs.coerceAtMost(totalDurationMs) else rawMs
        if (capped >= lastReportedMs) {
            backwardsCount = 0
            lastReportedMs = capped
            return capped
        }
        backwardsCount++
        if (backwardsCount > BACKWARDS_TOLERANCE) {
            backwardsCount = 0
            lastReportedMs = capped
            return capped
        }
        return lastReportedMs
    }

    internal open fun probe(filePath: String): SourceProbe {
        if (!FFmpegBinaryManager.isAvailable()) {
            Logger.w(tag = TAG) { "FFmpeg not available, falling back to file-size estimate" }
            return SourceProbe(estimateDurationFromFileSize(filePath), null)
        }
        return try {
            val proc = ProcessBuilder(
                FFmpegBinaryManager.ffprobePath(),
                "-v", "error",
                "-select_streams", "a:0",
                "-show_entries", "format=duration:stream=sample_rate,channels,bits_per_raw_sample,bits_per_sample",
                "-of", "default=noprint_wrappers=1",
                filePath
            ).redirectErrorStream(true).start()

            val output = proc.inputStream.bufferedReader().readText()
            val completed = proc.waitFor(10, TimeUnit.SECONDS)
            if (!completed) {
                proc.destroy()
                Logger.w(tag = TAG) { "ffprobe timed out" }
            }
            parseProbe(output)
        } catch (e: Exception) {
            Logger.e(e, tag = TAG) { "ffprobe failed" }
            SourceProbe(0, null)
        }
    }

    companion object {
        private const val TAG = "JvmAudioPlayer"
        internal const val SAMPLE_RATE = 44100
        internal const val SAMPLE_SIZE_BITS = 16
        internal const val CHANNELS = 2
        internal val DEFAULT_FORMAT = PcmFormat(SAMPLE_RATE, SAMPLE_SIZE_BITS, CHANNELS)
        // About the 8 KB the line used to get at CD quality; hi-res needs proportionally more bytes.
        internal const val BUFFER_MS = 50
        internal const val PROGRESS_INTERVAL_MS = 80L
        internal const val BACKWARDS_TOLERANCE = 3

        internal fun bufferBytes(format: AudioFormat): Int =
            format.frameSize * (format.sampleRate.toInt() * BUFFER_MS / 1000).coerceAtLeast(1)

        /** ffprobe `key=value` lines; fields a codec lacks come back as N/A or 0. */
        internal fun parseProbe(output: String): SourceProbe {
            val fields = output.lineSequence()
                .mapNotNull { line -> line.split('=', limit = 2).takeIf { it.size == 2 } }
                .associate { (key, value) -> key.trim() to value.trim() }
            fun positive(key: String) = fields[key]?.toLongOrNull()?.takeIf { it > 0 }
            val durationMs = fields["duration"]?.toDoubleOrNull()?.let { (it * 1000).toLong() } ?: 0
            val rate = positive("sample_rate")?.toInt() ?: return SourceProbe(durationMs, null)
            val bits = (positive("bits_per_raw_sample") ?: positive("bits_per_sample"))?.toInt() ?: SAMPLE_SIZE_BITS
            val channels = positive("channels")?.toInt() ?: CHANNELS
            return SourceProbe(durationMs, PcmFormat(rate, bits, channels))
        }

        /**
         * Best first: the source's own rate and channel count at 16 or 24 bits (Java Sound has no
         * 32-bit lines), then stereo, then 16-bit, then a common rate, then CD quality.
         */
        internal fun outputCandidates(source: PcmFormat?): List<PcmFormat> {
            if (source == null) return listOf(DEFAULT_FORMAT)
            val bits = if (source.bits > 16) 24 else 16
            val commonRate = if (source.sampleRate % 44_100 == 0) 44_100 else 48_000
            return listOf(
                PcmFormat(source.sampleRate, bits, source.channels),
                PcmFormat(source.sampleRate, bits, CHANNELS),
                PcmFormat(source.sampleRate, SAMPLE_SIZE_BITS, CHANNELS),
                PcmFormat(commonRate, SAMPLE_SIZE_BITS, CHANNELS),
                DEFAULT_FORMAT,
            ).distinct()
        }

        internal fun estimateDurationFromFileSize(filePath: String): Long {
            val sizeBytes = File(filePath).length()
            val bytesPerSecond = SAMPLE_RATE * CHANNELS * (SAMPLE_SIZE_BITS / 8)
            return (sizeBytes * 1000 / bytesPerSecond).coerceAtLeast(1)
        }
    }
}
