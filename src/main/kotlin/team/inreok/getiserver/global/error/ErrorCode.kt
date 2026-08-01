package team.inreok.getiserver.global.error

import org.springframework.http.HttpStatus
import org.springframework.modulith.NamedInterface

/**
 * [BusinessException]/[ErrorResponse]가 다루는 Error Code의 공통 계약이다. Framework 수준
 * 공통 Error는 [CommonErrorCode]가 구현하고, Domain별 Error Code는 각 Domain Module 내부에서
 * 이 Interface를 구현하는 별도 Enum으로 정의한다(`global` Package는 특정 Domain을 알지 못한다).
 * Domain Module이 구현해야 하므로 Named Interface로 공개한다.
 */
@NamedInterface
interface ErrorCode {
    val code: String
    val status: HttpStatus
    val defaultMessage: String
}

/**
 * Framework 수준의 공통 Error Code만 정의한다. 실제 처리하는 오류만 포함하며,
 * `USER_NOT_FOUND`처럼 특정 Domain에서만 의미가 있는 Error Code는 추가하지 않는다.
 * Domain Module이 공통 Error로 [BusinessException]을 던질 때 사용하므로 Named Interface로 공개한다.
 */
@NamedInterface
enum class CommonErrorCode(
    override val status: HttpStatus,
    override val defaultMessage: String,
) : ErrorCode {
    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "요청 값이 올바르지 않습니다."),
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "요청 값 검증에 실패했습니다."),
    MALFORMED_JSON(HttpStatus.BAD_REQUEST, "요청 본문을 읽을 수 없습니다."),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "지원하지 않는 HTTP Method입니다."),
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "지원하지 않는 Media Type입니다."),
    MISSING_REQUEST_PARAMETER(HttpStatus.BAD_REQUEST, "필수 요청 값이 누락되었습니다."),
    TYPE_MISMATCH(HttpStatus.BAD_REQUEST, "요청 값의 형식이 올바르지 않습니다."),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 리소스를 찾을 수 없습니다."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "인증이 필요합니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류가 발생했습니다."),
    ;

    override val code: String get() = name

    companion object {
        fun fromStatus(status: HttpStatus): CommonErrorCode =
            when {
                status == HttpStatus.NOT_FOUND -> RESOURCE_NOT_FOUND
                status == HttpStatus.METHOD_NOT_ALLOWED -> METHOD_NOT_ALLOWED
                status == HttpStatus.UNSUPPORTED_MEDIA_TYPE -> UNSUPPORTED_MEDIA_TYPE
                status == HttpStatus.UNAUTHORIZED -> UNAUTHORIZED
                status == HttpStatus.FORBIDDEN -> FORBIDDEN
                status.is5xxServerError -> INTERNAL_SERVER_ERROR
                status.is4xxClientError -> INVALID_REQUEST
                else -> INTERNAL_SERVER_ERROR
            }
    }
}
