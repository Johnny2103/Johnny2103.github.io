package io.github.johnny2103.novagalerie.data.index

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import androidx.room.withTransaction
import io.github.johnny2103.novagalerie.data.MediaItem
import io.github.johnny2103.novagalerie.data.db.AppDatabase
import io.github.johnny2103.novagalerie.data.db.Embeddings
import io.github.johnny2103.novagalerie.data.db.FaceEntity
import io.github.johnny2103.novagalerie.data.db.MediaMetaEntity
import io.github.johnny2103.novagalerie.data.db.PersonEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.coroutines.coroutineContext

data class IndexProgress(
    val running: Boolean = false,
    val done: Int = 0,
    val total: Int = 0,
    val faceModelAvailable: Boolean = true,
)

/**
 * Background analysis of the library: capture time and GPS from EXIF, offline country lookup,
 * face detection + embeddings, and online clustering of faces into people.
 * Everything runs on the device; results live in the Room database and the app's files dir.
 */
class MediaIndexer(private val context: Context, private val db: AppDatabase) {

    private class Cluster(val id: Long, var sum: FloatArray, var count: Int, var coverQuality: Float)

    private data class Config(val faces: Boolean, val threshold: Float)

    private val mutex = Mutex()
    private val _progress = MutableStateFlow(IndexProgress())
    val progress: StateFlow<IndexProgress> = _progress

    private val countryLookup by lazy { CountryLookup(context) }
    private var faceEngine: FaceEngine? = null
    private var clusters: MutableList<Cluster>? = null
    private var job: Job? = null

    @Volatile private var latestItems: List<MediaItem> = emptyList()
    @Volatile private var protectedIds: Set<Long> = emptySet()
    @Volatile private var allowCleanup: Boolean = true
    @Volatile private var dirty: Boolean = false
    @Volatile private var config = Config(faces = true, threshold = 0.58f)

    val facesDir: File = File(context.filesDir, "faces").apply { mkdirs() }

    fun faceFile(faceId: Long): File = File(facesDir, "$faceId.jpg")

    /**
     * (Re)starts indexing for the given library snapshot if nothing is running.
     * [keepIds] (e.g. trashed items) are never cleaned up; [cleanup] is false while the app only
     * has partial media access, because the visible list is then not the whole library.
     */
    fun start(
        scope: CoroutineScope,
        items: List<MediaItem>,
        faces: Boolean,
        threshold: Float,
        keepIds: Set<Long> = emptySet(),
        cleanup: Boolean = true,
    ) {
        latestItems = items
        protectedIds = keepIds
        allowCleanup = cleanup
        config = Config(faces, threshold)
        dirty = true
        if (job?.isActive == true) return
        job = scope.launch(Dispatchers.Default) { runLoop() }
    }

    suspend fun stop() {
        job?.cancel()
        job?.join()
        job = null
    }

    fun close() {
        faceEngine?.close()
        faceEngine = null
    }

    private suspend fun runLoop() {
        try {
            while (true) {
                coroutineContext.ensureActive()
                dirty = false
                val cfg = config
                val items = latestItems.filter { !it.isExternal }
                if (allowCleanup) cleanupMissing(items.mapTo(HashSet()) { it.id } + protectedIds)
                val indexed = db.mediaMetaDao().indexedIds(INDEX_VERSION).toHashSet()
                val pending = items.filter { it.id !in indexed }
                if (pending.isEmpty()) {
                    if (dirty) continue else break
                }

                val engine = if (cfg.faces) faceEngine ?: FaceEngine(context).also { faceEngine = it } else null
                _progress.value = IndexProgress(running = true, done = 0, total = pending.size, faceModelAvailable = engine?.available ?: true)
                pending.forEachIndexed { i, item ->
                    coroutineContext.ensureActive()
                    val ok = runCatching { indexOne(item, engine, cfg.threshold) }.isSuccess
                    // A broken file must still count as indexed, otherwise the loop would retry it forever.
                    if (!ok) runCatching { markFailed(item) }
                    _progress.update { it.copy(done = i + 1) }
                }
            }
        } finally {
            _progress.update { it.copy(running = false) }
        }
    }

    private suspend fun indexOne(item: MediaItem, engine: FaceEngine?, threshold: Float) {
        var latitude: Double? = null
        var longitude: Double? = null
        var dateTaken = 0L

        if (item.isVideo) {
            runCatching {
                val r = MediaMetadataRetriever()
                try {
                    r.setDataSource(context, item.uri)
                    r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_LOCATION)?.let { loc ->
                        ISO6709.matchFind(loc)?.let { (lat, lon) ->
                            latitude = lat
                            longitude = lon
                        }
                    }
                } finally {
                    r.release()
                }
            }
        } else {
            runCatching {
                context.contentResolver.openInputStream(originalUri(item.uri))?.use { stream ->
                    val exif = ExifInterface(stream)
                    exif.latLong?.let {
                        latitude = it[0]
                        longitude = it[1]
                    }
                    dateTaken = exif.dateTimeOriginal ?: exif.dateTime ?: 0L
                }
            }
        }

        val lat = latitude
        val lon = longitude
        val country = if (lat != null && lon != null) countryLookup.find(lat, lon) else null

        var faceCount = 0
        if (engine != null && engine.available && !item.isVideo) {
            val bitmap = BitmapLoading.loadUpright(context, item.uri, ANALYSIS_MAX_SIDE)
            if (bitmap != null) {
                val faces = try {
                    engine.analyze(bitmap)
                } finally {
                    bitmap.recycle()
                }
                faceCount = faces.size
                mutex.withLock {
                    // A previous, interrupted run may have stored faces for this item already.
                    val stale = db.faceDao().idsForMedia(listOf(item.id))
                    if (stale.isNotEmpty()) {
                        stale.forEach { faceFile(it).delete() }
                        db.faceDao().deleteByMedia(listOf(item.id))
                        recomputePersonsLocked()
                    }
                    for (face in faces) {
                        val faceId = db.faceDao().insert(
                            FaceEntity(
                                mediaId = item.id,
                                personId = null,
                                boxLeft = face.box.left,
                                boxTop = face.box.top,
                                boxRight = face.box.right,
                                boxBottom = face.box.bottom,
                                embedding = Embeddings.toBytes(face.embedding),
                                quality = face.quality,
                            ),
                        )
                        saveCrop(faceId, face.crop)
                        face.crop.recycle()
                        if (face.quality >= MIN_ASSIGN_QUALITY) {
                            assignLocked(faceId, face.embedding, face.quality, threshold)
                        }
                    }
                }
            }
        }

        db.mediaMetaDao().upsert(
            MediaMetaEntity(
                mediaId = item.id,
                dateTakenMs = dateTaken,
                latitude = lat,
                longitude = lon,
                country = country,
                faceCount = faceCount,
                indexedAt = System.currentTimeMillis(),
                version = INDEX_VERSION,
            ),
        )
    }

    private suspend fun markFailed(item: MediaItem) {
        db.mediaMetaDao().upsert(
            MediaMetaEntity(
                mediaId = item.id,
                dateTakenMs = 0L,
                latitude = null,
                longitude = null,
                country = null,
                faceCount = 0,
                indexedAt = System.currentTimeMillis(),
                version = INDEX_VERSION,
            ),
        )
    }

    private fun saveCrop(faceId: Long, crop: Bitmap) {
        runCatching {
            FileOutputStream(faceFile(faceId)).use { crop.compress(Bitmap.CompressFormat.JPEG, 85, it) }
        }
    }

    // --- clustering ------------------------------------------------------------------------

    private suspend fun clustersLocked(): MutableList<Cluster> {
        clusters?.let { return it }
        val loaded = db.personDao().all().map { p ->
            val coverQuality = p.coverFaceId?.let { db.faceDao().get(it)?.quality } ?: 0f
            Cluster(p.id, Embeddings.fromBytes(p.centroid), p.faceCount, coverQuality)
        }.toMutableList()
        clusters = loaded
        return loaded
    }

    /** Greedy online assignment: join the most similar person above the threshold or start a new one. */
    private suspend fun assignLocked(faceId: Long, embedding: FloatArray, quality: Float, threshold: Float) {
        val list = clustersLocked()
        var best: Cluster? = null
        var bestSim = -1f
        for (c in list) {
            val sim = Embeddings.cosine(embedding, c.sum)
            if (sim > bestSim) {
                bestSim = sim
                best = c
            }
        }
        if (best != null && bestSim >= threshold) {
            best.sum = Embeddings.add(best.sum, embedding)
            best.count += 1
            db.faceDao().assign(faceId, best.id)
            db.personDao().setStats(best.id, best.count, Embeddings.toBytes(best.sum))
            if (quality > best.coverQuality + 0.1f) {
                best.coverQuality = quality
                db.personDao().setCover(best.id, faceId)
            }
        } else {
            val id = db.personDao().insert(
                PersonEntity(name = null, coverFaceId = faceId, centroid = Embeddings.toBytes(embedding), faceCount = 1),
            )
            db.faceDao().assign(faceId, id)
            list += Cluster(id, embedding.copyOf(), 1, quality)
        }
    }

    /** Re-runs the clustering over all stored embeddings, keeping names where clusters overlap. */
    suspend fun recluster(threshold: Float) = withContext(Dispatchers.Default) {
        mutex.withLock {
            db.withTransaction { reclusterLocked(threshold) }
        }
    }

    private suspend fun reclusterLocked(threshold: Float) {
        run {
            val faces = db.faceDao().all().sortedByDescending { it.quality }
            val oldPersons = db.personDao().all().associateBy { it.id }
            val oldAssignment = faces.associate { it.id to it.personId }

            db.faceDao().clearAssignments()
            db.personDao().deleteAll()
            clusters = mutableListOf()

            for (face in faces) {
                if (face.quality < MIN_ASSIGN_QUALITY) continue
                assignLocked(face.id, Embeddings.fromBytes(face.embedding), face.quality, threshold)
            }

            // Carry names over: each new cluster takes the name of the old person most of its faces had.
            val newFaces = db.faceDao().all()
            val byPerson = newFaces.filter { it.personId != null }.groupBy { it.personId!! }
            val usedNames = HashSet<String>()
            val candidates = byPerson.entries.sortedByDescending { it.value.size }
            for ((newId, members) in candidates) {
                val votes = HashMap<Long, Int>()
                for (f in members) {
                    val old = oldAssignment[f.id] ?: continue
                    votes[old] = (votes[old] ?: 0) + 1
                }
                val winner = votes.entries.sortedByDescending { it.value }.firstNotNullOfOrNull { (oldId, _) ->
                    oldPersons[oldId]?.takeIf { !it.name.isNullOrBlank() && it.name !in usedNames }
                } ?: continue
                usedNames += winner.name!!
                db.personDao().rename(newId, winner.name)
                if (winner.hidden) db.personDao().setHidden(newId, true)
            }
        }
    }

    suspend fun mergePersons(fromId: Long, toId: Long) = withContext(Dispatchers.Default) {
        if (fromId == toId) return@withContext
        mutex.withLock {
            val list = clustersLocked()
            val from = db.personDao().get(fromId) ?: return@withLock
            val to = db.personDao().get(toId) ?: return@withLock
            db.faceDao().movePerson(fromId, toId)
            val sum = Embeddings.add(Embeddings.fromBytes(to.centroid), Embeddings.fromBytes(from.centroid))
            val count = to.faceCount + from.faceCount
            db.personDao().setStats(toId, count, Embeddings.toBytes(sum))
            if (to.name.isNullOrBlank() && !from.name.isNullOrBlank()) db.personDao().rename(toId, from.name)
            db.personDao().delete(from)
            list.removeAll { it.id == fromId }
            list.firstOrNull { it.id == toId }?.let {
                it.sum = sum
                it.count = count
            }
        }
    }

    /** Drops every analysis result; the next [start] re-indexes the whole library. */
    suspend fun rebuild() = withContext(Dispatchers.IO) {
        stop()
        mutex.withLock {
            db.faceDao().deleteAll()
            db.personDao().deleteAll()
            db.mediaMetaDao().deleteAll()
            clusters = null
            facesDir.listFiles()?.forEach { it.delete() }
        }
    }

    // --- housekeeping ----------------------------------------------------------------------

    private suspend fun cleanupMissing(currentIds: Set<Long>) {
        val known = db.mediaMetaDao().allIds()
        val missing = known.filter { it !in currentIds }
        if (missing.isEmpty()) return
        mutex.withLock {
            missing.chunked(400).forEach { chunk ->
                db.faceDao().idsForMedia(chunk).forEach { faceFile(it).delete() }
                db.faceDao().deleteByMedia(chunk)
                db.mediaMetaDao().deleteByIds(chunk)
                db.tagDao().deleteByMedia(chunk)
            }
            db.tagDao().deleteUnused()
            recomputePersonsLocked()
        }
    }

    private suspend fun recomputePersonsLocked() {
        val faces = db.faceDao().all().filter { it.personId != null }
        val grouped = faces.groupBy { it.personId!! }
        val persons = db.personDao().all()
        val list = mutableListOf<Cluster>()
        for (p in persons) {
            val members = grouped[p.id]
            if (members.isNullOrEmpty()) {
                db.personDao().delete(p)
                continue
            }
            var sum = FloatArray(0)
            for (m in members) sum = Embeddings.add(sum, Embeddings.fromBytes(m.embedding))
            db.personDao().setStats(p.id, members.size, Embeddings.toBytes(sum))
            val cover = members.firstOrNull { it.id == p.coverFaceId } ?: members.maxBy { it.quality }
            if (cover.id != p.coverFaceId) db.personDao().setCover(p.id, cover.id)
            list += Cluster(p.id, sum, members.size, cover.quality)
        }
        clusters = list
    }

    private fun originalUri(uri: Uri): Uri =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && uri.scheme == "content" && uri.authority == MediaStore.AUTHORITY) {
            runCatching { MediaStore.setRequireOriginal(uri) }.getOrDefault(uri)
        } else uri

    private object ISO6709 {
        private val regex = Regex("([+-]\\d+(?:\\.\\d+)?)([+-]\\d+(?:\\.\\d+)?)")
        fun matchFind(value: String): Pair<Double, Double>? {
            val m = regex.find(value) ?: return null
            val lat = m.groupValues[1].toDoubleOrNull() ?: return null
            val lon = m.groupValues[2].toDoubleOrNull() ?: return null
            return lat to lon
        }
    }

    companion object {
        const val INDEX_VERSION = 1
        private const val MIN_ASSIGN_QUALITY = 0.3f
        private const val ANALYSIS_MAX_SIDE = 1280
    }
}
