@file:OptIn(ExperimentalFoundationApi::class)

package io.github.johnny2103.novagalerie.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.johnny2103.novagalerie.R
import io.github.johnny2103.novagalerie.data.MediaItem
import io.github.johnny2103.novagalerie.util.Format
import java.time.LocalDate

private sealed interface GridEntry {
    data class Header(val key: String, val title: String) : GridEntry
    data class Item(val item: MediaItem, val index: Int) : GridEntry
}

/**
 * Photo grid grouped by day. Tapping opens the viewer, long-press starts multi-selection.
 */
@Composable
fun MediaGrid(
    items: List<MediaItem>,
    favorites: Set<Long>,
    selection: Set<Long>,
    columns: Int,
    contentPadding: PaddingValues,
    onOpen: (index: Int) -> Unit,
    onToggleSelect: (id: Long) -> Unit,
    modifier: Modifier = Modifier,
    groupByDate: Boolean = true,
    emptyText: String = stringResource(R.string.empty_timeline),
    state: LazyGridState = rememberLazyGridState(),
) {
    if (items.isEmpty()) {
        Box(modifier = modifier.fillMaxSize().padding(contentPadding), contentAlignment = Alignment.Center) {
            Text(
                emptyText,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(32.dp),
            )
        }
        return
    }

    val today = stringResource(R.string.date_today)
    val yesterday = stringResource(R.string.date_yesterday)
    val entries = remember(items, groupByDate, today, yesterday) { buildEntries(items, groupByDate, today, yesterday) }
    val selectionMode = selection.isNotEmpty()

    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        state = state,
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        items(
            count = entries.size,
            key = { i ->
                when (val e = entries[i]) {
                    is GridEntry.Header -> "h:${e.key}"
                    is GridEntry.Item -> e.item.id
                }
            },
            span = { i -> if (entries[i] is GridEntry.Header) GridItemSpan(maxLineSpan) else GridItemSpan(1) },
            contentType = { i -> if (entries[i] is GridEntry.Header) "header" else "item" },
        ) { i ->
            when (val e = entries[i]) {
                is GridEntry.Header -> Text(
                    e.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = 12.dp, top = 18.dp, bottom = 6.dp),
                )
                is GridEntry.Item -> {
                    val selected = e.item.id in selection
                    MediaThumbnail(
                        item = e.item,
                        isFavorite = e.item.id in favorites,
                        selectionMode = selectionMode,
                        selected = selected,
                        modifier = Modifier
                            .animateItem()
                            .combinedClickable(
                                onClick = { if (selectionMode) onToggleSelect(e.item.id) else onOpen(e.index) },
                                onLongClick = { onToggleSelect(e.item.id) },
                            ),
                    )
                }
            }
        }
    }
}

private fun buildEntries(items: List<MediaItem>, groupByDate: Boolean, today: String, yesterday: String): List<GridEntry> {
    if (!groupByDate) return items.mapIndexed { i, m -> GridEntry.Item(m, i) }
    val now = LocalDate.now()
    val result = ArrayList<GridEntry>(items.size + 32)
    var lastDate: LocalDate? = null
    items.forEachIndexed { i, m ->
        val date = Format.localDate(m.dateMs)
        if (date != lastDate) {
            val title = when (date) {
                now -> today
                now.minusDays(1) -> yesterday
                else -> Format.date(m.dateMs)
            }
            result += GridEntry.Header(date.toString(), title)
            lastDate = date
        }
        result += GridEntry.Item(m, i)
    }
    return result
}
