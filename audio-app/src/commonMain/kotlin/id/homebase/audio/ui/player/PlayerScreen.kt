package id.homebase.audio.ui.player

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import id.homebase.audio.resources.AR
import id.homebase.audio.resources.navigate_back
import id.homebase.audio.resources.player_details
import id.homebase.audio.resources.player_details_recorded
import id.homebase.audio.resources.player_failed
import id.homebase.audio.resources.player_next
import id.homebase.audio.resources.player_nothing_playing
import id.homebase.audio.resources.player_now_playing
import id.homebase.audio.resources.player_pause
import id.homebase.audio.resources.player_play
import id.homebase.audio.resources.player_position
import id.homebase.audio.resources.player_previous
import id.homebase.audio.ui.common.TrackArtwork
import id.homebase.audio.ui.common.artworkPalette
import id.homebase.audio.ui.common.formatDate
import id.homebase.audio.ui.common.formatDuration
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(viewModel: PlayerViewModel, onBack: () -> Unit) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val seed = uiState.artworkSeed
    val tint = if (seed != null) MaterialTheme.colorScheme.artworkPalette(seed).start else MaterialTheme.colorScheme.surface
    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .background(
                Brush.verticalGradient(
                    0f to tint.copy(alpha = if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) 0.28f else 0.32f),
                    0.65f to MaterialTheme.colorScheme.surface,
                )
            )
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onSurface,
            topBar = {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                    title = {
                        Column {
                            Text(stringResource(AR.string.player_now_playing), style = MaterialTheme.typography.titleMedium)
                            if (uiState.trackCount > 1) {
                                Text(
                                    stringResource(AR.string.player_position, uiState.trackNumber, uiState.trackCount),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
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
            if (title == null || seed == null) {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Text(stringResource(AR.string.player_nothing_playing), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                return@Scaffold
            }
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 28.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceEvenly,
            ) {
                val artScale by animateFloatAsState(
                    if (uiState.isPlaying) 1f else 0.9f,
                    animationSpec = spring(dampingRatio = 0.6f, stiffness = 300f),
                )
                TrackArtwork(
                    title = title,
                    seed = seed,
                    cornerRadius = 32.dp,
                    modifier = Modifier
                        .widthIn(max = 420.dp)
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .scale(artScale)
                        .shadow(elevation = 16.dp, shape = RoundedCornerShape(32.dp)),
                )
                TrackHeading(uiState, title)
                SeekSection(uiState, viewModel)
                Controls(uiState, viewModel)
            }
        }
    }
}

@Composable
private fun TrackHeading(uiState: PlayerUiState, title: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Text(
            title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        val date = uiState.dateAddedMs?.let(::formatDate)
        if (date != null) {
            Spacer(Modifier.height(4.dp))
            Text(
                if (uiState.recorded) stringResource(AR.string.player_details_recorded, date)
                else stringResource(AR.string.player_details, uiState.format ?: "", date),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (uiState.failed) {
            Spacer(Modifier.height(8.dp))
            Text(stringResource(AR.string.player_failed), color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun SeekSection(uiState: PlayerUiState, viewModel: PlayerViewModel) {
    var dragFraction by remember { mutableStateOf<Float?>(null) }
    val duration = uiState.durationMs.coerceAtLeast(1)
    val fraction = dragFraction ?: (uiState.positionMs.toFloat() / duration)
    Column(Modifier.fillMaxWidth()) {
        WavySeekBar(
            fraction = fraction,
            animated = uiState.isPlaying,
            enabled = uiState.durationMs > 0 && !uiState.isLoading,
            onDrag = { dragFraction = it },
            onSeek = { viewModel.seekTo((it * duration).toLong()) },
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                formatDuration((fraction * duration).toLong()),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                formatDuration(uiState.durationMs),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun Controls(uiState: PlayerUiState, viewModel: PlayerViewModel) {
    // A playing button is a rounded square, a paused one a circle: the shape itself shows the state.
    val corner by animateDpAsState(if (uiState.isPlaying) 28.dp else 48.dp, animationSpec = spring(dampingRatio = 0.55f))
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(28.dp)) {
        FilledTonalIconButton(onClick = viewModel::previous, enabled = uiState.hasPrevious, modifier = Modifier.size(60.dp)) {
            Icon(Icons.Filled.SkipPrevious, contentDescription = stringResource(AR.string.player_previous), modifier = Modifier.size(30.dp))
        }
        FilledIconButton(
            onClick = viewModel::togglePlayPause,
            enabled = !uiState.isLoading,
            shape = RoundedCornerShape(corner),
            modifier = Modifier.size(96.dp),
        ) {
            when {
                uiState.isLoading -> LoadingIndicator(Modifier.size(48.dp))
                uiState.isPlaying -> Icon(Icons.Filled.Pause, contentDescription = stringResource(AR.string.player_pause), modifier = Modifier.size(44.dp))
                else -> Icon(Icons.Filled.PlayArrow, contentDescription = stringResource(AR.string.player_play), modifier = Modifier.size(44.dp))
            }
        }
        FilledTonalIconButton(onClick = viewModel::next, enabled = uiState.hasNext, modifier = Modifier.size(60.dp)) {
            Icon(Icons.Filled.SkipNext, contentDescription = stringResource(AR.string.player_next), modifier = Modifier.size(30.dp))
        }
    }
}
