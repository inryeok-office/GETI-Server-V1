package team.inreok.getiserver.domain.portfolio.dto

import io.swagger.v3.oas.annotations.media.Schema
import org.springframework.data.domain.Page

// PortfolioRequestListResponse와 같은 관례로 Domain 전용 Pagination 응답 DTO를 직접 만든다.
@Schema(description = "관리자 제출 현황 목록 결과. Spring Data Page 규약과 동일하게 page는 0부터 시작한다.")
data class PortfolioSubmissionStatusListResponse(
    @param:Schema(description = "조회 결과 목록")
    val content: List<PortfolioSubmissionStatusResponse>,
    @param:Schema(description = "현재 Page 번호(0부터 시작)", example = "0")
    val page: Int,
    @param:Schema(description = "Page당 개수", example = "20")
    val size: Int,
    @param:Schema(description = "필터 적용 후 전체 결과 개수", example = "20")
    val totalElements: Long,
    @param:Schema(description = "전체 Page 수", example = "1")
    val totalPages: Int,
    @param:Schema(description = "첫 Page 여부", example = "true")
    val first: Boolean,
    @param:Schema(description = "마지막 Page 여부", example = "true")
    val last: Boolean,
) {
    companion object {
        fun from(page: Page<PortfolioSubmissionStatusResponse>): PortfolioSubmissionStatusListResponse =
            PortfolioSubmissionStatusListResponse(
                content = page.content,
                page = page.number,
                size = page.size,
                totalElements = page.totalElements,
                totalPages = page.totalPages,
                first = page.isFirst,
                last = page.isLast,
            )
    }
}
