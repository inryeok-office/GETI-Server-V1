package team.inreok.getiserver.domain.recommendation.entity.type

/**
 * Recommendation Reason의 종류다(R2 설계). 학생에게 실제로 의미 있는 상위 요소만 담고, Score에는
 * 반영되지만 설명 가치가 낮은 요소(difficulty 등)는 넣지 않는다 -- 과도한 세분화를 피한다(R1
 * 결정).
 *
 * OpenAI로 생성한 자연어 문장이 아니라 Type + 매칭 개수만 저장한다. 실제 문구 변환은 이 값을
 * 읽는 쪽(향후 API 응답 조립 단계)의 책임이다.
 */
enum class RecommendationReasonType {
    REQUIRED_SKILL_MATCH,
    PREFERRED_SKILL_MATCH,
    HIGH_SCHOOL_SUITABLE,
    ENTRY_LEVEL_SUITABLE,
}
