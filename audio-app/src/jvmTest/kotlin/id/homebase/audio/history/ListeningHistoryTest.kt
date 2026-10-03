package id.homebase.audio.history

import id.homebase.api.client.KeyHeader
import id.homebase.api.client.eventbus.BackendEvent
import id.homebase.api.client.eventbus.EventBus
import id.homebase.audio.data.AudioTrack
import id.homebase.audio.data.AudioTrackContent
import id.homebase.audio.playback.PlaybackController
import id.homebase.core.audio.AudioPlaybackObserver
import id.homebase.core.audio.AudioPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okio.FileSystem
import java.io.File
import java.nio.file.Files
import java.util.Collections
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class ListeningHistoryTest {
    private val dir: File = Files.createTempDirectory("hba-history").toFile()
    private val file = File(dir, "history.json").absolutePath
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var clock = 1_000_000L

    @AfterTest
    fun tearDown() {
        scope.cancel()
        dir.deleteRecursively()
    }

    private fun history(eventBus: EventBus? = null) =
        ListeningHistory(file, FileSystem.SYSTEM, scope, eventBus, now = { clock })

    private suspend fun awaitFile(predicate: (String) -> Boolean) = withTimeout(5_000) {
        while (!File(file).exists() || !predicate(File(file).readText())) delay(20)
    }

    @Test
    fun `progress marks tracks resumable or finished`() {
        val history = history()
        val a = Uuid.random()
        history.record(a, 5_000, 600_000)
        assertFalse(history.entry(a)!!.resumable, "too early to offer a resume")
        history.record(a, 120_000, 600_000)
        val entry = history.entry(a)!!
        assertTrue(entry.resumable)
        assertEquals(480_000, entry.remainingMs)
        assertEquals(0.2f, entry.progress)
        history.record(a, 590_000, 600_000)
        assertTrue(history.entry(a)!!.finished)
        assertFalse(history.entry(a)!!.resumable)
    }

    @Test
    fun `saved progress survives a restart and newer progress wins`() = runBlocking {
        val a = Uuid.random()
        history().record(a, 42_000, 100_000, force = true)
        awaitFile { it.contains("42000") }
        val restarted = history()
        withTimeout(5_000) { restarted.entries.first { a.toString() in it } }
        assertEquals(42_000, restarted.entry(a)!!.positionMs)
    }

    /** Reads of the stored file wait for [release], so a test can act while startup loading is in flight. */
    private class GatedReads : okio.ForwardingFileSystem(FileSystem.SYSTEM) {
        val release = java.util.concurrent.CountDownLatch(1)
        override fun source(file: okio.Path): okio.Source {
            release.await()
            return super.source(file)
        }
    }

    private suspend fun storedHistoryWith(vararg ids: Uuid) {
        val writer = history()
        ids.forEach { writer.record(it, 30_000, 100_000, force = true) }
        awaitFile { text -> ids.all { it.toString() in text } }
    }

    @Test
    fun `signing out while the stored history is still loading doesn't bring it back`() = runBlocking<Unit> {
        val a = Uuid.random()
        storedHistoryWith(a)
        val gated = GatedReads()
        val bus = EventBus()
        val history = ListeningHistory(file, gated, scope, bus, now = { clock })
        bus.emit(BackendEvent.SessionEnded)
        withTimeout(5_000) { history.entries.first { it.isEmpty() } }
        gated.release.countDown()
        withTimeout(5_000) { history.isLoaded.first { it } }
        assertTrue(history.entries.value.isEmpty())
    }

    @Test
    fun `forgetting a track while the stored history is still loading keeps it forgotten`() = runBlocking<Unit> {
        val a = Uuid.random()
        val b = Uuid.random()
        storedHistoryWith(a, b)
        val gated = GatedReads()
        val history = ListeningHistory(file, gated, scope, null, now = { clock })
        history.forget(a)
        gated.release.countDown()
        withTimeout(5_000) { history.isLoaded.first { it } }
        assertNull(history.entry(a))
        assertEquals(30_000, history.entry(b)?.positionMs)
    }

    @Test
    fun `a save made while the stored history is still loading doesn't overwrite it`() = runBlocking<Unit> {
        val a = Uuid.random()
        val b = Uuid.random()
        val c = Uuid.random()
        storedHistoryWith(a, b)
        val gated = GatedReads()
        val history = ListeningHistory(file, gated, scope, null, now = { clock })
        history.record(c, 20_000, 100_000, force = true)
        // Long enough for an ungated save to land; the stored file must be untouched until it's read.
        delay(300)
        val stored = File(file).readText()
        assertTrue(a.toString() in stored && b.toString() in stored, "stored history overwritten before load: $stored")
        gated.release.countDown()
        withTimeout(5_000) { history.isLoaded.first { it } }
        awaitFile { text -> listOf(a, b, c).all { it.toString() in text } }
    }

    @Test
    fun `sign-out and delete clear entries`() = runBlocking {
        val bus = EventBus()
        val history = history(bus)
        val a = Uuid.random()
        val b = Uuid.random()
        history.record(a, 30_000, 100_000, force = true)
        history.record(b, 30_000, 100_000, force = true)
        history.forget(a)
        assertNull(history.entry(a))
        bus.emit(BackendEvent.SessionEnded)
        withTimeout(5_000) { history.entries.first { it.isEmpty() } }
        awaitFile { it == "[]" }
    }

    @Test
    fun `recorder follows playback and resume starts part-way`() = runBlocking {
        val player = FakePlayer()
        val controller = PlaybackController(player, { it.title }, scope)
        val history = history()
        ListeningRecorder(controller, history, scope)
        val first = track("first")
        val second = track("second")

        controller.playQueue(listOf(first, second), 0, startAtMs = 70_000)
        withTimeout(5_000) { controller.state.first { it.isPlaying } }
        assertTrue("seek 70000" in player.calls, "resume should seek once the source is open")
        player.observer.onProgressUpdate(75_000, 300_000)
        withTimeout(5_000) { history.entries.first { (it[first.fileId.toString()]?.positionMs ?: 0) == 75_000L } }

        controller.togglePlayPause()
        withTimeout(5_000) { controller.state.first { !it.isPlaying } }
        controller.next()
        withTimeout(5_000) { controller.state.first { it.isPlaying && it.current == second } }
        val left = assertNotNull(history.entry(first.fileId))
        assertEquals(75_000, left.positionMs)
        assertTrue(left.resumable)
    }

    private fun track(title: String) = AudioTrack(
        Uuid.random(), null, AudioTrackContent(title, 1, "audio/mpeg", 300_000), 0, null, emptyList(),
        KeyHeader.empty(), KeyHeader.empty(),
    )

    private class FakePlayer : AudioPlayer {
        val calls: MutableList<String> = Collections.synchronizedList(mutableListOf())
        lateinit var observer: AudioPlaybackObserver
        override fun play(filePath: String) { calls += "play $filePath" }
        override fun jumpTo(positionMs: Long) { calls += "seek $positionMs" }
        override fun resume() { calls += "resume" }
        override fun pause() { calls += "pause" }
        override fun stop() { calls += "stop" }
        override fun release() = Unit
        override fun setSpeed(speed: Float) = Unit
        override fun setPlaybackObserver(observer: AudioPlaybackObserver) { this.observer = observer }
    }
}
