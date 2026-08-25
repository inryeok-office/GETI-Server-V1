package team.inreok.getiserver.domain.recommendation.dto

import io.swagger.v3.oas.annotations.media.Schema
import team.inreok.getiserver.domain.recommendation.entity.type.ExclusionType

@Schema(description = "관심 없음(추천 제외) 등록 요청")
data class RecommendationExclusionCreateRequest(
    @param:Schema(description = "관심 없음으로 설정할 공고 ID", example = "1")
    val jobId: Long,
    @param:Schema(description = "관심 없음 종류", example = "THIS_JOB")
    val exclusionType: ExclusionType,
)
