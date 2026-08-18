package team.inreok.getiserver.domain.recommendation.service

/**
 * Daily Scheduler(R4, Issue #160)가 회원 1명을 안전하게 처리하기 위한 Orchestration Use Case다.
 * `RecommendationGenerationScheduler`는 대상 memberId만 조회해 순회하고, 실제 처리(상태 기록 +
 * `RecommendationGenerationService` 호출)는 이 Interface에 위임한다("언제 도는가"만 아는
 * Scheduler와 "무엇을 하는가"를 아는 Service를 분리, `ProgramCloseScheduler`/`ProgramCloseService`와
 * 동일한 구조).
 */
interface RecommendationGenerationRunner {
    /**
     * [memberId]의 오늘자 추천을 다시 계산해 저장한다. 시작 시 상태를 GENERATING으로 기록하고,
     * 성공하면 READY/EMPTY로, 예외가 발생하면 FAILED로 기록한 뒤 그 예외를 그대로 다시 던진다
     * (호출자인 Scheduler가 회원별로 격리해서 처리하도록).
     */
    fun generateForMember(memberId: Long)
}
