package team.inreok.getiserver.domain.program.service.impl

import org.springframework.stereotype.Component
import team.inreok.getiserver.domain.operation.query.OperationJobState
import team.inreok.getiserver.domain.program.query.ProgramCloseSchedulerStatusQueryPort

/** Program close has no persisted scheduler execution record yet. */
@Component
class ProgramCloseSchedulerStatusQueryPortImpl : ProgramCloseSchedulerStatusQueryPort {
    override fun getStatus(): OperationJobState = OperationJobState()
}
