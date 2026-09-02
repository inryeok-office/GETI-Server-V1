package team.inreok.getiserver.domain.recommendation.dto

/**
 * Recommendation 조회 API가 표현하는 사용자 상태다(Recommendation R3 Issue #152, R4 Issue #160).
 * [GENERATING]/[FAILED]는 R3에서 값만 미리 예약해 뒀고, R4가 `RecommendationGenerationState`
 * Persistence를 추가하며 실제로 반환하기 시작했다 -- Frontend Contract가 두 값을 미리 알고 있어
 * Enum을 다시 바꾸지 않고 그대로 확장할 수 있었다. 같은 개념(추천 상태)을 여러 Enum으로 중복
 * 정의하지 않기 위해 하나의 API 전용 Enum으로 둔다(Persistence 상태
 * [team.inreok.getiserver.domain.recommendation.entity.type.RecommendationGenerationStatus]와
 * 다른 이유는 그 KDoc 참고).
 */
enum class RecommendationStatus {
    /** 추천 기능이 꺼져 있다(`RecommendationPreference.enabled=false` 또는 설정한 적 없음). */
    DISABLED,

    /** 추천 기능은 켜져 있지만 오늘자 추천 결과가 없다. */
    EMPTY,

    /** 추천 기능이 켜져 있고 오늘자 추천 결과가 1건 이상 있다. */
    READY,

    /** Daily Scheduler가 이 회원의 추천을 계산하는 중이다. */
    GENERATING,

    /** Daily Scheduler의 마지막 계산이 실패했다. */
    FAILED,
}
