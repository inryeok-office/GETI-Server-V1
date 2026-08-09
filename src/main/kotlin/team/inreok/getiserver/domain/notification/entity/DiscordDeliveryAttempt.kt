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
import team.inreok.getiserver.domain.notification.entity.type.DiscordDeliveryAttemptResult
import team.inreok.getiserver.domain.notification.entity.type.DiscordDeliveryAttemptType
import java.time.LocalDateTime

/**
 * Bot Internal API를 실제로 호출한 이력 한 건이다(후속 요구사항 문서 §12). Delivery Row는
 * "지금 상태"만 담고, 시도별 이력은 이 Table이 담는다 — 수동 재시도가 Delivery의
 * `automaticRetryCount`를 0으로 되돌려도 여기 남은 이력은 사라지지 않는다.
 *
 * **저장하지 않는 것**: Bot Token, Internal API Key, Authorization Header, 전체 Request
 * Payload, 문의 본문, Discord SDK의 raw Error와 Stack Trace. 운영에 필요한 최소 정보만 남긴다.
 * 실패 문구도 여기에 복제하지 않는다 — 최신 실패 사유는
 * [DiscordDelivery.lastErrorMessage] 한 곳으로 충분하고, 시도마다 복제하면 개인정보가 섞여
 * 들어갈 표면만 넓어진다.
 *
 * [discordDeliveryId]는 JPA 연관관계가 아니라 ID 참조다 — Attempt는 Delivery 없이 조회되지
 * 않고, 연관을 걸면 Worker가 Attempt를 저장할 때마다 Delivery를 불필요하게 로딩한다.
 */
@Entity
@Table(name = "discord_delivery_attempts")
class DiscordDeliveryAttempt(
    @Column(name = "discord_delivery_id", nullable = false)
    val discordDeliveryId: Long,
    @Enumerated(EnumType.STRING)
    @Column(name = "attempt_type", nullable = false, length = 20)
    val attemptType: DiscordDeliveryAttemptType,
    /** 해당 Delivery에서 몇 번째 시도인지다. 자동/수동을 합친 누적 순번으로 1부터 시작한다. */
    @Column(name = "attempt_number", nullable = false)
    val attemptNumber: Int,
    /** Bot에 `X-Request-Id`로 보낸 값이다. Bot 로그와 서버 로그를 잇는 유일한 연결고리다. */
    @Column(name = "request_id", nullable = false, length = 64)
    val requestId: String,
    @Column(name = "started_at", nullable = false)
    val startedAt: LocalDateTime,
    @Column(name = "finished_at", nullable = false)
    val finishedAt: LocalDateTime,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    val result: DiscordDeliveryAttemptResult,
    @Column(name = "error_code", length = 50)
    val errorCode: String? = null,
    /** Bot 응답의 `retryable`이다. 성공했거나 Bot 응답을 해석하지 못했으면 null이다. */
    @Column(name = "retryable")
    val retryable: Boolean? = null,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: LocalDateTime? = null
}
