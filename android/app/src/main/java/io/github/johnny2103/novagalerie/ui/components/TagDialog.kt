@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package io.github.johnny2103.novagalerie.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.johnny2103.novagalerie.R
import io.github.johnny2103.novagalerie.ui.GalleryViewModel

/** Assign / remove local tags for one or more media items. */
@Composable
fun TagDialog(viewModel: GalleryViewModel, mediaIds: List<Long>, onDismiss: () -> Unit) {
    val tags by viewModel.tags.collectAsStateWithLifecycle()
    val mediaTagIds by viewModel.mediaTagIds.collectAsStateWithLifecycle()
    var newTag by remember { mutableStateOf("") }

    fun addNew() {
        if (newTag.isNotBlank()) {
            viewModel.addTag(newTag, mediaIds)
            newTag = ""
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.tags_dialog_title)) },
        text = {
            Column {
                if (tags.isEmpty()) {
                    Text(stringResource(R.string.tags_empty), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        tags.forEach { tag ->
                            // Selected when every chosen item carries the tag.
                            val assigned = mediaIds.isNotEmpty() && mediaIds.all { mediaTagIds[it]?.contains(tag.id) == true }
                            FilterChip(
                                selected = assigned,
                                onClick = {
                                    if (assigned) viewModel.removeTag(tag.id, mediaIds) else viewModel.addTag(tag.name, mediaIds)
                                },
                                label = { Text(tag.name) },
                                leadingIcon = if (assigned) {
                                    { Icon(Icons.Filled.Check, contentDescription = null) }
                                } else null,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = newTag,
                        onValueChange = { newTag = it },
                        placeholder = { Text(stringResource(R.string.tag_new_hint)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    IconButton(onClick = { addNew() }, enabled = newTag.isNotBlank()) {
                        Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.tag_add))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
        },
    )
}
