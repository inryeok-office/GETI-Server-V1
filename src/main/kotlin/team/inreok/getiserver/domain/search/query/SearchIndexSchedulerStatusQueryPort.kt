package team.inreok.getiserver.domain.search.query

import org.springframework.modulith.NamedInterface
import team.inreok.getiserver.domain.operation.query.OperationJobState

@NamedInterface
fun interface SearchIndexSchedulerStatusQueryPort {
    fun getStatus(): OperationJobState
}
