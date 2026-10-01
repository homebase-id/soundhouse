package id.homebase.audio.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.PlaylistRemove
import id.homebase.audio.resources.collection_remove_track
import id.homebase.audio.resources.collections_action
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import id.homebase.audio.data.AudioTrack
import id.homebase.audio.data.TrackOrigin
import id.homebase.audio.ui.theme.tabular
import id.homebase.audio.importing.ImportJob
import id.homebase.audio.importing.ImportFailure
import id.homebase.audio.importing.ImportStatus
import id.homebase.audio.resources.AR
import id.homebase.audio.resources.cancel
import id.homebase.audio.resources.chip_downloaded
import id.homebase.audio.resources.delete_action
import id.homebase.audio.resources.delete_confirm
import id.homebase.audio.resources.delete_message
import id.homebase.audio.resources.delete_title
import id.homebase.audio.resources.download_action
import id.homebase.audio.resources.download_done
import id.homebase.audio.resources.download_failed
import id.homebase.audio.resources.download_remove
import id.homebase.audio.resources.import_clear_finished
import id.homebase.audio.resources.import_dismiss
import id.homebase.audio.resources.import_done
import id.homebase.audio.resources.import_failed
import id.homebase.audio.resources.import_retry
import id.homebase.audio.resources.import_retrying
import id.homebase.audio.resources.import_failed_connection
import id.homebase.audio.resources.import_failed_not_allowed
import id.homebase.audio.resources.import_failed_server
import id.homebase.audio.resources.import_failed_too_large
import id.homebase.audio.resources.import_failed_unreadable
import id.homebase.audio.resources.import_files
import id.homebase.audio.resources.import_queued
import id.homebase.audio.resources.library_downloaded_empty
import id.homebase.audio.resources.library_empty_body
import id.homebase.audio.resources.library_empty_title
import id.homebase.audio.resources.library_no_matches
import id.homebase.audio.resources.library_search_clear
import id.homebase.audio.resources.library_search_hint
import id.homebase.audio.resources.library_sort_newest
import id.homebase.audio.resources.library_sort_title_ascending
import id.homebase.audio.resources.library_sort_title_descending
import id.homebase.audio.resources.record_action
import id.homebase.audio.resources.record_name_label
import id.homebase.audio.resources.rename_action
import id.homebase.audio.resources.rename_confirm
import id.homebase.audio.resources.rename_title
import id.homebase.audio.resources.track_actions
import id.homebase.audio.resources.track_duration_unknown
import id.homebase.audio.resources.track_added_on
import id.homebase.audio.resources.track_recorded_on
import id.homebase.audio.ui.common.NowPlayingBars
import id.homebase.audio.ui.common.TrackCover
import id.homebase.audio.ui.common.formatDate
import id.homebase.audio.ui.common.formatDuration
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.uuid.Uuid

class TrackActions(
    val download: (AudioTrack) -> Unit,
    val removeDownload: (AudioTrack) -> Unit,
    val rename: (AudioTrack) -> Unit,
    val delete: (AudioTrack) -> Unit,
    val collections: (AudioTrack) -> Unit,
    val removeFromCollection: ((AudioTrack) -> Unit)? = null,
)

@Composable
internal fun SearchPill(query: String, onQueryChange: (String) -> Unit, modifier: Modifier = Modifier) {
    TextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier.clip(CircleShape),
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
        shape = CircleShape,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
        ),
    )
}

@Composable
internal fun FilterChips(
    sort: LibrarySort,
    downloadedOnly: Boolean,
    onSortChange: (LibrarySort) -> Unit,
    onDownloadedOnlyChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        LibrarySort.entries.forEach { option ->
            FilterChip(
                selected = option == sort,
                onClick = { onSortChange(option) },
                label = { Text(stringResource(option.label())) },
                leadingIcon = if (option == sort) {
                    { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
                } else {
                    null
                },
            )
        }
        FilterChip(
            selected = downloadedOnly,
            onClick = { onDownloadedOnlyChange(!downloadedOnly) },
            label = { Text(stringResource(AR.string.chip_downloaded)) },
            leadingIcon = {
                Icon(Icons.Filled.DownloadDone, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize))
            },
        )
    }
}

private fun LibrarySort.label(): StringResource = when (this) {
    LibrarySort.Newest -> AR.string.library_sort_newest
    LibrarySort.TitleAscending -> AR.string.library_sort_title_ascending
    LibrarySort.TitleDescending -> AR.string.library_sort_title_descending
}

@Composable
internal fun TrackRow(
    track: AudioTrack,
    isCurrent: Boolean,
    isPlaying: Boolean,
    downloaded: Boolean,
    downloadProgress: Float?,
    downloadFailed: Boolean,
    onClick: () -> Unit,
    actions: TrackActions,
) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = { TrackCover(track, modifier = Modifier.size(52.dp)) },
        headlineContent = {
            Text(
                track.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleMedium,
                color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
        },
        supportingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (downloaded) {
                    Icon(
                        Icons.Filled.DownloadDone,
                        contentDescription = stringResource(AR.string.download_done),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(Modifier.size(4.dp))
                }
                if (downloadFailed) {
                    Icon(
                        Icons.Filled.ErrorOutline,
                        contentDescription = stringResource(AR.string.download_failed),
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(Modifier.size(4.dp))
                }
                Text(
                    trackOriginLine(track),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                when {
                    downloadProgress != null -> CircularProgressIndicator(
                        progress = { downloadProgress },
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                    )
                    isCurrent -> NowPlayingBars(
                        playing = isPlaying,
                        color = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(width = 18.dp, height = 16.dp),
                    )
                    else -> Text(
                        track.durationMs?.let(::formatDuration) ?: stringResource(AR.string.track_duration_unknown),
                        style = MaterialTheme.typography.labelLarge.tabular(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TrackMenu(track, downloaded, downloading = downloadProgress != null, actions)
            }
        },
    )
}

/** "Added Sep 20, 2026" or "Recorded Sep 20, 2026". */
@Composable
internal fun trackOriginLine(track: AudioTrack): String {
    val date = formatDate(track.dateAddedMs)
    return if (track.content.origin == TrackOrigin.Recorded) stringResource(AR.string.track_recorded_on, date)
    else stringResource(AR.string.track_added_on, date)
}

@Composable
internal fun EmptyLibrary(onImport: () -> Unit, onRecord: () -> Unit, modifier: Modifier = Modifier) {
    EmptyState(
        icon = Icons.Filled.LibraryMusic,
        title = stringResource(AR.string.library_empty_title),
        body = stringResource(AR.string.library_empty_body),
        modifier = modifier,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = onImport) {
                Icon(Icons.Filled.FileUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text(stringResource(AR.string.import_files))
            }
            OutlinedButton(onClick = onRecord) {
                Icon(Icons.Filled.Mic, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text(stringResource(AR.string.record_action))
            }
        }
    }
}

@Composable
internal fun NoResults(downloadedOnly: Boolean, modifier: Modifier = Modifier) {
    EmptyState(
        icon = if (downloadedOnly) Icons.Filled.Download else Icons.Filled.SearchOff,
        title = stringResource(if (downloadedOnly) AR.string.library_downloaded_empty else AR.string.library_no_matches),
        body = null,
        modifier = modifier,
    )
}

@Composable
private fun EmptyState(
    icon: ImageVector,
    title: String,
    body: String?,
    modifier: Modifier = Modifier,
    actions: @Composable () -> Unit = {},
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(112.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(52.dp))
            }
        }
        Spacer(Modifier.height(24.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        if (body != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.height(24.dp))
        actions()
    }
}

@Composable
internal fun TrackMenu(track: AudioTrack, downloaded: Boolean, downloading: Boolean, actions: TrackActions) {
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
                text = { Text(stringResource(AR.string.collections_action)) },
                leadingIcon = { Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = null) },
                onClick = {
                    expanded = false
                    actions.collections(track)
                },
            )
            actions.removeFromCollection?.let { remove ->
                DropdownMenuItem(
                    text = { Text(stringResource(AR.string.collection_remove_track)) },
                    leadingIcon = { Icon(Icons.Filled.PlaylistRemove, contentDescription = null) },
                    onClick = {
                        expanded = false
                        remove(track)
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
internal fun RenameDialog(track: AudioTrack, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
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
internal fun DeleteDialog(track: AudioTrack, onConfirm: () -> Unit, onDismiss: () -> Unit) {
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

private fun importFailureReason(failure: ImportFailure?): StringResource? = when (failure) {
    ImportFailure.Connection -> AR.string.import_failed_connection
    ImportFailure.TooLarge -> AR.string.import_failed_too_large
    ImportFailure.NotAllowed -> AR.string.import_failed_not_allowed
    ImportFailure.Server -> AR.string.import_failed_server
    ImportFailure.Unreadable -> AR.string.import_failed_unreadable
    ImportFailure.Unknown, null -> null
}

@Composable
internal fun ImportPanel(jobs: List<ImportJob>, onDismiss: (Uuid) -> Unit, onRetry: (Uuid) -> Unit, onClearFinished: () -> Unit) {
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
                        ImportStatus.Retrying -> Text(
                            stringResource(AR.string.import_retrying),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        ImportStatus.Done -> Text(
                            stringResource(AR.string.import_done),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        ImportStatus.Failed -> {
                            Text(
                                stringResource(AR.string.import_failed),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                            importFailureReason(job.failure)?.let { reason ->
                                Text(
                                    stringResource(reason),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
                if (job.status == ImportStatus.Failed) {
                    IconButton(onClick = { onRetry(job.id) }) {
                        Icon(Icons.Filled.Refresh, contentDescription = stringResource(AR.string.import_retry))
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

