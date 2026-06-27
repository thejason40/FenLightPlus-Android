package com.fenlight.companion.data.kodi

import com.fenlight.companion.FenLightApp
import com.fenlight.companion.data.api.KodiRpc
import com.fenlight.companion.data.model.KodiSubtitle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/** Snapshot of what's playing on Kodi, surfaced to the mini-bar and full screen. */
data class NowPlayingUiState(
    val isActive: Boolean = false,
    val playerId: Int = -1,
    val title: String = "",
    val subtitleLine: String? = null,
    val artUrl: String? = null,
    val positionSec: Int = 0,
    val durationSec: Int = 0,
    val isPaused: Boolean = false,
    val subtitleEnabled: Boolean = false,
    val currentSubtitleIndex: Int? = null,
    val subtitles: List<KodiSubtitle> = emptyList(),
) {
    val isSeekable: Boolean get() = durationSec > 0
}

/**
 * Single source of truth for now-playing state. Polled from the UI (foreground only),
 * shared by the mini-bar and the full-screen remote. Artwork is resolved from TMDB via
 * the id Kodi reports; when nothing resolves, [NowPlayingUiState.artUrl] stays null.
 */
class NowPlayingRepository(private val app: FenLightApp) {

    private val _state = MutableStateFlow(NowPlayingUiState())
    val state: StateFlow<NowPlayingUiState> = _state.asStateFlow()

    // Short timeouts so a dead Kodi never stalls the 1s loop.
    private val pollClient = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private data class Conn(val host: String, val port: Int, val user: String, val pass: String)
    private var cachedConn: Conn? = null
    private var cachedRpc: KodiRpc? = null

    // Art resolution is keyed per item so we don't re-resolve every poll, and negatives are cached.
    private val artCache = mutableMapOf<String, String?>()
    private var currentArtKey: String? = null

    private suspend fun currentKodi(): KodiRpc? {
        val host = app.prefs.kodiHost.first()
        if (host.isBlank()) return null
        val conn = Conn(host, app.prefs.kodiPort.first(), app.prefs.kodiUser.first(), app.prefs.kodiPass.first())
        if (conn != cachedConn || cachedRpc == null) {
            cachedConn = conn
            cachedRpc = KodiRpc(conn.host, conn.port, conn.user, conn.pass, pollClient)
        }
        return cachedRpc
    }

    /** Foreground poll loop: 1s while active, slow heartbeat when idle. Cancel to stop. */
    suspend fun poll() {
        while (true) {
            refreshOnce()
            delay(if (_state.value.isActive) 1000L else 5000L)
        }
    }

    suspend fun refreshOnce() {
        val kodi = currentKodi()
        if (kodi == null) {
            setInactive()
            return
        }
        try {
            val playerId = kodi.activeVideoPlayerId()
            if (playerId == null) {
                setInactive()
                return
            }
            val props = kodi.playerProperties(playerId)
            val item = kodi.playerItem(playerId)

            val isEpisode = item.type == "episode"
            val title = when {
                isEpisode -> item.showTitle ?: item.title.ifBlank { fileName(item.file) }
                item.title.isNotBlank() -> item.title
                else -> fileName(item.file)
            }
            val subtitleLine = when {
                isEpisode && item.season != null && item.episode != null -> {
                    val name = item.title.takeIf { it.isNotBlank() }
                    "S${item.season}E${"%02d".format(item.episode)}" + (name?.let { " · $it" } ?: "")
                }
                item.year != null -> item.year.toString()
                else -> null
            }

            val artKey = item.tmdbId?.let { "${item.type}:$it:${item.season}:${item.episode}" }
                ?: item.file
                ?: "unknown"
            resolveArtIfNeeded(artKey, item)

            _state.update {
                it.copy(
                    isActive = true,
                    playerId = playerId,
                    title = title,
                    subtitleLine = subtitleLine,
                    artUrl = artCache[artKey],
                    positionSec = props.positionSec,
                    durationSec = props.durationSec,
                    isPaused = props.isPaused,
                    subtitleEnabled = props.subtitleEnabled,
                    currentSubtitleIndex = props.currentSubtitleIndex,
                    subtitles = props.subtitles,
                )
            }
        } catch (_: Exception) {
            setInactive()
        }
    }

    private fun setInactive() {
        if (_state.value.isActive || _state.value.playerId != -1) {
            _state.value = NowPlayingUiState()
            currentArtKey = null
        }
    }

    private fun resolveArtIfNeeded(artKey: String, item: com.fenlight.companion.data.model.KodiPlayingItem) {
        if (artKey == currentArtKey) return
        currentArtKey = artKey
        if (artCache.containsKey(artKey)) return
        // Mark pending (null) and resolve off the poll path.
        artCache[artKey] = null
        scope.launch {
            val url = runCatching { resolveArt(item) }.getOrNull()
            artCache[artKey] = url
            if (currentArtKey == artKey) _state.update { it.copy(artUrl = url) }
        }
    }

    private suspend fun resolveArt(item: com.fenlight.companion.data.model.KodiPlayingItem): String? {
        val tmdbId = item.tmdbId ?: return null
        return when {
            item.type == "episode" && item.season != null && item.episode != null -> {
                val still = runCatching {
                    app.tmdbApi.episodeDetail(tmdbId, item.season, item.episode).stillPath
                }.getOrNull()
                FenLightApp.backdropUrl(still)
                    ?: runCatching { FenLightApp.posterUrl(app.tmdbApi.tvDetail(tmdbId, "").posterPath) }.getOrNull()
            }
            else -> {
                val movie = runCatching { app.tmdbApi.movieDetail(tmdbId) }.getOrNull() ?: return null
                FenLightApp.backdropUrl(movie.backdropPath) ?: FenLightApp.posterUrl(movie.posterPath)
            }
        }
    }

    private fun fileName(file: String?): String =
        file?.substringAfterLast('/')?.substringBefore('?')?.ifBlank { "Now Playing" } ?: "Now Playing"

    // ---- Controls (call the RPC, then refresh immediately so the UI feels instant) ----

    suspend fun playPause() = withPlayer { id, kodi -> kodi.playPause(id) }
    suspend fun stop() = withPlayer { id, kodi -> kodi.stopPlayer(id) }
    suspend fun seekToPercent(percent: Double) = withPlayer { id, kodi -> kodi.seekPercentage(id, percent) }
    suspend fun skip(seconds: Int) = withPlayer { id, kodi -> kodi.seekRelative(id, seconds) }

    suspend fun selectSubtitle(index: Int?) = withPlayer { id, kodi ->
        if (index == null) kodi.setSubtitle(id, "off") else kodi.setSubtitleIndex(id, index)
    }

    private suspend fun withPlayer(action: suspend (Int, KodiRpc) -> Unit) {
        val id = _state.value.playerId
        if (id < 0) return
        val kodi = currentKodi() ?: return
        runCatching { action(id, kodi) }
        refreshOnce()
    }
}
