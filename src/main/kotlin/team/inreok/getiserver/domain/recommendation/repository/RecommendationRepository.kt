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
}
