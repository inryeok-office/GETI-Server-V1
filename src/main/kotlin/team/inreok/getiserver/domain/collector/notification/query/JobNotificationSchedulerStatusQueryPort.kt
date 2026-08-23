package team.inreok.getiserver.domain.collector.notification.query

import org.springframework.modulith.NamedInterface
import team.inreok.getiserver.domain.operation.query.OperationJobState

@NamedInterface
fun interface JobNotificationSchedulerStatusQueryPort {
    fun getStatus(): OperationJobState
}
