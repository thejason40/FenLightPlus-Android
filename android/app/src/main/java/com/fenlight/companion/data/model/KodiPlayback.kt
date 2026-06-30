package com.fenlight.companion.data.model

/** A raw directory item returned by Files.GetDirectory (label + plugin file URL). */
data class KodiDirItem(
    val label: String,
    val file: String,
)

/** A subtitle track reported by Kodi for the currently-playing item. */
data class KodiSubtitle(
    val index: Int,
    val name: String,
    val language: String,
)

/** Live playback properties from `Player.GetProperties`. */
data class KodiPlayerProperties(
    val positionSec: Int,
    val durationSec: Int,
    val percentage: Double,
    val speed: Int,
    val subtitleEnabled: Boolean,
    val currentSubtitleIndex: Int?,
    val subtitles: List<KodiSubtitle>,
) {
    val isPaused: Boolean get() = speed == 0
}

/** Identity of the currently-playing item from `Player.GetItem`. */
data class KodiPlayingItem(
    val type: String,        // "movie", "episode", or "" when unknown
    val title: String,
    val showTitle: String?,
    val season: Int?,
    val episode: Int?,
    val year: Int?,
    val tmdbId: Int?,
    val file: String?,
)
