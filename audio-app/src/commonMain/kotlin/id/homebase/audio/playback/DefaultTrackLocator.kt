package id.homebase.audio.playback

import id.homebase.audio.data.AudioDriveApi
import id.homebase.audio.data.AudioTrack
import id.homebase.audio.download.DownloadStore

/** A complete offline copy wins; otherwise the track streams from the drive. */
class DefaultTrackLocator(
    private val downloads: DownloadStore,
    private val server: AudioStreamServer,
    private val api: AudioDriveApi,
) : TrackLocator {
    override suspend fun locate(track: AudioTrack): String =
        downloads.localPathFor(track) ?: server.urlFor(track.fileId.toString(), RemoteTrackSource(api, track))
}
