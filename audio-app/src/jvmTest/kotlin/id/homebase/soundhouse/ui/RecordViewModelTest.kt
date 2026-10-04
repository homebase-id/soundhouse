package id.homebase.soundhouse.ui

import id.homebase.api.client.drives.HomebaseFile
import id.homebase.soundhouse.data.AudioTrackContent
import id.homebase.soundhouse.data.TestFileOps
import id.homebase.soundhouse.data.TrackOrigin
import id.homebase.soundhouse.data.TrackUploadTarget
import id.homebase.soundhouse.data.UploadedTrack
import id.homebase.soundhouse.data.buildTrackFile
import id.homebase.soundhouse.data.trackContentJson
import id.homebase.soundhouse.importing.AudioFileMetadata
import id.homebase.soundhouse.importing.ImportStatus
import id.homebase.soundhouse.importing.TrackImporter
import id.homebase.soundhouse.playback.PlaybackController
import id.homebase.soundhouse.ui.record.RecordEvent
import id.homebase.soundhouse.ui.record.RecordPhase
import id.homebase.soundhouse.ui.record.RecordViewModel
import id.homebase.core.audio.AudioPlaybackObserver
import id.homebase.core.audio.AudioPlayer
import id.homebase.core.audio.AudioRecorder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

@OptIn(ExperimentalCoroutinesApi::class)
class RecordViewModelTest {
    private val dir: File = Files.createTempDirectory("hba-record").toFile()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @BeforeTest
    fun setUp() = Dispatchers.setMain(Dispatchers.Default)

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
        scope.cancel()
        dir.deleteRecursively()
    }

    private class FakeRecorder(private val bytes: Int) : AudioRecorder {
        var path: String? = null
        override fun getAudioFileExtension() = "wav"
        override fun startRecording(fileName: String) {
            path = fileName
        }
        override fun stopRecording(): String? = path?.also { File(it).writeBytes(ByteArray(bytes)) }
        override fun currentLevel(): Float = 0.25f
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

    private class RecordingTarget : TrackUploadTarget {
        val uploaded = mutableListOf<AudioTrackContent>()
        override suspend fun uploadTrack(
            sourcePath: String, content: AudioTrackContent, tags: List<Uuid>, uniqueId: Uuid, coverArt: ByteArray?,
            notes: String?, onProgress: (Float) -> Unit,
        ): UploadedTrack {
            uploaded += content
            return UploadedTrack(Uuid.random(), uniqueId, Uuid.random())
        }
        override suspend fun getTrackFile(fileId: Uuid): HomebaseFile = buildTrackFile(trackContentJson(uploaded.last()))
    }

    private fun viewModel(recorder: AudioRecorder, target: TrackUploadTarget): Pair<RecordViewModel, TrackImporter> {
        val fileOps = TestFileOps(dir)
        val importer = TrackImporter(target, fileOps, scope, onUploaded = {}, readMetadata = { AudioFileMetadata(null, 900) }, readCover = { null })
        val playback = PlaybackController(SilentPlayer(), { it.title }, scope)
        return RecordViewModel(recorder, SilentPlayer(), fileOps, importer, playback) to importer
    }

    private suspend fun RecordViewModel.awaitPhase(phase: RecordPhase) =
        withTimeout(5_000) { uiState.first { it.phase == phase } }

    @Test
    fun `record name and save queues an upload of the recording`() = runBlocking {
        val recorder = FakeRecorder(bytes = 4_000)
        val target = RecordingTarget()
        val (vm, importer) = viewModel(recorder, target)
        vm.startRecording("Recording default")
        assertEquals("Recording default", vm.awaitPhase(RecordPhase.Recording).name)
        val levels = withTimeout(5_000) { vm.uiState.first { it.levels.size >= 3 } }.levels
        assertTrue(levels.all { it == 0.5f }, "levels should be the square root of the recorder's 0.25: $levels")
        vm.stopRecording()
        vm.awaitPhase(RecordPhase.Recorded)
        vm.onNameChange("Kitchen idea")
        val saved = async(start = CoroutineStart.UNDISPATCHED) { withTimeout(5_000) { vm.events.first() } }
        vm.save()
        assertEquals(RecordEvent.Saved, saved.await())
        withTimeout(5_000) { importer.jobs.first { jobs -> jobs.singleOrNull()?.status == ImportStatus.Done } }
        val content = target.uploaded.single()
        assertEquals("Kitchen idea", content.title)
        assertEquals(TrackOrigin.Recorded, content.origin)
        assertEquals("Kitchen idea.wav", content.fileName)
        assertEquals(4_000, content.sizeBytes)
        assertTrue(!File(recorder.path!!).exists(), "the recording temp file should be removed after upload")
    }

    @Test
    fun `an empty recording is reported and nothing is saved`() = runBlocking {
        val target = RecordingTarget()
        val (vm, _) = viewModel(FakeRecorder(bytes = 0), target)
        vm.startRecording("x")
        vm.awaitPhase(RecordPhase.Recording)
        val failed = async(start = CoroutineStart.UNDISPATCHED) { withTimeout(5_000) { vm.events.first() } }
        vm.stopRecording()
        assertEquals(RecordEvent.RecordingFailed, failed.await())
        vm.awaitPhase(RecordPhase.Idle)
        assertTrue(target.uploaded.isEmpty())
    }

    @Test
    fun `discard deletes the recording`() = runBlocking {
        val recorder = FakeRecorder(bytes = 10)
        val (vm, _) = viewModel(recorder, RecordingTarget())
        vm.startRecording("x")
        vm.awaitPhase(RecordPhase.Recording)
        vm.stopRecording()
        vm.awaitPhase(RecordPhase.Recorded)
        vm.discard()
        vm.awaitPhase(RecordPhase.Idle)
        assertTrue(!File(recorder.path!!).exists())
    }
}
