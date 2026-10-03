package id.homebase.soundhouse.ui.record

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import id.homebase.soundhouse.resources.AR
import id.homebase.soundhouse.resources.navigate_back
import id.homebase.soundhouse.resources.record_again
import id.homebase.soundhouse.resources.record_default_name
import id.homebase.soundhouse.resources.record_discard
import id.homebase.soundhouse.resources.record_failed
import id.homebase.soundhouse.resources.record_grant_permission
import id.homebase.soundhouse.resources.record_hint
import id.homebase.soundhouse.resources.record_listening
import id.homebase.soundhouse.resources.record_name_label
import id.homebase.soundhouse.resources.record_permission_needed
import id.homebase.soundhouse.resources.record_preview_pause
import id.homebase.soundhouse.resources.record_preview_play
import id.homebase.soundhouse.resources.record_preview_time
import id.homebase.soundhouse.resources.record_save
import id.homebase.soundhouse.resources.record_start
import id.homebase.soundhouse.resources.record_stop
import id.homebase.soundhouse.resources.record_title
import id.homebase.soundhouse.ui.common.LevelBars
import id.homebase.soundhouse.ui.common.formatDateTime
import id.homebase.soundhouse.ui.common.formatDuration
import id.homebase.soundhouse.ui.theme.tabular
import id.homebase.core.audio.rememberRecordAudioPermissionState
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

private const val LIVE_BARS = 48
private const val PREVIEW_BARS = 56

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordScreen(viewModel: RecordViewModel, onBack: () -> Unit, onSaved: () -> Unit) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val saved by rememberUpdatedState(onSaved)
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                RecordEvent.Saved -> saved()
                RecordEvent.RecordingFailed -> snackbar.showSnackbar(getString(AR.string.record_failed))
            }
        }
    }
    val permission = rememberRecordAudioPermissionState(onPermissionGranted = {})
    val defaultName = stringResource(AR.string.record_default_name, formatDateTime(RecordViewModel.nowMs()))

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(AR.string.record_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(AR.string.navigate_back))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        RecordContent(
            uiState = uiState,
            hasPermission = permission.hasPermission,
            onRequestPermission = permission::requestPermission,
            onStart = { viewModel.startRecording(defaultName) },
            onStop = viewModel::stopRecording,
            onTogglePreview = viewModel::togglePreview,
            onSeekPreview = viewModel::seekPreview,
            onNameChange = viewModel::onNameChange,
            onSave = viewModel::save,
            onDiscard = viewModel::discard,
            modifier = Modifier.fillMaxSize().padding(padding),
        )
    }
}

@Composable
fun RecordContent(
    uiState: RecordUiState,
    hasPermission: Boolean,
    onRequestPermission: () -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onTogglePreview: () -> Unit,
    onSeekPreview: (Float) -> Unit,
    onNameChange: (String) -> Unit,
    onSave: () -> Unit,
    onDiscard: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        when (uiState.phase) {
            RecordPhase.Idle -> if (hasPermission) {
                PulsingRecordButton(recording = false, level = 0f, onClick = onStart)
                Spacer(Modifier.height(24.dp))
                Text(stringResource(AR.string.record_hint), color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Text(stringResource(AR.string.record_permission_needed), style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(16.dp))
                Button(onClick = onRequestPermission) { Text(stringResource(AR.string.record_grant_permission)) }
            }

            RecordPhase.Recording -> {
                Text(
                    formatDuration(uiState.elapsedMs),
                    style = MaterialTheme.typography.displayLarge.tabular(),
                    fontWeight = FontWeight.Light,
                )
                Text(
                    stringResource(AR.string.record_listening),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.error,
                )
                Spacer(Modifier.height(32.dp))
                LevelBars(
                    levels = uiState.levels.takeLast(LIVE_BARS),
                    slots = LIVE_BARS,
                    color = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.widthIn(max = 420.dp).fillMaxWidth().height(96.dp),
                )
                Spacer(Modifier.height(40.dp))
                PulsingRecordButton(recording = true, level = uiState.levels.lastOrNull() ?: 0f, onClick = onStop)
            }

            RecordPhase.Recorded -> Recorded(uiState, onTogglePreview, onSeekPreview, onNameChange, onSave, onDiscard, onStart)
        }
    }
}

@Composable
private fun PulsingRecordButton(recording: Boolean, level: Float, onClick: () -> Unit) {
    val halo by animateFloatAsState(if (recording) 1f + level * 0.55f else 1f, animationSpec = spring(stiffness = 600f))
    val haloColor = if (recording) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(168.dp)) {
        Box(Modifier.size(112.dp).scale(halo).background(haloColor, CircleShape))
        FilledIconButton(
            onClick = onClick,
            modifier = Modifier.size(96.dp),
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = if (recording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            ),
        ) {
            if (recording) {
                Icon(Icons.Filled.Stop, contentDescription = stringResource(AR.string.record_stop), modifier = Modifier.size(44.dp))
            } else {
                Icon(Icons.Filled.Mic, contentDescription = stringResource(AR.string.record_start), modifier = Modifier.size(44.dp))
            }
        }
    }
}

@Composable
private fun Recorded(
    uiState: RecordUiState,
    onTogglePreview: () -> Unit,
    onSeekPreview: (Float) -> Unit,
    onNameChange: (String) -> Unit,
    onSave: () -> Unit,
    onDiscard: () -> Unit,
    onRecordAgain: () -> Unit,
) {
    val duration = uiState.previewDurationMs.coerceAtLeast(1)
    val played = (uiState.previewPositionMs.toFloat() / duration).coerceIn(0f, 1f)
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp)) {
            LevelBars(
                levels = waveformBars(uiState.levels, PREVIEW_BARS),
                color = MaterialTheme.colorScheme.outlineVariant,
                playedColor = MaterialTheme.colorScheme.tertiary,
                playedFraction = played,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp)
                    .pointerInput(Unit) { detectTapGestures { onSeekPreview(it.x / size.width) } },
            )
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                FilledIconButton(onClick = onTogglePreview, modifier = Modifier.size(52.dp)) {
                    if (uiState.isPreviewPlaying) {
                        Icon(Icons.Filled.Pause, contentDescription = stringResource(AR.string.record_preview_pause))
                    } else {
                        Icon(Icons.Filled.PlayArrow, contentDescription = stringResource(AR.string.record_preview_play))
                    }
                }
                Spacer(Modifier.size(16.dp))
                Text(
                    stringResource(
                        AR.string.record_preview_time,
                        formatDuration(uiState.previewPositionMs),
                        formatDuration(uiState.previewDurationMs),
                    ),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
    }
    Spacer(Modifier.height(24.dp))
    OutlinedTextField(
        value = uiState.name,
        onValueChange = onNameChange,
        label = { Text(stringResource(AR.string.record_name_label)) },
        singleLine = true,
        modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth(),
    )
    Spacer(Modifier.height(24.dp))
    Button(onClick = onSave, enabled = uiState.name.isNotBlank(), modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth()) {
        Text(stringResource(AR.string.record_save))
    }
    Spacer(Modifier.height(8.dp))
    Row(Modifier.widthIn(max = 480.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        TextButton(onClick = onDiscard) { Text(stringResource(AR.string.record_discard)) }
        OutlinedButton(onClick = onRecordAgain) { Text(stringResource(AR.string.record_again)) }
    }
}
