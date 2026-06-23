package com.fenlight.companion.util

/** Small, unit-testable pagination predicates shared across paged screens. */
object Pagination {

    /** When the API returns a full page, assume there may be more (Real-Debrid style). */
    fun hasMoreByPageSize(resultCount: Int, pageSize: Int): Boolean = resultCount == pageSize

    /** When the API reports a total page count (TMDB / Trakt header style). */
    fun hasMoreByPageCount(currentPage: Int, totalPages: Int): Boolean = currentPage < totalPages

    /** Trakt reports paging via response headers, not the body. */
    const val TRAKT_PAGE_COUNT_HEADER = "X-Pagination-Page-Count"

    /**
     * Trakt header-based paging. A missing or unparsable page-count header treats the
     * current page as the last one: Trakt reliably sends the header, so this only matters
     * for malformed responses, where stopping is safer than risking an endless scroll.
     */
    fun hasMoreByTraktHeader(pageCountHeader: String?, currentPage: Int): Boolean =
        currentPage < (pageCountHeader?.toIntOrNull() ?: currentPage)
}
