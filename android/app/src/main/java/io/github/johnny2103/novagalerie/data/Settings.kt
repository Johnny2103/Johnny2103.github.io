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

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    val gridColumns: Int = 3,
    val showVideosInTimeline: Boolean = true,
)

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/** Small persistence layer on top of Preferences DataStore (settings + favourites). */
class SettingsRepository(context: Context) {

    private val store = context.applicationContext.dataStore

    val settings: Flow<AppSettings> = store.data.map { p ->
        AppSettings(
            themeMode = p[KEY_THEME]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM,
            dynamicColor = p[KEY_DYNAMIC] ?: true,
            gridColumns = (p[KEY_COLUMNS] ?: 3).coerceIn(2, 6),
            showVideosInTimeline = p[KEY_SHOW_VIDEOS] ?: true,
        )
    }

    val favorites: Flow<Set<Long>> = store.data.map { p ->
        p[KEY_FAVORITES]?.mapNotNullTo(HashSet()) { it.toLongOrNull() } ?: emptySet()
    }

    suspend fun setThemeMode(mode: ThemeMode) = store.edit { it[KEY_THEME] = mode.name }
    suspend fun setDynamicColor(enabled: Boolean) = store.edit { it[KEY_DYNAMIC] = enabled }
    suspend fun setGridColumns(columns: Int) = store.edit { it[KEY_COLUMNS] = columns.coerceIn(2, 6) }
    suspend fun setShowVideos(enabled: Boolean) = store.edit { it[KEY_SHOW_VIDEOS] = enabled }

    suspend fun toggleFavorite(id: Long) = store.edit { p ->
        val current = p[KEY_FAVORITES] ?: emptySet()
        val key = id.toString()
        p[KEY_FAVORITES] = if (key in current) current - key else current + key
    }

    suspend fun setFavorites(ids: Collection<Long>, favorite: Boolean) = store.edit { p ->
        val current = p[KEY_FAVORITES] ?: emptySet()
        val keys = ids.map { it.toString() }.toSet()
        p[KEY_FAVORITES] = if (favorite) current + keys else current - keys
    }

    private companion object {
        val KEY_THEME = stringPreferencesKey("theme_mode")
        val KEY_DYNAMIC = booleanPreferencesKey("dynamic_color")
        val KEY_COLUMNS = intPreferencesKey("grid_columns")
        val KEY_SHOW_VIDEOS = booleanPreferencesKey("show_videos")
        val KEY_FAVORITES = stringSetPreferencesKey("favorites")
    }
}
