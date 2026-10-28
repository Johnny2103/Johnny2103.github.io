@file:OptIn(ExperimentalMaterial3Api::class)

package io.github.johnny2103.novagalerie.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.johnny2103.novagalerie.R
import io.github.johnny2103.novagalerie.data.MediaDetails
import io.github.johnny2103.novagalerie.data.MediaItem
import io.github.johnny2103.novagalerie.data.MetadataReader
import io.github.johnny2103.novagalerie.ui.GalleryViewModel
import io.github.johnny2103.novagalerie.ui.components.VideoPlayer
import io.github.johnny2103.novagalerie.ui.components.ZoomableImage
import io.github.johnny2103.novagalerie.ui.components.rememberDeleteHandler
import io.github.johnny2103.novagalerie.util.Format
import io.github.johnny2103.novagalerie.util.MediaActions
import io.github.johnny2103.novagalerie.util.findActivity

@Composable
fun ViewerScreen(
    viewModel: GalleryViewModel,
    startIndex: Int,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val items by viewModel.viewerItems.collectAsStateWithLifecycle()
    val favorites by viewModel.favorites.collectAsStateWithLifecycle()

    if (items.isEmpty()) {
        LaunchedEffect(Unit) { onBack() }
        return
    }

    val pagerState = rememberPagerState(initialPage = startIndex.coerceIn(0, items.lastIndex)) { items.size }
    LaunchedEffect(items.size) {
        if (pagerState.currentPage > items.lastIndex) pagerState.scrollToPage(items.lastIndex)
    }
    val current = items[pagerState.currentPage.coerceIn(0, items.lastIndex)]
    val isFavorite = current.id in favorites

    var showUi by rememberSaveable { mutableStateOf(true) }
    var showInfo by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    val deleteItems = rememberDeleteHandler { }

    // Immersive mode while the chrome is hidden.
    val view = LocalView.current
    DisposableEffect(showUi) {
        val window = view.context.findActivity()?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (showUi) controller?.show(WindowInsetsCompat.Type.systemBars()) else controller?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose { controller?.show(WindowInsetsCompat.Type.systemBars()) }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        HorizontalPager(
            state = pagerState,
            key = { items[it].id },
            beyondViewportPageCount = 1,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            val item = items[page]
            val isCurrent = pagerState.currentPage == page
            if (item.isVideo) {
                if (isCurrent) {
                    VideoPlayer(uri = item.uri, autoPlay = true)
                } else {
                    Box(Modifier.fillMaxSize())
                }
            } else {
                ZoomableImage(item = item, resetKey = isCurrent, onTap = { showUi = !showUi })
            }
        }

        AnimatedVisibility(
            visible = showUi,
            enter = fadeIn() + slideInVertically { -it },
            exit = fadeOut() + slideOutVertically { -it },
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            TopAppBar(
                title = {
                    Column {
                        Text(current.displayName.ifBlank { stringResource(R.string.external_item) }, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
                        Text(Format.dateTime(current.dateMs), style = MaterialTheme.typography.bodySmall)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back)) }
                },
                actions = {
                    if (!current.isExternal) {
                        IconButton(onClick = { viewModel.toggleFavorite(current.id) }) {
                            Icon(
                                if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                contentDescription = stringResource(if (isFavorite) R.string.action_unfavorite else R.string.action_favorite),
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black.copy(alpha = 0.55f),
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                    actionIconContentColor = Color.White,
                ),
            )
        }

        AnimatedVisibility(
            visible = showUi,
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it },
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.55f))
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ViewerAction(Icons.Filled.Share, R.string.action_share) { MediaActions.share(context, listOf(current)) }
                ViewerAction(Icons.Filled.Edit, R.string.action_edit) { MediaActions.edit(context, current) }
                ViewerAction(Icons.Filled.Info, R.string.action_info) { showInfo = true }
                if (!current.isExternal) {
                    ViewerAction(Icons.Filled.Delete, R.string.action_delete) { deleteItems(listOf(current)) }
                }
                Box {
                    ViewerAction(Icons.Filled.MoreVert, R.string.action_more) { showMenu = true }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        if (!current.isVideo) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_set_wallpaper)) },
                                onClick = {
                                    showMenu = false
                                    MediaActions.setWallpaper(context, current)
                                },
                            )
                        }
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_open_with)) },
                            onClick = {
                                showMenu = false
                                MediaActions.openWith(context, current)
                            },
                        )
                    }
                }
            }
        }
    }

    if (showInfo) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(onDismissRequest = { showInfo = false }, sheetState = sheetState) {
            DetailsSheet(item = current)
        }
    }
}

@Composable
private fun ViewerAction(icon: androidx.compose.ui.graphics.vector.ImageVector, labelRes: Int, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(icon, contentDescription = stringResource(labelRes), tint = Color.White)
    }
}

@Composable
private fun DetailsSheet(item: MediaItem) {
    val context = LocalContext.current
    val details by produceState<MediaDetails?>(initialValue = null, key1 = item) {
        value = MetadataReader.read(context, item)
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(start = 24.dp, end = 24.dp, bottom = 32.dp),
    ) {
        Text(stringResource(R.string.details_title), style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(12.dp))
        val d = details
        if (d == null) {
            Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            d.rows.forEach { row ->
                Column(modifier = Modifier.padding(vertical = 6.dp)) {
                    Text(stringResource(row.labelRes), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(row.value, style = MaterialTheme.typography.bodyMedium)
                }
            }
            if (d.latitude != null && d.longitude != null) {
                TextButton(onClick = { MediaActions.showOnMap(context, d.latitude, d.longitude) }) {
                    Text(stringResource(R.string.action_map))
                }
            }
        }
    }
}
