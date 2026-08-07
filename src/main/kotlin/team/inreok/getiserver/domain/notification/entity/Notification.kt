package team.inreok.getiserver.domain.notification.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UpdateTimestamp
import team.inreok.getiserver.domain.notification.entity.type.NotificationTargetType
import team.inreok.getiserver.domain.notification.entity.type.NotificationType
import java.time.LocalDateTime

/**
 * 사용자별 인앱 알림이다. `recipient_member_id`는 `members`를 가리키는 느슨한 FK이고(Domain
 * 경계 유지, JPA 연관관계 없음), `target_type`+`target_id`는 원본 리소스를 가리키는 다형적
 * 참조라 물리 FK가 없다.
 *
 * `targetAvailable`/`targetUnavailableReason`/`deepLink`는 이 Entity에 저장하지 않는다 — 원본이
 * 이후에 삭제되거나 비공개로 바뀔 수 있어 조회 시점에 계산해야 하기 때문이다
 * ([team.inreok.getiserver.domain.notification.service.NotificationTargetResolver] 참고).
 */
@Entity
@Table(name = "notifications")
class Notification(
    @Column(name = "recipient_member_id", nullable = false)
    var recipientMemberId: Long,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 100)
    var type: NotificationType,
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

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", length = 100)
    var targetType: NotificationTargetType? = null

    @Column(name = "target_id")
    var targetId: Long? = null

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: LocalDateTime? = null

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime? = null

    @Column(name = "read_at")
    var readAt: LocalDateTime? = null

    // 알림 삭제 API는 요구사항에 없어(원본 요구사항 문서 4절) 이 Column을 채우는 코드는 아직
    // 없다. V2 Schema에 이미 있으므로 Mapping만 유지하고, 조회 Query는 방어적으로
    // deletedAt IS NULL을 건다.
    @Column(name = "deleted_at")
    var deletedAt: LocalDateTime? = null

    /**
     * 읽지 않은 알림만 읽음 처리한다. 이미 읽은 알림은 [readAt]을 다시 쓰지 않아 여러 번 호출해도
     * 결과가 같다(원본 요구사항 문서 4절 "이미 읽은 알림은 멱등 처리").
     */
    fun markAsRead(readAt: LocalDateTime) {
        if (isRead) return
        isRead = true
        this.readAt = readAt
    }
}
