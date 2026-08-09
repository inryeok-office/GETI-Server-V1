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
import team.inreok.getiserver.domain.notification.entity.type.DiscordDeliveryAction
import team.inreok.getiserver.domain.notification.entity.type.DiscordDeliveryStatus
import team.inreok.getiserver.domain.notification.entity.type.DiscordDeliveryTargetType
import team.inreok.getiserver.domain.notification.entity.type.DiscordMessageTemplate
import java.time.LocalDateTime

/**
 * 하나의 논리적인 Discord 작업(어떤 대상에 어떤 Action을 수행할 것인가)과 그 전달 상태다.
 * Notification 도메인이 Discord 전달 상태의 Source of Truth이며(후속 요구사항 문서 §38),
 * Bot의 InMemory Idempotency는 Bot 재시작 시 사라지므로 운영 상태 조회 근거로 쓰지 않는다.
 *
 * **Payload Snapshot을 저장하지 않는다**(§19·§39). 재시도할 때는 [targetType]+[targetId]로 원본
 * 도메인의 **최신** 데이터를 다시 조회해 의미 데이터를 새로 만든다. 실패 당시 내용을 그대로
 * 다시 보내면 그 사이 수정된 제목이 반영되지 않고, 문의 본문 같은 개인정보를 중복 저장하게
 * 된다.
 *
 * `target_type`+`target_id`는 다형적 참조라 물리 FK가 없다(`notifications`와 같은 방식).
 */
@Entity
@Table(name = "discord_deliveries")
class DiscordDelivery(
    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 20)
    val targetType: DiscordDeliveryTargetType,
    @Column(name = "target_id", nullable = false)
    val targetId: Long,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    val action: DiscordDeliveryAction,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    val template: DiscordMessageTemplate,
    /**
     * Discord Snowflake는 JavaScript Number의 안전 정수 범위를 넘을 수 있어 숫자형이 아니라
     * 문자열로 다룬다(§8·§46). 길이 64는 Bot `idSchema`의 상한과 같다.
     */
    @Column(name = "channel_id", nullable = false, length = 64)
    val channelId: String,
    /**
     * Mention할 Role Snowflake 목록을 쉼표로 이어 붙인 값이다. 최초 성공 CREATE에서만 쓰이고
     * (§24) 조회 조건이 되지 않아 별도 Table이나 jsonb를 쓰지 않는다. Bot이 최대 10개까지만
     * 받으므로 그보다 많이 담지 않는다.
     */
    @Column(name = "role_ids", length = ROLE_IDS_MAX_LENGTH)
    val roleIds: String? = null,
    @Column(name = "idempotency_key", nullable = false, length = 200)
    val idempotencyKey: String,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: DiscordDeliveryStatus = DiscordDeliveryStatus.PENDING

    @Column(name = "discord_message_id", length = 64)
    var discordMessageId: String? = null

    @Column(name = "automatic_retry_count", nullable = false)
    var automaticRetryCount: Int = 0

    @Column(name = "manual_retry_count", nullable = false)
    var manualRetryCount: Int = 0

    @Column(name = "next_retry_at")
    var nextRetryAt: LocalDateTime? = null

    /**
     * Worker가 이 Row를 선점한 시각이다. 서버가 Bot 호출 도중 죽으면 [status]가 PROCESSING에
     * 영구히 머물러 재시도 대상에서 빠지는데(§31), 이 시각이 임계값보다 오래되면 회수한다.
     *
     * Collector의 재기동 복구 Runner(`JobNotificationStaleDeliveryRecoveryRunner`)처럼 기동
     * 시점에 PROCESSING을 전부 되돌리는 방식은 쓰지 않는다 — 그 방식은 단일 인스턴스를
     * 전제하며, 인스턴스가 둘 이상이면 재기동한 쪽이 **다른 인스턴스가 지금 전송 중인 Row**를
     * 되돌려 같은 메시지를 중복 게시할 수 있다.
     */
    @Column(name = "processing_started_at")
    var processingStartedAt: LocalDateTime? = null

    @Column(name = "last_attempt_at")
    var lastAttemptAt: LocalDateTime? = null

    @Column(name = "delivered_at")
    var deliveredAt: LocalDateTime? = null

    @Column(name = "last_error_code", length = 50)
    var lastErrorCode: String? = null

    /**
     * 운영자가 원인을 파악할 최소한의 문구만 담는다. Bot이 내려준 message는 Discord SDK의 raw
     * Error나 Stack Trace를 포함하지 않도록 Bot 쪽에서 이미 걸러져 있다(§16).
     */
    @Column(name = "last_error_message", length = 1000)
    var lastErrorMessage: String? = null

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: LocalDateTime? = null

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime? = null

    /** Mention 대상 Role 목록이다. 값이 없으면 빈 List다. */
    fun roleIdList(): List<String> = roleIds?.split(ROLE_ID_DELIMITER)?.filter { it.isNotBlank() } ?: emptyList()

    /**
     * Bot 호출이 성공했을 때의 상태 전이다.
     *
     * CREATE는 Discord messageId를 반드시 받아야 한다(§21) — 이후 UPDATE/CLOSE_NOTICE/
     * DELETE_NOTICE가 그 id로 기존 메시지를 수정하기 때문이다. 호출부가 messageId 부재를
     * Contract 오류로 처리하도록, 여기서는 값이 있을 때만 저장한다.
     */
    fun markDelivered(
        messageId: String,
        now: LocalDateTime,
    ) {
        discordMessageId = messageId
        status = DiscordDeliveryStatus.DELIVERED
        deliveredAt = now
        lastAttemptAt = now
        processingStartedAt = null
        nextRetryAt = null
        lastErrorCode = null
        lastErrorMessage = null
    }

    /** 재시도 가능한 실패다. 자동 재시도 횟수를 늘리고 다음 시도 시각을 예약한다. */
    fun markRetryScheduled(
        retryAt: LocalDateTime,
        errorCode: String,
        errorMessage: String?,
        now: LocalDateTime,
    ) {
        status = DiscordDeliveryStatus.PENDING
        automaticRetryCount += 1
        nextRetryAt = retryAt
        processingStartedAt = null
        lastAttemptAt = now
        lastErrorCode = errorCode
        lastErrorMessage = errorMessage
    }

    /**
     * 더 이상 자동으로 재시도하지 않는 실패다. 재시도 불가능한 오류이거나 자동 횟수를 모두
     * 소진한 경우다. [nextRetryAt]을 비워 Worker가 다시 집어가지 않게 한다.
     *
     * [automaticRetryCount]를 늘리지 않는다 -- 이 값은 "예약해서 수행한 재시도 횟수"이고,
     * 여기서 또 늘리면 상한이 3인데 3을 넘는 값이 저장되어 상태 조회에 그대로 노출된다.
     */
    fun markFailed(
        errorCode: String,
        errorMessage: String?,
        now: LocalDateTime,
    ) {
        status = DiscordDeliveryStatus.FAILED
        nextRetryAt = null
        processingStartedAt = null
        lastAttemptAt = now
        lastErrorCode = errorCode
        lastErrorMessage = errorMessage
    }

    /**
     * 사람이 누른 수동 재시도다(§18). 새 Delivery를 만들지 않고 같은 논리적 Delivery를
     * 재사용하므로 `idempotencyKey`가 바뀌지 않는다(§20).
     *
     * 자동 재시도 횟수를 0으로 되돌려 백오프 사이클을 처음부터 다시 받는다. 시도 이력 전체는
     * `discord_delivery_attempts`에 남아 있어 이 초기화로 감사 정보가 사라지지 않는다.
     * [manualRetryCount]는 절대 초기화하지 않아 수동 재시도 상한이 유지된다.
     */
    fun markManualRetryRequested() {
        manualRetryCount += 1
        automaticRetryCount = 0
        status = DiscordDeliveryStatus.PENDING
        nextRetryAt = null
        processingStartedAt = null
    }

    companion object {
        private const val ROLE_ID_DELIMITER = ","

        /** Snowflake 최대 64자 × 10개 + 구분자 9개 = 649. 여유를 둔 값이다. */
        const val ROLE_IDS_MAX_LENGTH = 700

        /** Bot이 한 요청에서 받는 Role 개수 상한이다(`ROLE_IDS_MAX`). */
        const val MAX_ROLE_IDS = 10

        fun joinRoleIds(roleIds: List<String>): String? =
            roleIds
                .filter { it.isNotBlank() }
                .takeIf { it.isNotEmpty() }
                ?.joinToString(ROLE_ID_DELIMITER)
    }
}
