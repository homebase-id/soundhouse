package id.homebase.audio.ui.library

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import id.homebase.audio.data.AudioTrack
import id.homebase.audio.importing.playableExtensions
import id.homebase.audio.resources.AR
import id.homebase.audio.ui.importing.ImportActions
import id.homebase.audio.ui.importing.ImportSummaryCard
import id.homebase.audio.resources.collection_create
import id.homebase.audio.resources.collection_failed
import id.homebase.audio.resources.collection_new
import id.homebase.audio.ui.collections.CollectionChips
import id.homebase.audio.ui.collections.CollectionNameDialog
import id.homebase.audio.ui.collections.CollectionsDialog
import kotlin.uuid.Uuid
import id.homebase.audio.resources.account_menu
import id.homebase.audio.resources.delete_failed
import id.homebase.audio.resources.import_action
import id.homebase.audio.resources.library_title
import id.homebase.audio.resources.library_track_count
import id.homebase.audio.resources.record_open
import id.homebase.audio.resources.rename_failed
import id.homebase.audio.resources.sign_out
import io.github.vinceglb.filekit.dialogs.FileKitMode
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.compose.rememberFilePickerLauncher
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel,
    onOpenPlayer: () -> Unit,
    onOpenRecorder: () -> Unit,
    onOpenCollection: (Uuid) -> Unit,
    actions: @Composable () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            snackbar.showSnackbar(
                getString(
                    when (event) {
                        LibraryEvent.RenameFailed -> AR.string.rename_failed
                        LibraryEvent.DeleteFailed -> AR.string.delete_failed
                        LibraryEvent.CollectionFailed -> AR.string.collection_failed
                    }
                )
            )
        }
    }
    var renaming by remember { mutableStateOf<AudioTrack?>(null) }
    var showingDetails by remember { mutableStateOf<AudioTrack?>(null) }
    showingDetails?.let { shown ->
        TrackDetailsSheet(
            track = uiState.tracks.firstOrNull { it.fileId == shown.fileId } ?: shown,
            onOpen = viewModel::readQuality,
            onDismiss = { showingDetails = null },
        )
    }
    var deleting by remember { mutableStateOf<AudioTrack?>(null) }
    var choosingCollections by remember { mutableStateOf<AudioTrack?>(null) }
    var creatingCollection by remember { mutableStateOf(false) }
    choosingCollections?.let { track ->
        CollectionsDialog(
            track = track,
            collections = uiState.collections.map { it.collection },
            onSave = { selected ->
                viewModel.setCollections(track, selected)
                choosingCollections = null
            },
            onCreate = { name ->
                viewModel.createCollection(name, withTrack = track)
                choosingCollections = null
            },
            onDismiss = { choosingCollections = null },
        )
    }
    if (creatingCollection) {
        CollectionNameDialog(
            title = stringResource(AR.string.collection_new),
            initial = "",
            confirmLabel = stringResource(AR.string.collection_create),
            onConfirm = { name ->
                viewModel.createCollection(name)
                creatingCollection = false
            },
            onDismiss = { creatingCollection = false },
        )
    }
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
    val trackActions = remember(viewModel) {
        TrackActions(
            download = viewModel::download,
            removeDownload = viewModel::removeDownload,
            rename = { renaming = it },
            delete = { deleting = it },
            collections = { choosingCollections = it },
            details = { showingDetails = it },
        )
    }
    val importActions = remember(viewModel) {
        ImportActions(retry = viewModel::retryImport, dismiss = viewModel::dismissImport, clearFinished = viewModel::clearFinishedImports)
    }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val listState = rememberLazyListState()
    val fabExpanded by remember { derivedStateOf { listState.firstVisibleItemIndex == 0 } }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(AR.string.library_title)) },
                actions = { actions() },
                scrollBehavior = scrollBehavior,
            )
        },
        floatingActionButton = {
            if (uiState.totalTracks > 0) {
                ExtendedFloatingActionButton(
                    onClick = { importLauncher.launch() },
                    expanded = fabExpanded,
                    icon = { Icon(Icons.Filled.FileUpload, contentDescription = null) },
                    text = { Text(stringResource(AR.string.import_action)) },
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        if (!uiState.isLoaded) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                ContainedLoadingIndicator()
            }
            return@Scaffold
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = 96.dp),
        ) {
            if (uiState.totalTracks == 0 && uiState.imports.isEmpty()) {
                item {
                    EmptyLibrary(onImport = { importLauncher.launch() }, onRecord = onOpenRecorder)
                }
                return@LazyColumn
            }
            item { ImportSummaryCard(uiState.imports, importActions) }
            item {
                Text(
                    pluralStringResource(AR.plurals.library_track_count, uiState.totalTracks, uiState.totalTracks),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
            }
            item {
                SearchPill(
                    query = uiState.query,
                    onQueryChange = viewModel::onQueryChange,
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp),
                )
            }
            item {
                FilterChips(
                    sort = uiState.sort,
                    downloadedOnly = uiState.downloadedOnly,
                    onSortChange = viewModel::onSortChange,
                    onDownloadedOnlyChange = viewModel::onDownloadedOnlyChange,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            item {
                CollectionChips(
                    collections = uiState.collections,
                    onOpen = onOpenCollection,
                    onNew = { creatingCollection = true },
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
            if (uiState.tracks.isEmpty() && uiState.totalTracks > 0) {
                item { NoResults(downloadedOnly = uiState.downloadedOnly) }
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
                    actions = trackActions,
                )
            }
        }
    }
}
