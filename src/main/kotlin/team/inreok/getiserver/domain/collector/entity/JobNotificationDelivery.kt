package team.inreok.getiserver.domain.collector.entity

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
import team.inreok.getiserver.domain.collector.entity.type.JobNotificationDeliveryStatus
import java.time.LocalDateTime

/**
 * Job 신규 등록(CREATED)에 대한 Discord Webhook 발송 이력이다. Job 하나당 최대 한 건만
 * 존재한다(job_id UNIQUE) — 재시도는 이 Row를 갱신하며 진행한다. Webhook URL이나 Discord
 * 전용 Embed 구조(색상·Footer 등)는 저장하지 않는다. title/companyName 등은 재시도 시 Embed를
 * 다시 만들기 위한 최소 표시 필드 스냅샷이다(Docs, "Job Notification Delivery" 참고).
 */
@Entity
@Table(name = "job_notification_deliveries")
class JobNotificationDelivery(
    @Column(name = "job_id", nullable = false, unique = true)
    val jobId: Long,
    @Column(name = "source_code", nullable = false, length = 30)
    val sourceCode: String,
    @Column(name = "source_display_name", nullable = false, length = 255)
    val sourceDisplayName: String,
    @Column(nullable = false, length = 500)
    val title: String,
    @Column(name = "company_name", nullable = false, length = 255)
    val companyName: String,
    @Column(name = "external_url", length = 2000)
    val externalUrl: String? = null,
    @Column(name = "recruitment_ended_at")
    val recruitmentEndedAt: LocalDateTime? = null,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: JobNotificationDeliveryStatus = JobNotificationDeliveryStatus.PENDING

    @Column(name = "attempt_count", nullable = false)
    var attemptCount: Int = 0

    @Column(name = "next_retry_at")
    var nextRetryAt: LocalDateTime? = null

    @Column(name = "last_attempt_at")
    var lastAttemptAt: LocalDateTime? = null

    @Column(name = "sent_at")
    var sentAt: LocalDateTime? = null

    @Column(name = "discord_message_id", length = 64)
    var discordMessageId: String? = null

    @Column(name = "error_code", length = 100)
    var errorCode: String? = null

    @Column(name = "error_message", length = 1000)
    var errorMessage: String? = null

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: LocalDateTime? = null

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime? = null
}
