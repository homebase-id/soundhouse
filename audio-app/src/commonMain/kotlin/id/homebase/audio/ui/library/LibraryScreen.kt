package id.homebase.audio.ui.library

import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.MusicNote
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
import id.homebase.audio.resources.sign_out
import id.homebase.audio.resources.track_duration_unknown
import id.homebase.audio.resources.track_subtitle
import id.homebase.audio.ui.common.formatDate
import id.homebase.audio.ui.common.formatDuration
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(viewModel: LibraryViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(AR.string.app_name)) },
                actions = {
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
            when {
                !uiState.isLoaded -> CenteredContent { CircularProgressIndicator() }
                uiState.totalTracks == 0 -> CenteredMessage(stringResource(AR.string.library_empty))
                uiState.tracks.isEmpty() -> CenteredMessage(stringResource(AR.string.library_no_matches))
                else -> TrackList(uiState.tracks)
            }
        }
    }
}

@Composable
private fun TrackList(tracks: List<AudioTrack>) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 88.dp)) {
        items(tracks, key = { it.fileId.toString() }) { track ->
            TrackRow(track)
            HorizontalDivider()
        }
    }
}

@Composable
private fun TrackRow(track: AudioTrack) {
    val duration = track.durationMs?.let(::formatDuration) ?: stringResource(AR.string.track_duration_unknown)
    ListItem(
        headlineContent = { Text(track.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            Text(stringResource(AR.string.track_subtitle, duration, formatDate(track.dateAddedMs)))
        },
        leadingContent = { Icon(Icons.Filled.MusicNote, contentDescription = null) },
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
