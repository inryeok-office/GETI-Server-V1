package team.inreok.getiserver.domain.recommendation.repository

import org.springframework.data.jpa.repository.JpaRepository
import team.inreok.getiserver.domain.recommendation.entity.RecommendationPreference

interface RecommendationPreferenceRepository : JpaRepository<RecommendationPreference, Long> {
    fun findByMemberId(memberId: Long): RecommendationPreference?
}
