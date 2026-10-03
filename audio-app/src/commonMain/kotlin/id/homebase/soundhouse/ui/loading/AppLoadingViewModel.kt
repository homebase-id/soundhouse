package id.homebase.soundhouse.ui.loading

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import id.homebase.api.youauth.YouAuthFlowManager
import id.homebase.api.youauth.YouAuthState
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class AppLoadingViewModel(
    youAuthFlowManager: YouAuthFlowManager,
) : ViewModel() {
    private val _events = MutableSharedFlow<AppLoadingEvent>(replay = 1)
    val events: SharedFlow<AppLoadingEvent> = _events.asSharedFlow()

    init {
        viewModelScope.launch {
            val settled = youAuthFlowManager.authState.first {
                it !is YouAuthState.Initializing && it !is YouAuthState.Authenticating
            }
            _events.emit(
                if (settled is YouAuthState.Authenticated) AppLoadingEvent.SignedIn
                else AppLoadingEvent.SignedOut
            )
        }
    }
}

sealed interface AppLoadingEvent {
    data object SignedIn : AppLoadingEvent
    data object SignedOut : AppLoadingEvent
}
