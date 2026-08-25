package team.inreok.getiserver.domain.recommendation.service.impl

import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import team.inreok.getiserver.domain.operation.query.OperationJobState
import team.inreok.getiserver.domain.recommendation.entity.type.RecommendationGenerationStatus
import team.inreok.getiserver.domain.recommendation.query.RecommendationSchedulerStatusQueryPort
import team.inreok.getiserver.domain.recommendation.repository.RecommendationGenerationStateRepository

@Component
class RecommendationSchedulerStatusQueryPortImpl(
    private val stateRepository: RecommendationGenerationStateRepository,
) : RecommendationSchedulerStatusQueryPort {
    @Transactional(readOnly = true)
    override fun getStatus(): OperationJobState {
        val state = stateRepository.findFirstByOrderByUpdatedAtDescIdDesc() ?: return OperationJobState()
        val status =
            when (state.status) {
                RecommendationGenerationStatus.GENERATING -> "RUNNING"

                RecommendationGenerationStatus.READY,
                RecommendationGenerationStatus.EMPTY,
                -> "SUCCESS"

                RecommendationGenerationStatus.FAILED -> "FAILED"
            }
        val lastRunAt = state.generatedAt ?: state.lastFailureAt ?: state.updatedAt
        return OperationJobState(
            operationId = state.id.toString(),
            status = status,
            processedCount = 1,
            successCount =
                if (state.status == RecommendationGenerationStatus.READY ||
                    state.status == RecommendationGenerationStatus.EMPTY
                ) {
                    1
                } else {
                    0
                },
            failureCount = if (state.status == RecommendationGenerationStatus.FAILED) 1 else 0,
            startedAt = state.updatedAt,
            finishedAt = state.generatedAt ?: state.lastFailureAt,
            lastRunAt = lastRunAt,
        )
    }
}
