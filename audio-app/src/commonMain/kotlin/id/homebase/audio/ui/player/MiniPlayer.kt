package id.homebase.audio.ui.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import id.homebase.audio.resources.AR
import id.homebase.audio.resources.player_next
import id.homebase.audio.resources.player_open
import id.homebase.audio.resources.player_pause
import id.homebase.audio.resources.player_play
import id.homebase.audio.ui.common.TrackArtwork
import org.jetbrains.compose.resources.stringResource

/** Compact now-playing bar; tapping it opens the full player. Hidden when nothing is queued. */
@Composable
fun MiniPlayer(viewModel: PlayerViewModel, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val title = uiState.title ?: return
    val seed = uiState.artworkSeed ?: return
    val openLabel = stringResource(AR.string.player_open)
    Surface(
        modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 6.dp,
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClickLabel = openLabel, onClick = onOpen)
                    .padding(start = 8.dp, end = 4.dp, top = 8.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TrackArtwork(title, seed = seed, modifier = Modifier.size(44.dp), cornerRadius = 12.dp)
                Text(
                    title,
                    modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                IconButton(onClick = viewModel::togglePlayPause, enabled = !uiState.isLoading) {
                    when {
                        uiState.isLoading -> CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        uiState.isPlaying -> Icon(Icons.Filled.Pause, contentDescription = stringResource(AR.string.player_pause))
                        else -> Icon(Icons.Filled.PlayArrow, contentDescription = stringResource(AR.string.player_play))
                    }
                }
                IconButton(onClick = viewModel::next, enabled = uiState.hasNext) {
                    Icon(Icons.Filled.SkipNext, contentDescription = stringResource(AR.string.player_next))
                }
            }
            LinearProgressIndicator(
                progress = { if (uiState.durationMs > 0) (uiState.positionMs.toFloat() / uiState.durationMs).coerceIn(0f, 1f) else 0f },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 8.dp),
                drawStopIndicator = {},
            )
        }
    }
}
