package com.fenlight.companion.ui.nowplaying

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Forward30
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Replay30
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingScreen(
    onBack: () -> Unit,
    vm: NowPlayingViewModel = viewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    var showSubtitles by remember { mutableStateOf(false) }

    // Local scrubber state so dragging doesn't fight the 1s poll updates.
    var scrubbing by remember { mutableStateOf(false) }
    var scrubValue by remember { mutableFloatStateOf(0f) }
    val progress = if (state.durationSec > 0) {
        (state.positionSec.toFloat() / state.durationSec).coerceIn(0f, 1f)
    } else 0f
    val sliderValue = if (scrubbing) scrubValue else progress

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Now Playing") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    if (state.isActive) {
                        IconButton(onClick = { vm.stop(); onBack() }) {
                            Icon(Icons.Default.Stop, contentDescription = "Stop")
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (!state.isActive) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("Nothing playing on Kodi", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            // Hero: artwork, or a placeholder when no art could be resolved.
            Box(
                modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
                contentAlignment = Alignment.Center,
            ) {
                if (state.artUrl != null) {
                    AsyncImage(
                        model = state.artUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                    Box(
                        Modifier.matchParentSize().background(
                            Brush.verticalGradient(0.5f to Color.Transparent, 1f to Color(0xCC000000)),
                        ),
                    )
                } else {
                    Box(
                        Modifier.matchParentSize().background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Default.Movie,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(56.dp),
                        )
                    }
                }
            }

            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    state.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                state.subtitleLine?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                Spacer(Modifier.height(8.dp))

                // Seek bar (only when the stream reports a duration).
                if (state.isSeekable) {
                    Slider(
                        value = sliderValue,
                        onValueChange = { scrubbing = true; scrubValue = it },
                        onValueChangeFinished = {
                            vm.seekToPercent((scrubValue * 100).toDouble())
                            scrubbing = false
                        },
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        val shown = if (scrubbing) (scrubValue * state.durationSec).toInt() else state.positionSec
                        Text(formatTime(shown), style = MaterialTheme.typography.labelSmall)
                        Text(formatTime(state.durationSec), style = MaterialTheme.typography.labelSmall)
                    }
                }

                // Transport controls.
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { vm.skip(-30) }) { Icon(Icons.Default.Replay30, contentDescription = "Back 30s") }
                    IconButton(onClick = { vm.skip(-10) }) { Icon(Icons.Default.Replay10, contentDescription = "Back 10s") }
                    FilledIconButton(onClick = vm::playPause, modifier = Modifier.size(64.dp)) {
                        Icon(
                            if (state.isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                            contentDescription = if (state.isPaused) "Play" else "Pause",
                            modifier = Modifier.size(32.dp),
                        )
                    }
                    IconButton(onClick = { vm.skip(10) }) { Icon(Icons.Default.Forward10, contentDescription = "Forward 10s") }
                    IconButton(onClick = { vm.skip(30) }) { Icon(Icons.Default.Forward30, contentDescription = "Forward 30s") }
                }

                // Subtitles — only when Kodi reports tracks for this item.
                if (state.subtitles.isNotEmpty()) {
                    OutlinedButton(
                        onClick = { showSubtitles = true },
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    ) {
                        Icon(Icons.Default.Subtitles, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Subtitles")
                    }
                }
            }
        }
    }

    if (showSubtitles) {
        SubtitlePickerSheet(
            subtitles = state.subtitles,
            enabled = state.subtitleEnabled,
            currentIndex = state.currentSubtitleIndex,
            onSelect = { index -> vm.selectSubtitle(index); showSubtitles = false },
            onDismiss = { showSubtitles = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SubtitlePickerSheet(
    subtitles: List<com.fenlight.companion.data.model.KodiSubtitle>,
    enabled: Boolean,
    currentIndex: Int?,
    onSelect: (Int?) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(bottom = 24.dp)) {
            Text(
                "Subtitles",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            HorizontalDivider()
            SubtitleRow(label = "Off", selected = !enabled, onClick = { onSelect(null) })
            subtitles.forEach { sub ->
                val label = sub.name.ifBlank { sub.language.ifBlank { "Track ${sub.index + 1}" } }
                SubtitleRow(
                    label = label,
                    selected = enabled && currentIndex == sub.index,
                    onClick = { onSelect(sub.index) },
                )
            }
        }
    }
}

@Composable
private fun SubtitleRow(label: String, selected: Boolean, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(label) },
        trailingContent = if (selected) {
            { Icon(Icons.Default.Check, contentDescription = "Selected", tint = MaterialTheme.colorScheme.primary) }
        } else null,
        modifier = Modifier.clickable(onClick = onClick),
    )
}

private fun formatTime(totalSec: Int): String {
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
