package id.homebase.soundhouse.data

import id.homebase.api.client.drives.HomebaseFile
import id.homebase.soundhouse.importing.AudioQuality
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

    private class FakeEditor(private val fileId: Uuid, var content: AudioTrackContent, val failRename: Boolean = false) : TrackEditor {
        val calls = mutableListOf<String>()
        var deleted = false
        override suspend fun renameTrack(track: AudioTrack, newTitle: String): Uuid {
            if (failRename) error("409")
            calls += "rename $newTitle"
            content = content.copy(title = newTitle)
            return Uuid.random()
        }
        override suspend fun setTrackQuality(track: AudioTrack, quality: AudioQuality): Uuid {
            calls += "quality ${quality.codec}"
            content = content.copy(quality = quality)
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
    fun `rename updates server then index and queue`() = runBlocking {
        val log = mutableListOf<String>()
        val editor = FakeEditor(track.fileId, content)
        manager(editor, log).rename(track, "  New name ")
        assertEquals(listOf("rename New name"), editor.calls)
        assertEquals(listOf("local active New name", "queue New name"), log)
    }

    @Test
    fun `unchanged or blank titles do nothing`() = runBlocking {
        val log = mutableListOf<String>()
        val editor = FakeEditor(track.fileId, content)
        manager(editor, log).rename(track, "Old")
        assertFailsWith<IllegalArgumentException> { manager(editor, log).rename(track, "   ") }
        assertTrue(editor.calls.isEmpty() && log.isEmpty())
    }

    @Test
    fun `a failed rename touches nothing local`() = runBlocking {
        val log = mutableListOf<String>()
        assertFailsWith<IllegalStateException> { manager(FakeEditor(track.fileId, content, failRename = true), log).rename(track, "x") }
        assertTrue(log.isEmpty())
    }

    @Test
    fun `a probed format is written to the server then the index and queue`() = runBlocking {
        val log = mutableListOf<String>()
        val editor = FakeEditor(track.fileId, content)
        val quality = AudioQuality(codec = "flac", sampleRateHz = 96_000, bitDepth = 24)
        manager(editor, log).setQuality(track, quality)
        assertEquals(listOf("quality flac"), editor.calls)
        assertEquals(listOf("local active Old", "queue Old"), log)
        assertEquals(quality, editor.getTrackFile(track.fileId).toAudioTrackOrNull()?.quality)
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
