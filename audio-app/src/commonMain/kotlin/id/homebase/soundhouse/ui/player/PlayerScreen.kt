package id.homebase.soundhouse.ui.player

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
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Forward30
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.TextButton
import id.homebase.soundhouse.resources.player_back_10
import id.homebase.soundhouse.resources.player_forward_30
import id.homebase.soundhouse.resources.player_selected
import id.homebase.soundhouse.resources.player_sleep
import id.homebase.soundhouse.resources.player_sleep_end_of_track
import id.homebase.soundhouse.resources.player_sleep_minutes
import id.homebase.soundhouse.resources.player_sleep_off
import id.homebase.soundhouse.resources.player_speed_value
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
import id.homebase.soundhouse.resources.AR
import id.homebase.soundhouse.resources.navigate_back
import id.homebase.soundhouse.resources.track_added_on
import id.homebase.soundhouse.resources.track_recorded_on
import id.homebase.soundhouse.resources.player_failed
import id.homebase.soundhouse.resources.player_buffering
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import id.homebase.soundhouse.resources.player_next
import id.homebase.soundhouse.resources.player_nothing_playing
import id.homebase.soundhouse.resources.player_now_playing
import id.homebase.soundhouse.resources.player_pause
import id.homebase.soundhouse.resources.player_play
import id.homebase.soundhouse.resources.player_position
import id.homebase.soundhouse.resources.player_previous
import id.homebase.soundhouse.importing.AudioQuality
import id.homebase.soundhouse.importing.tier
import id.homebase.soundhouse.ui.common.QualityBadge
import id.homebase.soundhouse.ui.common.TrackCover
import id.homebase.soundhouse.ui.common.qualitySummary
import id.homebase.soundhouse.ui.common.artworkPalette
import id.homebase.soundhouse.ui.common.formatDate
import id.homebase.soundhouse.ui.common.formatDuration
import id.homebase.soundhouse.ui.theme.tabular
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import id.homebase.soundhouse.settings.Skin
import id.homebase.soundhouse.ui.theme.AluminiumTheme
import id.homebase.soundhouse.ui.theme.LocalAluminium
import id.homebase.soundhouse.ui.theme.bevel
import id.homebase.soundhouse.ui.theme.brushedMetal
import id.homebase.soundhouse.ui.theme.recessedPanel
import id.homebase.soundhouse.ui.theme.spunKnob
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(viewModel: PlayerViewModel, onBack: () -> Unit) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    if (uiState.skin == Skin.Aluminium) {
        AluminiumTheme { AluminiumPlayer(uiState, viewModel, onBack) }
        return
    }
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
            topBar = { PlayerTopBar(uiState, onBack) },
        ) { padding ->
            val title = uiState.title
            val track = uiState.track
            if (title == null || seed == null || track == null) {
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
                TrackCover(
                    track = track,
                    minPixels = 640,
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
                ListeningControls(uiState, viewModel)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlayerTopBar(uiState: PlayerUiState, onBack: () -> Unit) {
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
}

/** Brushed metal, a recessed display for the track, spun knobs for transport. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AluminiumPlayer(uiState: PlayerUiState, viewModel: PlayerViewModel, onBack: () -> Unit) {
    val metal = LocalAluminium.current
    Box(Modifier.fillMaxSize().brushedMetal(metal)) {
        Scaffold(
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onSurface,
            topBar = { PlayerTopBar(uiState, onBack) },
        ) { padding ->
            val title = uiState.title
            val track = uiState.track
            if (title == null || track == null) {
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
                val coverShape = RoundedCornerShape(16.dp)
                TrackCover(
                    track = track,
                    minPixels = 640,
                    cornerRadius = 16.dp,
                    modifier = Modifier
                        .widthIn(max = 360.dp)
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .shadow(elevation = 12.dp, shape = coverShape)
                        .bevel(coverShape, metal),
                )
                DisplayPanel(uiState, title)
                SeekSection(uiState, viewModel, machined = true)
                KnobControls(uiState, viewModel)
                ListeningControls(uiState, viewModel)
            }
        }
    }
}

@Composable
private fun DisplayPanel(uiState: PlayerUiState, title: String) {
    val metal = LocalAluminium.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .recessedPanel(RoundedCornerShape(12.dp), metal)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            color = metal.onPanel,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        uiState.credits?.let { credits ->
            Text(
                credits,
                style = MaterialTheme.typography.titleSmall,
                color = metal.onPanel,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        val date = uiState.dateAddedMs?.let(::formatDate)
        if (date != null) {
            Text(
                if (uiState.recorded) stringResource(AR.string.track_recorded_on, date)
                else stringResource(AR.string.track_added_on, date),
                style = MaterialTheme.typography.bodySmall,
                color = metal.onPanelDim,
            )
        }
        uiState.quality?.let { quality ->
            Spacer(Modifier.height(6.dp))
            Text(
                qualitySummary(quality),
                style = MaterialTheme.typography.labelLarge.tabular(),
                color = MaterialTheme.colorScheme.tertiary,
            )
        }
        if (uiState.failed) {
            Spacer(Modifier.height(6.dp))
            Text(stringResource(AR.string.player_failed), color = MaterialTheme.colorScheme.error)
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun KnobControls(uiState: PlayerUiState, viewModel: PlayerViewModel) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(28.dp)) {
        Knob(onClick = viewModel::previous, enabled = uiState.hasPrevious, size = 60.dp) {
            Icon(Icons.Filled.SkipPrevious, contentDescription = stringResource(AR.string.player_previous), modifier = Modifier.size(28.dp))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            PlayingLed(on = uiState.isPlaying)
            Spacer(Modifier.height(8.dp))
            Knob(onClick = viewModel::togglePlayPause, enabled = !uiState.isLoading, size = 96.dp) {
                when {
                    uiState.isLoading || uiState.isBuffering -> LoadingIndicator(Modifier.size(48.dp))
                    uiState.isPlaying -> Icon(Icons.Filled.Pause, contentDescription = stringResource(AR.string.player_pause), modifier = Modifier.size(40.dp))
                    else -> Icon(Icons.Filled.PlayArrow, contentDescription = stringResource(AR.string.player_play), modifier = Modifier.size(40.dp))
                }
            }
            // Balances the LED so the play knob stays centred on the row.
            Spacer(Modifier.height(14.dp))
        }
        Knob(onClick = viewModel::next, enabled = uiState.hasNext, size = 60.dp) {
            Icon(Icons.Filled.SkipNext, contentDescription = stringResource(AR.string.player_next), modifier = Modifier.size(28.dp))
        }
    }
}

@Composable
private fun Knob(onClick: () -> Unit, enabled: Boolean, size: Dp, content: @Composable () -> Unit) {
    val metal = LocalAluminium.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(size)
            .shadow(if (pressed) 2.dp else 8.dp, CircleShape)
            .clip(CircleShape)
            .spunKnob(metal, pressed)
            .bevel(CircleShape, metal)
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick),
    ) {
        CompositionLocalProvider(LocalContentColor provides if (enabled) metal.engraving else metal.engravingDim.copy(alpha = 0.5f)) {
            content()
        }
    }
}

/** The one lamp on the deck: amber while sound is playing. */
@Composable
private fun PlayingLed(on: Boolean) {
    val metal = LocalAluminium.current
    val lamp = MaterialTheme.colorScheme.tertiary
    val glow by animateFloatAsState(if (on) 1f else 0f)
    Box(
        Modifier
            .size(6.dp)
            .drawBehind {
                drawCircle(lamp.copy(alpha = 0.35f * glow), radius = size.minDimension * 1.6f)
                drawCircle(if (glow > 0.5f) lamp else metal.shadow, radius = size.minDimension / 2)
            },
    )
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
        uiState.credits?.let { credits ->
            Spacer(Modifier.height(4.dp))
            Text(
                credits,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        val date = uiState.dateAddedMs?.let(::formatDate)
        if (date != null) {
            Spacer(Modifier.height(4.dp))
            Text(
                if (uiState.recorded) stringResource(AR.string.track_recorded_on, date)
                else stringResource(AR.string.track_added_on, date),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        uiState.quality?.let { quality ->
            Spacer(Modifier.height(8.dp))
            QualityLine(quality)
        }
        if (uiState.failed) {
            Spacer(Modifier.height(8.dp))
            Text(stringResource(AR.string.player_failed), color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun QualityLine(quality: AudioQuality) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        quality.tier?.let { QualityBadge(it) }
        Text(
            qualitySummary(quality),
            style = MaterialTheme.typography.labelLarge.tabular(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SeekSection(uiState: PlayerUiState, viewModel: PlayerViewModel, machined: Boolean = false) {
    var dragFraction by remember { mutableStateOf<Float?>(null) }
    val duration = uiState.durationMs.coerceAtLeast(1)
    val fraction = dragFraction ?: (uiState.positionMs.toFloat() / duration)
    Column(Modifier.fillMaxWidth()) {
        SeekBar(
            fraction = fraction,
            enabled = uiState.durationMs > 0 && !uiState.isLoading,
            onDrag = { dragFraction = it },
            onSeek = { viewModel.seekTo((it * duration).toLong()) },
            machined = machined,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                formatDuration((fraction * duration).toLong()),
                style = MaterialTheme.typography.labelMedium.tabular(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (uiState.isBuffering) {
                Text(
                    stringResource(AR.string.player_buffering),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
            Text(
                formatDuration(uiState.durationMs),
                style = MaterialTheme.typography.labelMedium.tabular(),
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
                uiState.isLoading || uiState.isBuffering -> LoadingIndicator(Modifier.size(48.dp))
                uiState.isPlaying -> Icon(Icons.Filled.Pause, contentDescription = stringResource(AR.string.player_pause), modifier = Modifier.size(44.dp))
                else -> Icon(Icons.Filled.PlayArrow, contentDescription = stringResource(AR.string.player_play), modifier = Modifier.size(44.dp))
            }
        }
        FilledTonalIconButton(onClick = viewModel::next, enabled = uiState.hasNext, modifier = Modifier.size(60.dp)) {
            Icon(Icons.Filled.SkipNext, contentDescription = stringResource(AR.string.player_next), modifier = Modifier.size(30.dp))
        }
    }
}

@Composable
private fun ListeningControls(uiState: PlayerUiState, viewModel: PlayerViewModel) {
    val enabled = !uiState.isLoading && uiState.durationMs > 0
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        IconButton(onClick = viewModel::skipBack, enabled = enabled) {
            Icon(Icons.Filled.Replay10, contentDescription = stringResource(AR.string.player_back_10))
        }
        SpeedMenu(uiState.speed, viewModel::setSpeed)
        SleepMenu(uiState, viewModel)
        IconButton(onClick = viewModel::skipForward, enabled = enabled) {
            Icon(Icons.Filled.Forward30, contentDescription = stringResource(AR.string.player_forward_30))
        }
    }
}

@Composable
private fun SpeedMenu(speed: Float, onSpeed: (Float) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { open = true }, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)) {
            Icon(Icons.Filled.Speed, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                stringResource(AR.string.player_speed_value, formatSpeed(speed)),
                style = MaterialTheme.typography.labelLarge.tabular(),
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            PlayerViewModel.SPEEDS.forEach { option ->
                DropdownMenuItem(
                    text = { Text(stringResource(AR.string.player_speed_value, formatSpeed(option))) },
                    onClick = { onSpeed(option); open = false },
                    trailingIcon = if (option == speed) {
                        { Icon(Icons.Filled.Check, contentDescription = stringResource(AR.string.player_selected)) }
                    } else null,
                )
            }
        }
    }
}

@Composable
private fun SleepMenu(uiState: PlayerUiState, viewModel: PlayerViewModel) {
    var open by remember { mutableStateOf(false) }
    val remaining = uiState.sleepRemainingMs
    val active = remaining != null || uiState.sleepAtEndOfTrack
    Box {
        TextButton(onClick = { open = true }, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)) {
            Icon(
                Icons.Filled.Bedtime,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = if (active) MaterialTheme.colorScheme.tertiary else LocalContentColor.current,
            )
            Spacer(Modifier.width(6.dp))
            Text(
                when {
                    remaining != null -> formatDuration(remaining)
                    uiState.sleepAtEndOfTrack -> stringResource(AR.string.player_sleep_end_of_track)
                    else -> stringResource(AR.string.player_sleep)
                },
                style = MaterialTheme.typography.labelLarge.tabular(),
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            PlayerViewModel.SLEEP_MINUTES.forEach { minutes ->
                DropdownMenuItem(
                    text = { Text(stringResource(AR.string.player_sleep_minutes, minutes)) },
                    onClick = { viewModel.sleepAfterMinutes(minutes); open = false },
                )
            }
            DropdownMenuItem(
                text = { Text(stringResource(AR.string.player_sleep_end_of_track)) },
                onClick = { viewModel.sleepAtEndOfTrack(); open = false },
            )
            if (active) {
                DropdownMenuItem(
                    text = { Text(stringResource(AR.string.player_sleep_off)) },
                    onClick = { viewModel.sleepAfterMinutes(null); open = false },
                )
            }
        }
    }
}

/** 1 → "1", 1.25 → "1.25", 0.5 → "0.5". */
internal fun formatSpeed(speed: Float): String {
    val hundredths = kotlin.math.round(speed * 100).toInt()
    val whole = hundredths / 100
    val fraction = (hundredths % 100).toString().padStart(2, '0').trimEnd('0')
    return if (fraction.isEmpty()) "$whole" else "$whole.$fraction"
}
