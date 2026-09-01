package team.inreok.getiserver.domain.system.service

import org.springframework.data.domain.Pageable
import team.inreok.getiserver.domain.operation.entity.type.OperationJobType
import team.inreok.getiserver.domain.system.dto.OperationJobListResponse
import java.time.LocalDateTime

interface OperationJobQueryService {
    fun search(
        jobType: OperationJobType?,
        status: String?,
        startAt: LocalDateTime?,
        endAt: LocalDateTime?,
        pageable: Pageable,
    ): OperationJobListResponse
}
