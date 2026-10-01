package id.homebase.audio

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import id.homebase.audio.ui.common.CoverLoader
import id.homebase.audio.ui.common.LocalCoverLoader
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import id.homebase.audio.navigation.AudioNavHost
import id.homebase.core.settings.ThemeState
import id.homebase.core.settings.UserPreferences
import id.homebase.audio.ui.theme.AudioTheme
import org.koin.compose.koinInject

@Composable
fun AudioApp() {
    val userPreferences: UserPreferences = koinInject()
    val prefState by userPreferences.preferenceState.collectAsStateWithLifecycle()
    val darkTheme = when (prefState.theme) {
        ThemeState.System -> isSystemInDarkTheme()
        ThemeState.Dark -> true
        ThemeState.Light -> false
    }
    AudioTheme(darkTheme = darkTheme, followsSystemTheme = prefState.theme == ThemeState.System) {
        CompositionLocalProvider(LocalCoverLoader provides koinInject<CoverLoader>()) {
            AudioNavHost()
        }
    }
}
