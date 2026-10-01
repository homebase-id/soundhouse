package id.homebase.audio.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.TextButton
import id.homebase.audio.importing.ImportJob
import id.homebase.audio.importing.ImportStatus
import id.homebase.audio.importing.playableExtensions
import id.homebase.audio.resources.import_action
import id.homebase.audio.resources.import_clear_finished
import id.homebase.audio.resources.import_dismiss
import id.homebase.audio.resources.import_done
import id.homebase.audio.resources.import_failed
import id.homebase.audio.resources.import_queued
import io.github.vinceglb.filekit.dialogs.FileKitMode
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.compose.rememberFilePickerLauncher
import kotlin.uuid.Uuid
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.LaunchedEffect
import org.jetbrains.compose.resources.getString
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import id.homebase.audio.data.AudioTrack
import id.homebase.audio.resources.AR
import id.homebase.audio.resources.app_name
import id.homebase.audio.resources.library_empty
import id.homebase.audio.resources.library_no_matches
import id.homebase.audio.resources.library_search_clear
import id.homebase.audio.resources.library_search_hint
import id.homebase.audio.resources.library_sort
import id.homebase.audio.resources.library_sort_newest
import id.homebase.audio.resources.library_sort_title_ascending
import id.homebase.audio.resources.library_sort_title_descending
import id.homebase.audio.resources.record_open
import id.homebase.audio.resources.sign_out
import id.homebase.audio.resources.download_action
import id.homebase.audio.resources.download_done
import id.homebase.audio.resources.download_failed
import id.homebase.audio.resources.download_remove
import id.homebase.audio.resources.track_actions
import id.homebase.audio.resources.cancel
import id.homebase.audio.resources.delete_action
import id.homebase.audio.resources.delete_confirm
import id.homebase.audio.resources.delete_failed
import id.homebase.audio.resources.delete_message
import id.homebase.audio.resources.delete_title
import id.homebase.audio.resources.record_name_label
import id.homebase.audio.resources.rename_action
import id.homebase.audio.resources.rename_confirm
import id.homebase.audio.resources.rename_failed
import id.homebase.audio.resources.rename_title
import id.homebase.audio.resources.track_duration_unknown
import id.homebase.audio.resources.track_subtitle
import id.homebase.audio.ui.common.formatDate
import id.homebase.audio.ui.common.formatDuration
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(viewModel: LibraryViewModel, onOpenPlayer: () -> Unit, onOpenRecorder: () -> Unit) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            snackbar.showSnackbar(
                getString(
                    when (event) {
                        LibraryEvent.RenameFailed -> AR.string.rename_failed
                        LibraryEvent.DeleteFailed -> AR.string.delete_failed
                    }
                )
            )
        }
    }
    var renaming by remember { mutableStateOf<AudioTrack?>(null) }
    var deleting by remember { mutableStateOf<AudioTrack?>(null) }
    renaming?.let { track ->
        RenameDialog(
            track = track,
            onConfirm = { title ->
                viewModel.rename(track, title)
                renaming = null
            },
            onDismiss = { renaming = null },
        )
    }
    deleting?.let { track ->
        DeleteDialog(
            track = track,
            onConfirm = {
                viewModel.delete(track)
                deleting = null
            },
            onDismiss = { deleting = null },
        )
    }
    val importLauncher = rememberFilePickerLauncher(
        type = FileKitType.File(extensions = playableExtensions.toList()),
        mode = FileKitMode.Multiple(),
    ) { files ->
        if (!files.isNullOrEmpty()) viewModel.onFilesPicked(files)
    }
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { importLauncher.launch() },
                icon = { Icon(Icons.Filled.FileUpload, contentDescription = null) },
                text = { Text(stringResource(AR.string.import_action)) },
            )
        },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(AR.string.app_name)) },
                actions = {
                    IconButton(onClick = onOpenRecorder) {
                        Icon(Icons.Filled.Mic, contentDescription = stringResource(AR.string.record_open))
                    }
                    SortMenu(selected = uiState.sort, onSelect = viewModel::onSortChange)
                    IconButton(onClick = viewModel::signOut) {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = stringResource(AR.string.sign_out))
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            SearchField(
                query = uiState.query,
                onQueryChange = viewModel::onQueryChange,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
            if (uiState.imports.isNotEmpty()) {
                ImportPanel(
                    jobs = uiState.imports,
                    onDismiss = viewModel::dismissImport,
                    onClearFinished = viewModel::clearFinishedImports,
                )
            }
            when {
                !uiState.isLoaded -> CenteredContent { CircularProgressIndicator() }
                uiState.totalTracks == 0 -> CenteredMessage(stringResource(AR.string.library_empty))
                uiState.tracks.isEmpty() -> CenteredMessage(stringResource(AR.string.library_no_matches))
                else -> TrackList(
                    uiState = uiState,
                    onTrackClick = { track ->
                        viewModel.play(track)
                        onOpenPlayer()
                    },
                    actions = TrackActions(
                        download = viewModel::download,
                        removeDownload = viewModel::removeDownload,
                        rename = { renaming = it },
                        delete = { deleting = it },
                    ),
                )
            }
        }
    }
}

class TrackActions(
    val download: (AudioTrack) -> Unit,
    val removeDownload: (AudioTrack) -> Unit,
    val rename: (AudioTrack) -> Unit,
    val delete: (AudioTrack) -> Unit,
)

@Composable
private fun TrackList(uiState: LibraryUiState, onTrackClick: (AudioTrack) -> Unit, actions: TrackActions) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 88.dp)) {
        items(uiState.tracks, key = { it.fileId.toString() }) { track ->
            TrackRow(
                track = track,
                downloaded = track.fileId in uiState.downloaded,
                downloadProgress = uiState.downloadProgress[track.fileId],
                downloadFailed = track.fileId in uiState.downloadFailures,
                onClick = { onTrackClick(track) },
                actions = actions,
            )
            HorizontalDivider()
        }
    }
}

@Composable
private fun TrackRow(
    track: AudioTrack,
    downloaded: Boolean,
    downloadProgress: Float?,
    downloadFailed: Boolean,
    onClick: () -> Unit,
    actions: TrackActions,
) {
    val duration = track.durationMs?.let(::formatDuration) ?: stringResource(AR.string.track_duration_unknown)
    ListItem(
        headlineContent = { Text(track.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            Text(stringResource(AR.string.track_subtitle, duration, formatDate(track.dateAddedMs)))
        },
        leadingContent = { Icon(Icons.Filled.MusicNote, contentDescription = null) },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                when {
                    downloadProgress != null -> CircularProgressIndicator(
                        progress = { downloadProgress },
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                    )
                    downloaded -> Icon(
                        Icons.Filled.DownloadDone,
                        contentDescription = stringResource(AR.string.download_done),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    downloadFailed -> Icon(
                        Icons.Filled.ErrorOutline,
                        contentDescription = stringResource(AR.string.download_failed),
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
                TrackMenu(track, downloaded, downloading = downloadProgress != null, actions)
            }
        },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@Composable
private fun TrackMenu(track: AudioTrack, downloaded: Boolean, downloading: Boolean, actions: TrackActions) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Filled.MoreVert, contentDescription = stringResource(AR.string.track_actions, track.title))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            if (downloaded) {
                DropdownMenuItem(
                    text = { Text(stringResource(AR.string.download_remove)) },
                    leadingIcon = { Icon(Icons.Filled.DeleteSweep, contentDescription = null) },
                    onClick = {
                        expanded = false
                        actions.removeDownload(track)
                    },
                )
            } else if (!downloading) {
                DropdownMenuItem(
                    text = { Text(stringResource(AR.string.download_action)) },
                    leadingIcon = { Icon(Icons.Filled.Download, contentDescription = null) },
                    onClick = {
                        expanded = false
                        actions.download(track)
                    },
                )
            }
            DropdownMenuItem(
                text = { Text(stringResource(AR.string.rename_action)) },
                leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                onClick = {
                    expanded = false
                    actions.rename(track)
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(AR.string.delete_action)) },
                leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                onClick = {
                    expanded = false
                    actions.delete(track)
                },
            )
        }
    }
}

@Composable
private fun RenameDialog(track: AudioTrack, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var title by remember(track.fileId) { mutableStateOf(track.title) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(AR.string.rename_title)) },
        text = {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                singleLine = true,
                label = { Text(stringResource(AR.string.record_name_label)) },
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(title) }, enabled = title.isNotBlank()) {
                Text(stringResource(AR.string.rename_confirm))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(AR.string.cancel)) } },
    )
}

@Composable
private fun DeleteDialog(track: AudioTrack, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.Delete, contentDescription = null) },
        title = { Text(stringResource(AR.string.delete_title)) },
        text = { Text(stringResource(AR.string.delete_message, track.title)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(AR.string.delete_confirm), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(AR.string.cancel)) } },
    )
}

@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit, modifier: Modifier) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier,
        singleLine = true,
        placeholder = { Text(stringResource(AR.string.library_search_hint)) },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Filled.Clear, contentDescription = stringResource(AR.string.library_search_clear))
                }
            }
        },
    )
}

@Composable
private fun SortMenu(selected: LibrarySort, onSelect: (LibrarySort) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = stringResource(AR.string.library_sort))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            LibrarySort.entries.forEach { sort ->
                DropdownMenuItem(
                    text = { Text(stringResource(sort.label())) },
                    onClick = {
                        onSelect(sort)
                        expanded = false
                    },
                    trailingIcon = {
                        if (sort == selected) Icon(Icons.Filled.Check, contentDescription = null)
                    },
                )
            }
        }
    }
}

@Composable
private fun ImportPanel(jobs: List<ImportJob>, onDismiss: (Uuid) -> Unit, onClearFinished: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        jobs.forEach { job ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(job.fileName, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    when (job.status) {
                        ImportStatus.Queued -> Text(
                            stringResource(AR.string.import_queued),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        ImportStatus.Uploading -> LinearProgressIndicator(
                            progress = { job.progress },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        )
                        ImportStatus.Done -> Text(
                            stringResource(AR.string.import_done),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        ImportStatus.Failed -> Text(
                            stringResource(AR.string.import_failed),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                if (job.status == ImportStatus.Failed || job.status == ImportStatus.Done) {
                    IconButton(onClick = { onDismiss(job.id) }) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(AR.string.import_dismiss))
                    }
                }
            }
        }
        if (jobs.count { it.status == ImportStatus.Done } > 1) {
            TextButton(onClick = onClearFinished, modifier = Modifier.align(Alignment.End)) {
                Text(stringResource(AR.string.import_clear_finished))
            }
        }
        HorizontalDivider(Modifier.padding(top = 4.dp))
    }
}

private fun LibrarySort.label(): StringResource = when (this) {
    LibrarySort.Newest -> AR.string.library_sort_newest
    LibrarySort.TitleAscending -> AR.string.library_sort_title_ascending
    LibrarySort.TitleDescending -> AR.string.library_sort_title_descending
}

@Composable
private fun CenteredContent(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
}

@Composable
private fun CenteredMessage(message: String) {
    CenteredContent {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(24.dp),
        )
    }
}
