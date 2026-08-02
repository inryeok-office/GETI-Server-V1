package team.inreok.getiserver.global.web

import io.swagger.v3.oas.annotations.media.Schema
import org.springframework.data.domain.Page
import org.springframework.modulith.NamedInterface

// Domain Module의 Controller가 목록 응답으로 사용해야 하므로(.claude/rules/spring-boot.md
// "API 응답은 ApiResponse/PageResponse를 사용한다") ApiResponse와 동일하게 global Module 밖으로
// 명시적으로 공개한다. 공개 전에는 Domain에서 참조하면 ModularityTest가 실패했다(Issue #60).
@NamedInterface
@Schema(description = "공통 목록 응답 Wrapper. page는 0부터 시작한다.")
data class PageResponse<T : Any>(
    @param:Schema(description = "현재 Page의 항목 목록")
    val data: List<T>,
    @param:Schema(description = "Pagination 메타 정보")
    val meta: PageMeta,
) {
    @NamedInterface
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

@NamedInterface
@Schema(description = "Pagination 메타 정보")
data class PageMeta(
    @param:Schema(description = "현재 Page 번호(0부터 시작)", example = "0")
    val page: Int,
    @param:Schema(description = "Page당 개수", example = "20")
    val size: Int,
    @param:Schema(description = "전체 결과 개수", example = "42")
    val totalElements: Long,
    @param:Schema(description = "전체 Page 수", example = "3")
    val totalPages: Int,
    @param:Schema(description = "다음 Page 존재 여부", example = "true")
    val hasNext: Boolean,
    @param:Schema(description = "이전 Page 존재 여부", example = "false")
    val hasPrevious: Boolean,
)
