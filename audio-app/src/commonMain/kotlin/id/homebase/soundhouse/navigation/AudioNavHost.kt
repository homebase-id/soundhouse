package id.homebase.soundhouse.navigation

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import id.homebase.api.youauth.YouAuthFlowManager
import id.homebase.api.youauth.YouAuthState
import id.homebase.soundhouse.resources.AR
import id.homebase.soundhouse.resources.tab_home
import id.homebase.soundhouse.resources.tab_library
import id.homebase.soundhouse.ui.common.TopBarActions
import id.homebase.soundhouse.ui.home.HomeScreen
import id.homebase.soundhouse.ui.library.LibraryScreen
import id.homebase.soundhouse.ui.loading.AppLoadingScreen
import id.homebase.soundhouse.ui.player.MiniPlayer
import id.homebase.soundhouse.ui.player.PlayerScreen
import id.homebase.soundhouse.ui.settings.SettingsScreen
import id.homebase.soundhouse.ui.collections.CollectionScreen
import androidx.navigation.toRoute
import org.koin.core.parameter.parametersOf
import kotlin.uuid.Uuid
import id.homebase.soundhouse.ui.record.RecordScreen
import id.homebase.auth.login.LoginScreen
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

private enum class Tab(val route: AudioRoute, val label: StringResource, val selected: ImageVector, val unselected: ImageVector) {
    Home(AudioRoute.Home, AR.string.tab_home, Icons.Filled.Home, Icons.Outlined.Home),
    Library(AudioRoute.Library, AR.string.tab_library, Icons.Filled.LibraryMusic, Icons.Outlined.LibraryMusic),
}

private fun NavDestination?.isTab(tab: Tab) = this?.hasRoute(tab.route::class) == true

@Composable
fun AudioNavHost(navController: NavHostController = rememberNavController()) {
    val youAuthFlowManager: YouAuthFlowManager = koinInject()
    val authState by youAuthFlowManager.authState.collectAsStateWithLifecycle()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val destination = backStackEntry?.destination
    val scope = rememberCoroutineScope()

    LaunchedEffect(authState, destination) {
        val signedOut = authState is YouAuthState.Unauthenticated || authState is YouAuthState.Error
        val onPublicRoute = destination == null ||
            destination.hasRoute(AudioRoute.Login::class) ||
            destination.hasRoute(AudioRoute.Loading::class)
        if (signedOut && !onPublicRoute) {
            navController.navigate(AudioRoute.Login) { popUpTo(0) { inclusive = true } }
        }
    }

    val signedIn = authState is YouAuthState.Authenticated
    val onTab = Tab.entries.any { destination.isTab(it) }
    val showMiniPlayer = signedIn && (onTab || destination?.hasRoute(AudioRoute.Record::class) == true)
    val openPlayer = { navController.navigate(AudioRoute.Player) { launchSingleTop = true } }
    val openRecorder = { navController.navigate(AudioRoute.Record) { launchSingleTop = true } }
    val openCollection = { id: Uuid -> navController.navigate(AudioRoute.Collection(id.toString())) }
    val selectTab = { tab: Tab ->
        navController.navigate(tab.route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }
    val topBarActions: @Composable () -> Unit = {
        TopBarActions(
            identity = (authState as? YouAuthState.Authenticated)?.identity,
            onOpenRecorder = openRecorder,
            onOpenSettings = { navController.navigate(AudioRoute.Settings) { launchSingleTop = true } },
            onSignOut = { scope.launch { youAuthFlowManager.logout() } },
        )
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 600.dp
        val showBar = signedIn && onTab && !wide
        val showRail = signedIn && onTab && wide
        Row(Modifier.fillMaxSize()) {
            if (showRail) {
                NavigationRail {
                    Tab.entries.forEach { tab ->
                        val selected = destination.isTab(tab)
                        NavigationRailItem(
                            selected = selected,
                            onClick = { selectTab(tab) },
                            icon = { Icon(if (selected) tab.selected else tab.unselected, contentDescription = null) },
                            label = { Text(stringResource(tab.label)) },
                        )
                    }
                }
            }
            Column(Modifier.weight(1f)) {
                // The bottom chrome owns the navigation-bar inset; screens must not pad for it again.
                val hostModifier = if (showBar || showMiniPlayer) {
                    Modifier.weight(1f).consumeWindowInsets(WindowInsets.navigationBars)
                } else {
                    Modifier.weight(1f)
                }
                NavHost(navController = navController, startDestination = AudioRoute.Loading, modifier = hostModifier) {
                    composable<AudioRoute.Loading> {
                        AppLoadingScreen(
                            viewModel = koinViewModel(),
                            onSignedIn = {
                                navController.navigate(AudioRoute.Home) { popUpTo(AudioRoute.Loading) { inclusive = true } }
                            },
                            onSignedOut = {
                                navController.navigate(AudioRoute.Login) { popUpTo(AudioRoute.Loading) { inclusive = true } }
                            },
                        )
                    }
                    composable<AudioRoute.Login> {
                        LoginScreen(
                            viewModel = koinViewModel(),
                            onNavigateHome = {
                                navController.navigate(AudioRoute.Home) { popUpTo(AudioRoute.Login) { inclusive = true } }
                            },
                        )
                    }
                    composable<AudioRoute.Home> {
                        if (signedIn) {
                            HomeScreen(
                                viewModel = koinViewModel(),
                                onOpenPlayer = openPlayer,
                                onOpenRecorder = openRecorder,
                                onOpenCollection = openCollection,
                                actions = topBarActions,
                            )
                        }
                    }
                    composable<AudioRoute.Library> {
                        if (signedIn) {
                            LibraryScreen(
                                viewModel = koinViewModel(),
                                onOpenPlayer = openPlayer,
                                onOpenRecorder = openRecorder,
                                onOpenCollection = openCollection,
                                actions = topBarActions,
                            )
                        }
                    }
                    composable<AudioRoute.Record> {
                        RecordScreen(
                            viewModel = koinViewModel(),
                            onBack = { navController.popBackStack() },
                            onSaved = { navController.popBackStack() },
                        )
                    }
                    composable<AudioRoute.Player> {
                        PlayerScreen(viewModel = koinViewModel(), onBack = { navController.popBackStack() })
                    }
                    composable<AudioRoute.Collection> { entry ->
                        val id = Uuid.parse(entry.toRoute<AudioRoute.Collection>().id)
                        CollectionScreen(
                            viewModel = koinViewModel(key = id.toString()) { parametersOf(id) },
                            onBack = { navController.popBackStack() },
                            onOpenPlayer = openPlayer,
                        )
                    }
                    composable<AudioRoute.Settings> {
                        SettingsScreen(viewModel = koinViewModel(), onBack = { navController.popBackStack() })
                    }
                }
                if (showMiniPlayer) {
                    MiniPlayer(
                        viewModel = koinViewModel(),
                        onOpen = openPlayer,
                        modifier = if (showBar) Modifier else Modifier.navigationBarsPadding(),
                    )
                }
                if (showBar) {
                    NavigationBar {
                        Tab.entries.forEach { tab ->
                            val selected = destination.isTab(tab)
                            NavigationBarItem(
                                selected = selected,
                                onClick = { selectTab(tab) },
                                icon = { Icon(if (selected) tab.selected else tab.unselected, contentDescription = null) },
                                label = { Text(stringResource(tab.label)) },
                            )
                        }
                    }
                }
            }
        }
    }
}
