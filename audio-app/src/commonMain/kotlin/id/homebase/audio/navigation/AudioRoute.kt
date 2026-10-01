package id.homebase.audio.navigation

import kotlinx.serialization.Serializable

sealed interface AudioRoute {
    @Serializable
    data object Loading : AudioRoute

    @Serializable
    data object Login : AudioRoute

    @Serializable
    data object Home : AudioRoute

    @Serializable
    data object Library : AudioRoute

    @Serializable
    data object Player : AudioRoute

    @Serializable
    data object Record : AudioRoute

    @Serializable
    data object Settings : AudioRoute

    @Serializable
    data class Collection(val id: String) : AudioRoute
}
