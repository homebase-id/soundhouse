package id.homebase.soundhouse.live

import id.homebase.soundhouse.data.AudioDriveApi
import id.homebase.soundhouse.data.AudioTrack
import id.homebase.soundhouse.data.AudioTrackContent
import id.homebase.soundhouse.data.toAudioTrackOrNull
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/**
 * Upload → query → stream (start and mid-file) → download → rename → delete against the real
 * identity. Every file it writes carries [LIVE_TEST_TAG]; leftovers from earlier runs are
 * hard-deleted first, and this run's files in `finally`.
 */
class LiveTrackRoundTripTest {
    private val workDir: File = Files.createTempDirectory("hba-live").toFile()

    @AfterTest
    fun tearDown() {
        workDir.deleteRecursively()
    }

    @Test
    fun `mp3 round trip`() = runBlocking {
        val session = LiveSession.requireOrSkip()
        val api = session.audioDriveApi(File(workDir, "cache"))
        purgeTagged(api)

        val original = fixtureBytes("tone.mp3")
        val source = File(workDir, "tone.mp3").apply { writeBytes(original) }
        val title = "liveTest ${Uuid.random().toString().take(8)}"
        val progress = mutableListOf<Float>()
        try {
            val uploaded = api.uploadTrack(
                sourcePath = source.absolutePath,
                content = AudioTrackContent(
                    title = title,
                    sizeBytes = original.size.toLong(),
                    mimeType = "audio/mpeg",
                    durationMs = 6_034,
                    fileName = source.name,
                ),
                tags = listOf(LIVE_TEST_TAG),
                onProgress = { progress += it },
            )
            assertEquals(1f, progress.last(), "upload progress should finish at 1")
            assertEquals(progress.sorted(), progress, "upload progress should never go backwards")

            val listed = api.queryTracks(tagsAnyOf = listOf(LIVE_TEST_TAG)).singleOrNull { it.fileId == uploaded.fileId }
            val track = assertNotNull(listed, "uploaded track missing from query-batch")
            assertEquals(title, track.title)
            assertEquals(original.size.toLong(), track.sizeBytes)
            assertEquals(uploaded.uniqueId, track.uniqueId)

            assertRange(api, track, original, start = 0, length = 32 * 1024)
            val mid = original.size / 2 + 7
            assertRange(api, track, original, start = mid, length = 20_000)
            assertRange(api, track, original, start = original.size - 100, length = 100)

            val downloaded = File(workDir, "downloaded.mp3")
            assertTrue(api.downloadTo(track, downloaded.absolutePath), "download reported failure")
            assertContentEquals(original, downloaded.readBytes(), "downloaded bytes differ from the original")

            val newVersion = api.renameTrack(track, "$title renamed")
            assertNotEquals(track.versionTag, newVersion)
            val renamed = assertNotNull(api.getTrackFile(track.fileId)?.toAudioTrackOrNull())
            assertEquals("$title renamed", renamed.title)
            assertEquals(original.size.toLong(), renamed.sizeBytes)
            assertRange(api, renamed, original, start = 1_000, length = 4_000)

            api.deleteTrack(track.fileId)
            assertNull(api.getTrackFile(track.fileId)?.toAudioTrackOrNull(), "deleted track still maps as active")
            assertTrue(
                api.queryTracks(tagsAnyOf = listOf(LIVE_TEST_TAG)).none { it.fileId == track.fileId },
                "deleted track still listed",
            )
        } finally {
            purgeTagged(api)
        }
    }

    private suspend fun assertRange(api: AudioDriveApi, track: AudioTrack, original: ByteArray, start: Int, length: Int) {
        val bytes = api.readRange(track, start.toLong(), length.toLong())
        assertContentEquals(original.copyOfRange(start, start + length), bytes, "range start=$start length=$length")
    }
}
