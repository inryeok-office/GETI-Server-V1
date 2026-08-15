package team.inreok.getiserver.domain.notification.dto

import io.swagger.v3.oas.annotations.media.Schema
import team.inreok.getiserver.domain.notification.entity.type.DiscordDeliveryStatus
import team.inreok.getiserver.domain.notification.entity.type.DiscordDeliveryTargetType
import java.time.LocalDateTime

/**
 * Discord 전달 상태 조회·수동 재시도 응답이다(Notification 요구사항 §15). Program의 자체
 * `discordDelivery` 필드가 있던 자리를 대신한다 -- 등록·수정 응답 시점에는 아직 Delivery
 * Row가 없어 값을 담을 수 없었지만, 이 API는 실제 Row가 생긴 뒤에만 호출되므로 정확한 값을
 * 돌려줄 수 있다(`discord-event-wiring-plan.md` §6.1).
 */
@Schema(description = "Discord 전달 상태")
data class DiscordDeliveryStatusResponse(
    @param:Schema(description = "대상 종류", example = "PROGRAM")
    val targetType: DiscordDeliveryTargetType,
    @param:Schema(description = "대상 ID", example = "123")
    val targetId: Long,
    @param:Schema(description = "전송에 사용한 Discord 채널 Snowflake", example = "1234567890123456789")
    val channelId: String,
    @param:Schema(
        description = "Discord 메시지 ID. CREATE가 아직 성공하지 못했으면 null",
        example = "1111111111111111111",
        nullable = true,
    )
    val messageId: String?,
    @param:Schema(description = "전달 상태", example = "DELIVERED")
    val status: DiscordDeliveryStatus,
    @param:Schema(description = "지금까지 수행한 자동 재시도 횟수", example = "1")
    val automaticRetryCount: Int,
    @param:Schema(description = "지금까지 수행한 수동 재시도 횟수", example = "0")
    val manualRetryCount: Int,
    @param:Schema(description = "자동 재시도 최대 허용 횟수", example = "3")
    val maxAutomaticRetryCount: Int,
    @param:Schema(description = "수동 재시도 최대 허용 횟수", example = "3")
    val maxManualRetryCount: Int,
    @param:Schema(description = "지금 수동 재시도를 요청할 수 있는지 여부(FAILED이고 상한 미만)", example = "false")
    val canRetry: Boolean,
    @param:Schema(description = "마지막 실패 코드. 성공했거나 아직 시도한 적이 없으면 null", nullable = true, example = "RATE_LIMITED")
    val failureCode: String?,
    @param:Schema(description = "마지막 실패 사유. 성공했거나 아직 시도한 적이 없으면 null", nullable = true)
    val failureReason: String?,
    @param:Schema(description = "Discord 전달이 예약된 시각", example = "2026-08-10T09:00:00")
    val requestedAt: LocalDateTime,
    @param:Schema(description = "마지막으로 전송을 시도한 시각. 아직 시도한 적이 없으면 null", nullable = true)
    val lastSyncedAt: LocalDateTime?,
)
