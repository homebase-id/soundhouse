package id.homebase.audio.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import id.homebase.api.youauth.YouAuthFlowManager
import id.homebase.api.youauth.YouAuthState
import id.homebase.audio.ui.library.LibraryScreen
import id.homebase.audio.ui.loading.AppLoadingScreen
import id.homebase.audio.ui.player.PlayerScreen
import id.homebase.auth.login.LoginScreen
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun AudioNavHost(navController: NavHostController = rememberNavController()) {
    val youAuthFlowManager: YouAuthFlowManager = koinInject()
    val authState by youAuthFlowManager.authState.collectAsStateWithLifecycle()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val destination = backStackEntry?.destination

    LaunchedEffect(authState, destination) {
        val signedOut = authState is YouAuthState.Unauthenticated || authState is YouAuthState.Error
        val onPublicRoute = destination == null ||
            destination.hasRoute(AudioRoute.Login::class) ||
            destination.hasRoute(AudioRoute.Loading::class)
        if (signedOut && !onPublicRoute) {
            navController.navigate(AudioRoute.Login) { popUpTo(0) { inclusive = true } }
        }
    }

    NavHost(navController = navController, startDestination = AudioRoute.Loading) {
        composable<AudioRoute.Loading> {
            AppLoadingScreen(
                viewModel = koinViewModel(),
                onSignedIn = {
                    navController.navigate(AudioRoute.Library) {
                        popUpTo(AudioRoute.Loading) { inclusive = true }
                    }
                },
                onSignedOut = {
                    navController.navigate(AudioRoute.Login) {
                        popUpTo(AudioRoute.Loading) { inclusive = true }
                    }
                },
            )
        }
        composable<AudioRoute.Login> {
            LoginScreen(
                viewModel = koinViewModel(),
                onNavigateHome = {
                    navController.navigate(AudioRoute.Library) {
                        popUpTo(AudioRoute.Login) { inclusive = true }
                    }
                },
            )
        }
        composable<AudioRoute.Library> {
            if (authState is YouAuthState.Authenticated) {
                LibraryScreen(
                    viewModel = koinViewModel(),
                    onOpenPlayer = { navController.navigate(AudioRoute.Player) { launchSingleTop = true } },
                )
            }
        }
        composable<AudioRoute.Player> {
            PlayerScreen(viewModel = koinViewModel(), onBack = { navController.popBackStack() })
        }
    }
}
