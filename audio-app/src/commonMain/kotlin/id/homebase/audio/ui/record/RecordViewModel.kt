package id.homebase.audio.ui.record

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import id.homebase.api.file.FileOperationsProvider
import id.homebase.audio.data.TrackOrigin
import id.homebase.audio.importing.TrackImporter
import id.homebase.audio.playback.PlaybackController
import id.homebase.core.audio.AudioPlaybackObserver
import id.homebase.core.audio.AudioPlayer
import id.homebase.core.audio.AudioRecorder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.Clock
import kotlin.time.TimeSource

enum class RecordPhase { Idle, Recording, Recorded }

@Immutable
data class RecordUiState(
    val phase: RecordPhase = RecordPhase.Idle,
    val elapsedMs: Long = 0,
    val name: String = "",
    val isPreviewPlaying: Boolean = false,
    val previewPositionMs: Long = 0,
    val previewDurationMs: Long = 0,
)

sealed interface RecordEvent {
    data object Saved : RecordEvent
    data object RecordingFailed : RecordEvent
}

class RecordViewModel(
    private val recorder: AudioRecorder,
    private val previewPlayer: AudioPlayer,
    private val fileOps: FileOperationsProvider,
    private val importer: TrackImporter,
    private val playback: PlaybackController,
) : ViewModel() {
    private val _uiState = MutableStateFlow(RecordUiState())
    val uiState: StateFlow<RecordUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<RecordEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<RecordEvent> = _events.asSharedFlow()

    private var recordingPath: String? = null
    private var timerJob: Job? = null

    init {
        previewPlayer.setPlaybackObserver(object : AudioPlaybackObserver {
            override fun onComplete() {
                _uiState.update { it.copy(isPreviewPlaying = false, previewPositionMs = 0) }
            }

            override fun onProgressUpdate(positionMs: Long, durationMs: Long) {
                _uiState.update {
                    if (!it.isPreviewPlaying) it
                    else it.copy(previewPositionMs = positionMs, previewDurationMs = durationMs.takeIf { d -> d > 0 } ?: it.previewDurationMs)
                }
            }
        })
    }

    fun startRecording(defaultName: String) {
        if (_uiState.value.phase == RecordPhase.Recording) return
        playback.pause()
        stopPreview()
        discardFile()
        viewModelScope.launch {
            try {
                val path = fileOps.createUploadTempPath("recording-", ".${recorder.getAudioFileExtension()}")
                withContext(Dispatchers.IO) { recorder.startRecording(path) }
                recordingPath = path
                _uiState.update { RecordUiState(phase = RecordPhase.Recording, name = defaultName) }
                val started = TimeSource.Monotonic.markNow()
                timerJob = launch {
                    while (true) {
                        _uiState.update { it.copy(elapsedMs = started.elapsedNow().inWholeMilliseconds) }
                        delay(200)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Logger.e(e, TAG) { "Could not start recording" }
                _events.tryEmit(RecordEvent.RecordingFailed)
            }
        }
    }

    fun stopRecording() {
        if (_uiState.value.phase != RecordPhase.Recording) return
        timerJob?.cancel()
        viewModelScope.launch {
            val path = runCatching { withContext(Dispatchers.IO) { recorder.stopRecording() } }
                .onFailure { Logger.e(it, TAG) { "Stopping the recorder failed" } }
                .getOrNull() ?: recordingPath
            recordingPath = path
            if (path == null || fileOps.getFileSize(path) <= 0L) {
                _uiState.update { it.copy(phase = RecordPhase.Idle) }
                _events.tryEmit(RecordEvent.RecordingFailed)
                return@launch
            }
            _uiState.update { it.copy(phase = RecordPhase.Recorded, previewDurationMs = it.elapsedMs) }
        }
    }

    fun togglePreview() {
        val path = recordingPath ?: return
        val state = _uiState.value
        if (state.phase != RecordPhase.Recorded) return
        viewModelScope.launch(Dispatchers.IO) {
            if (state.isPreviewPlaying) {
                previewPlayer.pause()
                _uiState.update { it.copy(isPreviewPlaying = false) }
            } else {
                if (state.previewPositionMs > 0) previewPlayer.resume() else previewPlayer.play(path)
                _uiState.update { it.copy(isPreviewPlaying = true) }
            }
        }
    }

    fun onNameChange(name: String) {
        _uiState.update { it.copy(name = name) }
    }

    fun save() {
        val path = recordingPath ?: return
        val state = _uiState.value
        if (state.phase != RecordPhase.Recorded || state.name.isBlank()) return
        stopPreview()
        recordingPath = null
        val extension = recorder.getAudioFileExtension()
        importer.enqueue(
            path = path,
            fileName = "${state.name.trim()}.$extension",
            title = state.name,
            origin = TrackOrigin.Recorded,
            deleteSourceAfter = true,
        )
        _uiState.update { RecordUiState() }
        _events.tryEmit(RecordEvent.Saved)
    }

    fun discard() {
        stopPreview()
        discardFile()
        _uiState.update { RecordUiState() }
    }

    override fun onCleared() {
        if (_uiState.value.phase == RecordPhase.Recording) {
            timerJob?.cancel()
            runCatching { recorder.stopRecording() }
        }
        previewPlayer.release()
        discardFile()
    }

    private fun stopPreview() {
        if (_uiState.value.isPreviewPlaying || _uiState.value.previewPositionMs > 0) {
            previewPlayer.stop()
            _uiState.update { it.copy(isPreviewPlaying = false, previewPositionMs = 0) }
        }
    }

    private fun discardFile() {
        recordingPath?.let { fileOps.deleteTempFile(it) }
        recordingPath = null
    }

    companion object {
        private const val TAG = "RecordViewModel"

        fun nowMs(): Long = Clock.System.now().toEpochMilliseconds()
    }
}
