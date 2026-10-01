package id.homebase.audio.playback

/** Inclusive byte range within a resource of known size. */
data class ByteRange(val start: Long, val endInclusive: Long) {
    val length: Long get() = endInclusive - start + 1
}

sealed interface RangeRequest {
    data object Whole : RangeRequest
    data class Partial(val range: ByteRange) : RangeRequest
    data object Unsatisfiable : RangeRequest
}

/**
 * Parses a single-range `Range` header (`bytes=a-b`, `bytes=a-`, `bytes=-n`). Multi-range requests are
 * answered with their first range, which players accept.
 */
fun parseRangeHeader(header: String?, size: Long): RangeRequest {
    if (header.isNullOrBlank()) return RangeRequest.Whole
    val spec = header.trim().removePrefix("bytes=").takeIf { it != header.trim() } ?: return RangeRequest.Whole
    val first = spec.substringBefore(',').trim()
    val startText = first.substringBefore('-').trim()
    val endText = first.substringAfter('-', "").trim()
    if (size <= 0) return RangeRequest.Unsatisfiable
    val range = if (startText.isEmpty()) {
        val suffix = endText.toLongOrNull()?.takeIf { it > 0 } ?: return RangeRequest.Unsatisfiable
        ByteRange(maxOf(0, size - suffix), size - 1)
    } else {
        val start = startText.toLongOrNull() ?: return RangeRequest.Unsatisfiable
        if (start >= size) return RangeRequest.Unsatisfiable
        val end = if (endText.isEmpty()) size - 1 else endText.toLongOrNull()?.coerceAtMost(size - 1)
            ?: return RangeRequest.Unsatisfiable
        if (end < start) return RangeRequest.Unsatisfiable
        ByteRange(start, end)
    }
    return RangeRequest.Partial(range)
}
