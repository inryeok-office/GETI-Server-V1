package team.inreok.getiserver.domain.recommendation.dto

import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDateTime

/**
 * 내 추천 조회 응답이다(Recommendation R3, Issue #152). [status]가 [RecommendationStatus.DISABLED]면
 * [items]는 항상 빈 목록이고 [generatedAt]은 항상 null이다 -- 기존 추천 결과가 DB에 남아 있어도
 * 꺼진 사용자에게는 노출하지 않는다(요구사항 결정 사항).
 */
@Schema(description = "내 추천 조회 결과")
data class RecommendationListResponse(
    @param:Schema(description = "추천 기능 ON/OFF 여부", example = "true")
    val enabled: Boolean,
    @param:Schema(description = "추천 상태")
    val status: RecommendationStatus,
    @param:Schema(
        description = "오늘자 추천 결과가 생성된 시각. DISABLED이거나 아직 생성된 적 없으면 null.",
        nullable = true,
    )
    val generatedAt: LocalDateTime?,
    @param:Schema(description = "추천 결과 목록. rank 오름차순으로 정렬되어 있다.")
    val items: List<RecommendationItemResponse>,
)
