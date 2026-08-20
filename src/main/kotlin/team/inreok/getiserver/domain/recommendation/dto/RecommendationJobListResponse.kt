package team.inreok.getiserver.domain.recommendation.dto

import io.swagger.v3.oas.annotations.media.Schema
import org.springframework.data.domain.Page

@Schema(description = "내 북마크 공고 목록 조회 결과")
data class RecommendationJobListResponse(
    @param:Schema(description = "북마크한 공고 목록")
    val content: List<RecommendationJobResponse>,
    @param:Schema(description = "0부터 시작하는 현재 Page 번호", example = "0")
    val page: Int,
    @param:Schema(description = "Page당 개수(최대 100)", example = "20")
    val size: Int,
    @param:Schema(description = "전체 북마크 공고 개수", example = "3")
    val totalElements: Long,
    @param:Schema(description = "전체 Page 수", example = "1")
    val totalPages: Int,
    @param:Schema(description = "첫 Page 여부")
    val first: Boolean,
    @param:Schema(description = "마지막 Page 여부")
    val last: Boolean,
) {
    companion object {
        fun of(page: Page<RecommendationJobResponse>): RecommendationJobListResponse =
            RecommendationJobListResponse(
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
