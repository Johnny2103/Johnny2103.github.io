@file:OptIn(ExperimentalMaterial3Api::class)

package io.github.johnny2103.novagalerie.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.johnny2103.novagalerie.R
import io.github.johnny2103.novagalerie.data.GroupBy
import io.github.johnny2103.novagalerie.data.MediaSource
import io.github.johnny2103.novagalerie.ui.GalleryViewModel
import io.github.johnny2103.novagalerie.ui.components.MediaGrid
import io.github.johnny2103.novagalerie.ui.components.SelectionTopBar
import io.github.johnny2103.novagalerie.ui.components.TagDialog
import io.github.johnny2103.novagalerie.ui.components.rememberMediaRequestHandler
import io.github.johnny2103.novagalerie.util.MediaActions

/**
 * Generic grid screen for any [MediaSource] (album, person, country, tag, favourites, videos, trash)
 * with multi-selection, an optional header and extra top-bar actions.
 */
@Composable
fun CollectionScreen(
    viewModel: GalleryViewModel,
    source: MediaSource,
    title: String,
    onBack: () -> Unit,
    onOpenViewer: (MediaSource, Int) -> Unit,
    subtitle: String? = null,
    emptyText: String = stringResource(R.string.empty_album),
    trashMode: Boolean = false,
    groupBy: GroupBy? = null,
    header: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val context = LocalContext.current
    val itemsFlow = remember(source) { viewModel.observeItems(source) }
    val items by itemsFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val favorites by viewModel.favorites.collectAsStateWithLifecycle()
    val selection by viewModel.selection.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    var showTagDialog by remember { mutableStateOf(false) }

    val requests = rememberMediaRequestHandler {
        viewModel.clearSelection()
        if (trashMode) viewModel.loadTrash()
    }
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
                    onDelete = {
                        if (trashMode || !settings.useTrash) requests.delete(selected) else requests.trash(selected)
                    },
                    onTags = { showTagDialog = true },
                    trashMode = trashMode,
                    onRestore = { requests.restore(selected) },
                )
            } else {
                TopAppBar(
                    title = {
                        Column {
                            Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                subtitle ?: stringResource(R.string.items_count, items.size),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back)) }
                    },
                    actions = actions,
                )
            }
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            header?.invoke()
            MediaGrid(
                items = items,
                favorites = favorites,
                selection = selection,
                columns = settings.gridColumns,
                contentPadding = PaddingValues(bottom = 8.dp),
                onOpen = { index -> onOpenViewer(source, index) },
                onToggleSelect = viewModel::toggleSelected,
                groupBy = groupBy ?: settings.groupBy,
                onColumnsChange = { delta -> viewModel.setGridColumns(settings.gridColumns + delta) },
                emptyText = emptyText,
            )
        }
    }

    if (showTagDialog) {
        TagDialog(
            viewModel = viewModel,
            mediaIds = selection.toList(),
            onDismiss = { showTagDialog = false },
        )
    }
}
