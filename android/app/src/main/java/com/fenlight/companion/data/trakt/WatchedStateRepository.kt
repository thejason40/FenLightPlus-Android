package com.fenlight.companion.data.trakt

import com.fenlight.companion.FenLightApp
import com.fenlight.companion.data.model.WatchedCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * App-wide watched state from Trakt for the watched/unwatched toggle and poster indicators.
 *
 * Cost-conscious: [refresh] makes only two cheap calls — all watched movie ids, and the set
 * of shows with any history. A show's completion *fraction* (for the tick vs pie) is fetched
 * lazily via [requestShowProgress], called by cards as they scroll into view, so we never
 * sweep progress for the whole library up front.
 */
class WatchedStateRepository(private val app: FenLightApp) {

    data class State(
        val movieIds: Set<Int> = emptySet(),          // watched movies -> full tick
        val showIds: Set<Int> = emptySet(),           // shows with any history (fraction TBD)
        val showProgress: Map<Int, Float> = emptyMap(), // resolved fractions (lazy)
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val slugByTmdb = ConcurrentHashMap<Int, String>()
    private val inFlight = ConcurrentHashMap.newKeySet<Int>()
    private val cacheAdapter by lazy { app.moshi.adapter(WatchedCache::class.java) }

    /** Load the persisted snapshot so indicators show instantly on launch. Idempotent. */
    suspend fun loadCache() {
        val current = _state.value
        if (current.movieIds.isNotEmpty() || current.showIds.isNotEmpty()) return
        val json = app.prefs.watchedStateJson.first()
        if (json.isBlank()) return
        val c = runCatching { cacheAdapter.fromJson(json) }.getOrNull() ?: return
        // Restore movie ids + show fractions for instant display. showIds is left empty so
        // cards don't try to lazily fetch (slugs aren't persisted); refresh() repopulates it.
        _state.value = State(
            movieIds = c.movieIds.toSet(),
            showIds = emptySet(),
            showProgress = c.showProgress.mapNotNull { (k, v) -> k.toIntOrNull()?.let { it to v } }.toMap(),
        )
    }

    private fun persist() {
        val s = _state.value
        scope.launch {
            val cache = WatchedCache(
                movieIds = s.movieIds.toList(),
                showIds = s.showIds.toList(),
                showProgress = s.showProgress.mapKeys { it.key.toString() },
            )
            runCatching { app.prefs.saveWatchedState(cacheAdapter.toJson(cache)) }
        }
    }

    /** Two cheap calls: watched movie ids, and the set of watched shows (+ their slugs). */
    suspend fun refresh() {
        if (app.prefs.traktAccessToken.first().isBlank()) {
            slugByTmdb.clear()
            _state.value = State()
            persist()
            return
        }
        coroutineScope {
            launch {
                val movies = runCatching {
                    app.authedTraktApi.watchedMovies().mapNotNull { it.movie.ids.tmdb }.toSet()
                }.getOrDefault(emptySet())
                _state.update { it.copy(movieIds = movies) }
            }
            launch {
                val watched = runCatching { app.authedTraktApi.watchedShows() }.getOrDefault(emptyList())
                val ids = mutableSetOf<Int>()
                watched.forEach { w ->
                    val tmdb = w.show.ids.tmdb
                    val slug = w.show.ids.slug
                    if (tmdb != null && slug != null) {
                        ids.add(tmdb)
                        slugByTmdb[tmdb] = slug
                    }
                }
                _state.update { it.copy(showIds = ids) }
            }
        }
        persist()
    }

    /** Resolve one watched show's completion fraction on demand (deduped, cached). No-op for
     *  unwatched shows or ones already resolved. */
    fun requestShowProgress(tmdbId: Int) {
        val s = _state.value
        if (tmdbId !in s.showIds || tmdbId in s.showProgress) return
        if (!inFlight.add(tmdbId)) return
        scope.launch {
            try {
                val slug = slugByTmdb[tmdbId] ?: return@launch
                val frac = runCatching {
                    val p = app.authedTraktApi.showProgress(slug)
                    if (p.aired > 0) (p.completed.toFloat() / p.aired).coerceIn(0f, 1f) else 0f
                }.getOrNull()
                if (frac != null) {
                    _state.update { it.copy(showProgress = it.showProgress + (tmdbId to frac)) }
                    persist()
                }
            } finally {
                inFlight.remove(tmdbId)
            }
        }
    }

    /** Trakt slug for a watched show, if known (populated by [refresh]). */
    fun slugFor(tmdbId: Int): String? = slugByTmdb[tmdbId]

    /** Optimistic update after a mark-watched/unwatched action. */
    fun setWatched(tmdbId: Int, isMovie: Boolean, watched: Boolean) {
        _state.update { s ->
            if (isMovie) {
                s.copy(movieIds = if (watched) s.movieIds + tmdbId else s.movieIds - tmdbId)
            } else {
                s.copy(
                    showIds = if (watched) s.showIds + tmdbId else s.showIds - tmdbId,
                    showProgress = if (watched) s.showProgress + (tmdbId to 1f) else s.showProgress - tmdbId,
                )
            }
        }
        persist()
    }

    fun clear() {
        slugByTmdb.clear()
        _state.value = State()
        persist()
    }
}
