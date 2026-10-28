@file:OptIn(ExperimentalMaterial3Api::class)

package io.github.johnny2103.novagalerie.ui.screens

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.johnny2103.novagalerie.R
import io.github.johnny2103.novagalerie.data.MediaSource
import io.github.johnny2103.novagalerie.data.db.FaceEntity
import io.github.johnny2103.novagalerie.data.db.PersonEntity
import io.github.johnny2103.novagalerie.ui.GalleryViewModel
import io.github.johnny2103.novagalerie.ui.components.FaceAvatar

/** Grid of recognised people (home tab). */
@Composable
fun PeopleTab(viewModel: GalleryViewModel, onOpenPerson: (Long) -> Unit) {
    val people by viewModel.people.collectAsStateWithLifecycle()
    val indexData by viewModel.indexData.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val progress by viewModel.indexProgress.collectAsStateWithLifecycle()
    var showHidden by remember { mutableStateOf(false) }

    val visible = remember(people) { people.filter { !it.hidden } }
    val hidden = remember(people) { people.filter { it.hidden } }

    Column(modifier = Modifier.fillMaxSize()) {
        if (progress.running && progress.total > 0) IndexProgressBar(done = progress.done, total = progress.total)
        if (!settings.indexingEnabled) {
            Text(
                stringResource(R.string.people_disabled),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
        }
        if (visible.isEmpty() && hidden.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    stringResource(R.string.people_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(32.dp),
                )
            }
            return
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 104.dp),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(visible, key = { it.id }) { person ->
                PersonTile(person, photoCount = indexData.personMedia[person.id]?.distinct()?.size ?: 0, viewModel, onOpenPerson)
            }
            if (hidden.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.people_hidden) + " (${hidden.size})") },
                        modifier = Modifier.clickable { showHidden = !showHidden },
                    )
                }
                if (showHidden) {
                    items(hidden, key = { "hidden-${it.id}" }) { person ->
                        PersonTile(person, photoCount = indexData.personMedia[person.id]?.distinct()?.size ?: 0, viewModel, onOpenPerson)
                    }
                }
            }
        }
    }
}

@Composable
private fun PersonTile(person: PersonEntity, photoCount: Int, viewModel: GalleryViewModel, onOpenPerson: (Long) -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable { onOpenPerson(person.id) }
            .padding(6.dp),
    ) {
        FaceAvatar(file = viewModel.faceFile(person.coverFaceId), size = 88.dp)
        Spacer(Modifier.height(6.dp))
        Text(
            person.name ?: stringResource(R.string.people_unnamed),
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = if (person.name == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
        )
        Text(
            stringResource(R.string.photos_count, photoCount),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** All photos of one person with rename / merge / hide / cover actions. */
@Composable
fun PersonScreen(
    viewModel: GalleryViewModel,
    personId: Long,
    onBack: () -> Unit,
    onOpenViewer: (MediaSource, Int) -> Unit,
) {
    val people by viewModel.people.collectAsStateWithLifecycle()
    val person = people.firstOrNull { it.id == personId }
    if (person == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }
    var menu by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var merging by remember { mutableStateOf(false) }
    var choosingCover by remember { mutableStateOf(false) }
    val unnamed = stringResource(R.string.people_unnamed)

    CollectionScreen(
        viewModel = viewModel,
        source = MediaSource.Person(personId),
        title = person.name ?: unnamed,
        onBack = onBack,
        onOpenViewer = onOpenViewer,
        emptyText = stringResource(R.string.people_empty),
        header = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FaceAvatar(file = viewModel.faceFile(person.coverFaceId), size = 72.dp)
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        person.name ?: stringResource(R.string.person_name_hint),
                        style = MaterialTheme.typography.titleLarge,
                        color = if (person.name == null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.clickable { renaming = true },
                    )
                    Text(
                        stringResource(R.string.photos_count, person.faceCount),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        actions = {
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.action_more)) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.person_rename)) }, onClick = { menu = false; renaming = true })
                    DropdownMenuItem(text = { Text(stringResource(R.string.person_merge)) }, onClick = { menu = false; merging = true })
                    DropdownMenuItem(text = { Text(stringResource(R.string.person_cover)) }, onClick = { menu = false; choosingCover = true })
                    DropdownMenuItem(
                        text = { Text(stringResource(if (person.hidden) R.string.person_unhide else R.string.person_hide)) },
                        onClick = {
                            menu = false
                            viewModel.setPersonHidden(person.id, !person.hidden)
                        },
                    )
                }
            }
        },
    )

    if (renaming) {
        var name by remember { mutableStateOf(person.name ?: "") }
        AlertDialog(
            onDismissRequest = { renaming = false },
            title = { Text(stringResource(R.string.person_rename)) },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    placeholder = { Text(stringResource(R.string.person_name_hint)) },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.renamePerson(person.id, name)
                    renaming = false
                }) { Text(stringResource(R.string.action_save)) }
            },
            dismissButton = { TextButton(onClick = { renaming = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }

    if (merging) {
        val others = people.filter { it.id != person.id }
        AlertDialog(
            onDismissRequest = { merging = false },
            title = { Text(stringResource(R.string.person_merge_title)) },
            text = {
                androidx.compose.foundation.lazy.LazyColumn {
                    items(others.size, key = { others[it].id }) { i ->
                        val other = others[i]
                        ListItem(
                            leadingContent = { FaceAvatar(file = viewModel.faceFile(other.coverFaceId), size = 40.dp) },
                            headlineContent = { Text(other.name ?: unnamed) },
                            supportingContent = { Text(stringResource(R.string.photos_count, other.faceCount)) },
                            modifier = Modifier.clickable {
                                viewModel.mergePersons(person.id, other.id)
                                merging = false
                                onBack()
                            },
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { merging = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }

    if (choosingCover) {
        val faces by produceState<List<FaceEntity>>(initialValue = emptyList(), key1 = person.id) {
            value = viewModel.facesForPerson(person.id)
        }
        AlertDialog(
            onDismissRequest = { choosingCover = false },
            title = { Text(stringResource(R.string.person_cover)) },
            text = {
                LazyVerticalGrid(columns = GridCells.Adaptive(64.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(faces, key = { it.id }) { face ->
                        FaceAvatar(
                            file = viewModel.faceFile(face.id),
                            size = 64.dp,
                            modifier = Modifier.clickable {
                                viewModel.setPersonCover(person.id, face.id)
                                choosingCover = false
                            },
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { choosingCover = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}
