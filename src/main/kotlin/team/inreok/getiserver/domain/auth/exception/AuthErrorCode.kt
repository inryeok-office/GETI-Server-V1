package team.inreok.getiserver.domain.auth.exception

import org.springframework.http.HttpStatus
import team.inreok.getiserver.global.error.ErrorCode

enum class AuthErrorCode(
    override val status: HttpStatus,
    override val defaultMessage: String,
) : ErrorCode {
    UNSUPPORTED_OAUTH_PROVIDER(HttpStatus.BAD_REQUEST, "지원하지 않는 로그인 방식입니다."),
    OAUTH_STATE_INVALID(HttpStatus.BAD_REQUEST, "인증 요청이 만료되었거나 유효하지 않습니다. 다시 시도해 주세요."),
    OAUTH_LOGIN_FAILED(HttpStatus.UNAUTHORIZED, "로그인에 실패했습니다. 다시 시도해 주세요."),
    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "유효하지 않은 Refresh Token입니다."),
    REFRESH_TOKEN_REQUIRED(
        HttpStatus.BAD_REQUEST,
        "Refresh Token이 필요합니다. Cookie, X-Refresh-Token Header 또는 Body로 전달하세요.",
    ),
    OAUTH_WEB_REDIRECT_NOT_CONFIGURED(
        HttpStatus.INTERNAL_SERVER_ERROR,
        "Web Client Redirect URL이 설정되지 않았습니다.",
    ),
    ;

    override val code: String get() = name
}
