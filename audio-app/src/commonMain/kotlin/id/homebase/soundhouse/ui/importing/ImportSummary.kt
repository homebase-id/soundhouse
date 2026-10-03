package id.homebase.soundhouse.ui.importing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import id.homebase.soundhouse.importing.ImportFailure
import id.homebase.soundhouse.importing.ImportJob
import id.homebase.soundhouse.importing.ImportStatus
import id.homebase.soundhouse.resources.AR
import id.homebase.soundhouse.resources.import_clear_finished
import id.homebase.soundhouse.resources.import_dismiss
import id.homebase.soundhouse.resources.import_failed
import id.homebase.soundhouse.resources.import_failed_connection
import id.homebase.soundhouse.resources.import_failed_not_allowed
import id.homebase.soundhouse.resources.import_failed_server
import id.homebase.soundhouse.resources.import_failed_too_large
import id.homebase.soundhouse.resources.import_failed_unreadable
import id.homebase.soundhouse.resources.import_queued
import id.homebase.soundhouse.resources.import_retry
import id.homebase.soundhouse.resources.import_retrying
import id.homebase.soundhouse.resources.import_summary_added
import id.homebase.soundhouse.resources.import_summary_and_more
import id.homebase.soundhouse.resources.import_summary_failed
import id.homebase.soundhouse.resources.import_summary_progress
import id.homebase.soundhouse.resources.import_summary_review
import id.homebase.soundhouse.resources.import_summary_show
import id.homebase.soundhouse.resources.uploads_section_added
import id.homebase.soundhouse.resources.uploads_section_failed
import id.homebase.soundhouse.resources.uploads_section_uploading
import id.homebase.soundhouse.resources.uploads_section_waiting
import id.homebase.soundhouse.resources.uploads_retry_all
import id.homebase.soundhouse.resources.uploads_title
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.uuid.Uuid

@Immutable
data class ImportSummary(
    val total: Int,
    val done: Int,
    val uploading: List<ImportJob>,
    val waiting: List<ImportJob>,
    val failed: List<ImportJob>,
    val added: List<ImportJob>,
    /** 0..1 across the whole batch, counting partial progress of running uploads. */
    val progress: Float,
) {
    val isActive: Boolean get() = uploading.isNotEmpty() || waiting.isNotEmpty()
}

fun importSummary(jobs: List<ImportJob>): ImportSummary {
    val uploading = jobs.filter { it.status == ImportStatus.Uploading }
    val waiting = jobs.filter { it.status == ImportStatus.Queued || it.status == ImportStatus.Retrying }
    val failed = jobs.filter { it.status == ImportStatus.Failed }
    val added = jobs.filter { it.status == ImportStatus.Done }
    val batch = jobs.size - failed.size
    val progress = if (batch == 0) 0f else ((added.size + uploading.sumOf { it.progress.toDouble() }) / batch).toFloat()
    return ImportSummary(batch, added.size, uploading, waiting, failed, added, progress.coerceIn(0f, 1f))
}

class ImportActions(
    val retry: (Uuid) -> Unit,
    val dismiss: (Uuid) -> Unit,
    val clearFinished: () -> Unit,
)

/** One card for the whole batch; the details live in a sheet so a big import doesn't fill the list. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportSummaryCard(jobs: List<ImportJob>, actions: ImportActions, modifier: Modifier = Modifier) {
    if (jobs.isEmpty()) return
    val summary = importSummary(jobs)
    var showSheet by rememberSaveable { mutableStateOf(false) }
    Surface(
        onClick = { showSheet = true },
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (summary.isActive) Icons.Filled.CloudUpload else Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(
                        summaryTitle(summary),
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    summarySubtitle(summary)?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (summary.isActive || summary.failed.isNotEmpty()) {
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = stringResource(AR.string.import_summary_show),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    TextButton(onClick = actions.clearFinished) { Text(stringResource(AR.string.import_dismiss)) }
                }
            }
            if (summary.isActive) {
                LinearProgressIndicator(
                    progress = { summary.progress },
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp, end = 8.dp),
                )
            }
            if (summary.failed.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                    Text(
                        pluralStringResource(AR.plurals.import_summary_failed, summary.failed.size, summary.failed.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.weight(1f).padding(start = 36.dp),
                    )
                    TextButton(onClick = { showSheet = true }) { Text(stringResource(AR.string.import_summary_review)) }
                }
            }
        }
    }
    if (showSheet) {
        ModalBottomSheet(onDismissRequest = { showSheet = false }) {
            UploadsSheetContent(summary, actions)
        }
    }
}

@Composable
private fun summaryTitle(summary: ImportSummary): String = when {
    summary.isActive && summary.total == 1 -> (summary.uploading + summary.waiting).first().fileName
    summary.isActive -> stringResource(AR.string.import_summary_progress, summary.done, summary.total)
    else -> pluralStringResource(AR.plurals.import_summary_added, summary.done, summary.done)
}

@Composable
private fun summarySubtitle(summary: ImportSummary): String? {
    if (!summary.isActive) return null
    if (summary.uploading.isEmpty()) {
        return if (summary.waiting.any { it.status == ImportStatus.Retrying }) stringResource(AR.string.import_retrying)
        else stringResource(AR.string.import_queued)
    }
    if (summary.total == 1) return null
    val first = summary.uploading.first().fileName
    val more = summary.uploading.size - 1
    return if (more == 0) first else pluralStringResource(AR.plurals.import_summary_and_more, more, first, more)
}

@Composable
private fun UploadsSheetContent(summary: ImportSummary, actions: ImportActions) {
    LazyColumn(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
        item {
            Text(
                stringResource(AR.string.uploads_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
        }
        if (summary.failed.isNotEmpty()) {
            item {
                SectionHeader(stringResource(AR.string.uploads_section_failed)) {
                    TextButton(onClick = { summary.failed.forEach { actions.retry(it.id) } }) {
                        Text(stringResource(AR.string.uploads_retry_all))
                    }
                }
            }
            items(summary.failed, key = { "f-${it.id}" }) { job ->
                ListItem(
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    leadingContent = { Icon(Icons.Filled.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                    headlineContent = { Text(job.fileName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    supportingContent = { Text(stringResource(importFailureReason(job.failure) ?: AR.string.import_failed)) },
                    trailingContent = {
                        Row {
                            IconButton(onClick = { actions.retry(job.id) }) {
                                Icon(Icons.Filled.Refresh, contentDescription = stringResource(AR.string.import_retry))
                            }
                            IconButton(onClick = { actions.dismiss(job.id) }) {
                                Icon(Icons.Filled.Close, contentDescription = stringResource(AR.string.import_dismiss))
                            }
                        }
                    },
                )
            }
        }
        if (summary.uploading.isNotEmpty()) {
            item { SectionHeader(stringResource(AR.string.uploads_section_uploading)) }
            items(summary.uploading, key = { "u-${it.id}" }) { job ->
                ListItem(
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    headlineContent = { Text(job.fileName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    supportingContent = {
                        LinearProgressIndicator(progress = { job.progress }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
                    },
                )
            }
        }
        if (summary.waiting.isNotEmpty()) {
            item { SectionHeader(stringResource(AR.string.uploads_section_waiting)) }
            items(summary.waiting, key = { "w-${it.id}" }) { job ->
                ListItem(
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    headlineContent = { Text(job.fileName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    supportingContent = {
                        Text(stringResource(if (job.status == ImportStatus.Retrying) AR.string.import_retrying else AR.string.import_queued))
                    },
                )
            }
        }
        if (summary.added.isNotEmpty()) {
            item {
                SectionHeader(pluralStringResource(AR.plurals.uploads_section_added, summary.added.size, summary.added.size)) {
                    TextButton(onClick = actions.clearFinished) { Text(stringResource(AR.string.import_clear_finished)) }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, action: @Composable () -> Unit = {}) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp, top = 12.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        action()
    }
}

private fun importFailureReason(failure: ImportFailure?): StringResource? = when (failure) {
    ImportFailure.Connection -> AR.string.import_failed_connection
    ImportFailure.TooLarge -> AR.string.import_failed_too_large
    ImportFailure.NotAllowed -> AR.string.import_failed_not_allowed
    ImportFailure.Server -> AR.string.import_failed_server
    ImportFailure.Unreadable -> AR.string.import_failed_unreadable
    ImportFailure.Unknown, null -> null
}
