package team.inreok.getiserver.domain.recommendation.dto

import io.swagger.v3.oas.annotations.media.Schema
import team.inreok.getiserver.domain.recommendation.entity.type.ExclusionType
import java.time.LocalDateTime

/**
 * 관심 없음(추천 제외) Resource 1건이다(Notion 계약 정합성 Issue #155,
 * `POST`/`GET /api/v1/me/recommendation-exclusions`). [exclusionId]는
 * `member_job_preferences.id`(V24 Migration으로 추가한 Surrogate PK)를 그대로 노출한다 --
 * 가짜 Hash나 (memberId, jobId) 조합을 인코딩한 값이 아니다.
 */
@Schema(description = "관심 없음(추천 제외) Resource 1건")
data class RecommendationExclusionResponse(
    @param:Schema(description = "관심 없음 Resource ID", example = "1")
    val exclusionId: Long,
    @param:Schema(description = "관심 없음으로 설정한 공고 카드 정보")
    val job: RecommendationJobResponse,
    @param:Schema(description = "관심 없음 종류", example = "THIS_JOB")
    val exclusionType: ExclusionType,
    @param:Schema(description = "관심 없음이 설정된 시각")
    val createdAt: LocalDateTime,
)
