package team.inreok.getiserver.domain.collector.notification.service.impl

import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import team.inreok.getiserver.domain.collector.entity.type.JobNotificationDeliveryStatus
import team.inreok.getiserver.domain.collector.notification.query.JobNotificationSchedulerStatusQueryPort
import team.inreok.getiserver.domain.collector.repository.JobNotificationDeliveryRepository
import team.inreok.getiserver.domain.operation.query.OperationJobState

@Component
class JobNotificationSchedulerStatusQueryPortImpl(
    private val deliveryRepository: JobNotificationDeliveryRepository,
) : JobNotificationSchedulerStatusQueryPort {
    @Transactional(readOnly = true)
    override fun getStatus(): OperationJobState {
        val delivery = deliveryRepository.findFirstByOrderByUpdatedAtDescIdDesc() ?: return OperationJobState()
        val processed = if (delivery.lastAttemptAt != null || delivery.sentAt != null) 1L else 0L
        val success = if (delivery.status == JobNotificationDeliveryStatus.SENT) 1L else 0L
        val failure = if (delivery.status == JobNotificationDeliveryStatus.FAILED) 1L else 0L
        return OperationJobState(
            operationId = delivery.id.toString(),
            status = delivery.status.name,
            processedCount = processed,
            successCount = success,
            failureCount = failure,
            startedAt = delivery.lastAttemptAt ?: delivery.createdAt,
            finishedAt = delivery.sentAt,
            lastRunAt = delivery.lastAttemptAt ?: delivery.createdAt,
            lastError = delivery.errorMessage,
        )
    }
}
