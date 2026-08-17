package team.inreok.getiserver.domain.recommendation.dto

import io.swagger.v3.oas.annotations.media.Schema
import team.inreok.getiserver.domain.recommendation.entity.type.SuitabilityLevel

@Schema(description = "추천 결과 1건")
data class RecommendationItemResponse(
    @param:Schema(description = "추천 대상 공고 카드 정보")
    val job: RecommendationJobResponse,
    @param:Schema(description = "적합도 Score(0~100)", example = "82")
    val score: Int,
    @param:Schema(description = "Score 구간을 나타내는 적합도 등급", example = "RECOMMENDED")
    val suitability: SuitabilityLevel,
    @param:Schema(description = "같은 회원의 오늘자 추천 안에서의 순위(1부터 시작)", example = "1")
    val rank: Int,
    @param:Schema(description = "이 공고를 추천한 구조화된 이유 목록. 비어 있을 수 있다.")
    val reasons: List<RecommendationReasonResponse>,
)
