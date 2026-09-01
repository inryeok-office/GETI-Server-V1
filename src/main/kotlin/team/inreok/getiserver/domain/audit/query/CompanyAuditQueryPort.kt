package team.inreok.getiserver.domain.audit.query

import org.springframework.modulith.NamedInterface
import java.time.LocalDateTime

@NamedInterface
interface CompanyAuditQueryPort {
    fun findRecentChanges(
        targetId: Long,
        limit: Int,
    ): List<CompanyAuditSnapshot>
}

@NamedInterface
data class CompanyAuditSnapshot(
    val auditLogId: Long,
    val action: String,
    val actorMemberId: Long?,
    val changedAt: LocalDateTime?,
)
