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

    // Idempotency Identity의 나머지 두 축이다(recipientMemberId + 이 둘, Issue #193). 과거 Row에는
    // 값이 없어(원본 Event를 다시 알 수 없다) DB Column은 nullable로 남아 있다 -- "새 알림 생성은
    // 두 값을 필수로 요구한다"라는 규칙은 NotificationCreateCommand/NotificationServiceImpl.create가
    // Kotlin 계약으로 강제하며, Entity/DB는 과거 Row와의 호환성을 위해 강제하지 않는다.
    @Column(name = "source_event_type", length = 100)
    var sourceEventType: String? = null

    @Column(name = "source_event_id")
    var sourceEventId: Long? = null

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: LocalDateTime? = null

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime? = null

    @Column(name = "read_at")
    var readAt: LocalDateTime? = null

    // 삭제 시각. null이 아니면 목록·읽지 않은 개수·읽음 처리 대상에서 모두 제외된다
    // (Issue #187에서 DELETE API가 이 Column을 채우기 시작했다 -- V16의 "이번 범위에서는
    // 사용하지 않는다"라는 설명은 그 시점 기준이고, 이미 병합된 Migration이라 수정하지 않는다).
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

    /**
     * 알림을 Soft Delete한다. 읽음 여부는 건드리지 않는다 -- 읽지 않은 알림을 삭제하면 조회
     * Query가 `deletedAt IS NULL`을 걸고 있어 읽지 않은 개수에서 자연히 빠지므로, 상태를 함께
     * 바꿔 "읽었다"라는 사실을 지어낼 필요가 없다.
     */
    fun softDelete(deletedAt: LocalDateTime) {
        this.deletedAt = deletedAt
    }
}
