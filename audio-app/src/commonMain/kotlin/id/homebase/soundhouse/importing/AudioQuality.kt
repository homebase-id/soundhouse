package id.homebase.soundhouse.importing

import kotlinx.serialization.Serializable

/** The first audio stream's format. [codec] is one of the canonical names below, or the decoder's own name. */
@Serializable
data class AudioQuality(
    val codec: String? = null,
    val sampleRateHz: Int? = null,
    val bitDepth: Int? = null,
    val channels: Int? = null,
    val bitrateBps: Long? = null,
)

enum class QualityTier { Lossy, Lossless, HiRes }

object Codecs {
    const val MP3 = "mp3"
    const val AAC = "aac"
    const val OPUS = "opus"
    const val VORBIS = "vorbis"
    const val FLAC = "flac"
    const val ALAC = "alac"
    const val PCM = "pcm"
}

private val losslessCodecs = setOf(Codecs.FLAC, Codecs.ALAC, Codecs.PCM, "wavpack", "ape", "tta", "mlp", "truehd")

val AudioQuality.tier: QualityTier?
    get() {
        val codec = codec ?: return null
        if (codec !in losslessCodecs) return QualityTier.Lossy
        val hiRes = (bitDepth ?: 0) > 16 || (sampleRateHz ?: 0) > 48_000
        return if (hiRes) QualityTier.HiRes else QualityTier.Lossless
    }

/** ffprobe's codec_name to the canonical name: every PCM variant collapses to [Codecs.PCM]. */
fun canonicalCodec(ffprobeName: String): String = ffprobeName.lowercase().let { if (it.startsWith("pcm_")) Codecs.PCM else it }

/** Android MediaFormat MIME to the canonical name. */
fun codecForMime(mime: String): String? = when (mime.lowercase()) {
    "audio/mpeg" -> Codecs.MP3
    "audio/mp4a-latm", "audio/aac" -> Codecs.AAC
    "audio/opus" -> Codecs.OPUS
    "audio/vorbis" -> Codecs.VORBIS
    "audio/flac" -> Codecs.FLAC
    "audio/alac" -> Codecs.ALAC
    "audio/raw" -> Codecs.PCM
    else -> mime.substringAfter('/').takeIf { it.isNotEmpty() }
}

/** What the MIME type alone says, for tracks whose stream hasn't been probed. MP4 and Ogg can hold either kind. */
fun qualityFromMimeType(mimeType: String): AudioQuality? = when (mimeType) {
    "audio/flac" -> AudioQuality(codec = Codecs.FLAC)
    "audio/wav" -> AudioQuality(codec = Codecs.PCM)
    "audio/mpeg" -> AudioQuality(codec = Codecs.MP3)
    "audio/aac" -> AudioQuality(codec = Codecs.AAC)
    else -> null
}

/** 44100 → "44.1", 96000 → "96", 22050 → "22.05". */
fun formatKilohertz(hz: Int): String {
    val fraction = (hz % 1000).toString().padStart(3, '0').trimEnd('0')
    return if (fraction.isEmpty()) "${hz / 1000}" else "${hz / 1000}.$fraction"
}

/** Decimal megabytes to one place: 4_250_000 → "4.3". */
fun formatMegabytes(bytes: Long): String {
    val tenths = (bytes + 50_000) / 100_000
    return "${tenths / 10}.${tenths % 10}"
}
