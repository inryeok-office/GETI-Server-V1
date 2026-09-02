package team.inreok.getiserver.domain.program.query

import org.springframework.modulith.NamedInterface
import team.inreok.getiserver.domain.operation.query.OperationJobState

@NamedInterface
fun interface ProgramCloseSchedulerStatusQueryPort {
    fun getStatus(): OperationJobState
}
