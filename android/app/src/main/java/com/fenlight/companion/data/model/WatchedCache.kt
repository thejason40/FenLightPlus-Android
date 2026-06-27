package com.fenlight.companion.data.model

/**
 * Persisted snapshot of Trakt watched state so poster indicators appear instantly on launch
 * (before the background refresh). Show progress keys are stringified tmdb ids for JSON.
 */
data class WatchedCache(
    val movieIds: List<Int> = emptyList(),
    val showIds: List<Int> = emptyList(),
    val showProgress: Map<String, Float> = emptyMap(),
)
