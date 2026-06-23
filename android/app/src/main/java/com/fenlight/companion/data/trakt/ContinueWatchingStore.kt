package com.fenlight.companion.data.trakt

import com.fenlight.companion.data.api.TmdbApi
import com.fenlight.companion.data.api.TraktApi
import com.fenlight.companion.data.model.CwCache
import com.fenlight.companion.data.model.CwCacheEntry
import com.fenlight.companion.data.model.TraktShowProgress
import com.fenlight.companion.data.model.TraktWatchedShow
import com.fenlight.companion.data.prefs.AppPreferences
import com.squareup.moshi.Moshi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import java.time.LocalDate

class ContinueWatchingStore(private val prefs: AppPreferences, private val moshi: Moshi) {

    data class Snapshot(
        val shows: List<TraktWatchedShow>,
        val progressBySlug: Map<String, TraktShowProgress>,
        val postersByTmdb: Map<Int, String?>,
    )

    private val adapter by lazy { moshi.adapter(CwCache::class.java) }

    private suspend fun readCache(): CwCache {
        val json = prefs.traktCwCacheJson.first()
        if (json.isBlank()) return CwCache()
        return runCatching { adapter.fromJson(json) }
            .getOrNull()
            ?.takeIf { it.version == 1 }
            ?: CwCache()
    }

    suspend fun cachedSnapshot(): Snapshot? {
        val cache = readCache()
        if (cache.entries.isEmpty()) return null
        return buildSnapshot(cache.entries)
    }

    suspend fun sync(api: TraktApi, tmdbApi: TmdbApi): Snapshot {
        val allWatched = api.watchedShows()
        val watchedBySlug = allWatched.associateBy { it.show.ids.slug ?: "" }.filterKeys { it.isNotEmpty() }

        val cache = readCache()
        val cacheBySlug = cache.entries.associateBy { it.slug }

        val today = LocalDate.now().toString()
        val now = System.currentTimeMillis()
        val staleThresholdMs = 24 * 60 * 60 * 1000L

        val toFetch = mutableListOf<String>()
        val reused = mutableMapOf<String, CwCacheEntry>()

        for ((slug, watched) in watchedBySlug) {
            val cached = cacheBySlug[slug]
            if (cached != null && cached.lastWatchedAt == watched.lastWatchedAt) {
                val nextEp = cached.progress.nextEpisode
                val airDate = nextEp?.firstAired?.take(10)
                val hasAiredNextEp = nextEp != null && (airDate == null || airDate <= today)
                val caughtUpAndFresh = !hasAiredNextEp && (now - cached.fetchedAt) < staleThresholdMs
                if (hasAiredNextEp || caughtUpAndFresh) {
                    reused[slug] = cached
                    continue
                }
            }
            toFetch.add(slug)
        }

        val fetched = mutableMapOf<String, CwCacheEntry>()
        toFetch.chunked(15).forEach { chunk ->
            coroutineScope {
                chunk.map { slug ->
                    async {
                        val watched = watchedBySlug[slug] ?: return@async
                        runCatching {
                            val progress = api.showProgress(slug)
                            fetched[slug] = CwCacheEntry(
                                slug = slug,
                                lastWatchedAt = watched.lastWatchedAt,
                                show = watched.show,
                                plays = watched.plays,
                                progress = progress,
                                posterPath = cacheBySlug[slug]?.posterPath,
                                fetchedAt = now,
                            )
                        }.onFailure {
                            // Keep old entry on transient failure to avoid dropping shows
                            cacheBySlug[slug]?.let { old -> fetched[slug] = old }
                        }
                    }
                }.awaitAll()
            }
        }

        // Merge: fetched overrides reused; prune to slugs in the current watched list
        val merged = (reused + fetched).filterKeys { it in watchedBySlug }

        // Fetch missing posters for entries that pass the in-progress filter
        val filtered = filterInProgress(merged.values.toList(), today)
        val needsPosters = filtered.filter { it.progress.nextEpisode != null && it.posterPath == null && it.show.ids.tmdb != null }
        val posterUpdates = mutableMapOf<String, String?>()
        coroutineScope {
            needsPosters.map { entry ->
                async {
                    runCatching {
                        val path = tmdbApi.tvDetail(entry.show.ids.tmdb!!, "").posterPath
                        posterUpdates[entry.slug] = path
                    }
                }
            }.awaitAll()
        }

        val finalEntries = merged.values.map { entry ->
            if (entry.slug in posterUpdates) entry.copy(posterPath = posterUpdates[entry.slug]) else entry
        }

        val newCache = CwCache(version = 1, entries = finalEntries)
        prefs.saveTraktCwCache(adapter.toJson(newCache))

        return buildSnapshot(finalEntries)
    }

    private fun filterInProgress(entries: List<CwCacheEntry>, today: String): List<CwCacheEntry> {
        return entries.filter { entry ->
            val nextEp = entry.progress.nextEpisode ?: return@filter false
            val airDate = nextEp.firstAired?.take(10)
            airDate == null || airDate <= today
        }
    }

    private fun buildSnapshot(entries: List<CwCacheEntry>): Snapshot {
        val today = LocalDate.now().toString()
        val inProgress = filterInProgress(entries, today)
            .sortedByDescending { it.lastWatchedAt }

        val shows = inProgress.map { entry ->
            TraktWatchedShow(
                plays = entry.plays,
                lastWatchedAt = entry.lastWatchedAt,
                show = entry.show,
                seasons = emptyList(),
            )
        }
        val progressBySlug = inProgress.associate { it.slug to it.progress }
        val postersByTmdb = inProgress
            .mapNotNull { entry -> entry.show.ids.tmdb?.let { tmdb -> tmdb to entry.posterPath } }
            .toMap()

        return Snapshot(shows = shows, progressBySlug = progressBySlug, postersByTmdb = postersByTmdb)
    }
}
