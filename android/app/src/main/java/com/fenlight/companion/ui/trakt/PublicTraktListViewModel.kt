package com.fenlight.companion.ui.trakt

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.fenlight.companion.FenLightApp
import com.fenlight.companion.ui.components.PaginatedItem
import com.fenlight.companion.util.Pagination
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class PublicListState(
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val error: String? = null,
    val items: List<PaginatedItem> = emptyList(),
    val typeMap: Map<Int, String> = emptyMap(),
    val page: Int = 0,
    val hasMore: Boolean = false,
    val isLoadingMore: Boolean = false,
    val listSlug: String = "",
    val listUser: String = "",
)

class PublicTraktListViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as FenLightApp
    private val _state = MutableStateFlow(PublicListState())
    val state: StateFlow<PublicListState> = _state.asStateFlow()

    fun loadList(slug: String, user: String) {
        if (_state.value.listSlug == slug && (_state.value.items.isNotEmpty() || _state.value.isLoading)) return
        _state.update {
            it.copy(
                isLoading = true,
                error = null,
                items = emptyList(),
                typeMap = emptyMap(),
                page = 0,
                hasMore = false,
                listSlug = slug,
                listUser = user,
            )
        }
        fetchPage(slug, user, page = 1, append = false)
    }

    fun loadMore() {
        val s = _state.value
        if (s.isLoadingMore || !s.hasMore) return
        fetchPage(s.listSlug, s.listUser, page = s.page + 1, append = true)
    }

    fun refresh(slug: String, user: String) {
        _state.update { it.copy(isRefreshing = true) }
        fetchPage(slug, user, page = 1, append = false)
    }

    private fun fetchPage(slug: String, user: String, page: Int, append: Boolean) {
        viewModelScope.launch {
            _state.update { it.copy(isLoadingMore = append, isLoading = !append && !it.isRefreshing) }
            try {
                val response = if (user == "me") {
                    app.authedTraktApi.myListItems(slug, page = page)
                } else {
                    app.traktApi.listItems(user, slug, page = page)
                }
                val newItems = response.body() ?: emptyList()
                val pagesHeader = response.headers()[Pagination.TRAKT_PAGE_COUNT_HEADER]

                val enrichedPairs = coroutineScope {
                    newItems.map { item ->
                        async {
                            val tmdbId = item.movie?.ids?.tmdb ?: item.show?.ids?.tmdb
                                ?: return@async null
                            runCatching {
                                if (item.type == "movie") {
                                    val d = app.tmdbApi.movieDetail(tmdbId, "")
                                    PaginatedItem(
                                        id = d.id,
                                        title = d.title,
                                        posterUrl = FenLightApp.posterUrl(d.posterPath),
                                        rating = d.voteAverage.takeIf { it > 0 },
                                        backdropUrl = FenLightApp.backdropUrl(d.backdropPath),
                                    ) to item.type
                                } else {
                                    val d = app.tmdbApi.tvDetail(tmdbId, "")
                                    PaginatedItem(
                                        id = d.id,
                                        title = d.name,
                                        posterUrl = FenLightApp.posterUrl(d.posterPath),
                                        rating = d.voteAverage.takeIf { it > 0 },
                                        backdropUrl = FenLightApp.backdropUrl(d.backdropPath),
                                    ) to item.type
                                }
                            }.getOrNull()
                        }
                    }.awaitAll().filterNotNull()
                }

                val enrichedItems = enrichedPairs.map { it.first }
                val typeMap = enrichedPairs.associate { (item, type) -> item.id to type }

                _state.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        isLoadingMore = false,
                        items = if (append) it.items + enrichedItems else enrichedItems,
                        typeMap = if (append) it.typeMap + typeMap else typeMap,
                        page = page,
                        hasMore = Pagination.hasMoreByTraktHeader(pagesHeader, page),
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, isRefreshing = false, isLoadingMore = false, error = e.message) }
            }
        }
    }
}
