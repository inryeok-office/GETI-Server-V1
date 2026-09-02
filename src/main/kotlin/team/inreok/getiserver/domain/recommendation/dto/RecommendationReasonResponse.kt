package team.inreok.getiserver.domain.recommendation.dto

import io.swagger.v3.oas.annotations.media.Schema
import team.inreok.getiserver.domain.recommendation.entity.type.RecommendationReasonType

/**
 * 구조화된 추천 이유 하나를 API 응답으로 옮긴 값이다(Recommendation R3, Issue #152). R2가 저장한
 * `recommendations.reasons`(JSONB)를 그대로 반환하지 않고 이 DTO로 변환한다. R1/R2 설계와 같은
 * 이유로 자연어 문장은 서버가 만들지 않는다 -- `type`/`matchedCount`/`totalCount`만으로 Frontend가
 * 문구를 조립한다.
 */
@Schema(description = "구조화된 추천 이유 1건")
data class RecommendationReasonResponse(
    @param:Schema(description = "이유 종류", example = "REQUIRED_SKILL_MATCH")
    val type: RecommendationReasonType,
    @param:Schema(description = "일치한 개수. Skill Match 이유가 아니면 null.", example = "3", nullable = true)
    val matchedCount: Int?,
    @param:Schema(description = "전체 개수. Skill Match 이유가 아니면 null.", example = "4", nullable = true)
    val totalCount: Int?,
)
