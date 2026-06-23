package com.fenlight.companion.data.model

import org.junit.Assert.assertEquals
import org.junit.Test

class MediaTypeTest {

    @Test
    fun from_acceptsRouteAndLegacyStrings() {
        assertEquals(MediaType.MOVIE, MediaType.from("movie"))
        assertEquals(MediaType.TV, MediaType.from("tv"))
        assertEquals(MediaType.TV, MediaType.from("show"))
        assertEquals(MediaType.MOVIE, MediaType.from("anything"))
    }

    @Test
    fun apiStrings() {
        assertEquals("movie", MediaType.MOVIE.tmdbName)
        assertEquals("movies", MediaType.MOVIE.traktCollection)
        assertEquals("tv", MediaType.TV.tmdbName)
        assertEquals("shows", MediaType.TV.traktCollection)
    }
}
