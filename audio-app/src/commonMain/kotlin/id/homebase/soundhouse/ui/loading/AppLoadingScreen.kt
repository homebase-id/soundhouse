package id.homebase.soundhouse.ui.loading

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

@Composable
fun AppLoadingScreen(
    viewModel: AppLoadingViewModel,
    onSignedIn: () -> Unit,
    onSignedOut: () -> Unit,
) {
    val signedIn by rememberUpdatedState(onSignedIn)
    val signedOut by rememberUpdatedState(onSignedOut)
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                AppLoadingEvent.SignedIn -> signedIn()
                AppLoadingEvent.SignedOut -> signedOut()
            }
        }
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}
