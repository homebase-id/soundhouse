package id.homebase.audio.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import id.homebase.audio.data.AudioTrack
import id.homebase.audio.importing.playableExtensions
import id.homebase.audio.resources.AR
import id.homebase.audio.resources.greeting_afternoon
import id.homebase.audio.resources.greeting_evening
import id.homebase.audio.resources.greeting_morning
import id.homebase.audio.resources.home_continue
import id.homebase.audio.resources.home_history_hint
import id.homebase.audio.resources.home_play_all
import id.homebase.audio.resources.home_recently_added
import id.homebase.audio.resources.home_recently_played
import id.homebase.audio.resources.home_shuffle
import id.homebase.audio.resources.track_duration_unknown
import id.homebase.audio.ui.common.NowPlayingBars
import id.homebase.audio.ui.common.TrackCover
import id.homebase.audio.ui.common.formatDuration
import id.homebase.audio.ui.common.playedAgo
import id.homebase.audio.ui.common.timeLeft
import id.homebase.audio.ui.library.EmptyLibrary
import io.github.vinceglb.filekit.dialogs.FileKitMode
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.compose.rememberFilePickerLauncher
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Clock

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onOpenPlayer: () -> Unit,
    onOpenRecorder: () -> Unit,
    actions: @Composable () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val importLauncher = rememberFilePickerLauncher(
        type = FileKitType.File(extensions = playableExtensions.toList()),
        mode = FileKitMode.Multiple(),
    ) { files -> if (!files.isNullOrEmpty()) viewModel.onFilesPicked(files) }
    HomeContent(
        uiState = uiState,
        onImport = { importLauncher.launch() },
        onOpenRecorder = onOpenRecorder,
        onPlayAll = { shuffle -> viewModel.playAll(shuffle); onOpenPlayer() },
        onResume = { viewModel.resume(it); onOpenPlayer() },
        onPlayRecent = { viewModel.playRecent(it); onOpenPlayer() },
        onPlayAdded = { viewModel.playAdded(it); onOpenPlayer() },
        actions = actions,
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HomeContent(
    uiState: HomeUiState,
    onImport: () -> Unit,
    onOpenRecorder: () -> Unit,
    onPlayAll: (shuffle: Boolean) -> Unit,
    onResume: (ListenedTrack) -> Unit,
    onPlayRecent: (ListenedTrack) -> Unit,
    onPlayAdded: (AudioTrack) -> Unit,
    actions: @Composable () -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(title = { Text(greeting()) }, actions = { actions() }, scrollBehavior = scrollBehavior)
        },
    ) { padding ->
        if (!uiState.isLoaded) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { ContainedLoadingIndicator() }
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = 24.dp),
        ) {
            if (uiState.totalTracks == 0) {
                item { EmptyLibrary(onImport = onImport, onRecord = onOpenRecorder) }
                return@LazyColumn
            }
            item {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    FilledTonalButton(onClick = { onPlayAll(false) }) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(AR.string.home_play_all))
                    }
                    FilledTonalButton(onClick = { onPlayAll(true) }) {
                        Icon(Icons.Filled.Shuffle, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(AR.string.home_shuffle))
                    }
                }
            }
            if (uiState.continueListening.isNotEmpty()) {
                item { SectionTitle(stringResource(AR.string.home_continue)) }
                item {
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(uiState.continueListening, key = { it.track.fileId.toString() }) { item ->
                            ContinueCard(item, onClick = { onResume(item) })
                        }
                    }
                }
            }
            if (uiState.recentlyPlayed.isEmpty()) {
                item { HistoryHint() }
            } else {
                item { SectionTitle(stringResource(AR.string.home_recently_played)) }
                items(uiState.recentlyPlayed, key = { "recent-${it.track.fileId}" }) { item ->
                    RecentRow(
                        item = item,
                        isCurrent = item.track.fileId == uiState.nowPlayingId,
                        isPlaying = uiState.isPlaying,
                        onClick = { onPlayRecent(item) },
                    )
                }
            }
            item { SectionTitle(stringResource(AR.string.home_recently_added)) }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(uiState.recentlyAdded, key = { "added-${it.fileId}" }) { track ->
                        AddedTile(track, onClick = { onPlayAdded(track) })
                    }
                }
            }
        }
    }
}

@Composable
private fun greeting(): String {
    val hour = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).hour
    return stringResource(
        when (hour) {
            in 5..11 -> AR.string.greeting_morning
            in 12..17 -> AR.string.greeting_afternoon
            else -> AR.string.greeting_evening
        }
    )
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 12.dp),
    )
}

@Composable
private fun ContinueCard(item: ListenedTrack, onClick: () -> Unit) {
    OutlinedCard(onClick = onClick, modifier = Modifier.width(168.dp)) {
        TrackCover(item.track, modifier = Modifier.fillMaxWidth().height(168.dp), cornerRadius = 0.dp)
        LinearProgressIndicator(
            progress = { item.entry.progress },
            modifier = Modifier.fillMaxWidth(),
            drawStopIndicator = {},
            gapSize = 0.dp,
        )
        Column(Modifier.padding(12.dp)) {
            Text(
                item.track.title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                timeLeft(item.entry.remainingMs),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun RecentRow(item: ListenedTrack, isCurrent: Boolean, isPlaying: Boolean, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = {
            Box(contentAlignment = Alignment.Center) {
                TrackCover(item.track, modifier = Modifier.size(48.dp))
                if (isCurrent) {
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                        modifier = Modifier.size(26.dp),
                    ) {
                        NowPlayingBars(playing = isPlaying, modifier = Modifier.padding(6.dp))
                    }
                }
            }
        },
        headlineContent = {
            Text(
                item.track.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
        },
        supportingContent = {
            Text(
                playedAgo(item.entry.lastPlayedMs),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
    )
}

@Composable
private fun AddedTile(track: AudioTrack, onClick: () -> Unit) {
    Column(Modifier.width(132.dp).clickable(onClick = onClick)) {
        TrackCover(track, modifier = Modifier.size(132.dp), cornerRadius = 16.dp)
        Spacer(Modifier.height(8.dp))
        Text(track.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(
            track.durationMs?.let(::formatDuration) ?: stringResource(AR.string.track_duration_unknown),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun HistoryHint() {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.History, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
            Spacer(Modifier.width(12.dp))
            Text(
                stringResource(AR.string.home_history_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}
