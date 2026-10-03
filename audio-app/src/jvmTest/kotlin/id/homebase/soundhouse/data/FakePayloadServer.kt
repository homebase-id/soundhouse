package id.homebase.soundhouse.data

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import java.util.concurrent.atomic.AtomicInteger

/** Serves one encrypted payload the way the Homebase server does: whole body, or a `Range` slice as 206. */
internal class FakePayloadServer(private val ciphertext: ByteArray) {
    val requests = AtomicInteger()

    val engine = MockEngine { request -> handle(request) }

    private fun MockRequestHandleScope.handle(request: HttpRequestData) = run {
        requests.incrementAndGet()
        val range = request.headers[HttpHeaders.Range]
        val headers = headersOf(
            "payloadencrypted" to listOf("True"),
            HttpHeaders.ContentType to listOf("application/octet-stream"),
        )
        if (range == null) {
            respond(ciphertext, HttpStatusCode.OK, headers)
        } else {
            val (from, to) = range.removePrefix("bytes=").split("-")
            val start = from.toInt()
            val end = if (to.isEmpty()) ciphertext.size - 1 else minOf(to.toInt(), ciphertext.size - 1)
            respond(ciphertext.copyOfRange(start, end + 1), HttpStatusCode.PartialContent, headers)
        }
    }
}
