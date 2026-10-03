package id.homebase.soundhouse.playback

import id.homebase.soundhouse.data.AudioDriveApi
import id.homebase.soundhouse.data.AudioTrack

/** Random-access plaintext of one track. */
interface TrackByteSource {
    val size: Long
    val mimeType: String
    suspend fun read(start: Long, length: Long): ByteArray
}

/** Decrypted byte ranges fetched from the drive on demand. */
class RemoteTrackSource(private val api: AudioDriveApi, private val track: AudioTrack) : TrackByteSource {
    override val size: Long get() = track.sizeBytes
    override val mimeType: String get() = track.mimeType
    override suspend fun read(start: Long, length: Long): ByteArray = api.readRange(track, start, length)
}
