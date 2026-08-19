package team.inreok.getiserver.domain.recommendation.service

import team.inreok.getiserver.domain.recommendation.entity.type.RecommendationReasonType

/**
 * 구조화된 추천 이유 하나다(R2 설계 §23). 자연어 문장은 담지 않는다.
 *
 * [RecommendationGenerationService.generateForMember]의 반환 타입([RankedRecommendation])이
 * 참조하는 공개 계약이라 `service` Package에 둔다 -- Score Engine이 계산 과정에서만 쓰는
 * `RecommendationScoreResult` 같은 내부 전용 타입과 달리, 이 타입은 Service Interface를 통해
 * 밖으로 노출된다(PR #150 Review 반영, Service Interface가 `service.impl`을 참조하던 문제 수정).
 */
data class RecommendationReason(
    val type: RecommendationReasonType,
    val matchedCount: Int? = null,
    val totalCount: Int? = null,
)
