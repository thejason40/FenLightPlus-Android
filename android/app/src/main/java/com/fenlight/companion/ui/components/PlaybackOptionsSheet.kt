package com.fenlight.companion.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fenlight.companion.data.api.KodiRpc.PlaybackMode

/**
 * Bottom sheet mirroring FenLight+'s playback choices. DEFAULT autoplays; the rest
 * turn autoplay off so the source dialog shows on the Kodi device.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaybackOptionsSheet(
    onSelect: (PlaybackMode) -> Unit,
    onDismiss: () -> Unit,
    // Episodes only: when provided, a "Play multiple episodes…" entry is shown.
    onPlayMultiple: (() -> Unit)? = null,
) {
    val options = listOf(
        Triple(PlaybackMode.DEFAULT, "Play", "Autoplay the best matching source"),
        Triple(PlaybackMode.SELECT_SOURCE, "Select source…", "Pick a source on your Kodi device"),
        Triple(PlaybackMode.ALL_SOURCES, "Scrape all sources", "Include every external scraper (chosen on Kodi)"),
        Triple(PlaybackMode.IGNORE_FILTERS, "Ignore quality filters", "Show all results, unfiltered (chosen on Kodi)"),
    )
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(bottom = 24.dp)) {
            Text(
                "Playback options",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            HorizontalDivider()
            options.forEach { (mode, title, subtitle) ->
                ListItem(
                    headlineContent = { Text(title) },
                    supportingContent = { Text(subtitle) },
                    modifier = Modifier.clickable {
                        onSelect(mode)
                        onDismiss()
                    },
                )
            }
            if (onPlayMultiple != null) {
                ListItem(
                    headlineContent = { Text("Play multiple episodes…") },
                    supportingContent = { Text("Binge several episodes back-to-back from here") },
                    modifier = Modifier.clickable {
                        onPlayMultiple()
                        onDismiss()
                    },
                )
            }
        }
    }
}
