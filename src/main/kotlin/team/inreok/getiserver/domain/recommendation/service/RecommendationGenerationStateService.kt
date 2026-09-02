package team.inreok.getiserver.domain.recommendation.service

/**
 * 회원 1명의 일일 추천 Generation 상태(`RecommendationGenerationState`)를 갱신하는 Use Case다
 * (Recommendation R4, Issue #160). `RecommendationGenerationRunner`가 Generation 실행의 각 단계마다
 * 호출하며, 각 Method는 자신의 Transaction으로 즉시 Commit되어(Runner가 별도로 감싸지 않는다) 상태
 * 변경이 Generation 계산 자체와 분리된다 -- 계산이 오래 걸려도 GENERATING 상태는 이미 조회 가능하고,
 * 계산 성공/실패 여부와 무관하게 상태 기록 자체는 항상 반영된다.
 */
interface RecommendationGenerationStateService {
    /** 상태를 GENERATING으로 기록한다. */
    fun markGenerating(memberId: Long)

    /** 상태를 [hasResults] 여부에 따라 READY 또는 EMPTY로 기록하고 `generatedAt`을 갱신한다. */
    fun markCompleted(
        memberId: Long,
        hasResults: Boolean,
    )

    /** 상태를 FAILED로 기록한다. */
    fun markFailed(memberId: Long)
}
