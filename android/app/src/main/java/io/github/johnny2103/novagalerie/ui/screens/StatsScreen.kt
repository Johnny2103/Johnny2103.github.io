@file:OptIn(ExperimentalMaterial3Api::class)

package io.github.johnny2103.novagalerie.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.johnny2103.novagalerie.R
import io.github.johnny2103.novagalerie.data.index.CountryLookup
import io.github.johnny2103.novagalerie.ui.GalleryViewModel
import io.github.johnny2103.novagalerie.ui.components.FaceAvatar
import io.github.johnny2103.novagalerie.util.Format

/** Library statistics: totals, storage, per-year distribution, top albums / countries / people. */
@Composable
fun StatsScreen(viewModel: GalleryViewModel, onBack: () -> Unit) {
    val media by viewModel.media.collectAsStateWithLifecycle()
    val albums by viewModel.albums.collectAsStateWithLifecycle()
    val people by viewModel.people.collectAsStateWithLifecycle()
    val indexData by viewModel.indexData.collectAsStateWithLifecycle()
    val analysed by viewModel.analysedCount.collectAsStateWithLifecycle()

    val photos = remember(media) { media.count { !it.isVideo } }
    val videos = media.size - photos
    val storage = remember(media) { media.sumOf { it.sizeBytes } }
    val byYear = remember(media) {
        media.groupingBy { Format.localDate(it.dateMs).year }.eachCount().toSortedMap(compareByDescending { it })
    }
    val maxYear = byYear.values.maxOrNull() ?: 1
    val topAlbums = remember(albums) { albums.sortedByDescending { it.count }.take(6) }
    val topCountries = remember(indexData) { indexData.countries.entries.sortedByDescending { it.value.size }.take(6) }
    val topPeople = remember(people, indexData) {
        people.filter { !it.hidden }.sortedByDescending { indexData.personMedia[it.id]?.distinct()?.size ?: 0 }.take(6)
    }
    val unnamed = stringResource(R.string.people_unnamed)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.stats_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back)) }
                },
            )
        },
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatTile(stringResource(R.string.stats_total), media.size.toString(), Modifier.weight(1f))
                    StatTile(stringResource(R.string.stats_storage), Format.fileSize(storage), Modifier.weight(1f))
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatTile(stringResource(R.string.stats_photos), photos.toString(), Modifier.weight(1f))
                    StatTile(stringResource(R.string.stats_videos), videos.toString(), Modifier.weight(1f))
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatTile(stringResource(R.string.stats_albums), albums.size.toString(), Modifier.weight(1f))
                    StatTile(stringResource(R.string.stats_people), people.count { !it.hidden }.toString(), Modifier.weight(1f))
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatTile(stringResource(R.string.stats_countries), indexData.countries.size.toString(), Modifier.weight(1f))
                    StatTile(stringResource(R.string.stats_analysed), "$analysed / ${media.size}", Modifier.weight(1f))
                }
            }

            if (byYear.isNotEmpty()) {
                item { SectionHeader(stringResource(R.string.stats_by_year)) }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        byYear.forEach { (year, count) ->
                            BarRow(label = year.toString(), value = count, max = maxYear)
                        }
                    }
                }
            }

            if (topAlbums.isNotEmpty()) {
                item { SectionHeader(stringResource(R.string.stats_top_albums)) }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        val max = topAlbums.first().count
                        topAlbums.forEach { BarRow(label = it.name, value = it.count, max = max) }
                    }
                }
            }

            if (topCountries.isNotEmpty()) {
                item { SectionHeader(stringResource(R.string.stats_top_countries)) }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        val max = topCountries.first().value.size
                        topCountries.forEach { BarRow(label = CountryLookup.displayName(it.key), value = it.value.size, max = max) }
                    }
                }
            }

            if (topPeople.isNotEmpty()) {
                item { SectionHeader(stringResource(R.string.stats_top_people)) }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        topPeople.forEach { p ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                FaceAvatar(file = viewModel.faceFile(p.coverFaceId), size = 36.dp)
                                Spacer(Modifier.width(12.dp))
                                Text(p.name ?: unnamed, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    stringResource(R.string.photos_count, indexData.personMedia[p.id]?.distinct()?.size ?: 0),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(16.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        Text(value, style = MaterialTheme.typography.headlineSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun BarRow(label: String, value: Int, max: Int) {
    Column {
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(value.toString(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction = (value.toFloat() / max.coerceAtLeast(1)).coerceIn(0.02f, 1f))
                    .height(8.dp)
                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp)),
            )
        }
    }
}
