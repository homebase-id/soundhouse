package id.homebase.audio.ui.record

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import id.homebase.audio.resources.AR
import id.homebase.audio.resources.navigate_back
import id.homebase.audio.resources.record_again
import id.homebase.audio.resources.record_default_name
import id.homebase.audio.resources.record_discard
import id.homebase.audio.resources.record_failed
import id.homebase.audio.resources.record_grant_permission
import id.homebase.audio.resources.record_hint
import id.homebase.audio.resources.record_name_label
import id.homebase.audio.resources.record_permission_needed
import id.homebase.audio.resources.record_preview_pause
import id.homebase.audio.resources.record_preview_play
import id.homebase.audio.resources.record_preview_time
import id.homebase.audio.resources.record_save
import id.homebase.audio.resources.record_start
import id.homebase.audio.resources.record_stop
import id.homebase.audio.resources.record_title
import id.homebase.audio.ui.common.formatDateTime
import id.homebase.audio.ui.common.formatDuration
import id.homebase.core.audio.rememberRecordAudioPermissionState
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

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
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            when (uiState.phase) {
                RecordPhase.Idle -> if (permission.hasPermission) {
                    RecordButton(recording = false, onClick = { viewModel.startRecording(defaultName) })
                    Spacer(Modifier.height(16.dp))
                    Text(stringResource(AR.string.record_hint), color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Text(stringResource(AR.string.record_permission_needed), style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = permission::requestPermission) { Text(stringResource(AR.string.record_grant_permission)) }
                }

                RecordPhase.Recording -> {
                    Text(formatDuration(uiState.elapsedMs), style = MaterialTheme.typography.displayMedium)
                    Spacer(Modifier.height(32.dp))
                    RecordButton(recording = true, onClick = viewModel::stopRecording)
                }

                RecordPhase.Recorded -> Recorded(uiState, viewModel, defaultName)
            }
        }
    }
}

@Composable
private fun RecordButton(recording: Boolean, onClick: () -> Unit) {
    FilledIconButton(
        onClick = onClick,
        modifier = Modifier.size(96.dp),
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = if (recording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
        ),
    ) {
        if (recording) {
            Icon(Icons.Filled.Stop, contentDescription = stringResource(AR.string.record_stop), modifier = Modifier.size(48.dp))
        } else {
            Icon(Icons.Filled.Mic, contentDescription = stringResource(AR.string.record_start), modifier = Modifier.size(48.dp))
        }
    }
}

@Composable
private fun Recorded(uiState: RecordUiState, viewModel: RecordViewModel, defaultName: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        FilledIconButton(onClick = viewModel::togglePreview, modifier = Modifier.size(64.dp)) {
            if (uiState.isPreviewPlaying) {
                Icon(Icons.Filled.Pause, contentDescription = stringResource(AR.string.record_preview_pause))
            } else {
                Icon(Icons.Filled.PlayArrow, contentDescription = stringResource(AR.string.record_preview_play))
            }
        }
        Spacer(Modifier.size(16.dp))
        Text(
            stringResource(AR.string.record_preview_time, formatDuration(uiState.previewPositionMs), formatDuration(uiState.previewDurationMs)),
            style = MaterialTheme.typography.titleMedium,
        )
    }
    Spacer(Modifier.height(24.dp))
    OutlinedTextField(
        value = uiState.name,
        onValueChange = viewModel::onNameChange,
        label = { Text(stringResource(AR.string.record_name_label)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(24.dp))
    Button(onClick = viewModel::save, enabled = uiState.name.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(AR.string.record_save))
    }
    Spacer(Modifier.height(8.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        TextButton(onClick = viewModel::discard) { Text(stringResource(AR.string.record_discard)) }
        OutlinedButton(onClick = { viewModel.startRecording(defaultName) }) { Text(stringResource(AR.string.record_again)) }
    }
}
