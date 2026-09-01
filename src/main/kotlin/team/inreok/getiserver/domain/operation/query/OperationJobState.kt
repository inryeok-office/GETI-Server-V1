package team.inreok.getiserver.domain.operation.query

import org.springframework.modulith.NamedInterface
import java.time.LocalDateTime

/** Persisted state exposed by a domain-owned scheduler status port. */
@NamedInterface
data class OperationJobState(
    val operationId: String? = null,
    val status: String = NO_HISTORY_STATUS,
    val processedCount: Long = 0,
    val successCount: Long = 0,
    val failureCount: Long = 0,
    val partialSuccessCount: Long = 0,
    val startedAt: LocalDateTime? = null,
    val finishedAt: LocalDateTime? = null,
    val lastRunAt: LocalDateTime? = null,
    val lastError: String? = null,
) {
    companion object {
        const val NO_HISTORY_STATUS = "NO_HISTORY"
    }
}
