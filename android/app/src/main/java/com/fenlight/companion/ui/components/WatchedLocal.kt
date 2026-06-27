package com.fenlight.companion.ui.components

import androidx.compose.runtime.compositionLocalOf

/**
 * Watched lookup provided at the app root so poster cards can show a watched indicator
 * without threading state through every screen. Show fractions are resolved lazily:
 * a card calls [requestProgress] when it appears, and the result flows back via [showProgress].
 */
data class WatchedLookup(
    val movieIds: Set<Int> = emptySet(),
    val showIds: Set<Int> = emptySet(),
    val showProgress: Map<Int, Float> = emptyMap(),
    val requestProgress: (Int) -> Unit = {},
) {
    /** null = no indicator; 1f = fully watched (tick); 0<x<1 = partially watched (pie). */
    fun progressFor(tmdbId: Int): Float? = when {
        tmdbId in movieIds -> 1f
        else -> showProgress[tmdbId]?.takeIf { it > 0f }
    }

    /** A watched show whose fraction we haven't fetched yet. */
    fun needsProgress(tmdbId: Int): Boolean = tmdbId in showIds && tmdbId !in showProgress
}

val LocalWatchedState = compositionLocalOf { WatchedLookup() }
