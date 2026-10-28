@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.RestoreFromTrash
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AssistChip
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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
import io.github.johnny2103.novagalerie.data.MediaSource
import io.github.johnny2103.novagalerie.data.MetadataReader
import io.github.johnny2103.novagalerie.data.db.PersonEntity
import io.github.johnny2103.novagalerie.data.index.CountryLookup
import io.github.johnny2103.novagalerie.ui.GalleryViewModel
import io.github.johnny2103.novagalerie.ui.components.FaceAvatar
import io.github.johnny2103.novagalerie.ui.components.TagDialog
import io.github.johnny2103.novagalerie.ui.components.VideoPlayer
import io.github.johnny2103.novagalerie.ui.components.ZoomableImage
import io.github.johnny2103.novagalerie.ui.components.rememberMediaRequestHandler
import io.github.johnny2103.novagalerie.util.Format
import io.github.johnny2103.novagalerie.util.MediaActions
import io.github.johnny2103.novagalerie.util.findActivity
import kotlinx.coroutines.delay

@Composable
fun ViewerScreen(
    viewModel: GalleryViewModel,
    startIndex: Int,
    onBack: () -> Unit,
    onOpenPerson: (Long) -> Unit,
) {
    val context = LocalContext.current
    val items by viewModel.viewerItems.collectAsStateWithLifecycle()
    val favorites by viewModel.favorites.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val source by viewModel.viewerSource.collectAsStateWithLifecycle()
    val isTrash = source is MediaSource.Trash

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
    var showTags by remember { mutableStateOf(false) }
    var slideshow by remember { mutableStateOf(false) }
    val requests = rememberMediaRequestHandler { if (isTrash) viewModel.loadTrash() }

    // Immersive mode while the chrome is hidden.
    val view = LocalView.current
    DisposableEffect(showUi) {
        val window = view.context.findActivity()?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (showUi) controller?.show(WindowInsetsCompat.Type.systemBars()) else controller?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose { controller?.show(WindowInsetsCompat.Type.systemBars()) }
    }

    // Slideshow: advance automatically, keep the screen on, stop at the end or on tap.
    DisposableEffect(slideshow) {
        view.keepScreenOn = slideshow
        onDispose { view.keepScreenOn = false }
    }
    LaunchedEffect(slideshow, settings.slideshowSeconds) {
        if (!slideshow) return@LaunchedEffect
        showUi = false
        while (true) {
            delay(settings.slideshowSeconds * 1000L)
            val next = pagerState.currentPage + 1
            if (next > pagerState.pageCount - 1) {
                slideshow = false
                break
            }
            pagerState.animateScrollToPage(next)
        }
    }

    val onTap: () -> Unit = {
        if (slideshow) {
            slideshow = false
        } else {
            showUi = !showUi
        }
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
                if (isCurrent) VideoPlayer(uri = item.uri, autoPlay = true) else Box(Modifier.fillMaxSize())
            } else {
                ZoomableImage(item = item, resetKey = isCurrent, onTap = onTap)
            }
        }

        AnimatedVisibility(
            visible = showUi,
            enter = fadeIn() + slideInVertically { -it },
            exit = fadeOut() + slideOutVertically { -it },
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            Box(modifier = Modifier.background(Brush.verticalGradient(0f to Color.Black.copy(alpha = 0.7f), 1f to Color.Transparent))) {
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
                        if (!current.isExternal && !isTrash) {
                            IconButton(onClick = { viewModel.toggleFavorite(current.id) }) {
                                Icon(
                                    if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                    contentDescription = stringResource(if (isFavorite) R.string.action_unfavorite else R.string.action_favorite),
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        titleContentColor = Color.White,
                        navigationIconContentColor = Color.White,
                        actionIconContentColor = Color.White,
                    ),
                )
            }
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
                    .background(Brush.verticalGradient(0f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.75f)))
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (isTrash) {
                    ViewerAction(Icons.Filled.RestoreFromTrash, R.string.action_restore) { requests.restore(listOf(current)) }
                    ViewerAction(Icons.Filled.Info, R.string.action_info) { showInfo = true }
                    ViewerAction(Icons.Filled.DeleteForever, R.string.action_delete_forever) { requests.delete(listOf(current)) }
                } else {
                    ViewerAction(Icons.Filled.Share, R.string.action_share) { MediaActions.share(context, listOf(current)) }
                    ViewerAction(Icons.Filled.Edit, R.string.action_edit) { MediaActions.edit(context, current) }
                    ViewerAction(Icons.Filled.Info, R.string.action_info) { showInfo = true }
                    if (!current.isExternal) {
                        ViewerAction(Icons.Filled.Delete, if (settings.useTrash && MediaActions.isTrashSupported()) R.string.action_trash else R.string.action_delete) {
                            if (settings.useTrash) requests.trash(listOf(current)) else requests.delete(listOf(current))
                        }
                    }
                    Box {
                        ViewerAction(Icons.Filled.MoreVert, R.string.action_more) { showMenu = true }
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            if (items.size > 1) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.action_slideshow)) },
                                    onClick = {
                                        showMenu = false
                                        slideshow = true
                                    },
                                )
                            }
                            if (!current.isExternal) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.action_tags)) },
                                    onClick = {
                                        showMenu = false
                                        showTags = true
                                    },
                                )
                            }
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
    }

    if (showInfo) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(onDismissRequest = { showInfo = false }, sheetState = sheetState) {
            DetailsSheet(
                viewModel = viewModel,
                item = current,
                onOpenPerson = {
                    showInfo = false
                    onOpenPerson(it)
                },
                onEditTags = { showTags = true },
            )
        }
    }

    if (showTags) {
        TagDialog(viewModel = viewModel, mediaIds = listOf(current.id), onDismiss = { showTags = false })
    }
}

@Composable
private fun ViewerAction(icon: ImageVector, labelRes: Int, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(icon, contentDescription = stringResource(labelRes), tint = Color.White)
    }
}

@Composable
private fun DetailsSheet(
    viewModel: GalleryViewModel,
    item: MediaItem,
    onOpenPerson: (Long) -> Unit,
    onEditTags: () -> Unit,
) {
    val context = LocalContext.current
    val people by viewModel.people.collectAsStateWithLifecycle()
    val tags by viewModel.tags.collectAsStateWithLifecycle()
    val mediaTagIds by viewModel.mediaTagIds.collectAsStateWithLifecycle()
    val unnamed = stringResource(R.string.people_unnamed)

    val details by produceState<MediaDetails?>(initialValue = null, key1 = item) {
        value = MetadataReader.read(context, item)
    }
    val personsOnPhoto by produceState<List<PersonEntity>>(initialValue = emptyList(), key1 = item, key2 = people) {
        if (item.isExternal) return@produceState
        val ids = viewModel.facesForMedia(item.id).mapNotNull { it.personId }.toSet()
        value = people.filter { it.id in ids && !it.hidden }
    }
    val country by produceState<String?>(initialValue = null, key1 = item) {
        if (!item.isExternal) value = viewModel.metaFor(item.id)?.country
    }
    val itemTags = remember(tags, mediaTagIds, item) {
        val ids = mediaTagIds[item.id] ?: emptySet()
        tags.filter { it.id in ids }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(start = 24.dp, end = 24.dp, bottom = 32.dp),
    ) {
        Text(stringResource(R.string.details_title), style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(12.dp))

        if (personsOnPhoto.isNotEmpty()) {
            Label(stringResource(R.string.detail_people))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                personsOnPhoto.forEach { p ->
                    AssistChip(
                        onClick = { onOpenPerson(p.id) },
                        label = { Text(p.name ?: unnamed) },
                        leadingIcon = { FaceAvatar(file = viewModel.faceFile(p.coverFaceId), size = 24.dp) },
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        if (!item.isExternal) {
            Label(stringResource(R.string.detail_tags))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                itemTags.forEach { t -> AssistChip(onClick = onEditTags, label = { Text(t.name) }) }
                TextButton(onClick = onEditTags) { Text(stringResource(R.string.tag_add)) }
            }
            Spacer(Modifier.height(8.dp))
        }

        val d = details
        if (d == null) {
            Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            d.rows.forEach { row ->
                Column(modifier = Modifier.padding(vertical = 6.dp)) {
                    Label(stringResource(row.labelRes))
                    Text(row.value, style = MaterialTheme.typography.bodyMedium)
                }
            }
            country?.let {
                Column(modifier = Modifier.padding(vertical = 6.dp)) {
                    Label(stringResource(R.string.detail_country))
                    Text(CountryLookup.displayName(it), style = MaterialTheme.typography.bodyMedium)
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

@Composable
private fun Label(text: String) {
    Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
