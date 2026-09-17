package team.inreok.getiserver.domain.notification.exception

import org.springframework.http.HttpStatus
import team.inreok.getiserver.global.error.ErrorCode

enum class DiscordSendTargetErrorCode(
    override val status: HttpStatus,
    override val defaultMessage: String,
) : ErrorCode {
    INVALID_TARGET_GRADE(HttpStatus.BAD_REQUEST, "대상 학년은 1, 2, 3 중 하나여야 합니다."),
    ;

    override val code: String get() = name
}
