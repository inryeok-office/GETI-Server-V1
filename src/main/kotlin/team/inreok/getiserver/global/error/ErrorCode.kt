package team.inreok.getiserver.global.error

import org.springframework.http.HttpStatus

/**
 * Framework 수준의 공통 Error Code만 정의한다. 실제 처리하는 오류만 포함하며,
 * `USER_NOT_FOUND`처럼 아직 존재하지 않는 Domain Error Code는 추가하지 않는다.
 */
enum class ErrorCode(
    val status: HttpStatus,
    val defaultMessage: String,
) {
    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "요청 값이 올바르지 않습니다."),
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "요청 값 검증에 실패했습니다."),
    MALFORMED_JSON(HttpStatus.BAD_REQUEST, "요청 본문을 읽을 수 없습니다."),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "지원하지 않는 HTTP Method입니다."),
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "지원하지 않는 Media Type입니다."),
    MISSING_REQUEST_PARAMETER(HttpStatus.BAD_REQUEST, "필수 요청 값이 누락되었습니다."),
    TYPE_MISMATCH(HttpStatus.BAD_REQUEST, "요청 값의 형식이 올바르지 않습니다."),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 리소스를 찾을 수 없습니다."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류가 발생했습니다."),
    ;

    companion object {
        fun fromStatus(status: HttpStatus): ErrorCode =
            when {
                status == HttpStatus.NOT_FOUND -> RESOURCE_NOT_FOUND
                status == HttpStatus.METHOD_NOT_ALLOWED -> METHOD_NOT_ALLOWED
                status == HttpStatus.UNSUPPORTED_MEDIA_TYPE -> UNSUPPORTED_MEDIA_TYPE
                status.is5xxServerError -> INTERNAL_SERVER_ERROR
                status.is4xxClientError -> INVALID_REQUEST
                else -> INTERNAL_SERVER_ERROR
            }
    }
}
