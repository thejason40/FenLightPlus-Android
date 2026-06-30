package com.fenlight.companion.ui.sources

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fenlight.companion.data.model.FenLightSource
import com.fenlight.companion.ui.components.ErrorMessage
import com.fenlight.companion.ui.components.rememberPlayMessageSnackbar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SourceSelectionScreen(
    query: String,
    title: String,
    onBack: () -> Unit,
    vm: SourceSelectionViewModel = viewModel(),
) {
    LaunchedEffect(query) { vm.start(query, title) }
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbarHostState = rememberPlayMessageSnackbar(state.playMessage) { vm.clearPlayMessage() }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(state.title.ifBlank { "Select source" }, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                state.loading -> Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(16.dp))
                    Text("Scraping sources…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(16.dp))
                    TextButton(onClick = { vm.cancel(); onBack() }) { Text("Cancel") }
                }

                state.error != null -> ErrorMessage(
                    message = state.error!!,
                    modifier = Modifier.align(Alignment.Center),
                )

                state.sources.isEmpty() -> Text(
                    "No sources found",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Center),
                )

                else -> LazyColumn(Modifier.fillMaxSize()) {
                    items(state.sources, key = { it.file }) { source ->
                        SourceRow(source = source, onClick = { vm.playSource(source); onBack() })
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun SourceRow(source: FenLightSource, onClick: () -> Unit) {
    val headline = listOf(source.provider, source.quality).filter { it.isNotBlank() }.joinToString(" · ")
    val firstLine = buildList {
        source.size.takeIf { it.isNotBlank() && it != "N/A" }?.let { add(it) }
        if (!source.cached) add("UNCACHED" + (source.seeders?.let { " · $it seeders" } ?: ""))
        source.pack?.takeIf { it.isNotBlank() }?.let { add("PACK") }
    }.joinToString(" · ")
    ListItem(
        headlineContent = { Text(headline.ifBlank { source.label }, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            Column {
                if (firstLine.isNotEmpty()) Text(firstLine, style = MaterialTheme.typography.labelMedium)
                source.info.takeIf { it.isNotBlank() && it != "N/A" }?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                source.name.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        },
        modifier = Modifier.clickable(onClick = onClick),
    )
}
