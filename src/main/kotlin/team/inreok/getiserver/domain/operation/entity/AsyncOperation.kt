package team.inreok.getiserver.domain.operation.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UuidGenerator
import team.inreok.getiserver.domain.operation.entity.type.OperationStatus
import team.inreok.getiserver.domain.operation.entity.type.OperationType
import java.time.LocalDateTime
import java.util.UUID

@Entity
@Table(name = "async_operations")
class AsyncOperation(
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    var type: OperationType,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    var status: OperationStatus = OperationStatus.PENDING,
    @Column(name = "progress_percent", nullable = false)
    var progressPercent: Int = 0,
) {
    @Id
    @UuidGenerator
    @Column(columnDefinition = "uuid")
    var id: UUID? = null

    @Column(name = "requested_by_member_id")
    var requestedByMemberId: Long? = null

    @Column(name = "target_type", length = 100)
    var targetType: String? = null

    @Column(name = "target_id")
    var targetId: Long? = null

    @Column(name = "result_file_id")
    var resultFileId: Long? = null

    @Column(name = "error_message", columnDefinition = "text")
    var errorMessage: String? = null

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: LocalDateTime? = null

    @Column(name = "completed_at")
    var completedAt: LocalDateTime? = null

    @Column(name = "expires_at")
    var expiresAt: LocalDateTime? = null
}
