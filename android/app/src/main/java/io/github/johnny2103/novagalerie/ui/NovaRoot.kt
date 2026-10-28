package io.github.johnny2103.novagalerie.ui

import android.net.Uri
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
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import io.github.johnny2103.novagalerie.R
import io.github.johnny2103.novagalerie.data.MediaSource
import io.github.johnny2103.novagalerie.data.index.CountryLookup
import io.github.johnny2103.novagalerie.ui.components.PermissionGate
import io.github.johnny2103.novagalerie.ui.screens.CollectionScreen
import io.github.johnny2103.novagalerie.ui.screens.HomeNavigation
import io.github.johnny2103.novagalerie.ui.screens.HomeScreen
import io.github.johnny2103.novagalerie.ui.screens.PersonScreen
import io.github.johnny2103.novagalerie.ui.screens.SearchScreen
import io.github.johnny2103.novagalerie.ui.screens.SettingsScreen
import io.github.johnny2103.novagalerie.ui.screens.StatsScreen
import io.github.johnny2103.novagalerie.ui.screens.TrashScreen
import io.github.johnny2103.novagalerie.ui.screens.ViewerScreen
import io.github.johnny2103.novagalerie.ui.theme.NovaTheme

private object Routes {
    const val HOME = "home"
    const val ALBUM = "album/{bucketId}"
    const val PERSON = "person/{personId}"
    const val COUNTRY = "country/{name}"
    const val TAG = "tag/{tagId}"
    const val FAVORITES = "favorites"
    const val VIDEOS = "videos"
    const val TRASH = "trash"
    const val STATS = "stats"
    const val SEARCH = "search"
    const val SETTINGS = "settings"
    const val VIEWER = "viewer/{index}"

    fun album(bucketId: Long) = "album/$bucketId"
    fun person(personId: Long) = "person/$personId"
    fun country(name: String) = "country/${Uri.encode(name)}"
    fun tag(tagId: Long) = "tag/$tagId"
    fun viewer(index: Int) = "viewer/$index"
}

@Composable
fun NovaRoot(viewModel: GalleryViewModel) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    NovaTheme(themeMode = settings.themeMode, dynamicColor = settings.dynamicColor) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            LockGate(enabled = settings.appLock) {
                NovaNavHost(viewModel)
            }
        }
    }
}

@Composable
private fun NovaNavHost(viewModel: GalleryViewModel) {
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
    val openPerson: (Long) -> Unit = { navController.navigate(Routes.person(it)) }
    val back: () -> Unit = { navController.popBackStack() }

    val homeNav = HomeNavigation(
        openViewer = openViewer,
        openAlbum = { navController.navigate(Routes.album(it)) },
        openPerson = openPerson,
        openCountry = { navController.navigate(Routes.country(it)) },
        openTag = { navController.navigate(Routes.tag(it)) },
        openCollection = { navController.navigate(it) },
        openSearch = { navController.navigate(Routes.SEARCH) },
        openSettings = { navController.navigate(Routes.SETTINGS) },
    )

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
                HomeScreen(viewModel = viewModel, partialAccess = partial, onRequestAccess = requestAgain, nav = homeNav)
            }
        }
        composable(Routes.ALBUM, arguments = listOf(navArgument("bucketId") { type = NavType.LongType })) { entry ->
            val bucketId = entry.arguments?.getLong("bucketId") ?: 0L
            val albums by viewModel.albums.collectAsStateWithLifecycle()
            val album = albums.firstOrNull { it.id == bucketId }
            CollectionScreen(
                viewModel = viewModel,
                source = MediaSource.AlbumSource(bucketId),
                title = album?.name ?: "",
                onBack = back,
                onOpenViewer = openViewer,
            )
        }
        composable(Routes.PERSON, arguments = listOf(navArgument("personId") { type = NavType.LongType })) { entry ->
            val personId = entry.arguments?.getLong("personId") ?: 0L
            PersonScreen(viewModel = viewModel, personId = personId, onBack = back, onOpenViewer = openViewer)
        }
        composable(Routes.COUNTRY, arguments = listOf(navArgument("name") { type = NavType.StringType })) { entry ->
            val name = entry.arguments?.getString("name") ?: ""
            CollectionScreen(
                viewModel = viewModel,
                source = MediaSource.Country(name),
                title = CountryLookup.displayName(name),
                onBack = back,
                onOpenViewer = openViewer,
            )
        }
        composable(Routes.TAG, arguments = listOf(navArgument("tagId") { type = NavType.LongType })) { entry ->
            val tagId = entry.arguments?.getLong("tagId") ?: 0L
            val tags by viewModel.tags.collectAsStateWithLifecycle()
            CollectionScreen(
                viewModel = viewModel,
                source = MediaSource.Tag(tagId),
                title = tags.firstOrNull { it.id == tagId }?.name ?: "",
                onBack = back,
                onOpenViewer = openViewer,
            )
        }
        composable(Routes.FAVORITES) {
            CollectionScreen(
                viewModel = viewModel,
                source = MediaSource.Favorites,
                title = stringResource(R.string.explore_favorites),
                onBack = back,
                onOpenViewer = openViewer,
                emptyText = stringResource(R.string.empty_favorites),
            )
        }
        composable(Routes.VIDEOS) {
            CollectionScreen(
                viewModel = viewModel,
                source = MediaSource.Videos,
                title = stringResource(R.string.explore_videos),
                onBack = back,
                onOpenViewer = openViewer,
            )
        }
        composable(Routes.TRASH) {
            TrashScreen(viewModel = viewModel, onBack = back, onOpenViewer = openViewer)
        }
        composable(Routes.STATS) {
            StatsScreen(viewModel = viewModel, onBack = back)
        }
        composable(Routes.SEARCH) {
            SearchScreen(viewModel = viewModel, onBack = back, onOpenViewer = openViewer)
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(viewModel = viewModel, onBack = back)
        }
        composable(Routes.VIEWER, arguments = listOf(navArgument("index") { type = NavType.IntType })) { entry ->
            val index = entry.arguments?.getInt("index") ?: 0
            ViewerScreen(viewModel = viewModel, startIndex = index, onBack = back, onOpenPerson = openPerson)
        }
    }
}
