package io.github.johnny2103.novagalerie.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface MediaMetaDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(meta: MediaMetaEntity)

    @Query("SELECT mediaId FROM media_meta WHERE version = :version")
    suspend fun indexedIds(version: Int): List<Long>

    @Query("SELECT mediaId FROM media_meta")
    suspend fun allIds(): List<Long>

    @Query("SELECT * FROM media_meta WHERE mediaId = :mediaId")
    suspend fun get(mediaId: Long): MediaMetaEntity?

    @Query("SELECT mediaId, country FROM media_meta WHERE country IS NOT NULL")
    fun countries(): Flow<List<CountryRow>>

    @Query("SELECT mediaId, latitude, longitude FROM media_meta WHERE latitude IS NOT NULL AND longitude IS NOT NULL")
    fun locations(): Flow<List<MediaLocationRow>>

    @Query("SELECT COUNT(*) FROM media_meta")
    fun count(): Flow<Int>

    @Query("DELETE FROM media_meta WHERE mediaId IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Query("DELETE FROM media_meta")
    suspend fun deleteAll()
}

@Dao
interface FaceDao {
    @Insert
    suspend fun insert(face: FaceEntity): Long

    @Update
    suspend fun update(face: FaceEntity)

    @Query("SELECT * FROM faces")
    suspend fun all(): List<FaceEntity>

    @Query("SELECT * FROM faces WHERE mediaId = :mediaId")
    suspend fun forMedia(mediaId: Long): List<FaceEntity>

    @Query("SELECT * FROM faces WHERE id = :id")
    suspend fun get(id: Long): FaceEntity?

    @Query("SELECT personId, mediaId FROM faces WHERE personId IS NOT NULL")
    fun personMedia(): Flow<List<PersonMediaRow>>

    @Query("SELECT * FROM faces WHERE personId = :personId ORDER BY quality DESC")
    suspend fun forPerson(personId: Long): List<FaceEntity>

    @Query("UPDATE faces SET personId = :toId WHERE personId = :fromId")
    suspend fun movePerson(fromId: Long, toId: Long)

    @Query("UPDATE faces SET personId = :personId WHERE id = :faceId")
    suspend fun assign(faceId: Long, personId: Long?)

    @Query("UPDATE faces SET personId = NULL")
    suspend fun clearAssignments()

    @Query("SELECT id FROM faces WHERE mediaId IN (:ids)")
    suspend fun idsForMedia(ids: List<Long>): List<Long>

    @Query("DELETE FROM faces WHERE mediaId IN (:ids)")
    suspend fun deleteByMedia(ids: List<Long>)

    @Query("DELETE FROM faces")
    suspend fun deleteAll()
}

@Dao
interface PersonDao {
    @Insert
    suspend fun insert(person: PersonEntity): Long

    @Update
    suspend fun update(person: PersonEntity)

    @Delete
    suspend fun delete(person: PersonEntity)

    @Query("SELECT * FROM people ORDER BY faceCount DESC")
    fun observeAll(): Flow<List<PersonEntity>>

    @Query("SELECT * FROM people")
    suspend fun all(): List<PersonEntity>

    @Query("SELECT * FROM people WHERE id = :id")
    suspend fun get(id: Long): PersonEntity?

    @Query("UPDATE people SET name = :name WHERE id = :id")
    suspend fun rename(id: Long, name: String?)

    @Query("UPDATE people SET hidden = :hidden WHERE id = :id")
    suspend fun setHidden(id: Long, hidden: Boolean)

    @Query("UPDATE people SET coverFaceId = :faceId WHERE id = :id")
    suspend fun setCover(id: Long, faceId: Long)

    @Query("UPDATE people SET faceCount = :count, centroid = :centroid WHERE id = :id")
    suspend fun setStats(id: Long, count: Int, centroid: ByteArray)

    @Query("DELETE FROM people WHERE faceCount <= 0")
    suspend fun deleteEmpty()

    @Query("DELETE FROM people")
    suspend fun deleteAll()
}

@Dao
interface TagDao {
    @Query("SELECT * FROM tags ORDER BY name COLLATE NOCASE")
    fun observeTags(): Flow<List<TagEntity>>

    @Query("SELECT * FROM media_tags")
    fun observeMediaTags(): Flow<List<MediaTagEntity>>

    @Query("SELECT * FROM tags WHERE name = :name COLLATE NOCASE LIMIT 1")
    suspend fun findByName(name: String): TagEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTag(tag: TagEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMediaTag(row: MediaTagEntity)

    @Query("DELETE FROM media_tags WHERE mediaId = :mediaId AND tagId = :tagId")
    suspend fun deleteMediaTag(mediaId: Long, tagId: Long)

    @Query("DELETE FROM tags WHERE id = :tagId")
    suspend fun deleteTag(tagId: Long)

    @Query("DELETE FROM media_tags WHERE tagId = :tagId")
    suspend fun deleteMediaTagsForTag(tagId: Long)

    @Query("DELETE FROM media_tags WHERE mediaId IN (:ids)")
    suspend fun deleteByMedia(ids: List<Long>)

    @Query("DELETE FROM tags WHERE id NOT IN (SELECT DISTINCT tagId FROM media_tags)")
    suspend fun deleteUnused()
}
