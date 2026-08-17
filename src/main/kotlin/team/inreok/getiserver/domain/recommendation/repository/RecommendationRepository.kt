package team.inreok.getiserver.domain.recommendation.repository

import org.springframework.data.jpa.repository.JpaRepository
import team.inreok.getiserver.domain.recommendation.entity.Recommendation
import java.time.LocalDate

interface RecommendationRepository : JpaRepository<Recommendation, Long> {
    fun findByMemberIdAndJobIdAndRecommendationDate(
        memberId: Long,
        jobId: Long,
        recommendationDate: LocalDate,
    ): Recommendation?

    fun findAllByMemberIdAndRecommendationDateOrderByRank(
        memberId: Long,
        recommendationDate: LocalDate,
    ): List<Recommendation>

    // 회원 1명의 추천 결과를 하루치 Snapshot으로 통째로 교체하기 위한 삭제다(R2 Persistence
    // 설계, `RecommendationGenerationServiceImpl`이 삭제 -> 재삽입을 한 Transaction에서
    // 수행한다). `MemberTechStackRepository.deleteAllByIdMemberId`와 같은 관례로 `@Modifying`
    // 없이 Spring Data의 derived delete를 그대로 쓴다.
    fun deleteAllByMemberIdAndRecommendationDate(
        memberId: Long,
        recommendationDate: LocalDate,
    )

    // 관심 없음(THIS_JOB) 설정 시 현재 노출 중인 Recommendation을 즉시 제거하기 위한 삭제다(R3,
    // Issue #152). 조회 API가 항상 오늘자 결과만 보여주므로(recommendationDate 제한 없이) 그
    // 회원의 해당 Job Recommendation을 통째로 지워도 무방하다 -- 과거 날짜 Row가 남아 있어도
    // 조회에 다시 나타나지 않는다. Hard Filter(`computeExclusionReason`)가 다음 생성부터
    // 이 Job을 이미 걸러내므로 재생성 때 다시 만들어지지도 않는다.
    fun deleteAllByMemberIdAndJobId(
        memberId: Long,
        jobId: Long,
    )
}
