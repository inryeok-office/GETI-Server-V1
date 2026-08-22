package team.inreok.getiserver.domain.audit.service

import org.springframework.data.domain.Pageable
import team.inreok.getiserver.domain.audit.dto.AdminAuditLogDetailResponse
import team.inreok.getiserver.domain.audit.dto.AdminAuditLogListResponse
import team.inreok.getiserver.domain.audit.entity.type.AuditResult
import java.time.LocalDateTime

interface AuditLogQueryService {
    fun list(
        actorId: Long?,
        actionType: String?,
        targetType: String?,
        targetId: Long?,
        result: AuditResult?,
        startAt: LocalDateTime?,
        endAt: LocalDateTime?,
        pageable: Pageable,
    ): AdminAuditLogListResponse

    fun get(auditLogId: Long): AdminAuditLogDetailResponse
}
