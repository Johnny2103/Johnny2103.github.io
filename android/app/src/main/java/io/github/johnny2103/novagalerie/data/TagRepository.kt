package io.github.johnny2103.novagalerie.data

import io.github.johnny2103.novagalerie.data.db.AppDatabase
import io.github.johnny2103.novagalerie.data.db.MediaTagEntity
import io.github.johnny2103.novagalerie.data.db.TagEntity
import kotlinx.coroutines.flow.Flow

/** Local tags (not written into files). */
class TagRepository(private val db: AppDatabase) {

    val tags: Flow<List<TagEntity>> = db.tagDao().observeTags()
    val mediaTags: Flow<List<MediaTagEntity>> = db.tagDao().observeMediaTags()

    suspend fun addTag(name: String, mediaIds: Collection<Long>): Long? {
        val clean = name.trim()
        if (clean.isEmpty()) return null
        val dao = db.tagDao()
        val id = dao.findByName(clean)?.id ?: dao.insertTag(TagEntity(name = clean)).takeIf { it > 0 } ?: dao.findByName(clean)?.id ?: return null
        mediaIds.forEach { dao.insertMediaTag(MediaTagEntity(it, id)) }
        return id
    }

    suspend fun removeTag(tagId: Long, mediaIds: Collection<Long>) {
        mediaIds.forEach { db.tagDao().deleteMediaTag(it, tagId) }
        db.tagDao().deleteUnused()
    }

    suspend fun deleteTag(tagId: Long) {
        db.tagDao().deleteMediaTagsForTag(tagId)
        db.tagDao().deleteTag(tagId)
    }
}
