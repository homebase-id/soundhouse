package id.homebase.api.client.drives.query

import kotlinx.serialization.Serializable

/**
 * Paging and cursor models for API responses.
 *
 * Ported from TypeScript paging interfaces
 */

@Serializable
data class PagedResult<T>(
    val totalPages: Int,
    val results: List<T>
)

