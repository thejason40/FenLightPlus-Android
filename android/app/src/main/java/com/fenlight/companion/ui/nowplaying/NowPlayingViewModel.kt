package com.fenlight.companion.ui.nowplaying

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.fenlight.companion.FenLightApp
import com.fenlight.companion.data.kodi.NowPlayingUiState
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Thin proxy over the app-wide [NowPlayingRepository]. Multiple instances (mini-bar,
 * full screen) all observe and drive the same singleton, so state stays in sync.
 * The poll loop itself is started once by HomeScreen, not here.
 */
class NowPlayingViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = (application as FenLightApp).nowPlaying

    val state: StateFlow<NowPlayingUiState> = repo.state

    fun playPause() = viewModelScope.launch { repo.playPause() }
    fun stop() = viewModelScope.launch { repo.stop() }
    fun seekToPercent(percent: Double) = viewModelScope.launch { repo.seekToPercent(percent) }
    fun skip(seconds: Int) = viewModelScope.launch { repo.skip(seconds) }
    fun selectSubtitle(index: Int?) = viewModelScope.launch { repo.selectSubtitle(index) }
}
