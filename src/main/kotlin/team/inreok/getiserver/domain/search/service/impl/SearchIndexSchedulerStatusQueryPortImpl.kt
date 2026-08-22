package team.inreok.getiserver.domain.search.service.impl

import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import team.inreok.getiserver.domain.operation.query.OperationJobState
import team.inreok.getiserver.domain.search.query.SearchIndexSchedulerStatusQueryPort
import team.inreok.getiserver.domain.search.repository.SearchReindexRunRepository

@Component
class SearchIndexSchedulerStatusQueryPortImpl(
    private val reindexRunRepository: SearchReindexRunRepository,
) : SearchIndexSchedulerStatusQueryPort {
    @Transactional(readOnly = true)
    override fun getStatus(): OperationJobState {
        val run = reindexRunRepository.findFirstByOrderByCreatedAtDescIdDesc() ?: return OperationJobState()
        val processed = run.totalCount ?: 0
        return OperationJobState(
            operationId = run.id.toString(),
            status = run.status.name,
            processedCount = processed,
            successCount = run.successCount ?: 0,
            failureCount = run.failureCount ?: 0,
            startedAt = run.startedAt,
            finishedAt = run.completedAt,
            lastRunAt = run.startedAt ?: run.createdAt,
            lastError = run.errorMessage,
        )
    }
}
