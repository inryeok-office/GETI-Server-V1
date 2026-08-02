package team.inreok.getiserver.global.web

import org.springframework.data.domain.Page

data class PageResponse<T : Any>(
    val data: List<T>,
    val meta: PageMeta,
) {
    companion object {
        fun <T : Any> of(page: Page<T>): PageResponse<T> =
            PageResponse(
                data = page.content,
                meta =
                    PageMeta(
                        page = page.number,
                        size = page.size,
                        totalElements = page.totalElements,
                        totalPages = page.totalPages,
                        hasNext = page.hasNext(),
                        hasPrevious = page.hasPrevious(),
                    ),
            )
    }
}

data class PageMeta(
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int,
    val hasNext: Boolean,
    val hasPrevious: Boolean,
)
