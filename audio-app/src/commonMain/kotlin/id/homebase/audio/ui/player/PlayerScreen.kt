package id.homebase.audio.ui.player

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import id.homebase.audio.resources.AR
import id.homebase.audio.resources.navigate_back
import id.homebase.audio.resources.player_failed
import id.homebase.audio.resources.player_next
import id.homebase.audio.resources.player_nothing_playing
import id.homebase.audio.resources.player_pause
import id.homebase.audio.resources.player_play
import id.homebase.audio.resources.player_previous
import id.homebase.audio.resources.player_position
import id.homebase.audio.ui.common.formatDuration
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(viewModel: PlayerViewModel, onBack: () -> Unit) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (uiState.trackCount > 0) {
                        Text(stringResource(AR.string.player_position, uiState.trackNumber, uiState.trackCount))
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(AR.string.navigate_back))
                    }
                },
            )
        },
    ) { padding ->
        val title = uiState.title
        if (title == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(stringResource(AR.string.player_nothing_playing), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return@Scaffold
        }
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                Icons.Filled.GraphicEq,
                contentDescription = null,
                modifier = Modifier.size(120.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(32.dp))
            Text(
                title,
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (uiState.failed) {
                Spacer(Modifier.height(8.dp))
                Text(stringResource(AR.string.player_failed), color = MaterialTheme.colorScheme.error)
            }
            Spacer(Modifier.height(24.dp))
            SeekBar(uiState, onSeek = viewModel::seekTo)
            Spacer(Modifier.height(16.dp))
            Controls(uiState, viewModel)
        }
    }
}

@Composable
private fun SeekBar(uiState: PlayerUiState, onSeek: (Long) -> Unit) {
    var dragPosition by remember { mutableStateOf<Float?>(null) }
    val duration = uiState.durationMs.coerceAtLeast(1)
    val shownMs = dragPosition?.let { (it * duration).toLong() } ?: uiState.positionMs
    Column(Modifier.fillMaxWidth()) {
        Slider(
            value = dragPosition ?: (uiState.positionMs.toFloat() / duration).coerceIn(0f, 1f),
            onValueChange = { dragPosition = it },
            onValueChangeFinished = {
                dragPosition?.let { onSeek((it * duration).toLong()) }
                dragPosition = null
            },
            enabled = uiState.durationMs > 0 && !uiState.isLoading,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatDuration(shownMs), style = MaterialTheme.typography.labelMedium)
            Text(formatDuration(uiState.durationMs), style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun Controls(uiState: PlayerUiState, viewModel: PlayerViewModel) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        IconButton(onClick = viewModel::previous, enabled = uiState.hasPrevious) {
            Icon(Icons.Filled.SkipPrevious, contentDescription = stringResource(AR.string.player_previous))
        }
        FilledIconButton(onClick = viewModel::togglePlayPause, modifier = Modifier.size(72.dp), enabled = !uiState.isLoading) {
            if (uiState.isLoading) {
                CircularProgressIndicator(Modifier.size(32.dp))
            } else if (uiState.isPlaying) {
                Icon(Icons.Filled.Pause, contentDescription = stringResource(AR.string.player_pause), modifier = Modifier.size(40.dp))
            } else {
                Icon(Icons.Filled.PlayArrow, contentDescription = stringResource(AR.string.player_play), modifier = Modifier.size(40.dp))
            }
        }
        IconButton(onClick = viewModel::next, enabled = uiState.hasNext) {
            Icon(Icons.Filled.SkipNext, contentDescription = stringResource(AR.string.player_next))
        }
    }
}
