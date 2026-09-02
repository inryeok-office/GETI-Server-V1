package team.inreok.getiserver.domain.portfolio.dto

import io.swagger.v3.oas.annotations.media.Schema
import org.springframework.data.domain.Page

// global.web.PageResponse는 아직 어떤 Domain도 실제로 쓰지 않고, 각 Domain이 전용 Pagination 응답
// DTO를 직접 만드는 관례(Program/Member Search/Inquiry)를 그대로 따랐다.
@Schema(description = "포트폴리오 수합 요청 목록 결과. Spring Data Page 규약과 동일하게 page는 0부터 시작한다.")
data class PortfolioRequestListResponse(
    @param:Schema(description = "조회 결과 목록")
    val content: List<PortfolioRequestSummaryResponse>,
    @param:Schema(description = "현재 Page 번호(0부터 시작)", example = "0")
    val page: Int,
    @param:Schema(description = "Page당 개수", example = "20")
    val size: Int,
    @param:Schema(description = "전체 결과 개수", example = "3")
    val totalElements: Long,
    @param:Schema(description = "전체 Page 수", example = "1")
    val totalPages: Int,
    @param:Schema(description = "첫 Page 여부", example = "true")
    val first: Boolean,
    @param:Schema(description = "마지막 Page 여부", example = "true")
    val last: Boolean,
) {
    companion object {
        fun from(page: Page<PortfolioRequestSummaryResponse>): PortfolioRequestListResponse =
            PortfolioRequestListResponse(
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
