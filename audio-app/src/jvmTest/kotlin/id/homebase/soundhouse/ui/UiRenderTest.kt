package id.homebase.soundhouse.ui

import id.homebase.soundhouse.importing.ImportFailure
import id.homebase.soundhouse.importing.ImportStatus
import id.homebase.soundhouse.importing.ImportJob
import id.homebase.soundhouse.ui.importing.ImportSummaryCard
import id.homebase.soundhouse.ui.importing.ImportActions
import id.homebase.soundhouse.data.AudioCollection
import id.homebase.soundhouse.ui.collections.CollectionSummary
import id.homebase.soundhouse.ui.collections.CollectionChips
import id.homebase.soundhouse.ui.settings.SettingsContent
import id.homebase.soundhouse.ui.settings.SettingsUiState
import id.homebase.soundhouse.settings.InMemoryAudioSettings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import id.homebase.api.client.KeyHeader
import id.homebase.soundhouse.data.AudioTrack
import id.homebase.soundhouse.data.AudioTrackContent
import id.homebase.soundhouse.data.TrackOrigin
import id.homebase.soundhouse.playback.PlaybackController
import id.homebase.soundhouse.history.ListenEntry
import id.homebase.soundhouse.ui.home.HomeContent
import id.homebase.soundhouse.ui.home.HomeUiState
import id.homebase.soundhouse.ui.home.ListenedTrack
import id.homebase.soundhouse.ui.library.EmptyLibrary
import id.homebase.soundhouse.ui.library.FilterChips
import id.homebase.soundhouse.ui.library.LibrarySort
import id.homebase.soundhouse.ui.library.SearchPill
import id.homebase.soundhouse.ui.library.TrackActions
import id.homebase.soundhouse.ui.library.TrackRow
import id.homebase.soundhouse.ui.player.MiniPlayer
import id.homebase.soundhouse.ui.record.RecordContent
import id.homebase.soundhouse.ui.record.RecordPhase
import id.homebase.soundhouse.ui.record.RecordUiState
import id.homebase.soundhouse.ui.player.PlayerScreen
import id.homebase.soundhouse.importing.TrackDetails
import id.homebase.soundhouse.ui.player.PlayerViewModel
import id.homebase.core.audio.AudioPlaybackObserver
import id.homebase.core.audio.AudioPlayer
import id.homebase.soundhouse.ui.theme.AudioTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/**
 * Renders the main screens with sample data and writes PNGs to build/ui-renders, so a layout can
 * be looked at without signing in. Fails if a screen throws while composing.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class UiRenderTest {
    private val outDir = File("build/ui-renders").apply { mkdirs() }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @BeforeTest
    fun setUp() = Dispatchers.setMain(Dispatchers.Default)

    @AfterTest
    fun tearDown() = scope.cancel()

    private fun track(
        title: String,
        durationMs: Long?,
        daysAgo: Int,
        origin: TrackOrigin = TrackOrigin.Imported,
        details: TrackDetails? = null,
    ) = AudioTrack(
        fileId = Uuid.random(),
        uniqueId = null,
        content = AudioTrackContent(title, 1, if (origin == TrackOrigin.Recorded) "audio/wav" else "audio/mpeg", durationMs, origin = origin, details = details),
        dateAddedMs = 1_790_000_000_000 - daysAgo * 86_400_000L,
        versionTag = null,
        tags = emptyList(),
        keyHeader = KeyHeader.empty(),
        payloadKeyHeader = KeyHeader.empty(),
    )

    private val tracks = listOf(
        track("Morning walk in the hills", 61_000, 0),
        track("Bass practice — scales", 312_000, 1, details = TrackDetails(artist = "Todd", album = "Practice tapes")),
        track("Kitchen idea", 18_000, 2, TrackOrigin.Recorded),
        track("Live at the Paradiso", 3_725_000, 5, details = TrackDetails(artist = "Nina Simone", album = "Live in Amsterdam")),
        track("Rain on the window", null, 9),
        track("🎵 Lullaby", 194_000, 12),
    )

    private val noActions = TrackActions({}, {}, {}, {}, {}, {})

    private fun sampleCollection(name: String) = AudioCollection(Uuid.random(), Uuid.random(), name, 0, null, KeyHeader.empty())

    private val collections = listOf(
        CollectionSummary(sampleCollection("DJ mixes"), 14),
        CollectionSummary(sampleCollection("Voice memos"), 6),
        CollectionSummary(sampleCollection("Lectures"), 3),
    )

    private fun render(name: String, dark: Boolean, width: Int = 412, height: Int = 892, content: @Composable () -> Unit) {
        val scene = ImageComposeScene(width = width * 2, height = height * 2, density = Density(2f)) {
            AudioTheme(darkTheme = dark, followsSystemTheme = false, updatesSystemChrome = false) { content() }
        }
        try {
            scene.render(0)
            val png = scene.render(500_000_000).encodeToData(EncodedImageFormat.PNG)!!.bytes
            File(outDir, "$name-${if (dark) "dark" else "light"}.png").writeBytes(png)
        } finally {
            scene.close()
        }
    }

    @Test
    fun `library renders`() {
        for (dark in listOf(false, true)) render("library", dark) {
            Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).padding(top = 24.dp)) {
                Text("Library", style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(horizontal = 16.dp))
                SearchPill("", {}, Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp))
                FilterChips(LibrarySort.Newest, false, {}, {}, Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                CollectionChips(collections, {}, {})
                tracks.forEachIndexed { index, track ->
                    TrackRow(
                        track = track,
                        isCurrent = index == 1,
                        isPlaying = true,
                        downloaded = index == 0 || index == 3,
                        downloadProgress = if (index == 4) 0.4f else null,
                        downloadFailed = false,
                        onClick = {},
                        actions = noActions,
                    )
                }
            }
        }
        assertTrue(File(outDir, "library-light.png").length() > 0)
    }

    @Test
    fun `home renders`() {
        val now = kotlin.time.Clock.System.now().toEpochMilliseconds()
        val history = listOf(
            ListenedTrack(tracks[3], ListenEntry(tracks[3].fileId.toString(), now - 20 * 60_000, 1_900_000, 3_725_000)),
            ListenedTrack(tracks[1], ListenEntry(tracks[1].fileId.toString(), now - 3 * 3_600_000, 120_000, 312_000)),
            ListenedTrack(tracks[0], ListenEntry(tracks[0].fileId.toString(), now - 30 * 3_600_000, 61_000, 61_000, finished = true)),
        )
        val state = HomeUiState(
            isLoaded = true,
            totalTracks = tracks.size,
            allTracks = tracks,
            continueListening = history.filter { it.entry.resumable },
            recentlyPlayed = history,
            recentlyAdded = tracks,
            nowPlayingId = tracks[3].fileId,
            isPlaying = true,
            collections = collections,
        )
        for (dark in listOf(false, true)) render("home", dark, height = 1100) {
            HomeContent(state, {}, {}, {}, {}, {}, {}, {}, {}, {}, actions = {})
        }
    }

    @Test
    fun `import summary renders`() {
        fun job(name: String, status: ImportStatus, progress: Float = 0f) =
            ImportJob(Uuid.random(), name, status, progress, if (status == ImportStatus.Failed) ImportFailure.Connection else null)
        val actions = ImportActions({}, {}, {})
        val active = List(12) { job("Mix $it.mp3", ImportStatus.Done) } +
            listOf(job("Cloud 9 Sessions.mp3", ImportStatus.Uploading, 0.6f), job("Voice memo 14.m4a", ImportStatus.Uploading, 0.2f)) +
            List(26) { job("Queued $it.mp3", ImportStatus.Queued) } + listOf(job("Broken.mp3", ImportStatus.Failed))
        render("import-summary", dark = false, height = 420) {
            Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).padding(top = 16.dp)) {
                ImportSummaryCard(active, actions)
                ImportSummaryCard(listOf(job("Bass practice.mp3", ImportStatus.Uploading, 0.4f)), actions)
                ImportSummaryCard(List(40) { job("Done $it.mp3", ImportStatus.Done) }, actions)
            }
        }
    }

    @Test
    fun `settings renders`() {
        val state = SettingsUiState(automaticBytes = 1_340_000_000, ownBytes = 412_000_000)
        render("settings", dark = false) { SettingsContent(state, {}, {}, {}, {}) }
    }

    @Test
    fun `record screen renders while recording and in preview`() {
        val levels = List(140) { i -> (kotlin.math.sin(i / 4.0) * 0.4 + 0.5 + (i % 7) * 0.03).toFloat().coerceIn(0f, 1f) }
        val recording = RecordUiState(phase = RecordPhase.Recording, elapsedMs = 83_000, levels = levels)
        val recorded = RecordUiState(
            phase = RecordPhase.Recorded,
            name = "Kitchen idea",
            levels = levels,
            previewDurationMs = 11_200,
            previewPositionMs = 4_100,
            isPreviewPlaying = true,
        )
        for ((name, state) in listOf("record-live" to recording, "record-preview" to recorded)) {
            render(name, dark = false) {
                RecordContent(
                    uiState = state, hasPermission = true, onRequestPermission = {}, onStart = {}, onStop = {},
                    onTogglePreview = {}, onSeekPreview = {}, onNameChange = {}, onSave = {}, onDiscard = {},
                    modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface),
                )
            }
        }
    }

    @Test
    fun `empty library renders`() {
        render("library-empty", dark = false) {
            Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).padding(top = 120.dp)) {
                EmptyLibrary(onImport = {}, onRecord = {})
            }
        }
    }

    @Test
    fun `player and mini player render`() = runBlocking {
        val controller = PlaybackController(SilentPlayer(), { it.title }, scope)
        controller.playQueue(tracks, 1)
        withTimeout(5_000) { controller.state.first { it.isPlaying } }
        controller.seekTo(124_000)
        val viewModel = PlayerViewModel(controller, InMemoryAudioSettings())
        withTimeout(5_000) { viewModel.uiState.first { it.title != null } }
        for (dark in listOf(false, true)) render("player", dark) {
            PlayerScreen(viewModel, onBack = {})
        }
        render("mini-player", dark = false, height = 120) {
            Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
                MiniPlayer(viewModel, onOpen = {})
            }
        }
    }

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
}
