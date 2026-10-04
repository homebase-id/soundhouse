package id.homebase.soundhouse.playback

import co.touchlab.kermit.Logger
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.server.application.ApplicationCall
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytesWriter
import io.ktor.server.routing.get
import io.ktor.server.routing.head
import io.ktor.server.routing.routing
import io.ktor.utils.io.writeFully
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.TimeSource
import kotlin.uuid.Uuid

/**
 * Loopback HTTP server that hands players a plain URL for an encrypted track. Each request is
 * answered from [TrackByteSource] chunk by chunk as the player reads, so playback starts after the
 * first chunk and a seek is just a new `Range` request. The random path segment keeps other local
 * processes from reading tracks by guessing ids.
 */
class AudioStreamServer(
    private val chunkSize: Long = DEFAULT_CHUNK_SIZE,
    private val readAhead: Int = DEFAULT_READ_AHEAD,
) {
    private val secret = Uuid.random().toHexString()
    private val lock = Mutex()
    private val sources = mutableMapOf<String, TrackByteSource>()
    private var server: EmbeddedServer<*, *>? = null
    private var port: Int = 0

    suspend fun urlFor(id: String, source: TrackByteSource): String = lock.withLock {
        sources[id] = source
        if (server == null) start()
        "http://127.0.0.1:$port/$secret/$id"
    }

    suspend fun unregister(id: String) = lock.withLock { sources.remove(id) }

    suspend fun stop() = lock.withLock {
        server?.stop(gracePeriodMillis = 0, timeoutMillis = 500)
        server = null
        sources.clear()
    }

    private suspend fun start() {
        val engine = embeddedServer(CIO, host = "127.0.0.1", port = 0) {
            routing {
                get("/{secret}/{id}") { serve(call, sendBody = true) }
                head("/{secret}/{id}") { serve(call, sendBody = false) }
            }
        }
        engine.start(wait = false)
        port = engine.engine.resolvedConnectors().first().port
        server = engine
    }

    private suspend fun serve(call: ApplicationCall, sendBody: Boolean) {
        val source = if (call.parameters["secret"] == secret) {
            lock.withLock { sources[call.parameters["id"]] }
        } else {
            null
        }
        if (source == null) {
            call.respond(HttpStatusCode.NotFound)
            return
        }
        val size = source.size
        call.response.header(HttpHeaders.AcceptRanges, "bytes")
        val (range, status) = when (val request = parseRangeHeader(call.request.headers[HttpHeaders.Range], size)) {
            RangeRequest.Whole -> ByteRange(0, size - 1) to HttpStatusCode.OK
            is RangeRequest.Partial -> request.range to HttpStatusCode.PartialContent
            RangeRequest.Unsatisfiable -> {
                call.response.header(HttpHeaders.ContentRange, "bytes */$size")
                call.respond(HttpStatusCode.RequestedRangeNotSatisfiable)
                return
            }
        }
        if (status == HttpStatusCode.PartialContent) {
            call.response.header(HttpHeaders.ContentRange, "bytes ${range.start}-${range.endInclusive}/$size")
        }
        val contentType = ContentType.parse(source.mimeType)
        if (!sendBody) {
            call.respond(object : OutgoingContent.NoContent() {
                override val status: HttpStatusCode = status
                override val contentType: ContentType = contentType
                override val contentLength: Long = range.length
            })
            return
        }
        val trace = StreamTrace(call.parameters["id"].orEmpty(), range, size)
        call.respondBytesWriter(contentType = contentType, status = status, contentLength = range.length) {
            try {
                coroutineScope {
                    // Each read is a ~0.4 s round trip whatever its size, so several run at once and
                    // are written in order; one at a time capped a stream at ~0.5 MB/s.
                    val pending = ArrayDeque<Pair<Long, Deferred<ByteArray>>>()
                    var nextStart = range.start
                    fun fill() {
                        while (pending.size < readAhead && nextStart <= range.endInclusive) {
                            val from = nextStart
                            val length = minOf(chunkSize, range.endInclusive - from + 1)
                            pending.addLast(length to async { source.read(from, length) })
                            nextStart += length
                        }
                    }
                    fill()
                    while (pending.isNotEmpty()) {
                        val (expected, read) = pending.removeFirst()
                        val bytes = read.await()
                        check(bytes.size.toLong() == expected) { "source returned ${bytes.size} of $expected bytes" }
                        fill()
                        writeFully(bytes)
                        flush()
                        trace.wrote(bytes.size)
                    }
                }
                trace.finished(completed = true)
            } catch (e: CancellationException) {
                trace.finished(completed = false)
                throw e
            } catch (e: Exception) {
                // Usually the player hung up mid-response because it seeked; the next request takes over.
                trace.finished(completed = false, reason = e::class.simpleName)
            }
        }
    }

    private companion object {
        const val DEFAULT_CHUNK_SIZE = 256L * 1024
        const val DEFAULT_READ_AHEAD = 4
    }
}

/**
 * One `StreamTrace` line per player request: where it started, how long until the player got its
 * first bytes (the wait a seek feels), and how much it read before finishing or hanging up.
 */
private class StreamTrace(private val id: String, private val range: ByteRange, private val size: Long) {
    private val started = TimeSource.Monotonic.markNow()
    private var firstBytesMs: Long? = null
    private var bytes = 0L

    fun wrote(count: Int) {
        if (firstBytesMs == null) firstBytesMs = started.elapsedNow().inWholeMilliseconds
        bytes += count
    }

    fun finished(completed: Boolean, reason: String? = null) {
        val ms = started.elapsedNow().inWholeMilliseconds
        val at = if (size > 0) range.start * 100 / size else 0
        val rate = if (ms > 0) bytes * 1000 / 1024 / ms else 0
        Logger.i(tag = "StreamTrace") {
            "request ${id.take(8)} from ${range.start} ($at%): first bytes ${firstBytesMs?.let { "${it} ms" } ?: "never"}, " +
                "${bytes / 1024} KB in $ms ms (~$rate KB/s), " +
                (if (completed) "complete" else "player hung up${reason?.let { " ($it)" } ?: ""}")
        }
    }
}
