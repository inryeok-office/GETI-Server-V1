package team.inreok.getiserver.domain.notification.exception

import org.springframework.http.HttpStatus
import team.inreok.getiserver.global.error.ErrorCode

// 원본 요구사항 문서("GETI Notification 도메인 개발 요구사항") 20절 목록 중 이번 Notification
// Core 범위에서 실제로 발생하는 Error Code만 정의한다. 같은 절의 DISCORD_* 10개는 Discord
// Delivery를 구현하는 후속 PR에서 추가한다.
//
// 타인의 알림에 접근하면 404로 감추지 않고 403을 돌려준다 — 개인 리소스 접근 거부를 403으로
// 처리하는 기존 관례(ApplicationErrorCode.APPLICATION_ACCESS_FORBIDDEN,
// ApplicationErrorCode.NOT_FORM_OWNER)를 따른 것이다.
enum class NotificationErrorCode(
    override val status: HttpStatus,
    override val defaultMessage: String,
) : ErrorCode {
    NOTIFICATION_NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 알림을 찾을 수 없습니다."),
    NOTIFICATION_ACCESS_DENIED(HttpStatus.FORBIDDEN, "본인의 알림만 접근할 수 있습니다."),
    ;

    override val code: String get() = name
}
