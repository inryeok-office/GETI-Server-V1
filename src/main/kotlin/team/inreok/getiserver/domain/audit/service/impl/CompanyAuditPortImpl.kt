package team.inreok.getiserver.domain.audit.service.impl

import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import team.inreok.getiserver.domain.audit.entity.AuditLog
import team.inreok.getiserver.domain.audit.query.AuditLogWriter
import team.inreok.getiserver.domain.audit.query.CompanyAuditQueryPort
import team.inreok.getiserver.domain.audit.query.CompanyAuditSnapshot
import team.inreok.getiserver.domain.audit.repository.AuditLogRepository

@Service
class CompanyAuditPortImpl(
    private val auditLogRepository: AuditLogRepository,
) : CompanyAuditQueryPort,
    AuditLogWriter {
    @Transactional(readOnly = true)
    override fun findRecentChanges(
        targetId: Long,
        limit: Int,
    ): List<CompanyAuditSnapshot> =
        auditLogRepository
            .findByTargetTypeAndTargetIdOrderByCreatedAtDesc(COMPANY_TARGET_TYPE, targetId, PageRequest.of(0, limit))
            .map { audit ->
                CompanyAuditSnapshot(
                    auditLogId = requireNotNull(audit.id),
                    action = audit.action,
                    actorMemberId = audit.actorMemberId,
                    changedAt = audit.createdAt,
                )
            }

    @Transactional
    override fun record(
        action: String,
        targetType: String,
        targetId: Long,
        actorMemberId: Long?,
    ) {
        auditLogRepository.save(
            AuditLog(action = action, targetType = targetType).apply {
                this.targetId = targetId
                this.actorMemberId = actorMemberId
            },
        )
    }

    private companion object {
        const val COMPANY_TARGET_TYPE = "COMPANY"
    }
}
