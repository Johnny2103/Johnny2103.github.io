@file:OptIn(ExperimentalMaterial3Api::class)

package io.github.johnny2103.novagalerie.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.PhotoAlbum
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Photo
import androidx.compose.material.icons.outlined.PhotoAlbum
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import io.github.johnny2103.novagalerie.R
import io.github.johnny2103.novagalerie.data.Album
import io.github.johnny2103.novagalerie.data.MediaSource
import io.github.johnny2103.novagalerie.ui.GalleryViewModel
import io.github.johnny2103.novagalerie.ui.components.MediaGrid
import io.github.johnny2103.novagalerie.ui.components.SelectionTopBar
import io.github.johnny2103.novagalerie.ui.components.rememberDeleteHandler
import io.github.johnny2103.novagalerie.util.MediaActions
import androidx.compose.ui.platform.LocalContext

private enum class HomeTab(val labelRes: Int) {
    TIMELINE(R.string.nav_timeline),
    ALBUMS(R.string.nav_albums),
    FAVORITES(R.string.nav_favorites),
}

@Composable
fun HomeScreen(
    viewModel: GalleryViewModel,
    partialAccess: Boolean,
    onRequestAccess: () -> Unit,
    onOpenViewer: (MediaSource, Int) -> Unit,
    onOpenAlbum: (Long) -> Unit,
    onOpenSearch: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val context = LocalContext.current
    var tabIndex by rememberSaveable { mutableIntStateOf(0) }
    val tab = HomeTab.entries[tabIndex]

    val timeline by viewModel.timeline.collectAsStateWithLifecycle()
    val albums by viewModel.albums.collectAsStateWithLifecycle()
    val favoriteItems by viewModel.favoriteItems.collectAsStateWithLifecycle()
    val favorites by viewModel.favorites.collectAsStateWithLifecycle()
    val selection by viewModel.selection.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val loading by viewModel.loading.collectAsStateWithLifecycle()

    val deleteItems = rememberDeleteHandler { viewModel.clearSelection() }
    BackHandler(enabled = selection.isNotEmpty()) { viewModel.clearSelection() }

    val visibleItems = when (tab) {
        HomeTab.TIMELINE -> timeline
        HomeTab.FAVORITES -> favoriteItems
        HomeTab.ALBUMS -> emptyList()
    }

    Scaffold(
        topBar = {
            if (selection.isNotEmpty()) {
                val selected = viewModel.selectedItems()
                SelectionTopBar(
                    count = selection.size,
                    allFavorite = selected.isNotEmpty() && selected.all { it.id in favorites },
                    onClose = viewModel::clearSelection,
                    onSelectAll = { viewModel.selectAll(visibleItems) },
                    onShare = { MediaActions.share(context, selected) },
                    onToggleFavorite = {
                        val all = selected.all { it.id in favorites }
                        viewModel.setFavorites(selected.map { it.id }, !all)
                    },
                    onDelete = { deleteItems(selected) },
                )
            } else {
                TopAppBar(
                    title = { Text(if (tab == HomeTab.TIMELINE) stringResource(R.string.app_name) else stringResource(tab.labelRes)) },
                    actions = {
                        IconButton(onClick = onOpenSearch) { Icon(Icons.Filled.Search, contentDescription = stringResource(R.string.action_search)) }
                        IconButton(onClick = onOpenSettings) { Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.action_settings)) }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                )
            }
        },
        bottomBar = {
            NavigationBar {
                HomeTab.entries.forEachIndexed { index, t ->
                    val selectedTab = index == tabIndex
                    NavigationBarItem(
                        selected = selectedTab,
                        onClick = {
                            tabIndex = index
                            viewModel.clearSelection()
                        },
                        icon = {
                            Icon(
                                when (t) {
                                    HomeTab.TIMELINE -> if (selectedTab) Icons.Filled.Photo else Icons.Outlined.Photo
                                    HomeTab.ALBUMS -> if (selectedTab) Icons.Filled.PhotoAlbum else Icons.Outlined.PhotoAlbum
                                    HomeTab.FAVORITES -> if (selectedTab) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder
                                },
                                contentDescription = null,
                            )
                        },
                        label = { Text(stringResource(t.labelRes)) },
                    )
                }
            }
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (loading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            if (partialAccess) PartialAccessBanner(onRequestAccess)
            when (tab) {
                HomeTab.TIMELINE -> MediaGrid(
                    items = timeline,
                    favorites = favorites,
                    selection = selection,
                    columns = settings.gridColumns,
                    contentPadding = PaddingValues(bottom = 8.dp),
                    onOpen = { index -> onOpenViewer(MediaSource.All, index) },
                    onToggleSelect = viewModel::toggleSelected,
                )
                HomeTab.FAVORITES -> MediaGrid(
                    items = favoriteItems,
                    favorites = favorites,
                    selection = selection,
                    columns = settings.gridColumns,
                    contentPadding = PaddingValues(bottom = 8.dp),
                    onOpen = { index -> onOpenViewer(MediaSource.Favorites, index) },
                    onToggleSelect = viewModel::toggleSelected,
                    emptyText = stringResource(R.string.empty_favorites),
                )
                HomeTab.ALBUMS -> AlbumsGrid(albums = albums, onOpenAlbum = onOpenAlbum)
            }
        }
    }
}

@Composable
private fun PartialAccessBanner(onRequestAccess: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Row(modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.permission_partial),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = onRequestAccess) { Text(stringResource(R.string.action_reselect)) }
        }
    }
}

@Composable
private fun AlbumsGrid(albums: List<Album>, onOpenAlbum: (Long) -> Unit) {
    if (albums.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.empty_timeline), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 160.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(albums, key = { it.id }) { album ->
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { onOpenAlbum(album.id) },
            ) {
                AsyncImage(
                    model = album.cover.uri,
                    contentDescription = album.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(16.dp)),
                )
                Spacer(Modifier.height(6.dp))
                Text(album.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 4.dp))
                Text(
                    stringResource(R.string.items_count, album.count),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                )
            }
        }
    }
}
