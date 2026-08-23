package team.inreok.getiserver.domain.collector.query

import org.springframework.modulith.NamedInterface
import team.inreok.getiserver.domain.operation.query.OperationJobState

@NamedInterface
fun interface CollectorSchedulerStatusQueryPort {
    fun getStatus(): OperationJobState
}
