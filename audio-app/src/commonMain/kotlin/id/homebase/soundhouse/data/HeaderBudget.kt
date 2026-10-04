package id.homebase.soundhouse.data

import co.touchlab.kermit.Logger
import id.homebase.api.serialization.OdinSystemSerializer
import id.homebase.api.util.truncateToCodePoints
import id.homebase.soundhouse.importing.TrackDetails

/** The server's MaxAppDataContentLength: appData.content as sent, i.e. after encryption and Base64. */
const val MAX_APP_DATA_CONTENT_LENGTH = 10 * 1024

/** How long [plaintextBytes] of content become once AES-CBC encrypted (PKCS7) and Base64-encoded. */
fun encryptedContentLength(plaintextBytes: Int): Int {
    val cipherBytes = (plaintextBytes / 16 + 1) * 16
    return (cipherBytes + 2) / 3 * 4
}

fun fitsAppDataContent(json: String): Boolean =
    encryptedContentLength(json.encodeToByteArray().size) <= MAX_APP_DATA_CONTENT_LENGTH

/**
 * This content trimmed until its header fits the server limit: the comment goes first, then the
 * other free text, the file name, and only as a last resort the title (never emptied).
 */
fun AudioTrackContent.fittedToHeader(): AudioTrackContent {
    fun fits(content: AudioTrackContent) = fitsAppDataContent(OdinSystemSerializer.serialize(content))
    if (fits(this)) return this
    var content = this
    fun details(change: (TrackDetails) -> TrackDetails?): (AudioTrackContent) -> AudioTrackContent? = { c ->
        c.details?.let(change)?.let { c.copy(details = it) }
    }
    val shrinkers: List<(AudioTrackContent) -> AudioTrackContent?> = listOf(
        details { d -> d.comment?.let { d.copy(comment = it.halved()) } },
        details { d -> d.composer?.let { d.copy(composer = it.halved()) } },
        details { d -> d.genre?.let { d.copy(genre = it.halved()) } },
        details { d -> d.album?.let { d.copy(album = it.halved()) } },
        details { d -> d.albumArtist?.let { d.copy(albumArtist = it.halved()) } },
        details { d -> d.artist?.let { d.copy(artist = it.halved()) } },
        details { d -> d.date?.let { d.copy(date = it.halved()) } },
        { c -> c.fileName?.let { c.copy(fileName = it.halved()) } },
        { c -> c.title.halved()?.let { c.copy(title = it) } },
    )
    for (shrink in shrinkers) {
        while (!fits(content)) content = shrink(content) ?: break
        if (fits(content)) break
    }
    Logger.w(tag = "HeaderBudget") { "Trimmed track content to fit the ${MAX_APP_DATA_CONTENT_LENGTH}-character header limit" }
    return content
}

// Halves by code points, which is what truncateToCodePoints counts, so every step strictly shrinks;
// null once a single code point is left.
private fun String.halved(): String? {
    val codePoints = count { !it.isLowSurrogate() }
    return if (codePoints <= 1) null else truncateToCodePoints(codePoints / 2)
}
