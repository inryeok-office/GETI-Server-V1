package team.inreok.getiserver.domain.collector.service.impl

import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import team.inreok.getiserver.domain.collector.query.CollectorSchedulerStatusQueryPort
import team.inreok.getiserver.domain.collector.repository.CollectionRunErrorRepository
import team.inreok.getiserver.domain.collector.repository.CollectionRunRepository
import team.inreok.getiserver.domain.operation.query.OperationJobState

@Component
class CollectorSchedulerStatusQueryPortImpl(
    private val collectionRunRepository: CollectionRunRepository,
    private val collectionRunErrorRepository: CollectionRunErrorRepository,
) : CollectorSchedulerStatusQueryPort {
    @Transactional(readOnly = true)
    override fun getStatus(): OperationJobState {
        val run = collectionRunRepository.findFirstByOrderByStartedAtDescIdDesc() ?: return OperationJobState()
        val lastError =
            requireNotNull(
                run.id,
            ).let { collectionRunErrorRepository.findFirstByRunIdOrderByOccurredAtDesc(it)?.message }
        return OperationJobState(
            operationId = run.id.toString(),
            status = run.status.name,
            processedCount = run.totalCount.toLong(),
            successCount = run.successCount.toLong(),
            failureCount = run.failureCount.toLong(),
            partialSuccessCount = run.partialQualityCount.toLong(),
            startedAt = run.startedAt,
            finishedAt = run.finishedAt,
            lastRunAt = run.startedAt,
            lastError = lastError,
        )
    }
}
