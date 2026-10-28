package io.github.johnny2103.novagalerie.data

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.database.ContentObserver
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.withContext

/**
 * Reads photos and videos from the MediaStore in a single query over the "files" table,
 * so images and videos come back interleaved and sorted once in memory.
 */
class MediaRepository(context: Context) {

    private val resolver: ContentResolver = context.applicationContext.contentResolver

    private val filesUri: Uri = MediaStore.Files.getContentUri("external")

    /** Emits Unit whenever the MediaStore reports a change (plus once on start). */
    fun changes(): Flow<Unit> = callbackFlow {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                trySend(Unit)
            }
        }
        resolver.registerContentObserver(filesUri, true, observer)
        trySend(Unit)
        awaitClose { resolver.unregisterContentObserver(observer) }
    }.conflate()

    suspend fun loadAll(): List<MediaItem> = withContext(Dispatchers.IO) {
        val projection = buildList {
            add(MediaStore.Files.FileColumns._ID)
            add(MediaStore.Files.FileColumns.DISPLAY_NAME)
            add(MediaStore.Files.FileColumns.MIME_TYPE)
            add(MediaStore.Files.FileColumns.MEDIA_TYPE)
            add(MediaStore.Files.FileColumns.DATE_MODIFIED)
            add(MediaStore.Files.FileColumns.DATE_ADDED)
            add(MediaStore.Files.FileColumns.SIZE)
            add(MediaStore.Files.FileColumns.WIDTH)
            add(MediaStore.Files.FileColumns.HEIGHT)
            add(MediaStore.Files.FileColumns.DATA)
            add(COL_DATE_TAKEN)
            add(COL_DURATION)
            add(COL_BUCKET_ID)
            add(COL_BUCKET_NAME)
            add(COL_ORIENTATION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) add(MediaStore.Files.FileColumns.RELATIVE_PATH)
        }.toTypedArray()

        val selection = "${MediaStore.Files.FileColumns.MEDIA_TYPE} IN (?, ?)"
        val args = arrayOf(
            MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString(),
            MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString(),
        )
        val sort = "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC"

        val items = ArrayList<MediaItem>(1024)
        val cursor = try {
            resolver.query(filesUri, projection, selection, args, sort)
        } catch (e: SecurityException) {
            null
        }
        cursor?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
            val nameCol = c.getColumnIndex(MediaStore.Files.FileColumns.DISPLAY_NAME)
            val mimeCol = c.getColumnIndex(MediaStore.Files.FileColumns.MIME_TYPE)
            val typeCol = c.getColumnIndex(MediaStore.Files.FileColumns.MEDIA_TYPE)
            val modCol = c.getColumnIndex(MediaStore.Files.FileColumns.DATE_MODIFIED)
            val addedCol = c.getColumnIndex(MediaStore.Files.FileColumns.DATE_ADDED)
            val sizeCol = c.getColumnIndex(MediaStore.Files.FileColumns.SIZE)
            val wCol = c.getColumnIndex(MediaStore.Files.FileColumns.WIDTH)
            val hCol = c.getColumnIndex(MediaStore.Files.FileColumns.HEIGHT)
            val dataCol = c.getColumnIndex(MediaStore.Files.FileColumns.DATA)
            val takenCol = c.getColumnIndex(COL_DATE_TAKEN)
            val durCol = c.getColumnIndex(COL_DURATION)
            val bucketIdCol = c.getColumnIndex(COL_BUCKET_ID)
            val bucketNameCol = c.getColumnIndex(COL_BUCKET_NAME)
            val orientCol = c.getColumnIndex(COL_ORIENTATION)
            val relCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) c.getColumnIndex(MediaStore.Files.FileColumns.RELATIVE_PATH) else -1

            while (c.moveToNext()) {
                val id = c.getLong(idCol)
                val mediaType = c.intOr(typeCol, MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE)
                val isVideo = mediaType == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO
                val mime = c.stringOr(mimeCol, if (isVideo) "video/*" else "image/*")
                val name = c.stringOr(nameCol, "")
                val data = c.stringOr(dataCol, "")
                val modified = c.longOr(modCol, 0L) * 1000
                val added = c.longOr(addedCol, 0L) * 1000
                val taken = c.longOr(takenCol, 0L)
                val date = when {
                    taken > 0 -> taken
                    modified > 0 -> modified
                    else -> added
                }
                val relPath = if (relCol >= 0) c.stringOr(relCol, "") else ""
                val displayPath = when {
                    data.isNotBlank() -> data
                    relPath.isNotBlank() -> relPath + name
                    else -> name
                }
                val bucketName = c.stringOr(bucketNameCol, "").ifBlank {
                    relPath.trimEnd('/').substringAfterLast('/').ifBlank { "—" }
                }
                val baseUri = if (isVideo) MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                items += MediaItem(
                    id = id,
                    uri = ContentUris.withAppendedId(baseUri, id),
                    displayName = name.ifBlank { displayPath.substringAfterLast('/') },
                    mimeType = mime,
                    dateMs = date,
                    dateModifiedMs = modified,
                    sizeBytes = c.longOr(sizeCol, 0L),
                    width = c.intOr(wCol, 0),
                    height = c.intOr(hCol, 0),
                    durationMs = if (isVideo) c.longOr(durCol, 0L) else 0L,
                    bucketId = c.longOr(bucketIdCol, 0L),
                    bucketName = bucketName,
                    path = displayPath,
                    orientation = c.intOr(orientCol, 0),
                )
            }
        }
        items.sortByDescending { it.dateMs }
        items
    }

    /** Builds a MediaItem for a content:// or file:// URI received from another app. */
    suspend fun describeExternal(uri: Uri, mimeHint: String?): MediaItem = withContext(Dispatchers.IO) {
        var name = uri.lastPathSegment ?: "file"
        var size = 0L
        runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    name = c.stringOr(c.getColumnIndex(OpenableColumns.DISPLAY_NAME), name)
                    size = c.longOr(c.getColumnIndex(OpenableColumns.SIZE), 0L)
                }
            }
        }
        val mime = mimeHint?.takeIf { it.contains('/') && !it.endsWith("*") }
            ?: resolver.getType(uri)
            ?: "image/*"
        MediaItem(
            id = -1L,
            uri = uri,
            displayName = name,
            mimeType = mime,
            dateMs = System.currentTimeMillis(),
            dateModifiedMs = 0L,
            sizeBytes = size,
            width = 0,
            height = 0,
            durationMs = 0L,
            bucketId = -1L,
            bucketName = "",
            path = uri.toString(),
            orientation = 0,
        )
    }

    private companion object {
        // Column names shared by the images/video/files tables since API 26.
        const val COL_DATE_TAKEN = "datetaken"
        const val COL_DURATION = "duration"
        const val COL_BUCKET_ID = "bucket_id"
        const val COL_BUCKET_NAME = "bucket_display_name"
        const val COL_ORIENTATION = "orientation"
    }
}

private fun Cursor.stringOr(col: Int, default: String): String =
    if (col >= 0 && !isNull(col)) getString(col) ?: default else default

private fun Cursor.longOr(col: Int, default: Long): Long =
    if (col >= 0 && !isNull(col)) getLong(col) else default

private fun Cursor.intOr(col: Int, default: Int): Int =
    if (col >= 0 && !isNull(col)) getInt(col) else default
