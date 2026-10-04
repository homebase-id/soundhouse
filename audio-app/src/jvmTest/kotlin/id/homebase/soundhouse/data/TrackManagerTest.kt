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

    private class FakeEditor(
        private val fileId: Uuid,
        var content: AudioTrackContent,
        val fail: Boolean = false,
        var notes: String? = null,
    ) : TrackEditor {
        val calls = mutableListOf<String>()
        val noteChanges = mutableListOf<NotesChange>()
        var deleted = false
        override suspend fun updateTrackContent(track: AudioTrack, content: AudioTrackContent, notes: NotesChange): Uuid {
            if (fail) error("409")
            calls += "update ${content.title}"
            noteChanges += notes
            this.content = content
            when (notes) {
                is NotesChange.Set -> this.notes = notes.text
                NotesChange.Remove -> this.notes = null
                NotesChange.Keep -> Unit
            }
            return Uuid.random()
        }
        override suspend fun readNotes(track: AudioTrack): String? = notes
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
        manager(editor, log).editDetails(track, "  New name ", TrackDetails(artist = " Nina Simone ", genre = ""), null)
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
        manager(editor, log).editDetails(editedTrack, "Old", TrackDetails(artist = "A"), null)
        assertFailsWith<IllegalArgumentException> { manager(editor, log).editDetails(editedTrack, "   ", TrackDetails(), null) }
        assertTrue(editor.calls.isEmpty() && log.isEmpty())
    }

    @Test
    fun `a failed edit touches nothing local`() = runBlocking {
        val log = mutableListOf<String>()
        assertFailsWith<IllegalStateException> {
            manager(FakeEditor(track.fileId, content, fail = true), log).editDetails(track, "x", TrackDetails(), null)
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

    private fun withNotesPayload(content: AudioTrackContent): AudioTrack =
        buildTrackFile(trackContentJson(content), payloadKeys = listOf(AUDIO_PAYLOAD_KEY, NOTES_PAYLOAD_KEY)).toAudioTrackOrNull()!!

    @Test
    fun `notes are added replaced kept and removed as the edit says`() = runBlocking {
        val log = mutableListOf<String>()
        val added = FakeEditor(track.fileId, content)
        manager(added, log).editDetails(track, "Old", TrackDetails(), "  Side A tracklist ")
        assertEquals(listOf<NotesChange>(NotesChange.Set("Side A tracklist")), added.noteChanges)

        val withNotes = withNotesPayload(content)
        val same = FakeEditor(withNotes.fileId, content, notes = "Side A tracklist")
        manager(same, log).editDetails(withNotes, "Old", TrackDetails(), "Side A tracklist")
        assertEquals(listOf<NotesChange>(NotesChange.Keep), same.noteChanges)

        val replaced = FakeEditor(withNotes.fileId, content, notes = "old")
        manager(replaced, log).editDetails(withNotes, "Old", TrackDetails(), "new")
        assertEquals(listOf<NotesChange>(NotesChange.Set("new")), replaced.noteChanges)

        val cleared = FakeEditor(withNotes.fileId, content, notes = "old")
        manager(cleared, log).editDetails(withNotes, "Old", TrackDetails(), "   ")
        assertEquals(listOf<NotesChange>(NotesChange.Remove), cleared.noteChanges)
    }

    @Test
    fun `notes that never loaded leave the notes and a legacy header comment alone`() = runBlocking {
        val legacy = content.copy(details = TrackDetails(comment = "written by an older version"))
        val legacyTrack = buildTrackFile(trackContentJson(legacy)).toAudioTrackOrNull()!!
        val editor = FakeEditor(legacyTrack.fileId, legacy)
        manager(editor, mutableListOf()).editDetails(legacyTrack, "Renamed", TrackDetails(), null)
        assertEquals(listOf<NotesChange>(NotesChange.Keep), editor.noteChanges)
        assertEquals("written by an older version", editor.content.details?.comment)
    }

    @Test
    fun `saving loaded legacy notes moves them out of the header into the payload`() = runBlocking {
        val legacy = content.copy(details = TrackDetails(comment = "written by an older version"))
        val legacyTrack = buildTrackFile(trackContentJson(legacy)).toAudioTrackOrNull()!!
        val editor = FakeEditor(legacyTrack.fileId, legacy)
        manager(editor, mutableListOf()).editDetails(legacyTrack, "Old", TrackDetails(), "written by an older version")
        assertEquals(listOf<NotesChange>(NotesChange.Set("written by an older version")), editor.noteChanges)
        assertEquals(null, editor.content.details?.comment)
    }

    @Test
    fun `notes from tags arrive only with the first read`() = runBlocking {
        val first = FakeEditor(track.fileId, content)
        manager(first, mutableListOf()).fillMissing(track, null, TrackDetails(artist = "A"), "From the comment tag")
        assertEquals(listOf<NotesChange>(NotesChange.Set("From the comment tag")), first.noteChanges)

        val alreadyRead = content.copy(details = TrackDetails(artist = "A"))
        val readTrack = buildTrackFile(trackContentJson(alreadyRead)).toAudioTrackOrNull()!!
        val later = FakeEditor(readTrack.fileId, alreadyRead)
        manager(later, mutableListOf()).fillMissing(readTrack, AudioQuality(codec = "mp3"), TrackDetails(artist = "A"), "From the comment tag")
        assertEquals(listOf<NotesChange>(NotesChange.Keep), later.noteChanges)
    }
}
