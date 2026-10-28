package io.github.johnny2103.novagalerie.data

import android.net.Uri

/** One photo or video from the MediaStore (or an external URI handed over by another app). */
data class MediaItem(
    val id: Long,
    val uri: Uri,
    val displayName: String,
    val mimeType: String,
    /** Best-effort capture time in epoch millis (falls back to modification time). */
    val dateMs: Long,
    val dateModifiedMs: Long,
    val sizeBytes: Long,
    val width: Int,
    val height: Int,
    val durationMs: Long,
    val bucketId: Long,
    val bucketName: String,
    val path: String,
    val orientation: Int,
) {
    val isVideo: Boolean get() = mimeType.startsWith("video/")
    val isExternal: Boolean get() = id < 0
}

/** A folder (MediaStore "bucket") that contains media. */
data class Album(
    val id: Long,
    val name: String,
    val count: Int,
    val cover: MediaItem,
    val latestMs: Long,
)

/** Which list the full-screen viewer currently pages through. */
sealed interface MediaSource {
    data object All : MediaSource
    data class AlbumSource(val bucketId: Long) : MediaSource
    data object Favorites : MediaSource
    data object Videos : MediaSource
    data object Trash : MediaSource
    data class Person(val personId: Long) : MediaSource
    data class Country(val name: String) : MediaSource
    data class Tag(val tagId: Long) : MediaSource
    data class Search(val query: String, val filter: TypeFilter) : MediaSource
    data class External(val item: MediaItem) : MediaSource
}

enum class TypeFilter { ALL, PHOTOS, VIDEOS }
