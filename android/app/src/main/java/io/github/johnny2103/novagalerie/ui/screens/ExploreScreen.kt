@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package io.github.johnny2103.novagalerie.ui.screens

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import io.github.johnny2103.novagalerie.R
import io.github.johnny2103.novagalerie.data.MediaItem
import io.github.johnny2103.novagalerie.data.index.CountryLookup
import io.github.johnny2103.novagalerie.ui.GalleryViewModel

/** "Library" tab: favourites, videos, places, tags, statistics and trash. */
@Composable
fun ExploreTab(viewModel: GalleryViewModel, nav: HomeNavigation) {
    val media by viewModel.media.collectAsStateWithLifecycle()
    val favoriteItems by viewModel.favoriteItems.collectAsStateWithLifecycle()
    val indexData by viewModel.indexData.collectAsStateWithLifecycle()
    val tags by viewModel.tags.collectAsStateWithLifecycle()

    val videos = remember(media) { media.filter { it.isVideo } }
    val byId = remember(media) { media.associateBy { it.id } }
    val countries = remember(indexData, media) {
        indexData.countries.entries
            .map { (name, ids) -> CountryCard(name, ids.size, ids.firstNotNullOfOrNull { byId[it] }) }
            .filter { it.cover != null }
            .sortedByDescending { it.count }
    }
    val tagCounts = remember(indexData) { indexData.tagMedia.mapValues { it.value.distinct().size } }

    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                QuickCard(
                    title = stringResource(R.string.explore_favorites),
                    count = favoriteItems.size,
                    cover = favoriteItems.firstOrNull(),
                    icon = Icons.Filled.Favorite,
                    modifier = Modifier.weight(1f),
                ) { nav.openCollection("favorites") }
                QuickCard(
                    title = stringResource(R.string.explore_videos),
                    count = videos.size,
                    cover = videos.firstOrNull(),
                    icon = Icons.Filled.VideoLibrary,
                    modifier = Modifier.weight(1f),
                ) { nav.openCollection("videos") }
            }
        }

        item { SectionTitle(stringResource(R.string.explore_places)) }
        if (countries.isEmpty()) {
            item { EmptyHint(stringResource(R.string.places_empty)) }
        } else {
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(countries, key = { it.name }) { c ->
                        Column(
                            modifier = Modifier
                                .width(140.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .clickable { nav.openCountry(c.name) },
                        ) {
                            Box(modifier = Modifier.size(140.dp).clip(RoundedCornerShape(16.dp))) {
                                AsyncImage(model = c.cover?.uri, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Brush.verticalGradient(0.5f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.7f))),
                                )
                                Column(modifier = Modifier.align(Alignment.BottomStart).padding(10.dp)) {
                                    Text(CountryLookup.displayName(c.name), color = Color.White, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(stringResource(R.string.items_count, c.count), color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            }
        }

        item { SectionTitle(stringResource(R.string.explore_tags)) }
        if (tags.isEmpty()) {
            item { EmptyHint(stringResource(R.string.tags_empty)) }
        } else {
            item {
                FlowRow(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    tags.forEach { tag ->
                        AssistChip(
                            onClick = { nav.openTag(tag.id) },
                            label = { Text("${tag.name} · ${tagCounts[tag.id] ?: 0}") },
                        )
                    }
                }
            }
        }

        item { Spacer(Modifier.height(12.dp)) }
        item {
            ListItem(
                headlineContent = { Text(stringResource(R.string.explore_stats)) },
                leadingContent = { Icon(Icons.Filled.BarChart, contentDescription = null) },
                trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) },
                modifier = Modifier.clickable { nav.openCollection("stats") },
            )
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.explore_trash)) },
                    supportingContent = { Text(stringResource(R.string.trash_info)) },
                    leadingContent = { Icon(Icons.Filled.Delete, contentDescription = null) },
                    trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) },
                    modifier = Modifier.clickable { nav.openCollection("trash") },
                )
            }
        }
    }
}

private data class CountryCard(val name: String, val count: Int, val cover: MediaItem?)

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
    )
}

@Composable
private fun EmptyHint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
    )
}

@Composable
private fun QuickCard(title: String, count: Int, cover: MediaItem?, icon: ImageVector, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .height(120.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick),
    ) {
        if (cover != null) {
            AsyncImage(model = cover.uri, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)))
        }
        val tint = if (cover != null) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
        Column(modifier = Modifier.align(Alignment.BottomStart).padding(12.dp)) {
            Icon(icon, contentDescription = null, tint = tint)
            Spacer(Modifier.height(4.dp))
            Text(title, style = MaterialTheme.typography.titleSmall, color = tint)
            Text(stringResource(R.string.items_count, count), style = MaterialTheme.typography.bodySmall, color = tint.copy(alpha = 0.85f))
        }
    }
}
