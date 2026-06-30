package com.fenlight.companion.data.api

import com.fenlight.companion.data.model.KodiDirItem
import com.fenlight.companion.data.model.KodiPlayerProperties
import com.fenlight.companion.data.model.KodiPlayingItem
import com.fenlight.companion.data.model.KodiSubtitle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class KodiRpc(
    private val host: String,
    private val port: Int,
    private val username: String = "",
    private val password: String = "",
    // Polling reuses one shared client; ad-hoc callers fall back to a private one.
    sharedClient: OkHttpClient? = null,
) {
    private val client = sharedClient ?: OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val endpoint get() = "http://$host:$port/jsonrpc"
    private val json = "application/json".toMediaType()

    private suspend fun call(method: String, params: JSONObject? = null, readTimeoutSeconds: Long = 0): JSONObject = withContext(Dispatchers.IO) {
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
        // Some calls (e.g. scraping via Files.GetDirectory) block for a long time; widen
        // the read timeout for those without disturbing the shared client's defaults.
        val httpClient = if (readTimeoutSeconds > 0) {
            client.newBuilder().readTimeout(readTimeoutSeconds, TimeUnit.SECONDS).build()
        } else client
        val response = httpClient.newCall(reqBuilder.build()).execute()
        if (!response.isSuccessful) throw IOException("Kodi returned ${response.code}")
        JSONObject(response.body?.string() ?: "{}")
    }

    suspend fun ping(): Boolean = try {
        val result = call("JSONRPC.Ping")
        result.optString("result") == "pong"
    } catch (_: Exception) {
        false
    }

    // ---- Device source selection ----

    /**
     * Runs a plugin directory and returns its items over JSON-RPC. Used to list scraped
     * sources (a long, blocking call while FenLight+ scrapes). JSON-RPC errors surface as
     * an empty list rather than throwing.
     */
    suspend fun getDirectory(directory: String, readTimeoutSeconds: Long = 120): List<KodiDirItem> {
        val params = JSONObject().put("directory", directory).put("media", "video")
        val result = call("Files.GetDirectory", params, readTimeoutSeconds).optJSONObject("result")
            ?: return emptyList()
        val arr = result.optJSONArray("files") ?: return emptyList()
        val out = ArrayList<KodiDirItem>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            out.add(KodiDirItem(label = o.optString("label"), file = o.optString("file")))
        }
        return out
    }

    /** Fork-safe probe: true only when the installed FenLight build implements device select. */
    suspend fun supportsDeviceSelect(): Boolean = try {
        getDirectory("plugin://plugin.video.fenlight/?mode=app_capabilities", readTimeoutSeconds = 10)
            .any { it.file.contains("device_select=1") }
    } catch (_: Exception) {
        false
    }

    /** Scrape sources for on-device selection (blocks while FenLight+ scrapes silently). */
    suspend fun listSources(query: String): List<KodiDirItem> =
        getDirectory("plugin://plugin.video.fenlight/?mode=app_list_sources&$query")

    // ---- Now-playing: state reads ----

    /** Returns the active video player id, or null when nothing is playing. */
    suspend fun activeVideoPlayerId(): Int? {
        val arr = call("Player.GetActivePlayers").optJSONArray("result") ?: return null
        if (arr.length() == 0) return null
        for (i in 0 until arr.length()) {
            val p = arr.optJSONObject(i) ?: continue
            if (p.optString("type") == "video") return p.optInt("playerid")
        }
        return arr.optJSONObject(0)?.optInt("playerid")
    }

    suspend fun playerProperties(playerId: Int): KodiPlayerProperties {
        val params = JSONObject()
            .put("playerid", playerId)
            .put(
                "properties",
                JSONArray(listOf("time", "totaltime", "percentage", "speed", "subtitleenabled", "currentsubtitle", "subtitles")),
            )
        val r = call("Player.GetProperties", params).optJSONObject("result") ?: JSONObject()
        val subtitleEnabled = r.optBoolean("subtitleenabled", false)
        val currentSub = r.optJSONObject("currentsubtitle")
        val currentIndex = if (subtitleEnabled && currentSub != null && currentSub.has("index")) currentSub.optInt("index") else null
        val subs = mutableListOf<KodiSubtitle>()
        r.optJSONArray("subtitles")?.let { a ->
            for (i in 0 until a.length()) {
                val s = a.optJSONObject(i) ?: continue
                subs += KodiSubtitle(
                    index = s.optInt("index", i),
                    name = s.optString("name", ""),
                    language = s.optString("language", ""),
                )
            }
        }
        return KodiPlayerProperties(
            positionSec = r.optJSONObject("time").secondsOrZero(),
            durationSec = r.optJSONObject("totaltime").secondsOrZero(),
            percentage = r.optDouble("percentage", 0.0),
            speed = r.optInt("speed", 0),
            subtitleEnabled = subtitleEnabled,
            currentSubtitleIndex = currentIndex,
            subtitles = subs,
        )
    }

    suspend fun playerItem(playerId: Int): KodiPlayingItem {
        val params = JSONObject()
            .put("playerid", playerId)
            .put("properties", JSONArray(listOf("title", "showtitle", "season", "episode", "year", "uniqueid", "file")))
        val item = call("Player.GetItem", params).optJSONObject("result")?.optJSONObject("item") ?: JSONObject()
        val uniqueTmdb = item.optJSONObject("uniqueid")?.optString("tmdb").orEmptyToNull()?.toIntOrNull()
        val file = item.optString("file").ifBlank { null }
        return KodiPlayingItem(
            type = item.optString("type"),
            title = item.optString("title"),
            showTitle = item.optString("showtitle").ifBlank { null },
            season = item.optInt("season", -1).takeIf { it >= 0 },
            episode = item.optInt("episode", -1).takeIf { it >= 0 },
            year = item.optInt("year", 0).takeIf { it > 0 },
            tmdbId = uniqueTmdb ?: parseTmdbIdFromFile(file),
            file = file,
        )
    }

    // ---- Now-playing: controls ----

    suspend fun playPause(playerId: Int) {
        call("Player.PlayPause", JSONObject().put("playerid", playerId))
    }

    suspend fun stopPlayer(playerId: Int) {
        call("Player.Stop", JSONObject().put("playerid", playerId))
    }

    /** Absolute seek to a percentage (0..100) — used by the scrubber. */
    suspend fun seekPercentage(playerId: Int, percent: Double) {
        val params = JSONObject()
            .put("playerid", playerId)
            .put("value", JSONObject().put("percentage", percent.coerceIn(0.0, 100.0)))
        call("Player.Seek", params)
    }

    /** Relative seek by [seconds] (negative to rewind) — used by skip buttons. */
    suspend fun seekRelative(playerId: Int, seconds: Int) {
        val params = JSONObject()
            .put("playerid", playerId)
            .put("value", JSONObject().put("seconds", seconds))
        call("Player.Seek", params)
    }

    /** Select a specific subtitle track and show it. */
    suspend fun setSubtitleIndex(playerId: Int, index: Int) {
        call("Player.SetSubtitle", JSONObject().put("playerid", playerId).put("subtitle", index).put("enable", true))
    }

    /** [command] is one of "off", "on", "next", "previous". */
    suspend fun setSubtitle(playerId: Int, command: String) {
        call("Player.SetSubtitle", JSONObject().put("playerid", playerId).put("subtitle", command))
    }

    private fun JSONObject?.secondsOrZero(): Int =
        if (this == null) 0 else optInt("hours") * 3600 + optInt("minutes") * 60 + optInt("seconds")

    private fun String?.orEmptyToNull(): String? = this?.ifBlank { null }

    private fun parseTmdbIdFromFile(file: String?): Int? {
        if (file == null) return null
        val m = Regex("[?&]tmdb_id=(\\d+)").find(file) ?: return null
        return m.groupValues[1].toIntOrNull()
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
