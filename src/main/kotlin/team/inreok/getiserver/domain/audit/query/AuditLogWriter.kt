package team.inreok.getiserver.domain.audit.query

import org.springframework.modulith.NamedInterface
import team.inreok.getiserver.domain.audit.entity.type.AuditResult

@NamedInterface
interface AuditLogWriter {
    fun record(
        action: String,
        targetType: String,
        targetId: Long,
        actorMemberId: Long?,
        result: AuditResult = AuditResult.SUCCESS,
        resultMessage: String? = null,
        requestPath: String? = null,
    )
}
