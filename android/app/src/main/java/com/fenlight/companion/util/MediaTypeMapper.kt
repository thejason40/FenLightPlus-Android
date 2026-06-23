package com.fenlight.companion.util

import com.fenlight.companion.data.model.MediaType

/**
 * Maps the caller-supplied media type (which may be "movie", "show", or "tv" depending on
 * the source screen) to the exact strings each upstream API expects. The canonical mapping
 * lives in [MediaType]; this remains as the string-to-string convenience for call sites
 * that haven't adopted the enum.
 */
object MediaTypeMapper {

    /** Trakt request bodies use the plural collection name. */
    fun traktKey(mediaType: String): String = MediaType.from(mediaType).traktCollection

    /** TMDB v4 uses the singular "tv"/"movie". */
    fun tmdbType(mediaType: String): String = MediaType.from(mediaType).tmdbName
}
