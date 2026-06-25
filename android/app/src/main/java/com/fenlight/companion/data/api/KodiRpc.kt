package com.fenlight.companion.data.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class KodiRpc(
    private val host: String,
    private val port: Int,
    private val username: String = "",
    private val password: String = "",
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val endpoint get() = "http://$host:$port/jsonrpc"
    private val json = "application/json".toMediaType()

    private suspend fun call(method: String, params: JSONObject? = null): JSONObject = withContext(Dispatchers.IO) {
        val body = JSONObject().apply {
            put("jsonrpc", "2.0")
            put("id", 1)
            put("method", method)
            if (params != null) put("params", params)
        }
        val reqBuilder = Request.Builder()
            .url(endpoint)
            .post(body.toString().toRequestBody(json))
        if (username.isNotBlank()) {
            reqBuilder.header("Authorization", Credentials.basic(username, password))
        }
        val response = client.newCall(reqBuilder.build()).execute()
        if (!response.isSuccessful) throw IOException("Kodi returned ${response.code}")
        JSONObject(response.body?.string() ?: "{}")
    }

    suspend fun ping(): Boolean = try {
        val result = call("JSONRPC.Ping")
        result.optString("result") == "pong"
    } catch (_: Exception) {
        false
    }

    suspend fun playUrl(url: String) {
        val item = JSONObject().put("file", url)
        val params = JSONObject().put("item", item)
        call("Player.Open", params)
    }

    suspend fun playMovieViaFenLight(tmdbId: Int, title: String, year: Int, mode: PlaybackMode = PlaybackMode.DEFAULT) {
        playUrl(buildMovieUrl(tmdbId, title, year, mode))
    }

    /**
     * Sends an episode to FenLight+ for playback.
     *
     * When [numEpisodes] is greater than 1, FenLight+ binges that many episodes
     * back-to-back starting from the given one (it resolves each subsequent
     * episode itself via the `num_episodes` plugin parameter).
     */
    suspend fun playEpisodeViaFenLight(
        tmdbId: Int,
        title: String,
        year: Int,
        season: Int,
        episode: Int,
        numEpisodes: Int? = null,
        mode: PlaybackMode = PlaybackMode.DEFAULT,
    ) {
        playUrl(buildEpisodeUrl(tmdbId, title, year, season, episode, numEpisodes, mode))
    }

    /**
     * How FenLight+ should resolve a stream. DEFAULT autoplays the best match;
     * the others turn autoplay off so the source dialog appears on the Kodi device,
     * optionally widening the scrapers / relaxing filters.
     */
    enum class PlaybackMode(val autoplay: Boolean, val extraParams: Map<String, String>) {
        DEFAULT(true, emptyMap()),
        SELECT_SOURCE(false, emptyMap()),
        ALL_SOURCES(false, mapOf("disabled_ext_ignored" to "true", "prescrape" to "false")),
        IGNORE_FILTERS(false, mapOf("ignore_scrape_filters" to "true", "prescrape" to "false")),
    }

    companion object {
        private fun String.encodeUrl() = java.net.URLEncoder.encode(this, "UTF-8")

        private fun StringBuilder.appendMode(mode: PlaybackMode) {
            append("&autoplay=${mode.autoplay}")
            mode.extraParams.forEach { (k, v) -> append("&$k=$v") }
        }

        fun buildMovieUrl(tmdbId: Int, title: String, year: Int, mode: PlaybackMode = PlaybackMode.DEFAULT): String = buildString {
            append("plugin://plugin.video.fenlight/?mode=playback.media")
            append("&media_type=movie")
            append("&tmdb_id=$tmdbId")
            append("&title=${title.encodeUrl()}")
            append("&year=$year")
            appendMode(mode)
        }

        fun buildEpisodeUrl(
            tmdbId: Int,
            title: String,
            year: Int,
            season: Int,
            episode: Int,
            numEpisodes: Int? = null,
            mode: PlaybackMode = PlaybackMode.DEFAULT,
        ): String = buildString {
            append("plugin://plugin.video.fenlight/?mode=playback.media")
            append("&media_type=episode")
            append("&tmdb_id=$tmdbId")
            append("&title=${title.encodeUrl()}")
            append("&year=$year")
            append("&season=$season")
            append("&episode=$episode")
            if (numEpisodes != null && numEpisodes > 1) append("&num_episodes=$numEpisodes")
            appendMode(mode)
        }
    }
}
