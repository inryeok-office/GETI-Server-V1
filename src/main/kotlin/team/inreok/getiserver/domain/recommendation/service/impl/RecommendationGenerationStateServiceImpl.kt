package team.inreok.getiserver.domain.recommendation.service.impl

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import team.inreok.getiserver.domain.recommendation.entity.type.RecommendationGenerationStatus
import team.inreok.getiserver.domain.recommendation.repository.RecommendationGenerationStateRepository
import team.inreok.getiserver.domain.recommendation.service.RecommendationGenerationStateService
import java.time.LocalDateTime

/** [RecommendationGenerationStateService]의 구현이다(Recommendation R4, Issue #160). */
@Service
class RecommendationGenerationStateServiceImpl(
    private val repository: RecommendationGenerationStateRepository,
) : RecommendationGenerationStateService {
    @Transactional
    override fun markGenerating(memberId: Long) {
        repository.markGenerating(memberId)
    }

    @Transactional
    override fun markCompleted(
        memberId: Long,
        hasResults: Boolean,
    ) {
        val status = if (hasResults) RecommendationGenerationStatus.READY else RecommendationGenerationStatus.EMPTY
        repository.markCompleted(memberId, status.name, LocalDateTime.now())
    }

    @Transactional
    override fun markFailed(memberId: Long) {
        repository.markFailed(memberId, LocalDateTime.now())
    }
}
