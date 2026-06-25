package com.fenlight.companion.data.api

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KodiRpcUrlTest {

    @Test
    fun episodeUrl_includesNumEpisodes_whenGreaterThanOne() {
        val url = KodiRpc.buildEpisodeUrl(
            tmdbId = 1399, title = "Game of Thrones", year = 2011,
            season = 2, episode = 4, numEpisodes = 3,
        )
        assertTrue(url.contains("media_type=episode"))
        assertTrue(url.contains("tmdb_id=1399"))
        assertTrue(url.contains("season=2"))
        assertTrue(url.contains("episode=4"))
        assertTrue(url.contains("num_episodes=3"))
    }

    @Test
    fun episodeUrl_omitsNumEpisodes_whenOneOrNull() {
        val single = KodiRpc.buildEpisodeUrl(
            tmdbId = 1399, title = "Game of Thrones", year = 2011,
            season = 2, episode = 4, numEpisodes = 1,
        )
        val none = KodiRpc.buildEpisodeUrl(
            tmdbId = 1399, title = "Game of Thrones", year = 2011,
            season = 2, episode = 4, numEpisodes = null,
        )
        assertFalse(single.contains("num_episodes"))
        assertFalse(none.contains("num_episodes"))
    }

    @Test
    fun episodeUrl_encodesTitle() {
        val url = KodiRpc.buildEpisodeUrl(
            tmdbId = 1, title = "It's Always Sunny", year = 2005,
            season = 1, episode = 1,
        )
        // Space must be percent/plus encoded, never a raw space.
        assertFalse(url.contains(" "))
        assertTrue(url.contains("title=It"))
    }
}
