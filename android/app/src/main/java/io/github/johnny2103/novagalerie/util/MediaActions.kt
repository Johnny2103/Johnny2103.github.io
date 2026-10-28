package io.github.johnny2103.novagalerie.util

import android.app.Activity
import android.app.RecoverableSecurityException
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import io.github.johnny2103.novagalerie.R
import io.github.johnny2103.novagalerie.data.MediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Result of a delete/trash/restore attempt: either done, or the system needs the user to confirm first. */
sealed interface DeleteResult {
    data class Done(val deleted: Int) : DeleteResult
    data class NeedsConfirmation(val intentSender: IntentSender, val pending: List<MediaItem>) : DeleteResult
}

enum class TrashOp { TRASH, RESTORE }

object MediaActions {

    fun share(context: Context, items: List<MediaItem>) {
        if (items.isEmpty()) return
        val uris = ArrayList(items.map { it.uri })
        val type = commonMimeType(items)
        val intent = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).apply {
                this.type = type
                putExtra(Intent.EXTRA_STREAM, uris.first())
            }
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                this.type = type
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            }
        }
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        launch(context, Intent.createChooser(intent, context.getString(R.string.action_share)))
    }

    fun openWith(context: Context, item: MediaItem) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(item.uri, item.mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        launch(context, Intent.createChooser(intent, context.getString(R.string.action_open_with)))
    }

    fun edit(context: Context, item: MediaItem) {
        val intent = Intent(Intent.ACTION_EDIT).apply {
            setDataAndType(item.uri, item.mimeType)
            // No write grant: MediaProvider rejects it for items this app does not own (SecurityException).
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        launch(context, Intent.createChooser(intent, context.getString(R.string.action_edit)))
    }

    fun setWallpaper(context: Context, item: MediaItem) {
        val intent = Intent(Intent.ACTION_ATTACH_DATA).apply {
            setDataAndType(item.uri, item.mimeType)
            putExtra("mimeType", item.mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        launch(context, Intent.createChooser(intent, context.getString(R.string.action_set_wallpaper)))
    }

    fun showOnMap(context: Context, latitude: Double, longitude: Double) {
        val geo = Uri.parse("geo:$latitude,$longitude?q=$latitude,$longitude")
        launch(context, Intent(Intent.ACTION_VIEW, geo))
    }

    /**
     * Deletes the given items. From Android 11 the system shows its own confirmation dialog
     * (via [DeleteResult.NeedsConfirmation]); on Android 10 a RecoverableSecurityException may
     * ask for the same; below that the delete goes through directly.
     */
    suspend fun delete(context: Context, items: List<MediaItem>): DeleteResult = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val local = items.filter { !it.isExternal }
        if (local.isEmpty()) return@withContext DeleteResult.Done(0)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // One system dialog per chunk keeps the Binder transaction small; the rest is retried after OK.
            val chunk = local.take(MAX_URIS_PER_REQUEST)
            val pi = MediaStore.createDeleteRequest(resolver, chunk.map { it.uri })
            return@withContext DeleteResult.NeedsConfirmation(pi.intentSender, local.drop(MAX_URIS_PER_REQUEST))
        }

        var deleted = 0
        for (item in local) {
            try {
                deleted += resolver.delete(item.uri, null, null)
            } catch (e: SecurityException) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && e is RecoverableSecurityException) {
                    return@withContext DeleteResult.NeedsConfirmation(
                        e.userAction.actionIntent.intentSender,
                        local.dropWhile { it.id != item.id },
                    )
                }
            }
        }
        DeleteResult.Done(deleted)
    }

    /** Moves items to or restores them from the system trash (Android 11+). */
    suspend fun trash(context: Context, items: List<MediaItem>, op: TrashOp): DeleteResult = withContext(Dispatchers.IO) {
        val local = items.filter { !it.isExternal }
        if (local.isEmpty()) return@withContext DeleteResult.Done(0)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val chunk = local.take(MAX_URIS_PER_REQUEST)
            val pi = MediaStore.createTrashRequest(context.contentResolver, chunk.map { it.uri }, op == TrashOp.TRASH)
            DeleteResult.NeedsConfirmation(pi.intentSender, local.drop(MAX_URIS_PER_REQUEST))
        } else {
            if (op == TrashOp.TRASH) delete(context, local) else DeleteResult.Done(0)
        }
    }

    fun isTrashSupported(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R

    private fun commonMimeType(items: List<MediaItem>): String {
        val types = items.map { it.mimeType }.toSet()
        if (types.size == 1) return types.first()
        val groups = types.map { it.substringBefore('/') }.toSet()
        return if (groups.size == 1) "${groups.first()}/*" else "*/*"
    }

    private fun launch(context: Context, intent: Intent) {
        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, R.string.no_app_toast, Toast.LENGTH_SHORT).show()
        } catch (e: SecurityException) {
            Toast.makeText(context, R.string.no_app_toast, Toast.LENGTH_SHORT).show()
        }
    }

    private const val MAX_URIS_PER_REQUEST = 500
}

fun Context.findActivity(): Activity? {
    var ctx: Context = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}
