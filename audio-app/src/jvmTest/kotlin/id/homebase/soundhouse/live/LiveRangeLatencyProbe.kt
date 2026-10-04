package id.homebase.soundhouse.live

import id.homebase.api.video.FFmpegBinaryManager
import id.homebase.soundhouse.data.AudioTrackContent
import id.homebase.soundhouse.data.toAudioTrackOrNull
import kotlin.uuid.Uuid
import id.homebase.api.client.drives.files.DriveFileHttpProvider
import id.homebase.api.client.drives.files.PayloadOperationOptions
import id.homebase.api.client.HttpClientProvider
import id.homebase.soundhouse.data.AUDIO_PAYLOAD_KEY
import id.homebase.soundhouse.data.audioDriveId
import id.homebase.soundhouse.playback.AudioStreamServer
import id.homebase.soundhouse.playback.RemoteTrackSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URI
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import org.junit.Assume.assumeTrue
import kotlin.time.TimeSource

/**
 * Streaming diagnostics against the live server: uploads a 30-minute MP3 (tagged, purged after) and
 * prints how ranged reads, read-ahead and player seeks perform. Opt-in, since it moves ~60 MB:
 * `SOUNDHOUSE_PROBE=1 ./gradlew :audio-app:liveTest --tests '*LiveRangeLatencyProbe' -i | grep -E 'PROBE|StreamTrace'`
 */
class LiveRangeLatencyProbe {
    @Test
    fun `measure ranged read latency`() = runBlocking {
        assumeTrue("set SOUNDHOUSE_PROBE=1 to run", System.getenv("SOUNDHOUSE_PROBE") == "1")
        val session = LiveSession.requireOrSkip()
        val work = Files.createTempDirectory("hba-probe").toFile()
        val api = session.audioDriveApi(File(work, "cache"))
        purgeTagged(api)
        val mp3 = File(work, "long.mp3")
        val encode = ProcessBuilder(
            FFmpegBinaryManager.ffmpegPath(), "-v", "error", "-y", "-f", "lavfi",
            "-i", "anoisesrc=d=1800:c=pink:r=44100:a=0.1", "-ac", "2", "-b:a", "128k", mp3.absolutePath,
        ).redirectErrorStream(true).start()
        check(encode.waitFor() == 0) { encode.inputStream.bufferedReader().readText() }
        val uploadMark = TimeSource.Monotonic.markNow()
        val uploaded = api.uploadTrack(
            mp3.absolutePath,
            AudioTrackContent("liveProbe ${Uuid.random().toString().take(8)}", mp3.length(), "audio/mpeg", 1_800_000),
            tags = listOf(LIVE_TEST_TAG),
        )
        println("PROBE uploaded ${mp3.length() / 1_000_000.0} MB in ${uploadMark.elapsedNow().inWholeMilliseconds} ms")
        try {
        val track = api.getTrackFile(uploaded.fileId)!!.toAudioTrackOrNull()!!
        suspend fun timed(start: Long, length: Long): Long {
            val mark = TimeSource.Monotonic.markNow()
            val bytes = api.readRange(track, start, length)
            check(bytes.size.toLong() == length) { "short read ${bytes.size}" }
            return mark.elapsedNow().inWholeMilliseconds
        }
        val size = track.sizeBytes
        val chunk = 256L * 1024
        timed(0, chunk) // warm-up: connection setup
        for (fraction in listOf(0.0, 0.25, 0.5, 0.75, 0.95)) {
            val start = (size * fraction).toLong().coerceAtMost(size - chunk)
            val samples = (1..3).map { timed(start, chunk) }
            println("PROBE 256KB at ${(fraction * 100).toInt()}%: ${samples.joinToString()} ms")
        }
        val mid = size / 2
        for (length in listOf(16L * 1024, 64L * 1024, 256L * 1024, 1024L * 1024, 4L * 1024 * 1024)) {
            if (mid + length > size) continue
            val samples = (1..3).map { timed(mid, length) }
            val best = samples.min()
            println("PROBE ${length / 1024}KB at 50%: ${samples.joinToString()} ms  (~${length * 1000 / 1024 / maxOf(best, 1)} KB/s best)")
        }
        // Fresh, non-overlapping regions for each pattern so no read is served from the cache.
        suspend fun pattern(label: String, regionStart: Long, chunkSize: Long, count: Int, inFlight: Int) {
            val mark = TimeSource.Monotonic.markNow()
            coroutineScope {
                (0 until count).chunked(inFlight).forEach { batch ->
                    batch.map { i -> async { api.readRange(track, regionStart + i * chunkSize, chunkSize) } }.awaitAll()
                }
            }
            val ms = mark.elapsedNow().inWholeMilliseconds
            val kb = count * chunkSize / 1024
            println("PROBE $label: ${kb} KB in $ms ms (~${kb * 1000 / maxOf(ms, 1)} KB/s)")
        }
        val mb = 1024L * 1024
        pattern("256KB x 8, 1 in flight (today)", (size * 0.10).toLong(), chunk, 8, 1)
        pattern("256KB x 8, 4 in flight", (size * 0.20).toLong(), chunk, 8, 4)
        pattern("256KB x 8, 8 in flight", (size * 0.30).toLong(), chunk, 8, 8)
        pattern("1MB x 4, 1 in flight", (size * 0.40).toLong(), mb, 4, 1)
        pattern("1MB x 4, 2 in flight", (size * 0.55).toLong(), mb, 4, 2)
        // The same reads straight to the network provider, skipping the cached provider's one-at-a-time gate.
        val network = DriveFileHttpProvider(HttpClientProvider.create(), session.credentialsManager())
        suspend fun direct(label: String, regionStart: Long, chunkSize: Long, count: Int, inFlight: Int) {
            val mark = TimeSource.Monotonic.markNow()
            coroutineScope {
                (0 until count).chunked(inFlight).forEach { batch ->
                    batch.map { i ->
                        async {
                            network.getPayloadBytesRawNetwork(
                                audioDriveId, track.fileId, AUDIO_PAYLOAD_KEY,
                                PayloadOperationOptions(chunkStart = regionStart + i * chunkSize, chunkLength = chunkSize),
                                null,
                            )
                        }
                    }.awaitAll()
                }
            }
            val ms = mark.elapsedNow().inWholeMilliseconds
            val kb = count * chunkSize / 1024
            println("PROBE direct $label: ${kb} KB in $ms ms (~${kb * 1000 / maxOf(ms, 1)} KB/s)")
        }
        direct("256KB x 8, 1 in flight", (size * 0.60).toLong(), chunk, 8, 1)
        direct("256KB x 8, 4 in flight", (size * 0.65).toLong(), chunk, 8, 4)
        direct("256KB x 8, 8 in flight", (size * 0.80).toLong(), chunk, 8, 8)
        direct("1MB x 4, 4 in flight", (size * 0.84).toLong(), mb, 4, 4)
        // A player's seek through the loopback server. Each scenario gets its own cold chunk cache,
        // so one can't be served from bytes an earlier one fetched.
        suspend fun seekThroughServer(label: String, readAhead: Int, at: Double, ffmpegFlags: List<String>) {
            val coldApi = session.audioDriveApi(File(work, "cache-$label".replace(' ', '-')))
            val server = AudioStreamServer(readAhead = readAhead)
            try {
                val url = server.urlFor(track.fileId.toString(), RemoteTrackSource(coldApi, track))
                val seconds = ((track.durationMs ?: 0) / 1000 * at).toInt()
                val mark = TimeSource.Monotonic.markNow()
                val decode = ProcessBuilder(
                    listOf(FFmpegBinaryManager.ffmpegPath(), "-v", "error") + ffmpegFlags +
                        listOf("-ss", seconds.toString(), "-i", url, "-t", "5", "-f", "null", "-"),
                ).redirectErrorStream(true).start()
                check(decode.waitFor() == 0) { decode.inputStream.bufferedReader().readText() }
                println("PROBE ffmpeg seek to ${(at * 100).toInt()}% + 5 s decode, $label: ${mark.elapsedNow().inWholeMilliseconds} ms")
            } finally {
                server.stop()
            }
        }
        seekThroughServer("read-ahead 1", 1, 0.30, emptyList())
        seekThroughServer("read-ahead 4", 4, 0.40, emptyList())
        seekThroughServer("read-ahead 4 fastseek", 4, 0.60, listOf("-fflags", "fastseek"))
        seekThroughServer("read-ahead 1 fastseek", 1, 0.70, listOf("-fflags", "fastseek"))
        pattern("first 64KB alone (time to first audio)", (size * 0.97).toLong(), 64 * 1024, 1, 1)
        pattern("first 256KB alone", (size * 0.98).toLong(), chunk, 1, 1)
        } finally {
            purgeTagged(api)
            work.deleteRecursively()
        }
    }
}
