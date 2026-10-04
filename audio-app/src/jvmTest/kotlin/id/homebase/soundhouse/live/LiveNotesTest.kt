package id.homebase.soundhouse.live

import id.homebase.soundhouse.data.AudioTrack
import id.homebase.soundhouse.data.AudioTrackContent
import id.homebase.soundhouse.data.NotesChange
import id.homebase.soundhouse.data.toAudioTrackOrNull
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/** Notes live in their own payload: uploaded with the track, replaced, kept across a header edit, removed. */
class LiveNotesTest {
    private val workDir: File = Files.createTempDirectory("hba-live-notes").toFile()

    @AfterTest
    fun tearDown() {
        workDir.deleteRecursively()
    }

    @Test
    fun `notes round trip through their own payload`() = runBlocking {
        val session = LiveSession.requireOrSkip()
        val api = session.audioDriveApi(File(workDir, "cache"))
        purgeTagged(api)
        val bytes = fixtureBytes("tone.mp3")
        // Bigger than the whole header budget, and not ASCII, so it can only live in the payload.
        val tracklist = (1..400).joinToString("\n") { "$it. Track — Ärtist 🎵" }
        try {
            val source = File(workDir, "tone.mp3").apply { writeBytes(bytes) }
            val title = "liveNotes ${Uuid.random().toString().take(8)}"
            val uploaded = api.uploadTrack(
                source.absolutePath,
                AudioTrackContent(title, bytes.size.toLong(), "audio/mpeg"),
                tags = listOf(LIVE_TEST_TAG),
                notes = tracklist,
            )
            suspend fun fetch(): AudioTrack = assertNotNull(api.getTrackFile(uploaded.fileId)?.toAudioTrackOrNull())

            var track = fetch()
            assertTrue(track.hasNotesPayload)
            assertEquals(tracklist, api.readNotes(track))

            api.updateTrackContent(track, track.content, NotesChange.Set("Replaced"))
            track = fetch()
            assertEquals("Replaced", api.readNotes(track))

            api.updateTrackContent(track, track.content.copy(title = "$title renamed"))
            track = fetch()
            assertEquals("$title renamed", track.title)
            assertEquals("Replaced", api.readNotes(track), "a header-only edit must keep the notes payload")

            api.updateTrackContent(track, track.content, NotesChange.Remove)
            track = fetch()
            assertFalse(track.hasNotesPayload)
            assertNull(api.readNotes(track))
        } finally {
            purgeTagged(api)
        }
    }
}
