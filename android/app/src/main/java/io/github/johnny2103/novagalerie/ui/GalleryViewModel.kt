package io.github.johnny2103.novagalerie.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.johnny2103.novagalerie.data.Album
import io.github.johnny2103.novagalerie.data.AppSettings
import io.github.johnny2103.novagalerie.data.FaceSensitivity
import io.github.johnny2103.novagalerie.data.GroupBy
import io.github.johnny2103.novagalerie.data.MediaItem
import io.github.johnny2103.novagalerie.data.MediaRepository
import io.github.johnny2103.novagalerie.data.MediaSource
import io.github.johnny2103.novagalerie.data.SettingsRepository
import io.github.johnny2103.novagalerie.data.TagRepository
import io.github.johnny2103.novagalerie.data.ThemeMode
import io.github.johnny2103.novagalerie.data.TypeFilter
import io.github.johnny2103.novagalerie.data.db.AppDatabase
import io.github.johnny2103.novagalerie.data.db.FaceEntity
import io.github.johnny2103.novagalerie.data.db.MediaMetaEntity
import io.github.johnny2103.novagalerie.data.db.PersonEntity
import io.github.johnny2103.novagalerie.data.db.TagEntity
import io.github.johnny2103.novagalerie.data.index.CountryLookup
import io.github.johnny2103.novagalerie.data.index.IndexProgress
import io.github.johnny2103.novagalerie.data.index.MediaIndexer
import io.github.johnny2103.novagalerie.ui.components.MediaPermissions
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

/** Analysis results the grids and the viewer need to resolve virtual collections. */
data class IndexData(
    val personMedia: Map<Long, List<Long>> = emptyMap(),
    val countries: Map<String, List<Long>> = emptyMap(),
    val tagMedia: Map<Long, List<Long>> = emptyMap(),
    val trash: List<MediaItem> = emptyList(),
)

@OptIn(FlowPreview::class)
class GalleryViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = MediaRepository(app)
    private val settingsRepository = SettingsRepository(app)
    private val db = AppDatabase.get(app)
    private val indexer = MediaIndexer(app, db)
    private val tagRepository = TagRepository(db)

    private val _media = MutableStateFlow<List<MediaItem>>(emptyList())
    val media: StateFlow<List<MediaItem>> = _media

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading

    private val _hasPermission = MutableStateFlow(false)
    val hasPermission: StateFlow<Boolean> = _hasPermission

    val settings: StateFlow<AppSettings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    /** False until DataStore has delivered the stored settings (used to avoid flashing locked content). */
    val settingsLoaded: StateFlow<Boolean> = settingsRepository.settings.map { true }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val favorites: StateFlow<Set<Long>> = settingsRepository.favorites
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    val albums: StateFlow<List<Album>> = _media.map { items ->
        items.groupBy { it.bucketId }.map { (id, list) ->
            val latest = list.maxBy { it.dateMs }
            Album(id = id, name = list.first().bucketName, count = list.size, cover = latest, latestMs = latest.dateMs)
        }.sortedByDescending { it.latestMs }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val timeline: StateFlow<List<MediaItem>> = combine(_media, settings) { items, s ->
        if (s.showVideosInTimeline) items else items.filter { !it.isVideo }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val favoriteItems: StateFlow<List<MediaItem>> = combine(_media, favorites) { items, favs ->
        items.filter { it.id in favs }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // --- analysis data ---------------------------------------------------------------------

    val indexProgress: StateFlow<IndexProgress> = indexer.progress

    val people: StateFlow<List<PersonEntity>> = db.personDao().observeAll()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val analysedCount: StateFlow<Int> = db.mediaMetaDao().count()
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    val tags: StateFlow<List<TagEntity>> = tagRepository.tags
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** mediaId → tagIds */
    val mediaTagIds: StateFlow<Map<Long, Set<Long>>> = tagRepository.mediaTags
        .map { rows -> rows.groupBy({ it.mediaId }, { it.tagId }).mapValues { it.value.toSet() } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    private val _trash = MutableStateFlow<List<MediaItem>>(emptyList())
    val trash: StateFlow<List<MediaItem>> = _trash

    val indexData: StateFlow<IndexData> = combine(
        db.faceDao().personMedia().map { rows -> rows.groupBy({ it.personId }, { it.mediaId }) },
        db.mediaMetaDao().countries().map { rows -> rows.groupBy({ it.country }, { it.mediaId }) },
        tagRepository.mediaTags.map { rows -> rows.groupBy({ it.tagId }, { it.mediaId }) },
        _trash,
    ) { personMedia, countries, tagMedia, trash ->
        IndexData(personMedia, countries, tagMedia, trash)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, IndexData())

    /** Multi-selection in grids (ids). */
    private val _selection = MutableStateFlow<Set<Long>>(emptySet())
    val selection: StateFlow<Set<Long>> = _selection

    /** The list the viewer pages through, derived live so deletions are reflected. */
    private val _viewerSource = MutableStateFlow<MediaSource>(MediaSource.All)
    val viewerSource: StateFlow<MediaSource> = _viewerSource
    val viewerItems: StateFlow<List<MediaItem>> = observeItemsFlow(_viewerSource)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** External item handed over by another app (VIEW / SEND intent), consumed by the UI. */
    private val _pendingExternal = MutableStateFlow<MediaItem?>(null)
    val pendingExternal: StateFlow<MediaItem?> = _pendingExternal

    private var watchJob: Job? = null

    init {
        viewModelScope.launch {
            settings.map { it.indexingEnabled to it.faceSensitivity }.distinctUntilChanged().collect { (enabled, _) ->
                if (!enabled) indexer.stop() else maybeStartIndexing()
            }
        }
    }

    fun onPermissionResult(granted: Boolean) {
        _hasPermission.value = granted
        if (granted) {
            loadTrash()
            startWatching()
        } else {
            _media.value = emptyList()
        }
    }

    private fun startWatching() {
        if (watchJob?.isActive == true) {
            refresh()
            return
        }
        watchJob = viewModelScope.launch {
            repository.changes().debounce(250).collect { refresh() }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _loading.value = _media.value.isEmpty()
            _media.value = repository.loadAll()
            _loading.value = false
            val ids = (_media.value + _trash.value).mapTo(HashSet()) { it.id }
            _selection.update { sel -> sel.filterTo(HashSet()) { it in ids } }
            maybeStartIndexing()
            if (_trash.value.isNotEmpty()) loadTrash()
        }
    }

    private fun maybeStartIndexing() {
        val s = settings.value
        if (!_hasPermission.value || !s.indexingEnabled || _media.value.isEmpty()) return
        indexer.start(
            viewModelScope,
            _media.value,
            faces = true,
            threshold = s.faceSensitivity.threshold,
            keepIds = _trash.value.mapTo(HashSet()) { it.id },
            cleanup = !MediaPermissions.isPartial(getApplication()),
        )
    }

    fun loadTrash() {
        viewModelScope.launch { _trash.value = repository.loadTrashed() }
    }

    override fun onCleared() {
        indexer.close()
        super.onCleared()
    }

    // --- collections -----------------------------------------------------------------------

    private fun observeItemsFlow(sourceFlow: Flow<MediaSource>): Flow<List<MediaItem>> =
        combine(_media, favorites, settings, indexData, sourceFlow) { items, favs, s, idx, source ->
            itemsFor(source, items, favs, s, idx)
        }

    fun observeItems(source: MediaSource): Flow<List<MediaItem>> =
        combine(_media, favorites, settings, indexData) { items, favs, s, idx -> itemsFor(source, items, favs, s, idx) }

    fun itemsFor(
        source: MediaSource,
        items: List<MediaItem> = _media.value,
        favs: Set<Long> = favorites.value,
        s: AppSettings = settings.value,
        idx: IndexData = indexData.value,
    ): List<MediaItem> = when (source) {
        MediaSource.All -> if (s.showVideosInTimeline) items else items.filter { !it.isVideo }
        is MediaSource.AlbumSource -> items.filter { it.bucketId == source.bucketId }
        MediaSource.Favorites -> items.filter { it.id in favs }
        MediaSource.Videos -> items.filter { it.isVideo }
        MediaSource.Trash -> idx.trash
        is MediaSource.Person -> {
            val ids = idx.personMedia[source.personId]?.toHashSet() ?: emptySet()
            items.filter { it.id in ids }
        }
        is MediaSource.Country -> {
            val ids = idx.countries[source.name]?.toHashSet() ?: emptySet()
            items.filter { it.id in ids }
        }
        is MediaSource.Tag -> {
            val ids = idx.tagMedia[source.tagId]?.toHashSet() ?: emptySet()
            items.filter { it.id in ids }
        }
        is MediaSource.Search -> search(items, source.query, source.filter)
        is MediaSource.External -> listOf(source.item)
    }

    /** Full-text style search over names, albums, people, places and tags. */
    fun search(items: List<MediaItem>, query: String, filter: TypeFilter): List<MediaItem> {
        val q = query.trim()
        val idx = indexData.value
        val extraIds = HashSet<Long>()
        if (q.isNotEmpty()) {
            people.value.filter { !it.hidden && it.name?.contains(q, ignoreCase = true) == true }
                .forEach { p -> idx.personMedia[p.id]?.let { extraIds.addAll(it) } }
            idx.countries.forEach { (country, ids) ->
                if (country.contains(q, true) || CountryLookup.displayName(country).contains(q, true)) extraIds.addAll(ids)
            }
            tags.value.filter { it.name.contains(q, ignoreCase = true) }
                .forEach { t -> idx.tagMedia[t.id]?.let { extraIds.addAll(it) } }
        }
        return items.asSequence()
            .filter {
                when (filter) {
                    TypeFilter.ALL -> true
                    TypeFilter.PHOTOS -> !it.isVideo
                    TypeFilter.VIDEOS -> it.isVideo
                }
            }
            .filter {
                q.isEmpty() ||
                    it.id in extraIds ||
                    it.displayName.contains(q, ignoreCase = true) ||
                    it.bucketName.contains(q, ignoreCase = true) ||
                    it.mimeType.contains(q, ignoreCase = true)
            }
            .toList()
    }

    fun openViewer(source: MediaSource) {
        _viewerSource.value = source
    }

    fun openExternal(uri: Uri, mimeHint: String?) {
        viewModelScope.launch {
            _pendingExternal.value = repository.describeExternal(uri, mimeHint)
        }
    }

    fun consumeExternal() {
        _pendingExternal.value = null
    }

    // --- selection -------------------------------------------------------------------------

    fun toggleSelected(id: Long) = _selection.update { if (id in it) it - id else it + id }
    fun selectAll(items: List<MediaItem>) = _selection.update { it + items.map { m -> m.id } }
    fun clearSelection() = _selection.update { emptySet() }
    fun selectedItems(): List<MediaItem> {
        val sel = _selection.value
        val all = _media.value + _trash.value
        return all.filter { it.id in sel }
    }

    // --- favourites ------------------------------------------------------------------------

    fun toggleFavorite(id: Long) = viewModelScope.launch { settingsRepository.toggleFavorite(id) }
    fun setFavorites(ids: Collection<Long>, favorite: Boolean) = viewModelScope.launch { settingsRepository.setFavorites(ids, favorite) }

    // --- people ----------------------------------------------------------------------------

    fun faceFile(faceId: Long?): File? = faceId?.let { indexer.faceFile(it) }
    suspend fun facesForMedia(mediaId: Long): List<FaceEntity> = db.faceDao().forMedia(mediaId)
    suspend fun facesForPerson(personId: Long): List<FaceEntity> = db.faceDao().forPerson(personId)
    suspend fun metaFor(mediaId: Long): MediaMetaEntity? = db.mediaMetaDao().get(mediaId)

    fun renamePerson(id: Long, name: String?) = viewModelScope.launch { db.personDao().rename(id, name?.trim()?.ifBlank { null }) }
    fun setPersonHidden(id: Long, hidden: Boolean) = viewModelScope.launch { db.personDao().setHidden(id, hidden) }
    fun setPersonCover(id: Long, faceId: Long) = viewModelScope.launch { db.personDao().setCover(id, faceId) }
    fun mergePersons(fromId: Long, toId: Long) = viewModelScope.launch { indexer.mergePersons(fromId, toId) }
    fun recluster() = viewModelScope.launch { indexer.recluster(settings.value.faceSensitivity.threshold) }
    fun rebuildIndex() = viewModelScope.launch {
        indexer.rebuild()
        maybeStartIndexing()
    }

    // --- tags ------------------------------------------------------------------------------

    fun addTag(name: String, mediaIds: Collection<Long>) = viewModelScope.launch { tagRepository.addTag(name, mediaIds) }
    fun removeTag(tagId: Long, mediaIds: Collection<Long>) = viewModelScope.launch { tagRepository.removeTag(tagId, mediaIds) }
    fun deleteTag(tagId: Long) = viewModelScope.launch { tagRepository.deleteTag(tagId) }

    // --- settings --------------------------------------------------------------------------

    fun setThemeMode(mode: ThemeMode) = viewModelScope.launch { settingsRepository.setThemeMode(mode) }
    fun setDynamicColor(enabled: Boolean) = viewModelScope.launch { settingsRepository.setDynamicColor(enabled) }
    fun setGridColumns(columns: Int) = viewModelScope.launch { settingsRepository.setGridColumns(columns) }
    fun setShowVideos(enabled: Boolean) = viewModelScope.launch { settingsRepository.setShowVideos(enabled) }
    fun setGroupBy(groupBy: GroupBy) = viewModelScope.launch { settingsRepository.setGroupBy(groupBy) }
    fun setIndexingEnabled(enabled: Boolean) = viewModelScope.launch { settingsRepository.setIndexingEnabled(enabled) }
    fun setFaceSensitivity(value: FaceSensitivity) = viewModelScope.launch { settingsRepository.setFaceSensitivity(value) }
    fun setUseTrash(enabled: Boolean) = viewModelScope.launch { settingsRepository.setUseTrash(enabled) }
    fun setSlideshowSeconds(seconds: Int) = viewModelScope.launch { settingsRepository.setSlideshowSeconds(seconds) }
    fun setAppLock(enabled: Boolean) = viewModelScope.launch { settingsRepository.setAppLock(enabled) }
}
