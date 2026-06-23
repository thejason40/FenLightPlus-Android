package com.fenlight.companion.data.model

import com.squareup.moshi.Json

data class CwCacheEntry(
    val slug: String,
    @Json(name = "last_watched_at") val lastWatchedAt: String,
    val show: TraktShow,
    val plays: Int = 0,
    val progress: TraktShowProgress,
    @Json(name = "poster_path") val posterPath: String? = null,
    @Json(name = "fetched_at") val fetchedAt: Long = 0L,
)

data class CwCache(val version: Int = 1, val entries: List<CwCacheEntry> = emptyList())
