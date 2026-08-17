package team.inreok.getiserver.domain.recommendation.dto

/**
 * Recommendation 조회 API가 표현하는 사용자 상태다(Recommendation R3, Issue #152). Persistence에
 * 아직 Generation 실행 상태를 저장하지 않으므로(R4 Daily Scheduler 선행 필요) R3 Service는
 * [DISABLED]/[EMPTY]/[READY] 세 값만 실제로 반환한다.
 *
 * [GENERATING]/[FAILED]는 R4가 Scheduler 실행 상태를 저장하기 시작하면 사용할 값을 미리
 * 예약해 둔 것이다 -- Frontend Contract가 두 값을 미리 알고 있어야 R4에서 Enum을 다시 바꾸지
 * 않고 그대로 확장할 수 있다. 같은 개념(추천 상태)을 여러 Enum으로 중복 정의하지 않기 위해
 * 하나의 API 전용 Enum으로 둔다.
 */
enum class RecommendationStatus {
    /** 추천 기능이 꺼져 있다(`RecommendationPreference.enabled=false` 또는 설정한 적 없음). */
    DISABLED,

    /** 추천 기능은 켜져 있지만 오늘자 추천 결과가 없다. */
    EMPTY,

    /** 추천 기능이 켜져 있고 오늘자 추천 결과가 1건 이상 있다. */
    READY,

    /** R4 Daily Scheduler가 이 회원의 추천을 계산하는 중이다. R3는 이 값을 반환하지 않는다. */
    GENERATING,

    /** R4 Daily Scheduler의 마지막 계산이 실패했다. R3는 이 값을 반환하지 않는다. */
    FAILED,
}
