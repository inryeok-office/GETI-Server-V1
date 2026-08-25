package team.inreok.getiserver.domain.notification.service.impl

import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import team.inreok.getiserver.domain.notification.entity.type.DiscordDeliveryStatus
import team.inreok.getiserver.domain.notification.query.DiscordDeliverySchedulerStatusQueryPort
import team.inreok.getiserver.domain.notification.repository.DiscordDeliveryRepository
import team.inreok.getiserver.domain.operation.query.OperationJobState

@Component
class DiscordDeliverySchedulerStatusQueryPortImpl(
    private val deliveryRepository: DiscordDeliveryRepository,
) : DiscordDeliverySchedulerStatusQueryPort {
    @Transactional(readOnly = true)
    override fun getStatus(): OperationJobState {
        val delivery = deliveryRepository.findFirstByOrderByUpdatedAtDescIdDesc() ?: return OperationJobState()
        val processed = if (delivery.lastAttemptAt != null || delivery.deliveredAt != null) 1L else 0L
        val success = if (delivery.status == DiscordDeliveryStatus.DELIVERED) 1L else 0L
        val failure = if (delivery.status == DiscordDeliveryStatus.FAILED) 1L else 0L
        return OperationJobState(
            operationId = delivery.id.toString(),
            status = delivery.status.name,
            processedCount = processed,
            successCount = success,
            failureCount = failure,
            startedAt = delivery.lastAttemptAt ?: delivery.createdAt,
            finishedAt = delivery.deliveredAt,
            lastRunAt = delivery.lastAttemptAt ?: delivery.createdAt,
            lastError = delivery.lastErrorMessage,
        )
    }
}
