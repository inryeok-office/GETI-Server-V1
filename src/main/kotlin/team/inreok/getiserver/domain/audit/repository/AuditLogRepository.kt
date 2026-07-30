package team.inreok.getiserver.domain.audit.repository

import org.springframework.data.jpa.repository.JpaRepository
import team.inreok.getiserver.domain.audit.entity.AuditLog

interface AuditLogRepository : JpaRepository<AuditLog, Long>
