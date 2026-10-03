package id.homebase.soundhouse.ui.library

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import id.homebase.soundhouse.data.AudioTrack
import id.homebase.soundhouse.data.TrackOrigin
import id.homebase.soundhouse.importing.QualityTier
import id.homebase.soundhouse.importing.formatMegabytes
import id.homebase.soundhouse.importing.tier
import id.homebase.soundhouse.resources.AR
import id.homebase.soundhouse.resources.details_added
import id.homebase.soundhouse.resources.details_bit_depth
import id.homebase.soundhouse.resources.details_bitrate
import id.homebase.soundhouse.resources.details_channels
import id.homebase.soundhouse.resources.details_duration
import id.homebase.soundhouse.resources.details_file_name
import id.homebase.soundhouse.resources.details_format
import id.homebase.soundhouse.resources.details_format_pending
import id.homebase.soundhouse.resources.details_quality
import id.homebase.soundhouse.resources.details_recorded
import id.homebase.soundhouse.resources.details_sample_rate
import id.homebase.soundhouse.resources.details_size
import id.homebase.soundhouse.resources.details_size_value
import id.homebase.soundhouse.ui.common.QualityBadge
import id.homebase.soundhouse.ui.common.TrackCover
import id.homebase.soundhouse.ui.common.bitDepthLabel
import id.homebase.soundhouse.ui.common.bitrateLabel
import id.homebase.soundhouse.ui.common.channelsLabel
import id.homebase.soundhouse.ui.common.codecDisplayName
import id.homebase.soundhouse.ui.common.formatDate
import id.homebase.soundhouse.ui.common.formatDuration
import id.homebase.soundhouse.ui.common.sampleRateLabel
import id.homebase.soundhouse.ui.common.tierLabel
import org.jetbrains.compose.resources.stringResource

/** [onOpen] asks for a format probe when the track has none; the sheet follows [track] as it updates. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TrackDetailsSheet(track: AudioTrack, onOpen: (AudioTrack) -> Unit, onDismiss: () -> Unit) {
    LaunchedEffect(track.fileId) { onOpen(track) }
    val quality = track.displayQuality
    val tier = quality?.tier
    val lossy = tier == QualityTier.Lossy
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 16.dp)) {
            ListItem(
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                leadingContent = { TrackCover(track, modifier = Modifier.size(64.dp)) },
                headlineContent = {
                    Text(track.title, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                },
                supportingContent = { tier?.let { QualityBadge(it) } },
            )
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            tier?.let { DetailRow(stringResource(AR.string.details_quality), tierLabel(it)) }
            quality?.codec?.let { DetailRow(stringResource(AR.string.details_format), codecDisplayName(it)) }
            quality?.sampleRateHz?.let { DetailRow(stringResource(AR.string.details_sample_rate), sampleRateLabel(it)) }
            quality?.bitDepth?.takeUnless { lossy }?.let { DetailRow(stringResource(AR.string.details_bit_depth), bitDepthLabel(it)) }
            quality?.channels?.let { DetailRow(stringResource(AR.string.details_channels), channelsLabel(it)) }
            quality?.bitrateBps?.let { DetailRow(stringResource(AR.string.details_bitrate), bitrateLabel(it)) }
            track.durationMs?.let { DetailRow(stringResource(AR.string.details_duration), formatDuration(it)) }
            DetailRow(stringResource(AR.string.details_size), stringResource(AR.string.details_size_value, formatMegabytes(track.sizeBytes)))
            track.content.fileName?.let { DetailRow(stringResource(AR.string.details_file_name), it) }
            DetailRow(
                stringResource(if (track.content.origin == TrackOrigin.Recorded) AR.string.details_recorded else AR.string.details_added),
                formatDate(track.dateAddedMs),
            )
            if (track.quality == null) {
                Text(
                    stringResource(AR.string.details_format_pending),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    ListItem(
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        overlineContent = { Text(label) },
        headlineContent = { Text(value) },
    )
}
