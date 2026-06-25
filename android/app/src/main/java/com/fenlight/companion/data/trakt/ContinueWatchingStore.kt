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
        val episodeStillsByTmdb: Map<Int, String?> = emptyMap(),
        val episodeNamesByTmdb: Map<Int, String?> = emptyMap(),
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
        // Exclude shows the user has hidden from progress on Trakt.
        val hiddenSlugs = runCatching { api.getHiddenProgress().body() ?: emptyList() }
            .getOrDefault(emptyList())
            .mapNotNull { it.show?.ids?.slug }
            .toSet()
        val watchedBySlug = allWatched.associateBy { it.show.ids.slug ?: "" }
            .filterKeys { it.isNotEmpty() && it !in hiddenSlugs }

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
                            val old = cacheBySlug[slug]
                            // Carry over the cached episode still/name only when the next
                            // episode is unchanged; otherwise drop it so it's re-fetched below.
                            val sameNextEp = old?.progress?.nextEpisode?.let { o ->
                                progress.nextEpisode?.let { n -> o.season == n.season && o.number == n.number }
                            } ?: false
                            fetched[slug] = CwCacheEntry(
                                slug = slug,
                                lastWatchedAt = watched.lastWatchedAt,
                                show = watched.show,
                                plays = watched.plays,
                                progress = progress,
                                posterPath = old?.posterPath,
                                episodeStillPath = if (sameNextEp) old?.episodeStillPath else null,
                                episodeName = if (sameNextEp) old?.episodeName else null,
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

        // Fetch the next-episode still + name for entries missing it (persisted so the
        // "Next Episodes" row needs no per-episode TMDB calls on a warm cache).
        val needsEpisodeMeta = filtered.filter {
            it.progress.nextEpisode != null && it.episodeStillPath == null && it.show.ids.tmdb != null
        }
        val episodeMetaUpdates = mutableMapOf<String, Pair<String?, String?>>()
        coroutineScope {
            needsEpisodeMeta.map { entry ->
                async {
                    val nextEp = entry.progress.nextEpisode!!
                    runCatching {
                        val ep = tmdbApi.episodeDetail(entry.show.ids.tmdb!!, nextEp.season, nextEp.number)
                        episodeMetaUpdates[entry.slug] = ep.stillPath to ep.name
                    }
                }
            }.awaitAll()
        }

        val finalEntries = merged.values.map { entry ->
            var updated = entry
            if (entry.slug in posterUpdates) updated = updated.copy(posterPath = posterUpdates[entry.slug])
            episodeMetaUpdates[entry.slug]?.let { (still, name) ->
                updated = updated.copy(episodeStillPath = still, episodeName = name)
            }
            updated
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
        val episodeStillsByTmdb = inProgress
            .mapNotNull { entry -> entry.show.ids.tmdb?.let { tmdb -> tmdb to entry.episodeStillPath } }
            .toMap()
        val episodeNamesByTmdb = inProgress
            .mapNotNull { entry -> entry.show.ids.tmdb?.let { tmdb -> tmdb to entry.episodeName } }
            .toMap()

        return Snapshot(
            shows = shows,
            progressBySlug = progressBySlug,
            postersByTmdb = postersByTmdb,
            episodeStillsByTmdb = episodeStillsByTmdb,
            episodeNamesByTmdb = episodeNamesByTmdb,
        )
    }
}
