package id.homebase.soundhouse.ui.library

import kotlinx.coroutines.CancellationException
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import id.homebase.soundhouse.data.AudioTrack
import id.homebase.soundhouse.importing.TrackDetails
import id.homebase.soundhouse.resources.AR
import id.homebase.soundhouse.resources.cancel
import id.homebase.soundhouse.resources.edit_details_save
import id.homebase.soundhouse.resources.edit_details_title
import id.homebase.soundhouse.resources.field_album
import id.homebase.soundhouse.resources.field_album_artist
import id.homebase.soundhouse.resources.field_artist
import id.homebase.soundhouse.resources.field_notes
import id.homebase.soundhouse.resources.notes_loading
import id.homebase.soundhouse.resources.field_composer
import id.homebase.soundhouse.resources.field_disc
import id.homebase.soundhouse.resources.field_genre
import id.homebase.soundhouse.resources.field_of
import id.homebase.soundhouse.resources.field_title
import id.homebase.soundhouse.resources.field_track
import id.homebase.soundhouse.resources.field_year
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Title plus the descriptive tags; numbers that don't parse are dropped rather than refused. Notes
 * load when the sheet opens; until they have (or if they can't), saving leaves them as they are.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EditDetailsSheet(
    track: AudioTrack,
    loadNotes: suspend () -> String?,
    onSave: (title: String, details: TrackDetails, notes: String?) -> Unit,
    onDismiss: () -> Unit,
) {
    val details = track.details ?: TrackDetails()
    val title = remember(track.fileId) { mutableStateOf(track.title) }
    val artist = remember(track.fileId) { mutableStateOf(details.artist.orEmpty()) }
    val album = remember(track.fileId) { mutableStateOf(details.album.orEmpty()) }
    val albumArtist = remember(track.fileId) { mutableStateOf(details.albumArtist.orEmpty()) }
    val trackNumber = remember(track.fileId) { mutableStateOf(details.trackNumber?.toString().orEmpty()) }
    val trackTotal = remember(track.fileId) { mutableStateOf(details.trackTotal?.toString().orEmpty()) }
    val discNumber = remember(track.fileId) { mutableStateOf(details.discNumber?.toString().orEmpty()) }
    val discTotal = remember(track.fileId) { mutableStateOf(details.discTotal?.toString().orEmpty()) }
    val date = remember(track.fileId) { mutableStateOf(details.date.orEmpty()) }
    val genre = remember(track.fileId) { mutableStateOf(details.genre.orEmpty()) }
    val composer = remember(track.fileId) { mutableStateOf(details.composer.orEmpty()) }
    val notes = remember(track.fileId) { mutableStateOf("") }
    var notesLoaded by remember(track.fileId) { mutableStateOf(false) }
    LaunchedEffect(track.fileId) {
        runCatching { loadNotes() }
            .onSuccess {
                notes.value = it.orEmpty()
                notesLoaded = true
            }
            .onFailure { if (it is CancellationException) throw it }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(AR.string.edit_details_title), style = MaterialTheme.typography.titleLarge)
            Field(AR.string.field_title, title)
            Field(AR.string.field_artist, artist)
            Field(AR.string.field_album, album)
            Field(AR.string.field_album_artist, albumArtist)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Field(AR.string.field_track, trackNumber, Modifier.weight(1f), number = true)
                Field(AR.string.field_of, trackTotal, Modifier.weight(1f), number = true)
                Field(AR.string.field_disc, discNumber, Modifier.weight(1f), number = true)
                Field(AR.string.field_of, discTotal, Modifier.weight(1f), number = true)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Field(AR.string.field_year, date, Modifier.weight(1f))
                Field(AR.string.field_genre, genre, Modifier.weight(2f))
            }
            Field(AR.string.field_composer, composer)
            Field(
                if (notesLoaded) AR.string.field_notes else AR.string.notes_loading,
                notes,
                singleLine = false,
                enabled = notesLoaded,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, androidx.compose.ui.Alignment.End)) {
                TextButton(onClick = onDismiss) { Text(stringResource(AR.string.cancel)) }
                Button(
                    enabled = title.value.isNotBlank(),
                    onClick = {
                        onSave(
                            title.value,
                            TrackDetails(
                                artist = artist.value,
                                album = album.value,
                                albumArtist = albumArtist.value,
                                trackNumber = trackNumber.value.trim().toIntOrNull(),
                                trackTotal = trackTotal.value.trim().toIntOrNull(),
                                discNumber = discNumber.value.trim().toIntOrNull(),
                                discTotal = discTotal.value.trim().toIntOrNull(),
                                date = date.value,
                                genre = genre.value,
                                composer = composer.value,
                            ),
                            notes.value.takeIf { notesLoaded },
                        )
                    },
                ) { Text(stringResource(AR.string.edit_details_save)) }
            }
        }
    }
}

@Composable
private fun Field(
    label: StringResource,
    state: MutableState<String>,
    modifier: Modifier = Modifier.fillMaxWidth(),
    number: Boolean = false,
    singleLine: Boolean = true,
    enabled: Boolean = true,
) {
    OutlinedTextField(
        enabled = enabled,
        value = state.value,
        onValueChange = { state.value = if (number) it.filter(Char::isDigit).take(4) else it },
        label = { Text(stringResource(label)) },
        singleLine = singleLine,
        minLines = if (singleLine) 1 else 3,
        keyboardOptions = if (number) KeyboardOptions(keyboardType = KeyboardType.Number) else KeyboardOptions.Default,
        modifier = modifier,
    )
}
