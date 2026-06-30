package com.fenlight.companion.ui.sources

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.fenlight.companion.FenLightApp
import com.fenlight.companion.data.api.KodiRpc
import com.fenlight.companion.data.model.FenLightSource
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SourceSelectionUiState(
    val title: String = "",
    val loading: Boolean = true,
    val sources: List<FenLightSource> = emptyList(),
    val error: String? = null,
    val playMessage: String? = null,
)

/**
 * Drives on-device source selection: asks FenLight+ to scrape (a long, blocking
 * `Files.GetDirectory` call), shows the returned sources, and plays the chosen one.
 */
class SourceSelectionViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as FenLightApp
    private val _state = MutableStateFlow(SourceSelectionUiState())
    val state: StateFlow<SourceSelectionUiState> = _state.asStateFlow()

    private var job: Job? = null
    private var started = false

    /** [query] is the scrape query string (tmdb_id=…&media_type=…&… plus scrape flags). */
    fun start(query: String, title: String) {
        if (started) return
        started = true
        _state.update { it.copy(title = title, loading = true, error = null) }
        job = viewModelScope.launch {
            try {
                val items = kodi().listSources(query)
                _state.update { it.copy(loading = false, sources = items.map { i -> FenLightSource.fromDirItem(i) }) }
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = e.message ?: "Couldn't get sources") }
            }
        }
    }

    fun cancel() {
        job?.cancel()
        _state.update { it.copy(loading = false) }
    }

    fun playSource(source: FenLightSource) {
        viewModelScope.launch {
            try {
                kodi().playUrl(source.file)
                _state.update { it.copy(playMessage = "Playing on Kodi…") }
            } catch (e: Exception) {
                _state.update { it.copy(playMessage = "Failed: ${e.message}") }
            }
        }
    }

    fun clearPlayMessage() = _state.update { it.copy(playMessage = null) }

    private suspend fun kodi(): KodiRpc = KodiRpc(
        app.prefs.kodiHost.first(),
        app.prefs.kodiPort.first(),
        app.prefs.kodiUser.first(),
        app.prefs.kodiPass.first(),
    )
}
