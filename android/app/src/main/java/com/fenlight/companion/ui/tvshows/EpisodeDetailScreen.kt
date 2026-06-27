package com.fenlight.companion.ui.tvshows

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.fenlight.companion.FenLightApp
import com.fenlight.companion.ui.components.ErrorMessage
import com.fenlight.companion.ui.components.PlaybackOptionsSheet
import com.fenlight.companion.ui.components.rememberPlayMessageSnackbar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EpisodeDetailScreen(
    showId: Int,
    season: Int,
    episodeNumber: Int,
    onBack: () -> Unit,
    vm: EpisodeDetailViewModel = viewModel(),
) {
    LaunchedEffect(showId, season, episodeNumber) { vm.load(showId, season, episodeNumber) }
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbarHostState = rememberPlayMessageSnackbar(state.playMessage) { vm.clearPlayMessage() }

    var showCountDialog by remember { mutableStateOf(false) }
    if (showCountDialog) {
        PlayEpisodeCountDialog(
            onDismiss = { showCountDialog = false },
            onConfirm = { count ->
                showCountDialog = false
                vm.play(showId, count)
            },
        )
    }

    var showPlaybackOptions by remember { mutableStateOf(false) }
    if (showPlaybackOptions) {
        PlaybackOptionsSheet(
            onSelect = { mode -> vm.play(showId, numEpisodes = 1, mode = mode) },
            onPlayMultiple = { showCountDialog = true },
            onDismiss = { showPlaybackOptions = false },
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    val ep = state.episode
                    Text(if (ep != null) "S${ep.seasonNumber}E${ep.episodeNumber}" else "Episode")
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") }
                },
            )
        },
    ) { padding ->
        val ep = state.episode
        if (ep == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                when {
                    state.error != null -> ErrorMessage(state.error!!, onRetry = { vm.load(showId, season, episodeNumber) })
                    else -> CircularProgressIndicator()
                }
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            Box {
                AsyncImage(
                    model = FenLightApp.backdropUrl(ep.stillPath),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
                )
                Box(
                    modifier = Modifier.matchParentSize().background(
                        Brush.verticalGradient(0.3f to Color.Transparent, 1.0f to Color(0xFF111820))
                    )
                )
            }

            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (state.showName.isNotBlank()) {
                            Text(
                                state.showName,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            "S${ep.seasonNumber}E${ep.episodeNumber} · ${ep.name}",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        val meta = buildList {
                            ep.airDate?.let { add(it) }
                            ep.runtime?.let { add("${it}m") }
                            if (ep.voteAverage > 0) add("★ ${"%.1f".format(ep.voteAverage)}")
                        }.joinToString(" · ")
                        if (meta.isNotBlank()) {
                            Text(
                                meta,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilledIconButton(onClick = { vm.play(showId) }) {
                            Icon(Icons.Default.PlayArrow, contentDescription = "Play")
                        }
                        FilledTonalIconButton(onClick = { showPlaybackOptions = true }) {
                            Icon(Icons.Default.Tune, contentDescription = "Playback options")
                        }
                    }
                }

                if (ep.overview.isNotBlank()) {
                    Text("Overview", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(ep.overview, style = MaterialTheme.typography.bodyMedium)
                }

                if (state.traktAuthed) {
                    TextButton(onClick = { vm.markWatched(showId, watched = !state.episodeWatched) }) {
                        Text(if (state.episodeWatched) "Mark as unwatched" else "Mark as watched")
                    }
                }
            }
        }
    }
}

private const val MIN_EPISODE_COUNT = 2
private const val MAX_EPISODE_COUNT = 50

@Composable
private fun PlayEpisodeCountDialog(
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    var count by remember { mutableStateOf(3) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Play multiple episodes") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "FenLight+ will play this many episodes back-to-back, starting from this one.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FilledTonalIconButton(
                        onClick = { if (count > MIN_EPISODE_COUNT) count-- },
                        enabled = count > MIN_EPISODE_COUNT,
                    ) {
                        Icon(Icons.Default.Remove, contentDescription = "Fewer episodes")
                    }
                    Text(
                        count.toString(),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    FilledTonalIconButton(
                        onClick = { if (count < MAX_EPISODE_COUNT) count++ },
                        enabled = count < MAX_EPISODE_COUNT,
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "More episodes")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(count) }) { Text("Play $count") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
