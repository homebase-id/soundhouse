package id.homebase.audio.playback

import id.homebase.api.client.drives.HomebaseFile
import id.homebase.audio.data.AudioTrack
import id.homebase.audio.data.AudioTrackContent
import id.homebase.audio.data.TrackEditor
import id.homebase.audio.data.TrackManager
import id.homebase.audio.data.buildTrackFile
import id.homebase.audio.data.toAudioTrackOrNull
import id.homebase.audio.data.trackContentJson
import id.homebase.audio.importing.AudioFileMetadata
import id.homebase.audio.importing.AudioQuality
import id.homebase.core.audio.AudioPlaybackObserver
import id.homebase.core.audio.AudioPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import java.util.Collections
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.uuid.Uuid

class QualityBackfillTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @AfterTest
    fun tearDown() = scope.cancel()

    private val flac = AudioQuality(codec = "flac", sampleRateHz = 96_000, bitDepth = 24, channels = 2)

    private class SilentPlayer : AudioPlayer {
        override fun play(filePath: String) = Unit
        override fun jumpTo(positionMs: Long) = Unit
        override fun resume() = Unit
        override fun pause() = Unit
        override fun stop() = Unit
        override fun release() = Unit
        override fun setSpeed(speed: Float) = Unit
        override fun setPlaybackObserver(observer: AudioPlaybackObserver) = Unit
    }

    private class Editor(private val fileId: Uuid, var content: AudioTrackContent) : TrackEditor {
        val writes: MutableList<AudioQuality> = Collections.synchronizedList(mutableListOf())
        override suspend fun renameTrack(track: AudioTrack, newTitle: String) = error("unused")
        override suspend fun setTrackQuality(track: AudioTrack, quality: AudioQuality): Uuid {
            writes += quality
            content = content.copy(quality = quality)
            return Uuid.random()
        }
        override suspend fun deleteTrack(fileId: Uuid) = error("unused")
        override suspend fun getTrackFile(fileId: Uuid): HomebaseFile = buildTrackFile(trackContentJson(content), fileId = this.fileId)
    }

    private fun setUp(content: AudioTrackContent, probe: suspend (String) -> AudioFileMetadata): Triple<QualityBackfill, Editor, AudioTrack> {
        val file = buildTrackFile(trackContentJson(content))
        val track = file.toAudioTrackOrNull()!!
        val editor = Editor(track.fileId, content)
        val controller = PlaybackController(SilentPlayer(), { "/tmp/${it.fileId}" }, scope)
        val manager = TrackManager(editor, writeLocal = {}, removeDownload = {}, onChanged = {}, onDeleted = {})
        return Triple(QualityBackfill(controller, { "/tmp/${it.fileId}" }, manager, scope, probe), editor, track)
    }

    @Test
    fun `a track without a format is probed once and written back`() = runBlocking {
        val probed = Collections.synchronizedList(mutableListOf<String>())
        val (backfill, editor, track) = setUp(AudioTrackContent("a", 1, "audio/flac")) { path ->
            probed += path
            AudioFileMetadata(null, null, flac)
        }
        backfill.fill(track)
        backfill.fill(track)
        assertEquals(listOf("/tmp/${track.fileId}"), probed)
        assertEquals(listOf(flac), editor.writes)
    }

    @Test
    fun `a track that already has its format is left alone`() = runBlocking {
        var probes = 0
        val (backfill, editor, track) = setUp(AudioTrackContent("a", 1, "audio/flac", quality = flac)) {
            probes++
            AudioFileMetadata(null, null, flac)
        }
        backfill.fill(track)
        assertEquals(0, probes)
        assertEquals(emptyList(), editor.writes)
    }

    @Test
    fun `an unparseable file writes nothing and is not retried`() = runBlocking {
        var probes = 0
        val (backfill, editor, track) = setUp(AudioTrackContent("a", 1, "audio/ogg")) {
            probes++
            AudioFileMetadata(null, null)
        }
        backfill.fill(track)
        backfill.fill(track)
        assertEquals(1, probes)
        assertEquals(emptyList(), editor.writes)
    }
}
