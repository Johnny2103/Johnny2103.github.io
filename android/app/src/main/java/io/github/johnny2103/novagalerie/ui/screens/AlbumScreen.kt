@file:OptIn(ExperimentalMaterial3Api::class)

package io.github.johnny2103.novagalerie.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.johnny2103.novagalerie.R
import io.github.johnny2103.novagalerie.data.MediaSource
import io.github.johnny2103.novagalerie.ui.GalleryViewModel
import io.github.johnny2103.novagalerie.ui.components.MediaGrid
import io.github.johnny2103.novagalerie.ui.components.SelectionTopBar
import io.github.johnny2103.novagalerie.ui.components.rememberDeleteHandler
import io.github.johnny2103.novagalerie.util.MediaActions

@Composable
fun AlbumScreen(
    viewModel: GalleryViewModel,
    bucketId: Long,
    onBack: () -> Unit,
    onOpenViewer: (MediaSource, Int) -> Unit,
) {
    val context = LocalContext.current
    val media by viewModel.media.collectAsStateWithLifecycle()
    val favorites by viewModel.favorites.collectAsStateWithLifecycle()
    val selection by viewModel.selection.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val items = remember(media, bucketId) { media.filter { it.bucketId == bucketId } }
    val title = items.firstOrNull()?.bucketName ?: ""

    val deleteItems = rememberDeleteHandler { viewModel.clearSelection() }
    BackHandler(enabled = selection.isNotEmpty()) { viewModel.clearSelection() }

    Scaffold(
        topBar = {
            if (selection.isNotEmpty()) {
                val selected = viewModel.selectedItems()
                SelectionTopBar(
                    count = selection.size,
                    allFavorite = selected.isNotEmpty() && selected.all { it.id in favorites },
                    onClose = viewModel::clearSelection,
                    onSelectAll = { viewModel.selectAll(items) },
                    onShare = { MediaActions.share(context, selected) },
                    onToggleFavorite = {
                        val all = selected.all { it.id in favorites }
                        viewModel.setFavorites(selected.map { it.id }, !all)
                    },
                    onDelete = { deleteItems(selected) },
                )
            } else {
                TopAppBar(
                    title = {
                        Column {
                            Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                stringResource(R.string.items_count, items.size),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back)) }
                    },
                )
            }
        },
    ) { padding ->
        MediaGrid(
            items = items,
            favorites = favorites,
            selection = selection,
            columns = settings.gridColumns,
            contentPadding = PaddingValues(bottom = 8.dp),
            onOpen = { index -> onOpenViewer(MediaSource.AlbumSource(bucketId), index) },
            onToggleSelect = viewModel::toggleSelected,
            modifier = Modifier.fillMaxSize().padding(padding),
            emptyText = stringResource(R.string.empty_album),
        )
    }
}
