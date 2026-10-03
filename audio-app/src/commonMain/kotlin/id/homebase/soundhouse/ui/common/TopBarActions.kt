package id.homebase.soundhouse.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import id.homebase.api.common.OdinId
import id.homebase.core.avatars.AvatarOptions
import id.homebase.core.avatars.PublicAvatar
import id.homebase.core.util.initials
import id.homebase.soundhouse.resources.AR
import id.homebase.soundhouse.resources.account_menu
import id.homebase.soundhouse.resources.record_open
import id.homebase.soundhouse.resources.settings_title
import id.homebase.soundhouse.resources.sign_out
import org.jetbrains.compose.resources.stringResource

/** Record and account actions, shared by the top bars of both tabs. */
@Composable
fun TopBarActions(identity: OdinId?, onOpenRecorder: () -> Unit, onOpenSettings: () -> Unit, onSignOut: () -> Unit) {
    IconButton(onClick = onOpenRecorder) {
        Icon(Icons.Filled.Mic, contentDescription = stringResource(AR.string.record_open))
    }
    var expanded by remember { mutableStateOf(false) }
    Box {
        val accountLabel = stringResource(AR.string.account_menu)
        IconButton(onClick = { expanded = true }, modifier = Modifier.semantics { contentDescription = accountLabel }) {
            if (identity != null) {
                PublicAvatar(
                    odinId = identity,
                    initials = identity.domainName.initials(),
                    options = AvatarOptions(size = 32.dp),
                    modifier = Modifier.clearAndSetSemantics {},
                )
            } else {
                Icon(Icons.Filled.AccountCircle, contentDescription = null)
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(AR.string.settings_title)) },
                leadingIcon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                onClick = {
                    expanded = false
                    onOpenSettings()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(AR.string.sign_out)) },
                leadingIcon = { Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null) },
                onClick = {
                    expanded = false
                    onSignOut()
                },
            )
        }
    }
}
