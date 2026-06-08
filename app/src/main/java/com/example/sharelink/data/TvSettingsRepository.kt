package com.example.sharelink.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.sharelink.adb.AdbTvClient
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Persisted TV connection settings using DataStore Preferences.
 * Supports multiple saved TVs with a selected TV for sending.
 */

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "tv_settings")

private val json = Json { ignoreUnknownKeys = true }

@Serializable
data class TvConfig(
    val id: String = "",
    val host: String = "",
    val port: Int = AdbTvClient.DEFAULT_PORT,
    val name: String = "My TV"
) {
    val isConfigured: Boolean get() = host.isNotBlank()
}

class TvSettingsRepository(private val context: Context) {

    private companion object {
        val KEY_TV_LIST = stringPreferencesKey("tv_list_json")
        val KEY_SELECTED_ID = stringPreferencesKey("selected_tv_id")
    }

    /** Flow of all saved TVs. */
    val tvList: Flow<List<TvConfig>> = context.dataStore.data.map { prefs ->
        val listJson = prefs[KEY_TV_LIST] ?: "[]"
        runCatching { json.decodeFromString<List<TvConfig>>(listJson) }.getOrDefault(emptyList())
    }

    /** Flow of the currently selected TV ID. */
    val selectedTvId: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_SELECTED_ID] ?: ""
    }

    /** Adds or updates a TV in the list. */
    suspend fun saveTv(config: TvConfig) {
        context.dataStore.edit { prefs ->
            val list = currentList(prefs).toMutableList()
            val index = list.indexOfFirst { it.id == config.id }
            if (index >= 0) {
                list[index] = config
            } else {
                list.add(config)
            }
            prefs[KEY_TV_LIST] = json.encodeToString(list)

            // Auto-select if it's the first TV or nothing is selected
            val selectedId = prefs[KEY_SELECTED_ID] ?: ""
            if (selectedId.isBlank() || list.size == 1) {
                prefs[KEY_SELECTED_ID] = config.id
            }
        }
    }

    /** Removes a TV from the list. */
    suspend fun removeTv(id: String) {
        context.dataStore.edit { prefs ->
            val list = currentList(prefs).filter { it.id != id }
            prefs[KEY_TV_LIST] = json.encodeToString(list)

            // If removed TV was selected, select the first remaining one
            if (prefs[KEY_SELECTED_ID] == id) {
                prefs[KEY_SELECTED_ID] = list.firstOrNull()?.id ?: ""
            }
        }
    }

    /** Sets the selected TV by ID. */
    suspend fun selectTv(id: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_SELECTED_ID] = id
        }
    }

    private fun currentList(prefs: Preferences): List<TvConfig> {
        val listJson = prefs[KEY_TV_LIST] ?: "[]"
        return runCatching { json.decodeFromString<List<TvConfig>>(listJson) }.getOrDefault(emptyList())
    }
}
