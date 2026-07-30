package team.inreok.getiserver.domain.notification.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.CreationTimestamp
import java.time.LocalDateTime

@Entity
@Table(name = "notifications")
class Notification(
    @Column(name = "recipient_member_id", nullable = false)
    var recipientMemberId: Long,
    @Column(nullable = false, length = 100)
    var type: String,
    @Column(nullable = false, length = 500)
    var title: String,
    @Column(nullable = false, columnDefinition = "text")
    var content: String,
    @Column(name = "is_read", nullable = false)
    var isRead: Boolean = false,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    @Column(name = "resource_type", length = 100)
    var resourceType: String? = null

    @Column(name = "resource_id")
    var resourceId: Long? = null

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: LocalDateTime? = null

    @Column(name = "read_at")
    var readAt: LocalDateTime? = null

    @Column(name = "deleted_at")
    var deletedAt: LocalDateTime? = null
}
