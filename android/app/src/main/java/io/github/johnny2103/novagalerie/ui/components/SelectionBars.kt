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
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.RestoreFromTrash
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
import io.github.johnny2103.novagalerie.util.TrashOp
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
    onTags: (() -> Unit)? = null,
    trashMode: Boolean = false,
    onRestore: (() -> Unit)? = null,
) {
    TopAppBar(
        title = { Text(stringResource(R.string.selected_count, count)) },
        navigationIcon = {
            IconButton(onClick = onClose) { Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_close)) }
        },
        actions = {
            IconButton(onClick = onSelectAll) { Icon(Icons.Filled.SelectAll, contentDescription = stringResource(R.string.action_select_all)) }
            if (trashMode) {
                if (onRestore != null) {
                    IconButton(onClick = onRestore) { Icon(Icons.Filled.RestoreFromTrash, contentDescription = stringResource(R.string.action_restore)) }
                }
                IconButton(onClick = onDelete) { Icon(Icons.Filled.DeleteForever, contentDescription = stringResource(R.string.action_delete_forever)) }
            } else {
                IconButton(onClick = onToggleFavorite) {
                    Icon(
                        if (allFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = stringResource(if (allFavorite) R.string.action_unfavorite else R.string.action_favorite),
                    )
                }
                if (onTags != null) {
                    IconButton(onClick = onTags) { Icon(Icons.Filled.Label, contentDescription = stringResource(R.string.action_tags)) }
                }
                IconButton(onClick = onShare) { Icon(Icons.Filled.Share, contentDescription = stringResource(R.string.action_share)) }
                IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.action_delete)) }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            actionIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    )
}

/** Delete / trash / restore operations that route through the system confirmation when needed. */
class MediaRequestHandler internal constructor(
    val delete: (List<MediaItem>) -> Unit,
    val trash: (List<MediaItem>) -> Unit,
    val restore: (List<MediaItem>) -> Unit,
)

/**
 * Returns a handler for deleting, trashing and restoring media. Android 11+ shows its own
 * confirmation; on Android 10 a RecoverableSecurityException may ask for the same; older
 * versions get our own dialog. [onDone] runs after a successful operation.
 */
@Composable
fun rememberMediaRequestHandler(onDone: () -> Unit): MediaRequestHandler {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var confirmItems by remember { mutableStateOf<List<MediaItem>?>(null) }
    var retryItems by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var pendingToast by remember { mutableStateOf(R.string.deleted_toast) }

    fun finished() {
        Toast.makeText(context, pendingToast, Toast.LENGTH_SHORT).show()
        onDone()
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

    fun handle(result: DeleteResult) {
        when (result) {
            is DeleteResult.Done -> if (result.deleted > 0) finished()
            is DeleteResult.NeedsConfirmation -> {
                retryItems = result.pending
                launcher.launch(IntentSenderRequest.Builder(result.intentSender).build())
            }
        }
    }

    fun performDelete(items: List<MediaItem>) {
        pendingToast = R.string.deleted_toast
        scope.launch { handle(MediaActions.delete(context, items)) }
    }

    confirmItems?.let { items ->
        AlertDialog(
            onDismissRequest = { confirmItems = null },
            title = { Text(stringResource(R.string.delete_confirm_title, items.size)) },
            text = { Text(stringResource(R.string.delete_confirm_text)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmItems = null
                    performDelete(items)
                }) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmItems = null }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }

    fun requestDelete(items: List<MediaItem>) {
        if (items.isEmpty()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            performDelete(items)
        } else {
            confirmItems = items
        }
    }

    fun requestTrash(items: List<MediaItem>) {
        if (items.isEmpty()) return
        if (!MediaActions.isTrashSupported()) {
            requestDelete(items)
            return
        }
        pendingToast = R.string.trashed_toast
        scope.launch { handle(MediaActions.trash(context, items, TrashOp.TRASH)) }
    }

    fun requestRestore(items: List<MediaItem>) {
        if (items.isEmpty()) return
        pendingToast = R.string.restored_toast
        scope.launch { handle(MediaActions.trash(context, items, TrashOp.RESTORE)) }
    }

    return MediaRequestHandler(
        delete = { requestDelete(it) },
        trash = { requestTrash(it) },
        restore = { requestRestore(it) },
    )
}
