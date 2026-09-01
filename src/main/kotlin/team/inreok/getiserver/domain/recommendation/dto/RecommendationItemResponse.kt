package team.inreok.getiserver.domain.recommendation.dto

import io.swagger.v3.oas.annotations.media.Schema
import team.inreok.getiserver.domain.recommendation.entity.type.SuitabilityLevel
import java.time.LocalDateTime

/**
 * 추천 결과 1건이다(Recommendation R3 Issue #152, Notion 계약 정합성 Issue #155).
 *
 * `reasons`가 구조화된 `RecommendationReasonResponse` 목록인 이유는 [RecommendationReasonResponse]
 * 문서 참고 -- Notion API 명세는 `reasons`를 `String[]`로 정의하지만, R1 설계(§23~24)가 자연어
 * 문장을 서버가 만들지 않기로 확정해 이 구조를 유지했다(CONTRACT_MISMATCH, PR 본문 Matrix 참고,
 * 근거 없이 자연어 생성 로직을 새로 추가하지 않는다).
 */
@Schema(description = "추천 결과 1건")
data class RecommendationItemResponse(
    @param:Schema(description = "추천 Row ID", example = "1")
    val recommendationId: Long,
    @param:Schema(description = "추천 대상 공고 카드 정보")
    val job: RecommendationJobResponse,
    @param:Schema(description = "적합도 Score(0~100)", example = "82")
    val score: Int,
    @param:Schema(description = "Score 구간을 나타내는 적합도 등급", example = "RECOMMENDED")
    val suitabilityLevel: SuitabilityLevel,
    @param:Schema(description = "같은 회원의 오늘자 추천 안에서의 순위(1부터 시작)", example = "1")
    val rank: Int,
    @param:Schema(description = "이 공고를 추천한 구조화된 이유 목록. 비어 있을 수 있다.")
    val reasons: List<RecommendationReasonResponse>,
    @param:Schema(description = "이 추천 결과가 생성된 시각")
    val generatedAt: LocalDateTime,
)
