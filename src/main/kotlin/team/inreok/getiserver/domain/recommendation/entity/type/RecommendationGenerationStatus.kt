package team.inreok.getiserver.domain.recommendation.entity.type

/**
 * 회원 1명의 마지막 일일 추천 Generation 실행 결과다(Recommendation R4, Issue #160).
 * `RecommendationGenerationState`에 회원당 1행으로 저장되며, Persistence에 실제로 저장되는 상태만
 * 담는다. `DISABLED`는 여기 없다 -- 추천 기능 ON/OFF는 `RecommendationPreference`가 이미 갖고
 * 있는 값이라 상태를 중복 저장하지 않는다([team.inreok.getiserver.domain.recommendation.dto.RecommendationStatus]와
 * 다른 이유는 그 KDoc 참고).
 */
enum class RecommendationGenerationStatus {
    /** Daily Scheduler가 이 회원의 추천을 계산하는 중이다. */
    GENERATING,

    /** 마지막 계산이 성공했고 결과가 1건 이상 있다. */
    READY,

    /** 마지막 계산이 성공했지만 결과가 없다(Hard Filter로 모두 제외됨 등). */
    EMPTY,

    /** 마지막 계산이 실패했다. */
    FAILED,
}
