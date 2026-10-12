package app.ttlauncher.data

import app.ttlauncher.apps.withNewTopics
import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

/** Everything the user has set up, kept as one JSON document so a change is saved whole or not at all. */
class Store(context: Context, scope: CoroutineScope) {
    private val dataStore = PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("launcher") }
    private val key = stringPreferencesKey("state")
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    private val _state = MutableStateFlow(LauncherState().withNewTopics())
    val state: StateFlow<LauncherState> = _state.asStateFlow()

    /** False until the saved state has been read, so the UI never draws the defaults first. */
    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    init {
        scope.launch {
            val saved = runCatching { dataStore.data.first()[key] }.getOrNull()
            saved?.let { runCatching { json.decodeFromString<LauncherState>(it) }.getOrNull() }?.let { _state.value = it.withNewTopics() }
            _ready.value = true
            // Sliders change the state many times a second; only the value they settle on is written.
            _state.drop(1).collectLatest { value ->
                delay(300)
                dataStore.edit { it[key] = json.encodeToString(value) }
            }
        }
    }

    fun update(change: (LauncherState) -> LauncherState) = _state.update(change)
}
