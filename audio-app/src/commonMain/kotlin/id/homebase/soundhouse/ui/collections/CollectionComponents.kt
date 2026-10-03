package id.homebase.soundhouse.ui.collections

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import id.homebase.soundhouse.data.AudioCollection
import id.homebase.soundhouse.data.AudioTrack
import id.homebase.soundhouse.data.isIn
import id.homebase.soundhouse.resources.AR
import id.homebase.soundhouse.resources.cancel
import id.homebase.soundhouse.resources.collection_create_and_add
import id.homebase.soundhouse.resources.collection_name_label
import id.homebase.soundhouse.resources.collection_new
import id.homebase.soundhouse.resources.collections_for_track
import id.homebase.soundhouse.resources.collections_none_yet
import id.homebase.soundhouse.resources.library_track_count
import id.homebase.soundhouse.resources.rename_confirm
import id.homebase.soundhouse.ui.common.TrackArtwork
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.uuid.Uuid

@Immutable
data class CollectionSummary(val collection: AudioCollection, val trackCount: Int)

fun summarize(collections: List<AudioCollection>, tracks: List<AudioTrack>): List<CollectionSummary> =
    collections.map { collection -> CollectionSummary(collection, tracks.count { it.isIn(collection) }) }

@Composable
fun CollectionChips(
    collections: List<CollectionSummary>,
    onOpen: (Uuid) -> Unit,
    onNew: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(collections, key = { it.collection.id.toString() }) { summary ->
            SuggestionChip(
                onClick = { onOpen(summary.collection.id) },
                label = {
                    Text(summary.collection.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.width(6.dp))
                    Text(summary.trackCount.toString(), color = MaterialTheme.colorScheme.onSurfaceVariant)
                },
            )
        }
        item(key = "new") {
            AssistChip(
                onClick = onNew,
                label = { Text(stringResource(AR.string.collection_new)) },
                leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp)) },
            )
        }
    }
}

@Composable
fun CollectionTile(summary: CollectionSummary, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.width(132.dp).clickable(onClick = onClick)) {
        TrackArtwork(summary.collection.name, seed = summary.collection.id.toString(), modifier = Modifier.size(132.dp), cornerRadius = 16.dp)
        Spacer(Modifier.height(8.dp))
        Text(summary.collection.name, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(
            pluralStringResource(AR.plurals.library_track_count, summary.trackCount, summary.trackCount),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Ticks [track]'s collections; a name typed at the bottom creates a collection with the track in it. */
@Composable
fun CollectionsDialog(
    track: AudioTrack,
    collections: List<AudioCollection>,
    onSave: (Set<Uuid>) -> Unit,
    onCreate: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var selected by remember(track.fileId) { mutableStateOf(collections.filter { track.isIn(it) }.mapTo(HashSet()) { it.id }.toSet()) }
    var newName by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(AR.string.collections_for_track, track.title), maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            Column {
                if (collections.isEmpty()) {
                    Text(
                        stringResource(AR.string.collections_none_yet),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    LazyColumn(Modifier.heightIn(max = 280.dp)) {
                        items(collections, key = { it.id.toString() }) { collection ->
                            val checked = collection.id in selected
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { selected = if (checked) selected - collection.id else selected + collection.id },
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(checked = checked, onCheckedChange = null, modifier = Modifier.padding(12.dp))
                                Text(collection.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
                HorizontalDivider(Modifier.padding(vertical = 12.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        singleLine = true,
                        label = { Text(stringResource(AR.string.collection_new)) },
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { onCreate(newName) }, enabled = newName.isNotBlank()) {
                        Text(stringResource(AR.string.collection_create_and_add))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(selected) }) { Text(stringResource(AR.string.rename_confirm)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(AR.string.cancel)) } },
    )
}

@Composable
fun CollectionNameDialog(title: String, initial: String, confirmLabel: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                label = { Text(stringResource(AR.string.collection_name_label)) },
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(name) }, enabled = name.isNotBlank()) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(AR.string.cancel)) } },
    )
}
