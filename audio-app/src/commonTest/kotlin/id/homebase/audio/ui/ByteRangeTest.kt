package id.homebase.audio.ui

import id.homebase.audio.playback.ByteRange
import id.homebase.audio.playback.RangeRequest
import id.homebase.audio.playback.parseRangeHeader
import kotlin.test.Test
import kotlin.test.assertEquals

class ByteRangeTest {
    private fun partial(start: Long, end: Long) = RangeRequest.Partial(ByteRange(start, end))

    @Test
    fun `parses the forms players send`() {
        assertEquals(RangeRequest.Whole, parseRangeHeader(null, 1000))
        assertEquals(partial(0, 999), parseRangeHeader("bytes=0-", 1000))
        assertEquals(partial(100, 199), parseRangeHeader("bytes=100-199", 1000))
        assertEquals(partial(500, 999), parseRangeHeader("bytes=500-5000", 1000))
        assertEquals(partial(900, 999), parseRangeHeader("bytes=-100", 1000))
        assertEquals(partial(0, 999), parseRangeHeader("bytes=-5000", 1000))
        assertEquals(partial(10, 19), parseRangeHeader("bytes=10-19, 30-39", 1000))
    }

    @Test
    fun `rejects ranges outside the resource`() {
        assertEquals(RangeRequest.Unsatisfiable, parseRangeHeader("bytes=1000-", 1000))
        assertEquals(RangeRequest.Unsatisfiable, parseRangeHeader("bytes=50-10", 1000))
        assertEquals(RangeRequest.Unsatisfiable, parseRangeHeader("bytes=abc-", 1000))
        assertEquals(RangeRequest.Unsatisfiable, parseRangeHeader("bytes=-0", 1000))
        assertEquals(RangeRequest.Whole, parseRangeHeader("items=0-1", 1000))
    }
}
