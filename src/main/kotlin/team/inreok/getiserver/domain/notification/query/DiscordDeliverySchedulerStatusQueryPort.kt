package team.inreok.getiserver.domain.notification.query

import org.springframework.modulith.NamedInterface
import team.inreok.getiserver.domain.operation.query.OperationJobState

@NamedInterface
fun interface DiscordDeliverySchedulerStatusQueryPort {
    fun getStatus(): OperationJobState
}
