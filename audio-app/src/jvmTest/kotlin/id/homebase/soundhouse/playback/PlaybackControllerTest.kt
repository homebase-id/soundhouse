package id.homebase.soundhouse.playback

import id.homebase.api.client.KeyHeader
import id.homebase.soundhouse.data.AudioTrack
import id.homebase.soundhouse.data.AudioTrackContent
import id.homebase.core.audio.AudioPlaybackObserver
import id.homebase.core.audio.AudioPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.util.Collections
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class PlaybackControllerTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @AfterTest
    fun tearDown() = scope.cancel()

    private class FakePlayer : AudioPlayer {
        val calls: MutableList<String> = Collections.synchronizedList(mutableListOf())
        lateinit var observer: AudioPlaybackObserver
        var failOn: String? = null
        override fun play(filePath: String) {
            if (filePath == failOn) error("cannot open")
            calls += "play $filePath"
        }
        override fun jumpTo(positionMs: Long) { calls += "seek $positionMs" }
        override fun resume() { calls += "resume" }
        override fun pause() { calls += "pause" }
        override fun stop() { calls += "stop" }
        override fun release() = Unit
        override fun setSpeed(speed: Float) { calls += "speed $speed" }
        override fun setPlaybackObserver(observer: AudioPlaybackObserver) { this.observer = observer }
    }

    private fun track(title: String, durationMs: Long = 60_000) = AudioTrack(
        Uuid.random(), null, AudioTrackContent(title, 1, "audio/mpeg", durationMs), 0, null, emptyList(),
        KeyHeader.empty(), KeyHeader.empty(),
    )

    private val tracks = listOf(track("a"), track("b"), track("c"))
    private val player = FakePlayer()
    private val controller = PlaybackController(player, { "url:${it.title}" }, scope)

    private suspend fun awaitState(predicate: (PlaybackState) -> Boolean) =
        withTimeout(5_000) { controller.state.first(predicate) }

    @Test
    fun `plays the chosen track with its known duration`() = runBlocking {
        controller.playQueue(tracks, 1)
        val state = awaitState { it.isPlaying }
        assertEquals("b", state.current?.title)
        assertEquals(60_000, state.durationMs)
        assertTrue("play url:b" in player.calls)
    }

    @Test
    fun `pause resume seek and progress`() = runBlocking {
        controller.playQueue(tracks, 0)
        awaitState { it.isPlaying }
        player.observer.onProgressUpdate(12_000, 61_000)
        assertEquals(12_000, controller.state.value.positionMs)
        assertEquals(61_000, controller.state.value.durationMs)
        controller.togglePlayPause()
        awaitState { !it.isPlaying }
        controller.seekTo(30_000)
        controller.togglePlayPause()
        awaitState { it.isPlaying }
        assertEquals(listOf("pause", "seek 30000", "resume"), player.calls.filter { it == "pause" || it.startsWith("seek") || it == "resume" })
    }

    @Test
    fun `next previous and auto advance`() = runBlocking {
        controller.playQueue(tracks, 0)
        awaitState { it.isPlaying }
        controller.next()
        awaitState { it.isPlaying && it.current?.title == "b" }
        player.observer.onProgressUpdate(10_000, 60_000)
        controller.previous()
        awaitState { it.positionMs == 0L && it.current?.title == "b" }
        controller.previous()
        awaitState { it.isPlaying && it.current?.title == "a" }
        controller.playQueue(tracks, 2)
        awaitState { it.isPlaying && it.current?.title == "c" }
        player.observer.onComplete()
        val ended = awaitState { !it.isPlaying }
        assertEquals("c", ended.current?.title)
        controller.playQueue(tracks, 0)
        awaitState { it.isPlaying && it.current?.title == "a" }
        player.observer.onComplete()
        awaitState { it.isPlaying && it.current?.title == "b" }
        Unit
    }

    @Test
    fun `a source that cannot open marks the track failed`() = runBlocking {
        player.failOn = "url:a"
        controller.playQueue(tracks, 0)
        val state = awaitState { it.failed }
        assertEquals(false, state.isPlaying)
        player.failOn = null
        controller.togglePlayPause()
        awaitState { it.isPlaying && !it.failed }
        Unit
    }

    @Test
    fun `removing the playing track stops and others shift the index`() = runBlocking {
        controller.playQueue(tracks, 1)
        awaitState { it.isPlaying }
        controller.removeTrack(tracks[0].fileId)
        assertEquals(0, controller.state.value.index)
        assertEquals("b", controller.state.value.current?.title)
        controller.removeTrack(tracks[1].fileId)
        assertEquals(null, controller.state.value.current)
    }

    @Test
    fun `speed reaches the player and carries over to the next queue`() = runBlocking {
        controller.playQueue(tracks, 0)
        awaitState { it.isPlaying }
        controller.setSpeed(1.5f)
        controller.playQueue(tracks, 2)
        val state = awaitState { it.isPlaying && it.current?.title == "c" }
        assertEquals(1.5f, state.speed)
        withTimeout(5_000) { while ("speed 1.5" !in player.calls) kotlinx.coroutines.delay(5) }
        controller.setSpeed(9f)
        assertEquals(2f, controller.state.value.speed)
    }

    @Test
    fun `skips move relative to the position and stay inside the track`() = runBlocking {
        controller.playQueue(tracks, 0)
        awaitState { it.isPlaying }
        controller.seekTo(5_000)
        controller.skipBy(-10_000)
        assertEquals(0, controller.state.value.positionMs)
        controller.skipBy(30_000)
        assertEquals(30_000, controller.state.value.positionMs)
        controller.skipBy(45_000)
        assertEquals(60_000, controller.state.value.positionMs)
    }

    @Test
    fun `a sleep timer pauses playback when it runs out`() = runBlocking {
        controller.playQueue(tracks, 0)
        awaitState { it.isPlaying }
        controller.sleepAfter(50)
        assertTrue(controller.state.value.sleepTimer is SleepTimer.At)
        val state = awaitState { !it.isPlaying }
        assertEquals(null, state.sleepTimer)
        assertTrue("pause" in player.calls)
    }

    @Test
    fun `cancelling the sleep timer keeps playing`() = runBlocking {
        controller.playQueue(tracks, 0)
        awaitState { it.isPlaying }
        controller.sleepAfter(50)
        controller.sleepAfter(null)
        kotlinx.coroutines.delay(150)
        assertTrue(controller.state.value.isPlaying)
    }

    @Test
    fun `sleeping at the end of the track stops instead of moving on`() = runBlocking {
        controller.playQueue(tracks, 0)
        awaitState { it.isPlaying }
        controller.sleepAtEndOfTrack()
        player.observer.onComplete()
        val state = awaitState { !it.isPlaying }
        assertEquals("a", state.current?.title)
        assertEquals(null, state.sleepTimer)
    }

    @Test
    fun `buffering after a seek holds the target until the player catches up`() = runBlocking {
        controller.playQueue(tracks, 0)
        awaitState { it.isPlaying }
        controller.seekTo(30_000)
        player.observer.onBufferingChanged(true)
        awaitState { it.isBuffering }
        // The player still reports where it was before the seek landed.
        player.observer.onProgressUpdate(1_000, 60_000)
        assertEquals(30_000, controller.state.value.positionMs)
        player.observer.onBufferingChanged(false)
        awaitState { !it.isBuffering }
        player.observer.onProgressUpdate(30_100, 60_000)
        assertEquals(30_100, awaitState { it.positionMs == 30_100L }.positionMs)
    }
}
