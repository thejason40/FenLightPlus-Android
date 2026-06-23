package com.fenlight.companion.data.model

/**
 * The two media kinds the app browses, with the exact strings each upstream API expects.
 * Navigation routes and persisted row configs keep using raw strings; convert at the edge
 * with [from].
 */
enum class MediaType(val tmdbName: String, val traktCollection: String) {
    MOVIE("movie", "movies"),
    TV("tv", "shows");

    companion object {
        /** Accepts the route/legacy strings: "movie", "tv", "show". */
        fun from(raw: String): MediaType = if (raw == "tv" || raw == "show") TV else MOVIE
    }
}
