package id.homebase.audio.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import id.homebase.audio.resources.AR
import id.homebase.audio.resources.navigate_back
import id.homebase.audio.resources.settings_keep_recent
import id.homebase.audio.resources.settings_keep_recent_detail
import id.homebase.audio.resources.settings_limit
import id.homebase.audio.resources.settings_limit_gb
import id.homebase.audio.resources.settings_offline_heading
import id.homebase.audio.resources.settings_title
import id.homebase.audio.resources.settings_usage_automatic
import id.homebase.audio.resources.settings_usage_own
import id.homebase.audio.resources.settings_wifi_only
import id.homebase.audio.resources.settings_uploads_at_once
import id.homebase.audio.resources.settings_uploads_detail
import id.homebase.audio.resources.settings_uploads_heading
import id.homebase.common.util.formatBytes
import org.jetbrains.compose.resources.stringResource

@Composable
fun SettingsScreen(viewModel: SettingsViewModel, onBack: () -> Unit) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    SettingsContent(
        uiState = uiState,
        onBack = onBack,
        onKeepRecent = viewModel::setKeepRecent,
        onLimit = viewModel::setLimit,
        onWifiOnly = viewModel::setWifiOnly,
        onUploadsAtOnce = viewModel::setUploadsAtOnce,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsContent(
    uiState: SettingsUiState,
    onBack: () -> Unit,
    onKeepRecent: (Boolean) -> Unit,
    onLimit: (Long) -> Unit,
    onWifiOnly: (Boolean) -> Unit,
    onUploadsAtOnce: (Int) -> Unit = {},
) {
    val prefs = uiState.preferences
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(AR.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(AR.string.navigate_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            Text(
                stringResource(AR.string.settings_offline_heading),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
            )
            ListItem(
                headlineContent = { Text(stringResource(AR.string.settings_keep_recent)) },
                supportingContent = { Text(stringResource(AR.string.settings_keep_recent_detail)) },
                trailingContent = { Switch(checked = prefs.keepRecentOffline, onCheckedChange = onKeepRecent) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            )
            ListItem(
                headlineContent = { Text(stringResource(AR.string.settings_wifi_only)) },
                trailingContent = {
                    Switch(checked = prefs.offlineOnWifiOnly, onCheckedChange = onWifiOnly, enabled = prefs.keepRecentOffline)
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            )
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(AR.string.settings_limit), style = MaterialTheme.typography.bodyLarge)
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    SettingsViewModel.LIMITS_GB.forEachIndexed { index, gb ->
                        val bytes = gb * SettingsViewModel.GB
                        SegmentedButton(
                            selected = prefs.offlineLimitBytes == bytes,
                            onClick = { onLimit(bytes) },
                            enabled = prefs.keepRecentOffline,
                            shape = SegmentedButtonDefaults.itemShape(index, SettingsViewModel.LIMITS_GB.size),
                        ) { Text(stringResource(AR.string.settings_limit_gb, gb)) }
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(AR.string.settings_usage_automatic, formatBytes(uiState.automaticBytes), formatBytes(prefs.offlineLimitBytes)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    stringResource(AR.string.settings_usage_own, formatBytes(uiState.ownBytes)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                stringResource(AR.string.settings_uploads_heading),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 4.dp),
            )
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(AR.string.settings_uploads_at_once), style = MaterialTheme.typography.bodyLarge)
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    SettingsViewModel.UPLOADS_AT_ONCE.forEachIndexed { index, count ->
                        SegmentedButton(
                            selected = prefs.uploadsAtOnce == count,
                            onClick = { onUploadsAtOnce(count) },
                            shape = SegmentedButtonDefaults.itemShape(index, SettingsViewModel.UPLOADS_AT_ONCE.size),
                        ) { Text(count.toString()) }
                    }
                }
                Text(
                    stringResource(AR.string.settings_uploads_detail),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
