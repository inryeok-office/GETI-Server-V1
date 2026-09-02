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
import team.inreok.getiserver.domain.notification.entity.type.PushDeliveryStatus
import java.time.LocalDateTime

/**
 * 인앱 알림(`Notification`) 한 건을 기기(`NotificationDevice`) 한 대에 Push로 전달하는 작업과 그
 * 상태다(Issue #190). 회원이 기기를 여러 대 등록했으면 알림 1건마다 기기 수만큼 Row가 생겨, 기기
 * 하나의 실패가 다른 기기의 전송에 영향을 주지 않는다(확정 계약).
 *
 * `DiscordDelivery`와 달리 Payload(title/content)를 별도로 들고 있지 않다 -- 발송 시점에
 * [notificationId]로 `Notification`을 다시 조회해 최신 제목/본문을 쓴다(같은 이유:
 * `DiscordDelivery` KDoc 참고). [deviceId]도 마찬가지로 발송 시점에 `NotificationDevice`를 다시
 * 조회해 최신 `pushToken`을 쓴다 -- Token은 재등록으로 언제든 바뀔 수 있다.
 *
 * [deviceId]에는 물리 FK가 없다(Migration KDoc 참고) -- 무효 Token 정리가 `NotificationDevice`
 * Row 자체를 지우는 방식이라, 이미 FAILED로 끝난 과거 Row가 그 삭제를 막으면 안 되기 때문이다.
 * [memberId]는 `NotificationDevice`가 삭제된 뒤에도 "누구에게 보내려던 시도였는지"를 유지하려고
 * 별도로 저장한다.
 */
@Entity
@Table(name = "push_deliveries")
class PushDelivery(
    @Column(name = "notification_id", nullable = false)
    val notificationId: Long,
    @Column(name = "member_id", nullable = false)
    val memberId: Long,
    @Column(name = "device_id", nullable = false)
    val deviceId: Long,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: PushDeliveryStatus = PushDeliveryStatus.PENDING

    @Column(name = "retry_count", nullable = false)
    var retryCount: Int = 0

    @Column(name = "next_retry_at")
    var nextRetryAt: LocalDateTime? = null

    @Column(name = "processing_started_at")
    var processingStartedAt: LocalDateTime? = null

    @Column(name = "last_attempt_at")
    var lastAttemptAt: LocalDateTime? = null

    @Column(name = "sent_at")
    var sentAt: LocalDateTime? = null

    @Column(name = "last_error_code", length = 50)
    var lastErrorCode: String? = null

    @Column(name = "last_error_message", length = 1000)
    var lastErrorMessage: String? = null

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: LocalDateTime? = null

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime? = null

    /** Provider가 전송 요청을 성공적으로 접수했다. */
    fun markSent(now: LocalDateTime) {
        status = PushDeliveryStatus.SENT
        sentAt = now
        lastAttemptAt = now
        processingStartedAt = null
        nextRetryAt = null
        lastErrorCode = null
        lastErrorMessage = null
    }

    /** 재시도 가능한(Temporary) 실패다. 재시도 횟수를 늘리고 다음 시도 시각을 예약한다. */
    fun markRetryScheduled(
        retryAt: LocalDateTime,
        errorCode: String,
        errorMessage: String?,
        now: LocalDateTime,
    ) {
        status = PushDeliveryStatus.PENDING
        retryCount += 1
        nextRetryAt = retryAt
        processingStartedAt = null
        lastAttemptAt = now
        lastErrorCode = errorCode
        lastErrorMessage = errorMessage
    }

    /**
     * 더 이상 재시도하지 않는 실패다(재시도 불가능한 오류, 무효 Token, 또는 재시도 횟수 소진).
     * [retryCount]를 늘리지 않는다 -- `DiscordDelivery.markFailed`와 같은 이유로, 이 값은 "예약해서
     * 수행한 재시도 횟수"이기 때문이다.
     */
    fun markFailed(
        errorCode: String,
        errorMessage: String?,
        now: LocalDateTime,
    ) {
        status = PushDeliveryStatus.FAILED
        nextRetryAt = null
        processingStartedAt = null
        lastAttemptAt = now
        lastErrorCode = errorCode
        lastErrorMessage = errorMessage
    }
}
