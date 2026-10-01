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
import androidx.compose.material3.Button
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
import id.homebase.audio.resources.home_continue
import id.homebase.audio.resources.home_newest
import id.homebase.audio.resources.home_play
import id.homebase.audio.resources.home_resume
import id.homebase.audio.resources.home_wordmark
import id.homebase.audio.ui.theme.tabular
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
import id.homebase.audio.ui.library.ImportPanel
import id.homebase.audio.ui.collections.CollectionTile
import id.homebase.audio.resources.collections_title
import kotlin.uuid.Uuid
import io.github.vinceglb.filekit.dialogs.FileKitMode
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.compose.rememberFilePickerLauncher
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onOpenPlayer: () -> Unit,
    onOpenRecorder: () -> Unit,
    onOpenCollection: (Uuid) -> Unit,
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
        onDismissImport = viewModel::dismissImport,
        onRetryImport = viewModel::retryImport,
        onClearFinishedImports = viewModel::clearFinishedImports,
        actions = actions,
        onOpenCollection = onOpenCollection,
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
    onDismissImport: (Uuid) -> Unit,
    onRetryImport: (Uuid) -> Unit,
    onClearFinishedImports: () -> Unit,
    actions: @Composable () -> Unit,
    onOpenCollection: (Uuid) -> Unit = {},
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(title = { Text(stringResource(AR.string.home_wordmark)) }, actions = { actions() }, scrollBehavior = scrollBehavior)
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
            if (uiState.imports.isNotEmpty()) {
                item { ImportPanel(jobs = uiState.imports, onDismiss = onDismissImport, onRetry = onRetryImport, onClearFinished = onClearFinishedImports) }
            }
            if (uiState.totalTracks == 0) {
                if (uiState.imports.isEmpty()) item { EmptyLibrary(onImport = onImport, onRecord = onOpenRecorder) }
                return@LazyColumn
            }
            val resume = uiState.continueListening.firstOrNull()
            val newest = uiState.recentlyAdded.firstOrNull()
            item {
                when {
                    resume != null -> Hero(
                        track = resume.track,
                        detail = timeLeft(resume.entry.remainingMs),
                        progress = resume.entry.progress,
                        actionLabel = stringResource(AR.string.home_resume),
                        onPlay = { onResume(resume) },
                    )
                    newest != null -> Hero(
                        track = newest,
                        detail = stringResource(AR.string.home_newest),
                        progress = null,
                        actionLabel = stringResource(AR.string.home_play),
                        onPlay = { onPlayAdded(newest) },
                    )
                }
            }
            item {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
            val moreInProgress = uiState.continueListening.drop(1)
            if (moreInProgress.isNotEmpty()) {
                item { SectionTitle(stringResource(AR.string.home_continue)) }
                items(moreInProgress, key = { "continue-${it.track.fileId}" }) { item ->
                    ContinueRow(item, onClick = { onResume(item) })
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
            if (uiState.collections.isNotEmpty()) {
                item { SectionTitle(stringResource(AR.string.collections_title)) }
                item {
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(uiState.collections, key = { "collection-${it.collection.id}" }) { summary ->
                            CollectionTile(summary, onClick = { onOpenCollection(summary.collection.id) })
                        }
                    }
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
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 12.dp),
    )
}

@Composable
private fun Hero(track: AudioTrack, detail: String, progress: Float?, actionLabel: String, onPlay: () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        onClick = onPlay,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TrackCover(track, modifier = Modifier.size(112.dp), cornerRadius = 20.dp)
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        track.title,
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (progress != null) {
                    LinearProgressIndicator(
                        progress = { progress },
                        color = MaterialTheme.colorScheme.tertiary,
                        trackColor = MaterialTheme.colorScheme.tertiaryContainer,
                        drawStopIndicator = {},
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    Spacer(Modifier.weight(1f))
                }
                Spacer(Modifier.width(16.dp))
                Button(onClick = onPlay) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(actionLabel)
                }
            }
        }
    }
}

@Composable
private fun ContinueRow(item: ListenedTrack, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = { TrackCover(item.track, modifier = Modifier.size(48.dp)) },
        headlineContent = { Text(item.track.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            Column {
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { item.entry.progress },
                    color = MaterialTheme.colorScheme.tertiary,
                    trackColor = MaterialTheme.colorScheme.tertiaryContainer,
                    drawStopIndicator = {},
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        trailingContent = {
            Text(
                timeLeft(item.entry.remainingMs),
                style = MaterialTheme.typography.labelMedium.tabular(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
    )
}

@Composable
private fun RecentRow(item: ListenedTrack, isCurrent: Boolean, isPlaying: Boolean, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = { TrackCover(item.track, modifier = Modifier.size(48.dp)) },
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
        trailingContent = if (isCurrent) {
            {
                NowPlayingBars(
                    playing = isPlaying,
                    color = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(width = 18.dp, height = 16.dp),
                )
            }
        } else {
            null
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
            style = MaterialTheme.typography.bodySmall.tabular(),
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
