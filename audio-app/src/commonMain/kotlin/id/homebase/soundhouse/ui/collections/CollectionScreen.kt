package id.homebase.soundhouse.ui.collections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import id.homebase.soundhouse.data.AudioTrack
import id.homebase.soundhouse.resources.AR
import id.homebase.soundhouse.resources.cancel
import id.homebase.soundhouse.resources.collection_delete
import id.homebase.soundhouse.resources.collection_delete_message
import id.homebase.soundhouse.resources.collection_empty
import id.homebase.soundhouse.resources.collection_failed
import id.homebase.soundhouse.resources.collection_more
import id.homebase.soundhouse.resources.collection_rename
import id.homebase.soundhouse.resources.delete_confirm
import id.homebase.soundhouse.resources.home_play_all
import id.homebase.soundhouse.resources.home_shuffle
import id.homebase.soundhouse.resources.library_track_count
import id.homebase.soundhouse.resources.navigate_back
import id.homebase.soundhouse.resources.rename_confirm
import id.homebase.soundhouse.ui.library.DeleteDialog
import id.homebase.soundhouse.ui.library.RenameDialog
import id.homebase.soundhouse.ui.library.TrackActions
import id.homebase.soundhouse.ui.library.TrackDetailsSheet
import id.homebase.soundhouse.ui.library.TrackRow
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollectionScreen(viewModel: CollectionViewModel, onBack: () -> Unit, onOpenPlayer: () -> Unit) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                CollectionEvent.Deleted -> onBack()
                CollectionEvent.Failed -> snackbar.showSnackbar(getString(AR.string.collection_failed))
            }
        }
    }
    var renamingCollection by remember { mutableStateOf(false) }
    var deletingCollection by remember { mutableStateOf(false) }
    var renamingTrack by remember { mutableStateOf<AudioTrack?>(null) }
    var deletingTrack by remember { mutableStateOf<AudioTrack?>(null) }
    var choosingCollections by remember { mutableStateOf<AudioTrack?>(null) }
    var showingDetails by remember { mutableStateOf<AudioTrack?>(null) }
    val collection = uiState.collection
    showingDetails?.let { shown ->
        TrackDetailsSheet(
            track = uiState.tracks.firstOrNull { it.fileId == shown.fileId } ?: shown,
            onOpen = viewModel::readQuality,
            onDismiss = { showingDetails = null },
        )
    }

    if (renamingCollection && collection != null) {
        CollectionNameDialog(
            title = stringResource(AR.string.collection_rename),
            initial = collection.name,
            confirmLabel = stringResource(AR.string.rename_confirm),
            onConfirm = { name ->
                viewModel.rename(name)
                renamingCollection = false
            },
            onDismiss = { renamingCollection = false },
        )
    }
    if (deletingCollection && collection != null) {
        AlertDialog(
            onDismissRequest = { deletingCollection = false },
            icon = { Icon(Icons.Filled.Delete, contentDescription = null) },
            title = { Text(stringResource(AR.string.collection_delete)) },
            text = { Text(stringResource(AR.string.collection_delete_message, collection.name)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete()
                    deletingCollection = false
                }) { Text(stringResource(AR.string.delete_confirm), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deletingCollection = false }) { Text(stringResource(AR.string.cancel)) } },
        )
    }
    renamingTrack?.let { track ->
        RenameDialog(track, onConfirm = { viewModel.renameTrack(track, it); renamingTrack = null }, onDismiss = { renamingTrack = null })
    }
    deletingTrack?.let { track ->
        DeleteDialog(track, onConfirm = { viewModel.deleteTrack(track); deletingTrack = null }, onDismiss = { deletingTrack = null })
    }
    choosingCollections?.let { track ->
        CollectionsDialog(
            track = track,
            collections = uiState.allCollections,
            onSave = { viewModel.setCollections(track, it); choosingCollections = null },
            onCreate = { viewModel.createCollection(it, track); choosingCollections = null },
            onDismiss = { choosingCollections = null },
        )
    }
    val actions = remember(viewModel) {
        TrackActions(
            download = viewModel::download,
            removeDownload = viewModel::removeDownload,
            rename = { renamingTrack = it },
            delete = { deletingTrack = it },
            collections = { choosingCollections = it },
            details = { showingDetails = it },
            removeFromCollection = viewModel::remove,
        )
    }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text(collection?.name.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(AR.string.navigate_back))
                    }
                },
                actions = { if (collection != null) CollectionMenu({ renamingCollection = true }, { deletingCollection = true }) },
                scrollBehavior = scrollBehavior,
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = 96.dp),
        ) {
            item {
                Text(
                    pluralStringResource(AR.plurals.library_track_count, uiState.tracks.size, uiState.tracks.size),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
            }
            if (uiState.tracks.isEmpty()) {
                item {
                    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text(
                            stringResource(AR.string.collection_empty),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
                return@LazyColumn
            }
            item {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = { viewModel.playAll(false); onOpenPlayer() }) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(AR.string.home_play_all))
                    }
                    FilledTonalButton(onClick = { viewModel.playAll(true); onOpenPlayer() }) {
                        Icon(Icons.Filled.Shuffle, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(AR.string.home_shuffle))
                    }
                }
            }
            items(uiState.tracks, key = { it.fileId.toString() }) { track ->
                TrackRow(
                    track = track,
                    isCurrent = track.fileId == uiState.nowPlayingId,
                    isPlaying = uiState.isPlaying,
                    downloaded = track.fileId in uiState.downloaded,
                    downloadProgress = uiState.downloadProgress[track.fileId],
                    downloadFailed = track.fileId in uiState.downloadFailures,
                    onClick = {
                        viewModel.play(track)
                        onOpenPlayer()
                    },
                    actions = actions,
                )
            }
        }
    }
}

@Composable
private fun CollectionMenu(onRename: () -> Unit, onDelete: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Filled.MoreVert, contentDescription = stringResource(AR.string.collection_more))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(AR.string.collection_rename)) },
                leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                onClick = { expanded = false; onRename() },
            )
            DropdownMenuItem(
                text = { Text(stringResource(AR.string.collection_delete)) },
                leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                onClick = { expanded = false; onDelete() },
            )
        }
    }
}
