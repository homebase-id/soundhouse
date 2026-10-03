package id.homebase.soundhouse.download

import id.homebase.api.client.KeyHeader
import id.homebase.soundhouse.data.AudioTrack
import id.homebase.soundhouse.data.AudioTrackContent
import id.homebase.soundhouse.history.ListenEntry
import id.homebase.soundhouse.settings.AudioPreferences
import id.homebase.soundhouse.settings.InMemoryAudioSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okio.FileSystem
import okio.Path.Companion.toPath
import java.io.File
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class OfflineKeeperTest {
    private val dir: File = Files.createTempDirectory("hba-offline").toFile()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @AfterTest
    fun tearDown() {
        scope.cancel()
        dir.deleteRecursively()
    }

    private fun track(title: String, size: Long) = AudioTrack(
        Uuid.random(), null, AudioTrackContent(title, size, "audio/mpeg", 600_000), 0, null, emptyList(),
        KeyHeader.empty(), KeyHeader.empty(),
    )

    private fun listened(track: AudioTrack, at: Long, positionMs: Long = 0) =
        ListenEntry(track.fileId.toString(), at, positionMs, 600_000)

    @Test
    fun `unfinished tracks come first then the most recent within the limit`() {
        val old = track("old", 10)
        val recent = track("recent", 10)
        val halfway = track("halfway", 10)
        val huge = track("huge", 100)
        val history = listOf(listened(old, 1), listened(recent, 5), listened(halfway, 2, positionMs = 60_000), listened(huge, 9))
        val picked = offlineSelection(listOf(old, recent, halfway, huge), history, limitBytes = 25)
        assertEquals(listOf("halfway", "recent"), picked.map { it.title })
    }

    @Test
    fun `a removed track stays out until it is played again`() {
        val a = track("a", 1)
        val skipped = mapOf(a.fileId.toString() to 10L)
        assertTrue(offlineSelection(listOf(a), listOf(listened(a, 5)), 100, skipped).isEmpty())
        assertEquals(listOf(a), offlineSelection(listOf(a), listOf(listened(a, 11)), 100, skipped))
    }

    private inner class Harness(
        prefs: AudioPreferences = AudioPreferences(offlineOnWifiOnly = false),
        unmetered: Boolean = true,
        recheckMs: Long = 60_000,
    ) {
        val tracks = MutableStateFlow<List<AudioTrack>>(emptyList())
        val history = MutableStateFlow<Map<String, ListenEntry>>(emptyMap())
        val settings = InMemoryAudioSettings(prefs)
        val wifi = AtomicBoolean(unmetered)
        val fetched = mutableListOf<String>()
        val downloads = DownloadStore(
            directory = File(dir, "downloads").path,
            downloader = { track, path, _ ->
                synchronized(fetched) { fetched += track.title }
                delay(20)
                File(path).apply { parentFile.mkdirs() }.writeBytes(ByteArray(track.sizeBytes.toInt()))
                true
            },
            scope = scope,
            fileSystem = FileSystem.SYSTEM,
        )
        val keeper = OfflineKeeper(
            downloads, tracks, MutableStateFlow(true), history, MutableStateFlow(true), settings,
            { wifi.get() }, File(dir, "offline.json").path, FileSystem.SYSTEM, scope, settleMs = 10, recheckMs = recheckMs,
        )

        fun play(vararg played: Pair<AudioTrack, Long>) {
            tracks.value = (tracks.value + played.map { it.first }).distinct()
            history.value = history.value + played.associate { (t, at) -> t.fileId.toString() to listened(t, at) }
        }

        suspend fun awaitDownloaded(vararg expected: AudioTrack) = withTimeout(5_000) {
            downloads.downloaded.first { it == expected.map { t -> t.fileId }.toSet() }
        }
    }

    @Test
    fun `recent listening is fetched and dropped once it falls out of the limit`() = runBlocking<Unit> {
        val h = Harness(AudioPreferences(offlineOnWifiOnly = false, offlineLimitBytes = 20))
        val a = track("a", 10)
        val b = track("b", 10)
        val c = track("c", 10)
        h.play(a to 1, b to 2)
        h.awaitDownloaded(a, b)
        h.play(c to 3)
        h.awaitDownloaded(b, c)
        assertEquals(setOf(b.fileId, c.fileId), h.keeper.autoKept.value)
    }

    @Test
    fun `the user's own downloads are never evicted and don't use the limit`() = runBlocking<Unit> {
        val h = Harness(AudioPreferences(offlineOnWifiOnly = false, offlineLimitBytes = 10))
        val mine = track("mine", 10)
        val a = track("a", 10)
        h.tracks.value = listOf(mine)
        h.keeper.keep(mine)
        h.awaitDownloaded(mine)
        h.play(a to 1)
        h.awaitDownloaded(mine, a)
        h.settings.update { it.copy(keepRecentOffline = false) }
        h.awaitDownloaded(mine)
    }

    @Test
    fun `a removed track isn't fetched again`() = runBlocking<Unit> {
        val h = Harness()
        val a = track("a", 10)
        h.play(a to 1)
        h.awaitDownloaded(a)
        h.keeper.release(a)
        h.awaitDownloaded()
        delay(100)
        assertEquals(listOf("a"), synchronized(h.fetched) { h.fetched.toList() })
    }

    @Test
    fun `wifi only waits for an unmetered network and picks it up on the next check`() = runBlocking<Unit> {
        val h = Harness(AudioPreferences(offlineOnWifiOnly = true), unmetered = false, recheckMs = 100)
        val a = track("a", 10)
        h.play(a to 1)
        delay(250)
        assertTrue(h.downloads.downloaded.value.isEmpty())
        h.wifi.set(true)
        h.awaitDownloaded(a)
    }
}
