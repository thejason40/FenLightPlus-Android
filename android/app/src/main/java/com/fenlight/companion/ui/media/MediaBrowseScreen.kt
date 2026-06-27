package com.fenlight.companion.ui.media

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fenlight.companion.data.model.BrowseRowConfig
import com.fenlight.companion.data.model.MediaType
import com.fenlight.companion.data.model.RowType
import com.fenlight.companion.data.model.TraktList
import com.fenlight.companion.ui.components.*
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaBrowseScreen(
    mediaType: MediaType,
    onItemClick: (Int) -> Unit,
    onShowRecommendations: (Int) -> Unit = {},
    onShowSimilar: (Int) -> Unit = {},
    onSeeAll: (BrowseRowConfig) -> Unit = {},
    onOpenPublicList: ((TraktList) -> Unit)? = null,
    onEpisodeClick: (showId: Int, season: Int, episode: Int) -> Unit = { _, _, _ -> },
    vm: MediaHomeViewModel,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    var selectedItem by remember { mutableStateOf<PaginatedItem?>(null) }

    selectedItem?.let { item ->
        ListManagementSheet(
            mediaId = item.id,
            mediaType = mediaType.tmdbName,
            title = item.title,
            posterUrl = item.posterUrl,
            onShowRecommendations = { onShowRecommendations(item.id) },
            onShowSimilar = { onShowSimilar(item.id) },
            onDismiss = { selectedItem = null },
            onOpenPublicList = onOpenPublicList,
        )
    }

    if (state.showAddRowSheet) {
        AddBrowseRowSheet(
            mediaType = mediaType.tmdbName,
            pendingType = state.pendingRowType,
            pendingLabel = state.pendingRowLabel,
            pendingFilters = state.pendingRowFilters,
            pendingListId = state.pendingListId,
            pendingTraktSlug = state.pendingTraktSlug,
            pendingTraktUser = state.pendingTraktUser,
            genres = state.genres,
            watchProviders = state.watchProviders,
            availableTmdbLists = state.availableTmdbLists,
            availableTraktLists = state.availableTraktLists,
            hasTraktAuth = state.hasTraktAuth,
            onTypeChange = vm::onPendingRowTypeChange,
            onLabelChange = vm::onPendingRowLabelChange,
            onFiltersChange = vm::onPendingRowFiltersChange,
            onListIdChange = vm::onPendingListIdChange,
            onTraktListChange = vm::onPendingTraktListChange,
            onConfirm = vm::addCustomRow,
            onDismiss = vm::dismissAddRowSheet,
            addRowError = state.addRowError,
        )
    }

    val lazyListState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(lazyListState) { from, to ->
        val fromId = from.key as? String ?: return@rememberReorderableLazyListState
        val toId = to.key as? String ?: return@rememberReorderableLazyListState
        vm.moveRow(fromId, toId)
    }

    PullToRefreshBox(
        isRefreshing = state.isRefreshing,
        onRefresh = vm::refresh,
        modifier = Modifier.fillMaxSize(),
    ) {
        LazyColumn(state = lazyListState, modifier = Modifier.fillMaxSize()) {
            items(state.rows, key = { it.config.id }) { rowState ->
                ReorderableItem(reorderState, key = rowState.config.id) { isDragging ->
                    Surface(shadowElevation = if (isDragging) 6.dp else 0.dp) {
                        BrowseRow(
                            state = rowState,
                            onItemClick = { item ->
                                if (item.nextSeason != null && item.nextEpisode != null)
                                    onEpisodeClick(item.id, item.nextSeason, item.nextEpisode)
                                else onItemClick(item.id)
                            },
                            onItemLongClick = { selectedItem = it },
                            onSeeAll = { onSeeAll(rowState.config) },
                            onRemove = { vm.removeRow(rowState.config.id) },
                            onRetry = { vm.retryRow(rowState.config.id) },
                            showSeeAll = rowState.config.type != RowType.NEXT_EPISODES,
                            dragHandle = {
                                IconButton(
                                    onClick = {},
                                    modifier = Modifier.draggableHandle(
                                        onDragStopped = { vm.persistRows() },
                                    ),
                                ) {
                                    Icon(
                                        Icons.Default.DragHandle,
                                        contentDescription = "Reorder row",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            },
                        )
                    }
                }
                HorizontalDivider()
            }
            item {
                TextButton(
                    onClick = vm::showAddRowSheet,
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Add Row")
                }
            }
        }
    }
}
