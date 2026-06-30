package com.fenlight.companion.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fenlight.companion.FenLightApp
import com.fenlight.companion.data.api.KodiRpc
import com.fenlight.companion.data.api.KodiRpc.PlaybackMode
import kotlinx.coroutines.flow.first

/**
 * Playback choices mirroring FenLight+. DEFAULT autoplays. The other (non-autoplay) modes
 * let you pick a specific source: either on Kodi (its on-TV dialog) or — when the plugin
 * supports it — on this device. Where the pick happens is driven by the user's setting
 * (ask / device / kodi).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaybackOptionsSheet(
    onPlayOnKodi: (PlaybackMode) -> Unit,
    onDismiss: () -> Unit,
    // When provided (and the plugin supports it), non-autoplay modes can select on-device.
    onSelectOnDevice: ((PlaybackMode) -> Unit)? = null,
    // Episodes only: when provided, a "Play multiple episodes…" entry is shown.
    onPlayMultiple: (() -> Unit)? = null,
) {
    val app = LocalContext.current.applicationContext as FenLightApp
    val setting by app.prefs.sourceSelection.collectAsStateWithLifecycle(initialValue = "ask")
    var deviceCapable by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        deviceCapable = runCatching {
            KodiRpc(
                app.prefs.kodiHost.first(), app.prefs.kodiPort.first(),
                app.prefs.kodiUser.first(), app.prefs.kodiPass.first(),
            ).supportsDeviceSelect()
        }.getOrDefault(false)
    }
    var pendingAsk by remember { mutableStateOf<PlaybackMode?>(null) }

    val choose: (PlaybackMode) -> Unit = choose@{ mode ->
        if (mode == PlaybackMode.DEFAULT) { onPlayOnKodi(mode); onDismiss(); return@choose }
        val canDevice = onSelectOnDevice != null && deviceCapable
        when {
            !canDevice || setting == "kodi" -> { onPlayOnKodi(mode); onDismiss() }
            setting == "device" -> { onSelectOnDevice!!(mode); onDismiss() }
            else -> pendingAsk = mode // "ask"
        }
    }

    val options = listOf(
        Triple(PlaybackMode.DEFAULT, "Play", "Autoplay the best matching source"),
        Triple(PlaybackMode.SELECT_SOURCE, "Select source…", "Choose a specific source"),
        Triple(PlaybackMode.ALL_SOURCES, "Scrape all sources", "Include every external scraper"),
        Triple(PlaybackMode.IGNORE_FILTERS, "Ignore quality filters", "Show all results, unfiltered"),
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
                    modifier = Modifier.clickable { choose(mode) },
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

    pendingAsk?.let { mode ->
        AlertDialog(
            onDismissRequest = { pendingAsk = null },
            title = { Text("Choose source on") },
            text = { Text("Pick the source on this device, or on Kodi.") },
            confirmButton = {
                TextButton(onClick = { pendingAsk = null; onSelectOnDevice?.invoke(mode); onDismiss() }) { Text("This device") }
            },
            dismissButton = {
                TextButton(onClick = { pendingAsk = null; onPlayOnKodi(mode); onDismiss() }) { Text("On Kodi") }
            },
        )
    }
}
