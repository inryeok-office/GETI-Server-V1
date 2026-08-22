package team.inreok.getiserver.domain.audit.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import team.inreok.getiserver.domain.audit.entity.type.AuditResult
import java.time.LocalDateTime
import java.util.UUID

@Entity
@Table(name = "audit_logs")
class AuditLog(
    @Column(nullable = false, length = 255)
    var action: String,
    @Column(name = "target_type", nullable = false, length = 100)
    var targetType: String,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    @Column(name = "actor_member_id")
    var actorMemberId: Long? = null

    @Column(name = "target_id")
    var targetId: Long? = null

    @Column(name = "request_id", columnDefinition = "uuid")
    var requestId: UUID? = null

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    var result: AuditResult? = null

    @Column(name = "result_message", columnDefinition = "text")
    var resultMessage: String? = null

    @Column(name = "request_path", length = 1000)
    var requestPath: String? = null

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "changed_data", columnDefinition = "jsonb")
    var changedData: String? = null

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: LocalDateTime? = null
}
