package id.homebase.soundhouse.playback

import id.homebase.soundhouse.data.AudioDriveApi
import id.homebase.soundhouse.data.AudioTrack
import id.homebase.soundhouse.download.DownloadStore

/** A complete offline copy wins; otherwise the track streams from the drive. */
class DefaultTrackLocator(
    private val downloads: DownloadStore,
    private val server: AudioStreamServer,
    private val api: AudioDriveApi,
) : TrackLocator {
    override suspend fun locate(track: AudioTrack): String =
        downloads.localPathFor(track) ?: server.urlFor(track.fileId.toString(), RemoteTrackSource(api, track))
}
