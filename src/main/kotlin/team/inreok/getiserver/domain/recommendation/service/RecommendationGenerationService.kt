package team.inreok.getiserver.domain.recommendation.service

/**
 * 회원 1명의 Recommendation을 계산하고 저장하는 R2 Core Use Case다. Daily Scheduler(R4)는 대상
 * 회원 목록을 순회하며 이 Use Case를 회원 단위로 호출하도록 설계되어 있다(R1 설계 §35, 회원 1명
 * 실패가 다른 회원 계산을 막지 않도록 하기 위함) -- Scheduler 자체는 R2 범위가 아니다.
 */
interface RecommendationGenerationService {
    /**
     * 오늘(`LocalDate.now()`) 기준으로 [memberId]의 Recommendation을 계산해 상위 [limit]건을
     * 저장한다. 같은 회원의 기존 오늘자 결과가 있으면 통째로 교체한다(R1 설계 §36 전체 교체
     * 방식). Member Profile을 찾을 수 없으면 아무것도 하지 않고 빈 목록을 반환한다.
     */
    fun generateForMember(
        memberId: Long,
        limit: Int,
    ): List<RankedRecommendation>
}
