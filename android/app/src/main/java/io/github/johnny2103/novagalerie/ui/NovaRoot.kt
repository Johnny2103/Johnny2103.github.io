package io.github.johnny2103.novagalerie.ui

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import io.github.johnny2103.novagalerie.data.MediaSource
import io.github.johnny2103.novagalerie.ui.components.PermissionGate
import io.github.johnny2103.novagalerie.ui.screens.AlbumScreen
import io.github.johnny2103.novagalerie.ui.screens.HomeScreen
import io.github.johnny2103.novagalerie.ui.screens.SearchScreen
import io.github.johnny2103.novagalerie.ui.screens.SettingsScreen
import io.github.johnny2103.novagalerie.ui.screens.ViewerScreen
import io.github.johnny2103.novagalerie.ui.theme.NovaTheme

private object Routes {
    const val HOME = "home"
    const val ALBUM = "album/{bucketId}"
    const val SEARCH = "search"
    const val SETTINGS = "settings"
    const val VIEWER = "viewer/{index}"

    fun album(bucketId: Long) = "album/$bucketId"
    fun viewer(index: Int) = "viewer/$index"
}

@Composable
fun NovaRoot(viewModel: GalleryViewModel) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    NovaTheme(themeMode = settings.themeMode, dynamicColor = settings.dynamicColor) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            val navController = rememberNavController()

            // A file shared from another app opens straight in the viewer.
            val pendingExternal by viewModel.pendingExternal.collectAsStateWithLifecycle()
            LaunchedEffect(pendingExternal) {
                val external = pendingExternal ?: return@LaunchedEffect
                viewModel.openViewer(MediaSource.External(external))
                navController.navigate(Routes.viewer(0))
                viewModel.consumeExternal()
            }

            val openViewer: (MediaSource, Int) -> Unit = { source, index ->
                viewModel.openViewer(source)
                navController.navigate(Routes.viewer(index))
            }

            NavHost(
                navController = navController,
                startDestination = Routes.HOME,
                enterTransition = { fadeIn() + scaleIn(initialScale = 0.96f) },
                exitTransition = { fadeOut() },
                popEnterTransition = { fadeIn() },
                popExitTransition = { fadeOut() + scaleOut(targetScale = 0.96f) },
            ) {
                composable(Routes.HOME) {
                    PermissionGate(onPermissionChanged = viewModel::onPermissionResult) { partial, requestAgain ->
                        HomeScreen(
                            viewModel = viewModel,
                            partialAccess = partial,
                            onRequestAccess = requestAgain,
                            onOpenViewer = openViewer,
                            onOpenAlbum = { id -> navController.navigate(Routes.album(id)) },
                            onOpenSearch = { navController.navigate(Routes.SEARCH) },
                            onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                        )
                    }
                }
                composable(
                    Routes.ALBUM,
                    arguments = listOf(navArgument("bucketId") { type = NavType.LongType }),
                ) { entry ->
                    val bucketId = entry.arguments?.getLong("bucketId") ?: 0L
                    AlbumScreen(
                        viewModel = viewModel,
                        bucketId = bucketId,
                        onBack = { navController.popBackStack() },
                        onOpenViewer = openViewer,
                    )
                }
                composable(Routes.SEARCH) {
                    SearchScreen(
                        viewModel = viewModel,
                        onBack = { navController.popBackStack() },
                        onOpenViewer = openViewer,
                    )
                }
                composable(Routes.SETTINGS) {
                    SettingsScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
                }
                composable(
                    Routes.VIEWER,
                    arguments = listOf(navArgument("index") { type = NavType.IntType }),
                ) { entry ->
                    val index = entry.arguments?.getInt("index") ?: 0
                    ViewerScreen(
                        viewModel = viewModel,
                        startIndex = index,
                        onBack = { navController.popBackStack() },
                    )
                }
            }
        }
    }
}
