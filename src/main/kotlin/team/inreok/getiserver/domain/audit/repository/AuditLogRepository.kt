package team.inreok.getiserver.domain.audit.repository

import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import team.inreok.getiserver.domain.audit.entity.AuditLog

interface AuditLogRepository : JpaRepository<AuditLog, Long> {
    fun findByTargetTypeAndTargetIdOrderByCreatedAtDesc(
        targetType: String,
        targetId: Long,
        pageable: Pageable,
    ): List<AuditLog>
}
