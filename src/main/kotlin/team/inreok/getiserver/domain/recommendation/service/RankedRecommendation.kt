package team.inreok.getiserver.domain.recommendation.service

/**
 * 순위가 매겨진 추천 결과 하나다. [RecommendationGenerationService.generateForMember]의 공개
 * 반환 타입이라 구현(`service.impl`)이 아니라 Interface와 같은 `service` Package에 둔다(PR #150
 * Review 반영) -- R3 Controller, R4 Scheduler가 이 Interface만 보고 써도 반환값을 다룰 수 있어야
 * 한다.
 */
data class RankedRecommendation(
    val jobId: Long,
    val score: Int,
    val reasons: List<RecommendationReason>,
    val rank: Int,
)
