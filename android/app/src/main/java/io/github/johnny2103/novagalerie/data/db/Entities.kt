package io.github.johnny2103.novagalerie.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Per-media analysis results (EXIF capture time, location, country, face count). */
@Entity(tableName = "media_meta")
data class MediaMetaEntity(
    @PrimaryKey val mediaId: Long,
    val dateTakenMs: Long,
    val latitude: Double?,
    val longitude: Double?,
    val country: String?,
    val faceCount: Int,
    val indexedAt: Long,
    val version: Int,
)

/** One detected face. Box coordinates are normalised to 0..1 of the upright image. */
@Entity(
    tableName = "faces",
    indices = [Index("mediaId"), Index("personId")],
)
data class FaceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val mediaId: Long,
    val personId: Long?,
    val boxLeft: Float,
    val boxTop: Float,
    val boxRight: Float,
    val boxBottom: Float,
    /** L2-normalised embedding, little-endian floats. */
    val embedding: ByteArray,
    val quality: Float,
)

/** A cluster of faces that belong to the same person. */
@Entity(tableName = "people")
data class PersonEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String?,
    val coverFaceId: Long?,
    /** Sum of member embeddings (not normalised), little-endian floats. */
    val centroid: ByteArray,
    val faceCount: Int,
    val hidden: Boolean = false,
)

@Entity(tableName = "tags", indices = [Index(value = ["name"], unique = true)])
data class TagEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
)

@Entity(
    tableName = "media_tags",
    primaryKeys = ["mediaId", "tagId"],
    indices = [Index("tagId")],
)
data class MediaTagEntity(
    val mediaId: Long,
    val tagId: Long,
)

/** Projection helpers used by the view model. */
data class CountryRow(val mediaId: Long, val country: String)
data class PersonMediaRow(val personId: Long, val mediaId: Long)
data class MediaLocationRow(val mediaId: Long, val latitude: Double, val longitude: Double)
