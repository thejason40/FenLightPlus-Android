package com.fenlight.companion.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PaginationTest {

    @Test
    fun fullPage_impliesMore() {
        assertTrue(Pagination.hasMoreByPageSize(resultCount = 50, pageSize = 50))
    }

    @Test
    fun partialPage_isLastPage() {
        assertFalse(Pagination.hasMoreByPageSize(resultCount = 47, pageSize = 50))
        assertFalse(Pagination.hasMoreByPageSize(resultCount = 0, pageSize = 50))
    }

    @Test
    fun pageCount_boundaries() {
        assertTrue(Pagination.hasMoreByPageCount(currentPage = 1, totalPages = 7))
        assertFalse(Pagination.hasMoreByPageCount(currentPage = 7, totalPages = 7))
        assertFalse(Pagination.hasMoreByPageCount(currentPage = 8, totalPages = 7))
    }

    @Test
    fun traktHeader_morePages() {
        assertTrue(Pagination.hasMoreByTraktHeader(pageCountHeader = "7", currentPage = 1))
    }

    @Test
    fun traktHeader_onLastPage() {
        assertFalse(Pagination.hasMoreByTraktHeader(pageCountHeader = "7", currentPage = 7))
    }

    @Test
    fun traktHeader_missing_treatedAsLastPage() {
        assertFalse(Pagination.hasMoreByTraktHeader(pageCountHeader = null, currentPage = 1))
    }

    @Test
    fun traktHeader_unparsable_treatedAsLastPage() {
        assertFalse(Pagination.hasMoreByTraktHeader(pageCountHeader = "abc", currentPage = 1))
    }
}
