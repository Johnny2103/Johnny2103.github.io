package io.github.johnny2103.novagalerie.data

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import io.github.johnny2103.novagalerie.R
import io.github.johnny2103.novagalerie.util.Format
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** One row of the details sheet. */
data class DetailRow(val labelRes: Int, val value: String)

data class MediaDetails(
    val rows: List<DetailRow>,
    val latitude: Double? = null,
    val longitude: Double? = null,
)

/** Reads EXIF / container metadata for the details sheet, off the main thread. */
object MetadataReader {

    suspend fun read(context: Context, item: MediaItem): MediaDetails = withContext(Dispatchers.IO) {
        val rows = ArrayList<DetailRow>()
        var lat: Double? = null
        var lon: Double? = null

        rows += DetailRow(R.string.detail_name, item.displayName)
        if (item.bucketName.isNotBlank()) rows += DetailRow(R.string.detail_album, item.bucketName)
        rows += DetailRow(R.string.detail_type, item.mimeType)
        if (item.sizeBytes > 0) rows += DetailRow(R.string.detail_size, Format.fileSize(item.sizeBytes))

        var width = item.width
        var height = item.height

        if (item.isVideo) {
            runCatching {
                val r = MediaMetadataRetriever()
                try {
                    r.setDataSource(context, item.uri)
                    r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull()?.let { width = it }
                    r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull()?.let { height = it }
                    val duration = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: item.durationMs
                    if (duration > 0) rows += DetailRow(R.string.detail_duration, Format.duration(duration))
                    r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toLongOrNull()?.let {
                        rows += DetailRow(R.string.detail_bitrate, "${it / 1000} kbit/s")
                    }
                    r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.let {
                        if (it != "0") rows += DetailRow(R.string.detail_orientation, "$it°")
                    }
                    r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DATE)?.let { d ->
                        rows += DetailRow(R.string.detail_taken, d)
                    }
                } finally {
                    r.release()
                }
            }
            if (width > 0 && height > 0) rows += DetailRow(R.string.detail_resolution, "$width × $height")
        } else {
            if (width > 0 && height > 0) rows += DetailRow(R.string.detail_resolution, "$width × $height  (${Format.megapixels(width, height)})")
            runCatching {
                val readUri = originalUri(item.uri)
                context.contentResolver.openInputStream(readUri)?.use { stream ->
                    val exif = ExifInterface(stream)
                    val taken = exif.dateTimeOriginal ?: exif.dateTime
                    rows += DetailRow(R.string.detail_taken, taken?.let { Format.dateTime(it) } ?: Format.dateTime(item.dateMs))

                    val make = exif.getAttribute(ExifInterface.TAG_MAKE)?.trim().orEmpty()
                    val model = exif.getAttribute(ExifInterface.TAG_MODEL)?.trim().orEmpty()
                    val camera = listOf(make, model).filter { it.isNotBlank() }.distinct().joinToString(" ")
                    if (camera.isNotBlank()) rows += DetailRow(R.string.detail_camera, camera)
                    exif.getAttribute(ExifInterface.TAG_LENS_MODEL)?.takeIf { it.isNotBlank() }?.let {
                        rows += DetailRow(R.string.detail_lens, it.trim())
                    }

                    val fNumber = exif.getAttributeDouble(ExifInterface.TAG_F_NUMBER, 0.0)
                    val exposure = exif.getAttributeDouble(ExifInterface.TAG_EXPOSURE_TIME, 0.0)
                    val iso = exif.getAttributeInt(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY, 0)
                    val focal = exif.getAttributeDouble(ExifInterface.TAG_FOCAL_LENGTH, 0.0)
                    val parts = buildList {
                        if (fNumber > 0) add("ƒ/${Format.decimal(fNumber)}")
                        if (exposure > 0) add(Format.exposure(exposure))
                        if (iso > 0) add("ISO $iso")
                        if (focal > 0) add("${Format.decimal(focal)} mm")
                    }
                    if (parts.isNotEmpty()) rows += DetailRow(R.string.detail_exposure, parts.joinToString("  ·  "))

                    val orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
                    val degrees = when (orientation) {
                        ExifInterface.ORIENTATION_ROTATE_90 -> 90
                        ExifInterface.ORIENTATION_ROTATE_180 -> 180
                        ExifInterface.ORIENTATION_ROTATE_270 -> 270
                        else -> 0
                    }
                    if (degrees != 0) rows += DetailRow(R.string.detail_orientation, "$degrees°")

                    exif.latLong?.let { ll ->
                        lat = ll[0]
                        lon = ll[1]
                        rows += DetailRow(R.string.detail_location, "${Format.decimal(ll[0], 5)}, ${Format.decimal(ll[1], 5)}")
                    }
                }
            }.onFailure {
                if (rows.none { it.labelRes == R.string.detail_taken }) {
                    rows += DetailRow(R.string.detail_taken, Format.dateTime(item.dateMs))
                }
            }
        }

        if (item.dateModifiedMs > 0) rows += DetailRow(R.string.detail_modified, Format.dateTime(item.dateModifiedMs))
        if (item.path.isNotBlank()) rows += DetailRow(R.string.detail_path, item.path)

        MediaDetails(rows, lat, lon)
    }

    /** On Android 10+ location EXIF is redacted unless the original is requested explicitly. */
    private fun originalUri(uri: Uri): Uri =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && uri.scheme == "content" && uri.authority == MediaStore.AUTHORITY) {
            runCatching { MediaStore.setRequireOriginal(uri) }.getOrDefault(uri)
        } else uri
}
