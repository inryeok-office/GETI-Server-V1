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
}
