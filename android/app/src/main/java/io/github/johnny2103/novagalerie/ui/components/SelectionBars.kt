@file:OptIn(ExperimentalMaterial3Api::class)

package io.github.johnny2103.novagalerie.ui.components

import android.app.Activity
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import io.github.johnny2103.novagalerie.R
import io.github.johnny2103.novagalerie.data.MediaItem
import io.github.johnny2103.novagalerie.util.DeleteResult
import io.github.johnny2103.novagalerie.util.MediaActions
import kotlinx.coroutines.launch

/** Top bar shown while items are selected in a grid. */
@Composable
fun SelectionTopBar(
    count: Int,
    allFavorite: Boolean,
    onClose: () -> Unit,
    onSelectAll: () -> Unit,
    onShare: () -> Unit,
    onToggleFavorite: () -> Unit,
    onDelete: () -> Unit,
) {
    TopAppBar(
        title = { Text(stringResource(R.string.selected_count, count)) },
        navigationIcon = {
            IconButton(onClick = onClose) { Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_close)) }
        },
        actions = {
            IconButton(onClick = onSelectAll) { Icon(Icons.Filled.SelectAll, contentDescription = stringResource(R.string.action_select_all)) }
            IconButton(onClick = onToggleFavorite) {
                Icon(
                    if (allFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    contentDescription = stringResource(if (allFavorite) R.string.action_unfavorite else R.string.action_favorite),
                )
            }
            IconButton(onClick = onShare) { Icon(Icons.Filled.Share, contentDescription = stringResource(R.string.action_share)) }
            IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.action_delete)) }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            actionIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    )
}

/**
 * Returns a function that deletes media items, going through the system confirmation
 * (Android 11+ / scoped storage) when required. [onDeleted] runs after a successful delete.
 */
@Composable
fun rememberDeleteHandler(onDeleted: () -> Unit): (List<MediaItem>) -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var confirmItems by remember { mutableStateOf<List<MediaItem>?>(null) }
    var retryItems by remember { mutableStateOf<List<MediaItem>>(emptyList()) }

    fun finished() {
        Toast.makeText(context, R.string.deleted_toast, Toast.LENGTH_SHORT).show()
        onDeleted()
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val retry = retryItems
            retryItems = emptyList()
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R && retry.isNotEmpty()) {
                scope.launch {
                    MediaActions.delete(context, retry)
                    finished()
                }
            } else {
                finished()
            }
        } else {
            retryItems = emptyList()
        }
    }

    fun perform(items: List<MediaItem>) {
        scope.launch {
            when (val result = MediaActions.delete(context, items)) {
                is DeleteResult.Done -> if (result.deleted > 0) finished()
                is DeleteResult.NeedsConfirmation -> {
                    retryItems = result.pending
                    launcher.launch(IntentSenderRequest.Builder(result.intentSender).build())
                }
            }
        }
    }

    confirmItems?.let { items ->
        AlertDialog(
            onDismissRequest = { confirmItems = null },
            title = { Text(stringResource(R.string.delete_confirm_title, items.size)) },
            text = { Text(stringResource(R.string.delete_confirm_text)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmItems = null
                    perform(items)
                }) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmItems = null }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }

    return remember {
        { items: List<MediaItem> ->
            if (items.isNotEmpty()) {
                // Android 11+ shows its own confirmation dialog; older versions get ours.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) perform(items) else confirmItems = items
            }
        }
    }
}
