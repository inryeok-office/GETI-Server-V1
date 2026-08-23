package team.inreok.getiserver.domain.audit.repository

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import team.inreok.getiserver.domain.audit.entity.AuditLog
import team.inreok.getiserver.domain.audit.entity.type.AuditResult
import java.time.LocalDateTime

interface AuditLogRepository : JpaRepository<AuditLog, Long> {
    @Query(
        """
        SELECT a FROM AuditLog a
        WHERE (:actorId IS NULL OR a.actorMemberId = :actorId)
          AND (:actionType IS NULL OR a.action = :actionType)
          AND (:targetType IS NULL OR a.targetType = :targetType)
          AND (:targetId IS NULL OR a.targetId = :targetId)
          AND (:result IS NULL OR a.result = :result)
          AND (:startAt IS NULL OR a.createdAt >= :startAt)
          AND (:endAt IS NULL OR a.createdAt <= :endAt)
        ORDER BY a.createdAt DESC, a.id DESC
        """,
    )
    fun searchForAdmin(
        @Param("actorId") actorId: Long?,
        @Param("actionType") actionType: String?,
        @Param("targetType") targetType: String?,
        @Param("targetId") targetId: Long?,
        @Param("result") result: AuditResult?,
        @Param("startAt") startAt: LocalDateTime?,
        @Param("endAt") endAt: LocalDateTime?,
        pageable: Pageable,
    ): Page<AuditLog>

    fun findByTargetTypeAndTargetIdOrderByCreatedAtDesc(
        targetType: String,
        targetId: Long,
        pageable: Pageable,
    ): List<AuditLog>
}
