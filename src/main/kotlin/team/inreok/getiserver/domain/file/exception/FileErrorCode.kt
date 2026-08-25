package team.inreok.getiserver.domain.file.exception

import org.springframework.http.HttpStatus
import team.inreok.getiserver.global.error.ErrorCode

/**
 * 원본 요구사항 문서("GETI File 도메인 개발 요구사항") §26의 확정 5개
 * (`FILE_TOO_LARGE`, `FILE_TYPE_NOT_ALLOWED`, `MIME_MISMATCH`, `FILE_NOT_FOUND`,
 * `FILE_ACCESS_DENIED`)에 이번 범위에서 실제로 발생하는 오류만 더했다. 같은 상황에 비슷한
 * Error Code를 여러 개 만들지 않는다(§26).
 *
 * §26이 후보로 든 `INVALID_FILE_PURPOSE`는 추가하지 않는다 -- 잘못된 Enum 값은
 * [team.inreok.getiserver.global.error.GlobalExceptionHandler]가 이미
 * `CommonErrorCode.TYPE_MISMATCH`로 처리한다.
 *
 * 소유권·접근 거부를 404로 감추지 않고 403으로 돌려주는 것은 기존 관례를 따른 것이다
 * (`ApplicationErrorCode.APPLICATION_ACCESS_FORBIDDEN`,
 * `NotificationErrorCode.NOTIFICATION_ACCESS_DENIED`).
 */
enum class FileErrorCode(
    override val status: HttpStatus,
    override val defaultMessage: String,
) : ErrorCode {
    FILE_EMPTY(HttpStatus.BAD_REQUEST, "빈 파일은 업로드할 수 없습니다."),
    INVALID_FILE_NAME(HttpStatus.BAD_REQUEST, "파일 이름이 올바르지 않습니다."),
    FILE_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, "허용된 파일 크기를 초과했습니다."),
    FILE_TYPE_NOT_ALLOWED(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "허용되지 않은 파일 형식입니다."),
    MIME_MISMATCH(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "파일 확장자와 실제 파일 형식이 일치하지 않습니다."),
    FILE_NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 파일을 찾을 수 없습니다."),
    FILE_ACCESS_DENIED(HttpStatus.FORBIDDEN, "이 파일에 접근할 권한이 없습니다."),
    FILE_NOT_OWNED(HttpStatus.FORBIDDEN, "본인이 업로드한 파일만 사용할 수 있습니다."),
    FILE_PURPOSE_MISMATCH(HttpStatus.BAD_REQUEST, "파일의 업로드 목적이 요청과 일치하지 않습니다."),
    FILE_ALREADY_LINKED(HttpStatus.CONFLICT, "이미 다른 곳에서 사용 중인 파일입니다."),
    FILE_COUNT_EXCEEDED(HttpStatus.BAD_REQUEST, "첨부할 수 있는 파일 개수를 초과했습니다."),
    FILE_STORAGE_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "파일 저장소 처리 중 오류가 발생했습니다."),
    FILE_ARCHIVE_EMPTY(HttpStatus.NOT_FOUND, "다운로드할 수 있는 파일이 없습니다."),
    FILE_ARCHIVE_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, "일괄 다운로드 허용 개수 또는 용량을 초과했습니다."),
    ;

    override val code: String get() = name
}
