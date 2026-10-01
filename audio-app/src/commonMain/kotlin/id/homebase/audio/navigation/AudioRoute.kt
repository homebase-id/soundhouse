package id.homebase.audio.navigation

import kotlinx.serialization.Serializable

sealed interface AudioRoute {
    @Serializable
    data object Loading : AudioRoute

    @Serializable
    data object Login : AudioRoute

    @Serializable
    data object Library : AudioRoute

    @Serializable
    data object Player : AudioRoute

    @Serializable
    data object Record : AudioRoute
}
