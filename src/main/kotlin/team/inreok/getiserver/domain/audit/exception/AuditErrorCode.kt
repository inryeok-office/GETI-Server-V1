package team.inreok.getiserver.domain.audit.exception

import org.springframework.http.HttpStatus
import team.inreok.getiserver.global.error.ErrorCode

enum class AuditErrorCode(
    override val status: HttpStatus,
    override val defaultMessage: String,
) : ErrorCode {
    AUDIT_LOG_NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 감사 로그를 찾을 수 없습니다."),
    ;

    override val code: String get() = name
}
