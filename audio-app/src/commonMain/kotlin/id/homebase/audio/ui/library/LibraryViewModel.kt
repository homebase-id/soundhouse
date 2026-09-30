package id.homebase.audio.ui.library

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import id.homebase.api.youauth.YouAuthFlowManager
import id.homebase.api.youauth.YouAuthState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class LibraryViewModel(
    private val youAuthFlowManager: YouAuthFlowManager,
) : ViewModel() {
    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            youAuthFlowManager.authState.collect { state ->
                _uiState.update {
                    it.copy(identity = (state as? YouAuthState.Authenticated)?.identity?.domainName)
                }
            }
        }
    }

    fun signOut() {
        viewModelScope.launch { youAuthFlowManager.logout() }
    }
}

@Immutable
data class LibraryUiState(
    val identity: String? = null,
)
