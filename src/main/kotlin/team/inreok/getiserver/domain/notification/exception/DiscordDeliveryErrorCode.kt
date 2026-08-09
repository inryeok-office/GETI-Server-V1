package team.inreok.getiserver.domain.notification.exception

import org.springframework.http.HttpStatus
import team.inreok.getiserver.global.error.ErrorCode

/**
 * Discord Delivery를 다루는 중 **API 오류로 응답해야 하는** 상황만 정의한다.
 *
 * Bot이 내려주는 9개 ErrorCode(`INVALID_REQUEST`, `RATE_LIMITED` 등)는 여기 넣지 않는다 --
 * 그것들은 Delivery Row의 `lastErrorCode`에 기록되는 **전달 실패 사유**이지, GETI API가
 * 사용자에게 돌려주는 오류가 아니다. 새 Error Code는 실제로 처리하는 오류에만 추가한다는 규칙
 * (.claude/rules/spring-boot.md)에 따른 것이다.
 */
enum class DiscordDeliveryErrorCode(
    override val status: HttpStatus,
    override val defaultMessage: String,
) : ErrorCode {
    DISCORD_DELIVERY_NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 Discord 전달 이력을 찾을 수 없습니다."),

    /** FAILED가 아닌 상태(PENDING/PROCESSING/DELIVERED)에서 수동 재시도를 요청한 경우다(§18). */
    DISCORD_DELIVERY_NOT_RETRYABLE(HttpStatus.CONFLICT, "실패한 Discord 전달만 다시 시도할 수 있습니다."),

    /** 수동 재시도 상한을 모두 소진한 경우다(§18). */
    DISCORD_DELIVERY_RETRY_LIMIT_EXCEEDED(HttpStatus.CONFLICT, "수동 재시도 가능 횟수를 모두 사용했습니다."),
    ;

    override val code: String get() = name
}
