package id.homebase.soundhouse.playback

import id.homebase.api.client.drives.HomebaseFile
import id.homebase.soundhouse.data.AudioTrack
import id.homebase.soundhouse.data.AudioTrackContent
import id.homebase.soundhouse.data.TrackEditor
import id.homebase.soundhouse.data.TrackManager
import id.homebase.soundhouse.data.buildTrackFile
import id.homebase.soundhouse.data.toAudioTrackOrNull
import id.homebase.soundhouse.data.trackContentJson
import id.homebase.soundhouse.importing.AudioFileMetadata
import id.homebase.soundhouse.importing.AudioQuality
import id.homebase.soundhouse.importing.TrackDetails
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

class MetadataBackfillTest {
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
        val writes: MutableList<AudioTrackContent> = Collections.synchronizedList(mutableListOf())
        override suspend fun updateTrackContent(track: AudioTrack, content: AudioTrackContent): Uuid {
            writes += content
            this.content = content
            return Uuid.random()
        }
        override suspend fun deleteTrack(fileId: Uuid) = error("unused")
        override suspend fun getTrackFile(fileId: Uuid): HomebaseFile = buildTrackFile(trackContentJson(content), fileId = this.fileId)
    }

    private fun setUp(content: AudioTrackContent, probe: suspend (String) -> AudioFileMetadata): Triple<MetadataBackfill, Editor, AudioTrack> {
        val file = buildTrackFile(trackContentJson(content))
        val track = file.toAudioTrackOrNull()!!
        val editor = Editor(track.fileId, content)
        val controller = PlaybackController(SilentPlayer(), { "/tmp/${it.fileId}" }, scope)
        val manager = TrackManager(editor, writeLocal = {}, removeDownload = {}, onChanged = {}, onDeleted = {})
        return Triple(MetadataBackfill(controller, { "/tmp/${it.fileId}" }, manager, scope, probe), editor, track)
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
        assertEquals(listOf(flac), editor.writes.map { it.quality })
    }

    @Test
    fun `a track that already has its format and details is left alone`() = runBlocking {
        var probes = 0
        val (backfill, editor, track) = setUp(AudioTrackContent("a", 1, "audio/flac", quality = flac, details = TrackDetails())) {
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

    @Test
    fun `read tags fill missing details but never replace an edit`() = runBlocking {
        val tags = TrackDetails(artist = "From the tags", album = "LP")
        val (fresh, freshEditor, freshTrack) = setUp(AudioTrackContent("a", 1, "audio/mpeg", quality = flac)) {
            AudioFileMetadata(null, null, flac, tags)
        }
        fresh.fill(freshTrack)
        assertEquals(listOf(tags), freshEditor.writes.map { it.details })

        val mine = TrackDetails(artist = "My edit")
        val (edited, editedEditor, editedTrack) = setUp(AudioTrackContent("b", 1, "audio/mpeg", details = mine)) {
            AudioFileMetadata(null, null, flac, tags)
        }
        edited.fill(editedTrack)
        assertEquals(listOf(mine), editedEditor.writes.map { it.details })
        assertEquals(listOf(flac), editedEditor.writes.map { it.quality })
    }
}
