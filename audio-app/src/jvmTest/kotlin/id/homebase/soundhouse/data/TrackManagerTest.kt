package id.homebase.soundhouse.data

import id.homebase.api.client.drives.HomebaseFile
import id.homebase.soundhouse.importing.AudioQuality
import id.homebase.soundhouse.importing.TrackDetails
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class TrackManagerTest {
    private val content = AudioTrackContent("Old", 10, "audio/mpeg")
    private val file = buildTrackFile(trackContentJson(content))
    private val track = file.toAudioTrackOrNull()!!

    private class FakeEditor(private val fileId: Uuid, var content: AudioTrackContent, val fail: Boolean = false) : TrackEditor {
        val calls = mutableListOf<String>()
        var deleted = false
        override suspend fun updateTrackContent(track: AudioTrack, content: AudioTrackContent): Uuid {
            if (fail) error("409")
            calls += "update ${content.title}"
            this.content = content
            return Uuid.random()
        }
        override suspend fun deleteTrack(fileId: Uuid) {
            calls += "delete"
            deleted = true
        }
        override suspend fun getTrackFile(fileId: Uuid): HomebaseFile =
            buildTrackFile(trackContentJson(content), fileId = this.fileId, fileState = if (deleted) "deleted" else "active")
    }

    private fun manager(editor: TrackEditor, log: MutableList<String>) = TrackManager(
        editor = editor,
        writeLocal = { log += "local ${it.fileState.name.lowercase()} ${it.toAudioTrackOrNull()?.title}" },
        removeDownload = { log += "undownload $it" },
        onChanged = { log += "queue ${it.title}" },
        onDeleted = { log += "dequeue $it" },
    )

    @Test
    fun `an edit updates the server then the index and queue`() = runBlocking {
        val log = mutableListOf<String>()
        val editor = FakeEditor(track.fileId, content)
        manager(editor, log).editDetails(track, "  New name ", TrackDetails(artist = " Nina Simone ", genre = ""))
        assertEquals(listOf("update New name"), editor.calls)
        assertEquals(listOf("local active New name", "queue New name"), log)
        assertEquals(TrackDetails(artist = "Nina Simone"), editor.content.details)
    }

    @Test
    fun `an unchanged edit does nothing and a blank title is refused`() = runBlocking {
        val log = mutableListOf<String>()
        val edited = content.copy(details = TrackDetails(artist = "A"))
        val editedTrack = buildTrackFile(trackContentJson(edited)).toAudioTrackOrNull()!!
        val editor = FakeEditor(editedTrack.fileId, edited)
        manager(editor, log).editDetails(editedTrack, "Old", TrackDetails(artist = "A"))
        assertFailsWith<IllegalArgumentException> { manager(editor, log).editDetails(editedTrack, "   ", TrackDetails()) }
        assertTrue(editor.calls.isEmpty() && log.isEmpty())
    }

    @Test
    fun `a failed edit touches nothing local`() = runBlocking {
        val log = mutableListOf<String>()
        assertFailsWith<IllegalStateException> {
            manager(FakeEditor(track.fileId, content, fail = true), log).editDetails(track, "x", TrackDetails())
        }
        assertTrue(log.isEmpty())
    }

    @Test
    fun `read metadata fills only what is missing`() = runBlocking {
        val log = mutableListOf<String>()
        val mine = TrackDetails(artist = "My edit")
        val partial = content.copy(details = mine)
        val partialTrack = buildTrackFile(trackContentJson(partial)).toAudioTrackOrNull()!!
        val editor = FakeEditor(partialTrack.fileId, partial)
        val quality = AudioQuality(codec = "flac", sampleRateHz = 96_000, bitDepth = 24)
        manager(editor, log).fillMissing(partialTrack, quality, TrackDetails(artist = "From the tags"))
        assertEquals(quality, editor.content.quality)
        assertEquals(mine, editor.content.details)
    }

    @Test
    fun `delete removes it everywhere`() = runBlocking {
        val log = mutableListOf<String>()
        val editor = FakeEditor(track.fileId, content)
        manager(editor, log).delete(track)
        assertEquals(listOf("delete"), editor.calls)
        assertEquals(listOf("dequeue ${track.fileId}", "undownload ${track.fileId}", "local deleted null"), log)
    }
}
