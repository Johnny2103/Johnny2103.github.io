package io.github.johnny2103.novagalerie.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.johnny2103.novagalerie.data.Album
import io.github.johnny2103.novagalerie.data.AppSettings
import io.github.johnny2103.novagalerie.data.MediaItem
import io.github.johnny2103.novagalerie.data.MediaRepository
import io.github.johnny2103.novagalerie.data.MediaSource
import io.github.johnny2103.novagalerie.data.SettingsRepository
import io.github.johnny2103.novagalerie.data.ThemeMode
import io.github.johnny2103.novagalerie.data.TypeFilter
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@OptIn(FlowPreview::class)
class GalleryViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = MediaRepository(app)
    private val settingsRepository = SettingsRepository(app)

    private val _media = MutableStateFlow<List<MediaItem>>(emptyList())
    val media: StateFlow<List<MediaItem>> = _media

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading

    private val _hasPermission = MutableStateFlow(false)
    val hasPermission: StateFlow<Boolean> = _hasPermission

    val settings: StateFlow<AppSettings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

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

    /** Multi-selection in grids (ids). */
    private val _selection = MutableStateFlow<Set<Long>>(emptySet())
    val selection: StateFlow<Set<Long>> = _selection

    /** The list the viewer pages through, derived live so deletions are reflected. */
    private val _viewerSource = MutableStateFlow<MediaSource>(MediaSource.All)
    val viewerItems: StateFlow<List<MediaItem>> = combine(_media, favorites, settings, _viewerSource) { items, favs, s, source ->
        itemsFor(source, items, favs, s)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** External item handed over by another app (VIEW / SEND intent), consumed by the UI. */
    private val _pendingExternal = MutableStateFlow<MediaItem?>(null)
    val pendingExternal: StateFlow<MediaItem?> = _pendingExternal

    private var watchJob: Job? = null

    fun onPermissionResult(granted: Boolean) {
        _hasPermission.value = granted
        if (granted) startWatching() else _media.value = emptyList()
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
            // Drop selections of items that vanished.
            val ids = _media.value.mapTo(HashSet()) { it.id }
            _selection.update { sel -> sel.filterTo(HashSet()) { it in ids } }
        }
    }

    fun itemsFor(source: MediaSource, items: List<MediaItem> = _media.value, favs: Set<Long> = favorites.value, s: AppSettings = settings.value): List<MediaItem> =
        when (source) {
            MediaSource.All -> if (s.showVideosInTimeline) items else items.filter { !it.isVideo }
            is MediaSource.AlbumSource -> items.filter { it.bucketId == source.bucketId }
            MediaSource.Favorites -> items.filter { it.id in favs }
            is MediaSource.Search -> search(items, source.query, source.filter)
            is MediaSource.External -> listOf(source.item)
        }

    fun search(items: List<MediaItem>, query: String, filter: TypeFilter): List<MediaItem> {
        val q = query.trim()
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
        return _media.value.filter { it.id in sel }
    }

    // --- favourites ------------------------------------------------------------------------

    fun toggleFavorite(id: Long) = viewModelScope.launch { settingsRepository.toggleFavorite(id) }
    fun setFavorites(ids: Collection<Long>, favorite: Boolean) = viewModelScope.launch { settingsRepository.setFavorites(ids, favorite) }

    // --- settings --------------------------------------------------------------------------

    fun setThemeMode(mode: ThemeMode) = viewModelScope.launch { settingsRepository.setThemeMode(mode) }
    fun setDynamicColor(enabled: Boolean) = viewModelScope.launch { settingsRepository.setDynamicColor(enabled) }
    fun setGridColumns(columns: Int) = viewModelScope.launch { settingsRepository.setGridColumns(columns) }
    fun setShowVideos(enabled: Boolean) = viewModelScope.launch { settingsRepository.setShowVideos(enabled) }
}
