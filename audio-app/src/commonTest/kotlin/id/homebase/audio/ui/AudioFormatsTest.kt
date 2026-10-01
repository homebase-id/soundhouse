package id.homebase.audio.ui

import id.homebase.audio.importing.AudioFileMetadata
import id.homebase.audio.importing.mimeTypeForFileName
import id.homebase.audio.importing.trackTitle
import kotlin.test.Test
import kotlin.test.assertEquals

class AudioFormatsTest {
    @Test
    fun `title prefers embedded metadata and falls back to the file name`() {
        assertEquals("Real Title", trackTitle(AudioFileMetadata("  Real Title ", null), "file.mp3"))
        assertEquals("my song", trackTitle(AudioFileMetadata("   ", null), "my song.mp3"))
        assertEquals("archive.tar", trackTitle(AudioFileMetadata(null, null), "archive.tar.flac"))
        assertEquals("noext", trackTitle(AudioFileMetadata(null, null), "noext"))
    }

    @Test
    fun `mime types by extension`() {
        assertEquals("audio/mpeg", mimeTypeForFileName("a.MP3"))
        assertEquals("audio/mp4", mimeTypeForFileName("a.m4a"))
        assertEquals("audio/flac", mimeTypeForFileName("a.flac"))
        assertEquals("audio/ogg", mimeTypeForFileName("a.ogg"))
        assertEquals("audio/wav", mimeTypeForFileName("a.wav"))
        assertEquals("application/octet-stream", mimeTypeForFileName("a.txt"))
    }
}
