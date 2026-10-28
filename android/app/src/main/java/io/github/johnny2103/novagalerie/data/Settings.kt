package io.github.johnny2103.novagalerie.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class ThemeMode { SYSTEM, LIGHT, DARK }
enum class GroupBy { DAY, MONTH, YEAR, NONE }
enum class FaceSensitivity(val threshold: Float) { STRICT(0.66f), NORMAL(0.58f), LOOSE(0.50f) }

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    val gridColumns: Int = 3,
    val showVideosInTimeline: Boolean = true,
    val groupBy: GroupBy = GroupBy.DAY,
    val indexingEnabled: Boolean = true,
    val faceSensitivity: FaceSensitivity = FaceSensitivity.NORMAL,
    val useTrash: Boolean = true,
    val slideshowSeconds: Int = 4,
    val appLock: Boolean = false,
)

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/** Small persistence layer on top of Preferences DataStore (settings + favourites). */
class SettingsRepository(context: Context) {

    private val store = context.applicationContext.dataStore

    val settings: Flow<AppSettings> = store.data.map { p ->
        AppSettings(
            themeMode = p[KEY_THEME].toEnum(ThemeMode.SYSTEM),
            dynamicColor = p[KEY_DYNAMIC] ?: true,
            gridColumns = (p[KEY_COLUMNS] ?: 3).coerceIn(2, 6),
            showVideosInTimeline = p[KEY_SHOW_VIDEOS] ?: true,
            groupBy = p[KEY_GROUP_BY].toEnum(GroupBy.DAY),
            indexingEnabled = p[KEY_INDEXING] ?: true,
            faceSensitivity = p[KEY_SENSITIVITY].toEnum(FaceSensitivity.NORMAL),
            useTrash = p[KEY_USE_TRASH] ?: true,
            slideshowSeconds = (p[KEY_SLIDESHOW] ?: 4).coerceIn(2, 30),
            appLock = p[KEY_APP_LOCK] ?: false,
        )
    }

    val favorites: Flow<Set<Long>> = store.data.map { p ->
        p[KEY_FAVORITES]?.mapNotNullTo(HashSet()) { it.toLongOrNull() } ?: emptySet()
    }

    suspend fun setThemeMode(mode: ThemeMode) { store.edit { it[KEY_THEME] = mode.name } }
    suspend fun setDynamicColor(enabled: Boolean) { store.edit { it[KEY_DYNAMIC] = enabled } }
    suspend fun setGridColumns(columns: Int) { store.edit { it[KEY_COLUMNS] = columns.coerceIn(2, 6) } }
    suspend fun setShowVideos(enabled: Boolean) { store.edit { it[KEY_SHOW_VIDEOS] = enabled } }
    suspend fun setGroupBy(groupBy: GroupBy) { store.edit { it[KEY_GROUP_BY] = groupBy.name } }
    suspend fun setIndexingEnabled(enabled: Boolean) { store.edit { it[KEY_INDEXING] = enabled } }
    suspend fun setFaceSensitivity(value: FaceSensitivity) { store.edit { it[KEY_SENSITIVITY] = value.name } }
    suspend fun setUseTrash(enabled: Boolean) { store.edit { it[KEY_USE_TRASH] = enabled } }
    suspend fun setSlideshowSeconds(seconds: Int) { store.edit { it[KEY_SLIDESHOW] = seconds.coerceIn(2, 30) } }
    suspend fun setAppLock(enabled: Boolean) { store.edit { it[KEY_APP_LOCK] = enabled } }

    suspend fun toggleFavorite(id: Long) {
        store.edit { p ->
            val current = p[KEY_FAVORITES] ?: emptySet()
            val key = id.toString()
            p[KEY_FAVORITES] = if (key in current) current - key else current + key
        }
    }

    suspend fun setFavorites(ids: Collection<Long>, favorite: Boolean) {
        store.edit { p ->
            val current = p[KEY_FAVORITES] ?: emptySet()
            val keys = ids.map { it.toString() }.toSet()
            p[KEY_FAVORITES] = if (favorite) current + keys else current - keys
        }
    }

    private inline fun <reified T : Enum<T>> String?.toEnum(default: T): T =
        this?.let { v -> runCatching { enumValueOf<T>(v) }.getOrNull() } ?: default

    private companion object {
        val KEY_THEME = stringPreferencesKey("theme_mode")
        val KEY_DYNAMIC = booleanPreferencesKey("dynamic_color")
        val KEY_COLUMNS = intPreferencesKey("grid_columns")
        val KEY_SHOW_VIDEOS = booleanPreferencesKey("show_videos")
        val KEY_GROUP_BY = stringPreferencesKey("group_by")
        val KEY_INDEXING = booleanPreferencesKey("indexing_enabled")
        val KEY_SENSITIVITY = stringPreferencesKey("face_sensitivity")
        val KEY_USE_TRASH = booleanPreferencesKey("use_trash")
        val KEY_SLIDESHOW = intPreferencesKey("slideshow_seconds")
        val KEY_APP_LOCK = booleanPreferencesKey("app_lock")
        val KEY_FAVORITES = stringSetPreferencesKey("favorites")
    }
}
