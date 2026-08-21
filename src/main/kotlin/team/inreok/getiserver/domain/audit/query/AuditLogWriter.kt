package team.inreok.getiserver.domain.audit.query

import org.springframework.modulith.NamedInterface

@NamedInterface
interface AuditLogWriter {
    fun record(
        action: String,
        targetType: String,
        targetId: Long,
        actorMemberId: Long?,
    )
}
