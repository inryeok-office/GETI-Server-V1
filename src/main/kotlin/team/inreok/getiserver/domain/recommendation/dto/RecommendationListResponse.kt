package team.inreok.getiserver.domain.recommendation.dto

import io.swagger.v3.oas.annotations.media.Schema
import org.springframework.data.domain.Page
import java.time.LocalDateTime

/**
 * 내 추천 조회 응답이다(Recommendation R3 Issue #152, Notion 계약 정합성 Issue #155
 * `GET /api/v1/me/job-recommendations`). [status]가 [RecommendationStatus.DISABLED]면
 * [content]는 항상 빈 목록이고 [generatedAt]은 항상 null이다 -- 기존 추천 결과가 DB에 남아 있어도
 * 꺼진 사용자에게는 노출하지 않는다(요구사항 결정 사항).
 *
 * [enabled]/[status]는 Notion API 명세 원문에는 없는 Field다 -- R3(#152)가 도입한
 * DISABLED/EMPTY/READY 구분을 이번 계약 정합성 작업에서도 유지했다(수정 원칙 "기존 구현 중 좋은
 * 부분은 최대한 재사용", PR 본문 Matrix 참고). Notion이 명시한 `content`/`page`/`size`/
 * `totalElements`/`totalPages`/`first`/`last`/`generatedAt`/`nextGenerationAt`은 그대로 둔다.
 */
@Schema(description = "내 추천 조회 결과")
data class RecommendationListResponse(
    @param:Schema(description = "추천 기능 ON/OFF 여부", example = "true")
    val enabled: Boolean,
    @param:Schema(description = "추천 상태")
    val status: RecommendationStatus,
    @param:Schema(
        description =
            "오늘자 추천 결과가 생성된 시각. DISABLED/GENERATING/FAILED이거나 아직 생성된 적 없으면 " +
                "null.",
        nullable = true,
    )
    val generatedAt: LocalDateTime?,
    @param:Schema(
        description =
            "다음 추천 생성 예정 시각. Daily Scheduler 실행 시각이 확정되어 있지 않으면(운영 환경에서 " +
                "명시적으로 설정하기 전까지) 항상 null이다(예정 시각을 계산할 근거가 없어 임의 값을 " +
                "채우지 않는다).",
        nullable = true,
    )
    val nextGenerationAt: LocalDateTime?,
    @param:Schema(description = "추천 결과 목록. rank 오름차순으로 정렬되어 있다.")
    val content: List<RecommendationItemResponse>,
    @param:Schema(description = "0부터 시작하는 현재 Page 번호", example = "0")
    val page: Int,
    @param:Schema(description = "Page당 개수(최대 100)", example = "20")
    val size: Int,
    @param:Schema(description = "전체 개수", example = "3")
    val totalElements: Long,
    @param:Schema(description = "전체 Page 수", example = "1")
    val totalPages: Int,
    @param:Schema(description = "첫 Page 여부")
    val first: Boolean,
    @param:Schema(description = "마지막 Page 여부")
    val last: Boolean,
) {
    companion object {
        fun of(
            enabled: Boolean,
            status: RecommendationStatus,
            generatedAt: LocalDateTime?,
            nextGenerationAt: LocalDateTime?,
            page: Page<RecommendationItemResponse>,
        ): RecommendationListResponse =
            RecommendationListResponse(
                enabled = enabled,
                status = status,
                generatedAt = generatedAt,
                nextGenerationAt = nextGenerationAt,
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
