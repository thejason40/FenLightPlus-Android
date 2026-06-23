package com.fenlight.companion.ui.movies

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.fenlight.companion.FenLightApp
import com.fenlight.companion.data.api.KodiRpc
import com.fenlight.companion.data.model.Movie
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class MovieDetailUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val movie: Movie? = null,
    val collectionName: String? = null,
    val collectionParts: List<Movie> = emptyList(),
    val playMessage: String? = null,
)

/**
 * Lightweight ViewModel for the movie detail screen. Unlike [MovieViewModel] it does not
 * eagerly load the popular list + genres on init, so opening a detail screen makes only
 * the single detail request.
 */
class MovieDetailViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as FenLightApp
    private val _state = MutableStateFlow(MovieDetailUiState())
    val state: StateFlow<MovieDetailUiState> = _state.asStateFlow()

    fun loadMovieDetail(tmdbId: Int) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null, collectionName = null, collectionParts = emptyList()) }
            try {
                val movie = app.tmdbApi.movieDetail(tmdbId)
                _state.update { it.copy(isLoading = false, movie = movie) }

                // If the movie is part of a collection, load the other films in it as a row.
                movie.belongsToCollection?.let { collection ->
                    try {
                        val detail = app.tmdbApi.collectionDetail(collection.id)
                        val parts = detail.parts.sortedBy { it.releaseDate ?: "" }
                        _state.update { it.copy(collectionName = detail.name, collectionParts = parts) }
                    } catch (_: Exception) {
                        // Collection art is non-critical; ignore failures.
                    }
                }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun playMovie(movie: Movie) {
        viewModelScope.launch {
            try {
                val host = app.prefs.kodiHost.first()
                val port = app.prefs.kodiPort.first()
                val user = app.prefs.kodiUser.first()
                val pass = app.prefs.kodiPass.first()
                val kodi = KodiRpc(host, port, user, pass)
                val year = movie.releaseDate?.take(4)?.toIntOrNull() ?: 0
                kodi.playMovieViaFenLight(movie.id, movie.title, year)
                _state.update { it.copy(playMessage = "Playing ${movie.title} on Kodi…") }
            } catch (e: Exception) {
                _state.update { it.copy(playMessage = "Failed to send play command: ${e.message}") }
            }
        }
    }

    fun clearPlayMessage() = _state.update { it.copy(playMessage = null) }
}
