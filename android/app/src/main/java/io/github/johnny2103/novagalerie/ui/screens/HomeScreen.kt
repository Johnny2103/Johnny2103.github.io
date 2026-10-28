@file:OptIn(ExperimentalMaterial3Api::class)

package io.github.johnny2103.novagalerie.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.material.icons.outlined.Face
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
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
import io.github.johnny2103.novagalerie.ui.components.TagDialog
import io.github.johnny2103.novagalerie.ui.components.rememberMediaRequestHandler
import io.github.johnny2103.novagalerie.util.MediaActions

private enum class HomeTab(val labelRes: Int) {
    TIMELINE(R.string.nav_timeline),
    ALBUMS(R.string.nav_albums),
    PEOPLE(R.string.nav_people),
    EXPLORE(R.string.nav_explore),
}

/** Navigation callbacks the home tabs can trigger. */
class HomeNavigation(
    val openViewer: (MediaSource, Int) -> Unit,
    val openAlbum: (Long) -> Unit,
    val openPerson: (Long) -> Unit,
    val openCountry: (String) -> Unit,
    val openTag: (Long) -> Unit,
    val openCollection: (String) -> Unit,
    val openSearch: () -> Unit,
    val openSettings: () -> Unit,
)

@Composable
fun HomeScreen(
    viewModel: GalleryViewModel,
    partialAccess: Boolean,
    onRequestAccess: () -> Unit,
    nav: HomeNavigation,
) {
    val context = LocalContext.current
    var tabIndex by rememberSaveable { mutableIntStateOf(0) }
    val tab = HomeTab.entries[tabIndex]

    val timeline by viewModel.timeline.collectAsStateWithLifecycle()
    val albums by viewModel.albums.collectAsStateWithLifecycle()
    val favorites by viewModel.favorites.collectAsStateWithLifecycle()
    val selection by viewModel.selection.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val loading by viewModel.loading.collectAsStateWithLifecycle()
    val progress by viewModel.indexProgress.collectAsStateWithLifecycle()
    var showTagDialog by remember { mutableStateOf(false) }

    val requests = rememberMediaRequestHandler { viewModel.clearSelection() }
    BackHandler(enabled = selection.isNotEmpty()) { viewModel.clearSelection() }

    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            if (selection.isNotEmpty()) {
                val selected = viewModel.selectedItems()
                SelectionTopBar(
                    count = selection.size,
                    allFavorite = selected.isNotEmpty() && selected.all { it.id in favorites },
                    onClose = viewModel::clearSelection,
                    onSelectAll = { viewModel.selectAll(timeline) },
                    onShare = { MediaActions.share(context, selected) },
                    onToggleFavorite = {
                        val all = selected.all { it.id in favorites }
                        viewModel.setFavorites(selected.map { it.id }, !all)
                    },
                    onDelete = { if (settings.useTrash) requests.trash(selected) else requests.delete(selected) },
                    onTags = { showTagDialog = true },
                )
            } else {
                TopAppBar(
                    title = {
                        Text(
                            if (tab == HomeTab.TIMELINE) stringResource(R.string.app_name) else stringResource(tab.labelRes),
                            fontWeight = FontWeight.Bold,
                        )
                    },
                    actions = {
                        IconButton(onClick = nav.openSearch) { Icon(Icons.Filled.Search, contentDescription = stringResource(R.string.action_search)) }
                        IconButton(onClick = nav.openSettings) { Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.action_settings)) }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                    scrollBehavior = scrollBehavior,
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
                                    HomeTab.PEOPLE -> Icons.Outlined.Face
                                    HomeTab.EXPLORE -> Icons.Outlined.Collections
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
            AnimatedVisibility(visible = progress.running && progress.total > 0 && tab != HomeTab.PEOPLE) {
                IndexProgressBar(done = progress.done, total = progress.total)
            }
            when (tab) {
                HomeTab.TIMELINE -> MediaGrid(
                    items = timeline,
                    favorites = favorites,
                    selection = selection,
                    columns = settings.gridColumns,
                    contentPadding = PaddingValues(bottom = 8.dp),
                    onOpen = { index -> nav.openViewer(MediaSource.All, index) },
                    onToggleSelect = viewModel::toggleSelected,
                    groupBy = settings.groupBy,
                    onColumnsChange = { delta -> viewModel.setGridColumns(settings.gridColumns + delta) },
                )
                HomeTab.ALBUMS -> AlbumsGrid(albums = albums, onOpenAlbum = nav.openAlbum)
                HomeTab.PEOPLE -> PeopleTab(viewModel = viewModel, onOpenPerson = nav.openPerson)
                HomeTab.EXPLORE -> ExploreTab(viewModel = viewModel, nav = nav)
            }
        }
    }

    if (showTagDialog) {
        TagDialog(viewModel = viewModel, mediaIds = selection.toList(), onDismiss = { showTagDialog = false })
    }
}

@Composable
fun IndexProgressBar(done: Int, total: Int) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        Text(
            stringResource(R.string.index_progress, done, total),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { if (total == 0) 0f else done.toFloat() / total },
            modifier = Modifier.fillMaxWidth(),
        )
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
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(18.dp))
                    .clickable { onOpenAlbum(album.id) },
            ) {
                AsyncImage(
                    model = album.cover.uri,
                    contentDescription = album.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                0f to Color.Transparent,
                                0.55f to Color.Transparent,
                                1f to Color.Black.copy(alpha = 0.75f),
                            ),
                        ),
                )
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(12.dp),
                ) {
                    Text(album.name, style = MaterialTheme.typography.titleMedium, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(stringResource(R.string.items_count, album.count), style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.85f))
                }
                if (album.cover.isVideo) {
                    Icon(
                        Icons.Filled.VideoLibrary,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.align(Alignment.TopEnd).padding(10.dp),
                    )
                }
            }
        }
    }
}

@Suppress("unused")
private val favoriteIcon = Icons.Filled.Favorite
