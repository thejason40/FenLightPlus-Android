package com.fenlight.companion.ui.tvshows

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.fenlight.companion.FenLightApp
import com.fenlight.companion.data.api.KodiRpc
import com.fenlight.companion.data.model.Episode
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class EpisodeDetailUiState(
    val episode: Episode? = null,
    val showName: String = "",
    val showYear: Int = 0,
    val isLoading: Boolean = false,
    val error: String? = null,
    val playMessage: String? = null,
    val traktAuthed: Boolean = false,
    val episodeWatched: Boolean = false,
)

class EpisodeDetailViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as FenLightApp
    private val _state = MutableStateFlow(EpisodeDetailUiState())
    val state: StateFlow<EpisodeDetailUiState> = _state.asStateFlow()

    private var loadedKey = Triple(-1, -1, -1)

    fun load(showId: Int, season: Int, episodeNumber: Int) {
        val key = Triple(showId, season, episodeNumber)
        if (loadedKey == key && _state.value.episode != null) return
        loadedKey = key
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null, traktAuthed = app.prefs.traktAccessToken.first().isNotBlank()) }
            try {
                coroutineScope {
                    val epDeferred = async { app.tmdbApi.episodeDetail(showId, season, episodeNumber) }
                    val showDeferred = async { app.tmdbApi.tvDetail(showId, append = "") }
                    val ep = epDeferred.await()
                    val show = showDeferred.await()
                    _state.update {
                        it.copy(
                            isLoading = false,
                            episode = ep,
                            showName = show.name,
                            showYear = show.firstAirDate?.take(4)?.toIntOrNull() ?: 0,
                        )
                    }
                }
                refreshEpisodeWatched(showId, season, episodeNumber)
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun play(showId: Int, numEpisodes: Int = 1, mode: KodiRpc.PlaybackMode = KodiRpc.PlaybackMode.DEFAULT) {
        val s = _state.value
        val ep = s.episode ?: return
        viewModelScope.launch {
            try {
                val host = app.prefs.kodiHost.first()
                val port = app.prefs.kodiPort.first()
                val user = app.prefs.kodiUser.first()
                val pass = app.prefs.kodiPass.first()
                KodiRpc(host, port, user, pass)
                    .playEpisodeViaFenLight(
                        showId, s.showName, s.showYear, ep.seasonNumber, ep.episodeNumber, numEpisodes, mode,
                    )
                val message = if (numEpisodes > 1) {
                    "Playing $numEpisodes episodes from S${ep.seasonNumber}E${ep.episodeNumber} of ${s.showName} on Kodi…"
                } else {
                    "Playing S${ep.seasonNumber}E${ep.episodeNumber} of ${s.showName} on Kodi…"
                }
                _state.update { it.copy(playMessage = message) }
            } catch (e: Exception) {
                _state.update { it.copy(playMessage = "Failed: ${e.message}") }
            }
        }
    }

    fun markWatched(showId: Int, watched: Boolean) {
        val ep = _state.value.episode ?: return
        viewModelScope.launch {
            try {
                val body = mapOf(
                    "shows" to listOf(
                        mapOf(
                            "ids" to mapOf("tmdb" to showId),
                            "seasons" to listOf(
                                mapOf(
                                    "number" to ep.seasonNumber,
                                    "episodes" to listOf(mapOf("number" to ep.episodeNumber)),
                                ),
                            ),
                        ),
                    ),
                )
                if (watched) app.authedTraktApi.addToHistory(body) else app.authedTraktApi.removeFromHistory(body)
                // Watched changes affect Continue Watching; drop the cache so it re-syncs.
                app.prefs.saveTraktCwCache("")
                _state.update {
                    it.copy(
                        episodeWatched = watched,
                        playMessage = if (watched) "Marked S${ep.seasonNumber}E${ep.episodeNumber} watched" else "Marked S${ep.seasonNumber}E${ep.episodeNumber} unwatched",
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(playMessage = "Failed: ${e.message}") }
            }
        }
    }

    // Determine if this episode is already watched. Only the (cheap) progress call is made
    // for shows we know have history; unwatched shows are treated as unwatched with no call.
    private suspend fun refreshEpisodeWatched(showId: Int, season: Int, episodeNumber: Int) {
        if (!_state.value.traktAuthed) return
        val slug = app.watchedState.slugFor(showId)
        val watched = if (slug == null) {
            false
        } else {
            runCatching {
                app.authedTraktApi.showEpisodeProgress(slug)
                    .seasons.firstOrNull { it.number == season }
                    ?.episodes?.firstOrNull { it.number == episodeNumber }
                    ?.completed ?: false
            }.getOrDefault(false)
        }
        _state.update { it.copy(episodeWatched = watched) }
    }

    fun clearPlayMessage() = _state.update { it.copy(playMessage = null) }
}
