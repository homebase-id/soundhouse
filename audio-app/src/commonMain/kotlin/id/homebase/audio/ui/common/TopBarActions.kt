package id.homebase.audio.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Mic
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
import id.homebase.audio.resources.AR
import id.homebase.audio.resources.account_menu
import id.homebase.audio.resources.record_open
import id.homebase.audio.resources.sign_out
import org.jetbrains.compose.resources.stringResource

/** Record and account actions, shared by the top bars of both tabs. */
@Composable
fun TopBarActions(onOpenRecorder: () -> Unit, onSignOut: () -> Unit) {
    IconButton(onClick = onOpenRecorder) {
        Icon(Icons.Filled.Mic, contentDescription = stringResource(AR.string.record_open))
    }
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Filled.AccountCircle, contentDescription = stringResource(AR.string.account_menu))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
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
