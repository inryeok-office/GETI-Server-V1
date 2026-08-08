package team.inreok.getiserver.domain.file.exception

import team.inreok.getiserver.domain.file.entity.type.FilePurpose
import team.inreok.getiserver.global.error.BusinessException

/**
 * File 도메인 예외를 한 파일에 모은다. 각각이 [BusinessException]을 상속한 몇 줄짜리 Class라
 * 파일을 12개로 쪼개면 탐색만 어려워진다.
 *
 * Message에는 사용자에게 보여도 안전한 정보만 넣는다 -- `objectKey`, Bucket 이름, Storage SDK의
 * 예외 Message는 절대 포함하지 않는다(지시서 §17/§26/§42).
 */
class FileEmptyException : BusinessException(FileErrorCode.FILE_EMPTY)

class InvalidFileNameException(
    reason: String,
) : BusinessException(FileErrorCode.INVALID_FILE_NAME, "파일 이름이 올바르지 않습니다. ($reason)")

class FileTooLargeException(
    sizeBytes: Long,
    maxSizeBytes: Long,
) : BusinessException(
        FileErrorCode.FILE_TOO_LARGE,
        "허용된 파일 크기를 초과했습니다. (size=${sizeBytes}바이트, 최대=${maxSizeBytes}바이트)",
    )

class FileTypeNotAllowedException(
    purpose: FilePurpose,
    detail: String,
) : BusinessException(
        FileErrorCode.FILE_TYPE_NOT_ALLOWED,
        "허용되지 않은 파일 형식입니다. (purpose=$purpose, $detail)",
    )

class MimeMismatchException(
    extension: String,
    detectedContentType: String,
) : BusinessException(
        FileErrorCode.MIME_MISMATCH,
        "파일 확장자와 실제 파일 형식이 일치하지 않습니다. (확장자=$extension, 실제 형식=$detectedContentType)",
    )

class FileNotFoundException(
    fileId: Long,
) : BusinessException(FileErrorCode.FILE_NOT_FOUND, "요청한 파일을 찾을 수 없습니다. (fileId=$fileId)")

class FileAccessDeniedException : BusinessException(FileErrorCode.FILE_ACCESS_DENIED)

class FileNotOwnedException(
    fileId: Long,
) : BusinessException(
        FileErrorCode.FILE_NOT_OWNED,
        "본인이 업로드한 파일만 사용할 수 있습니다. (fileId=$fileId)",
    )

class FilePurposeMismatchException(
    fileId: Long,
    expected: FilePurpose,
) : BusinessException(
        FileErrorCode.FILE_PURPOSE_MISMATCH,
        "파일의 업로드 목적이 요청과 일치하지 않습니다. (fileId=$fileId, 기대=$expected)",
    )

class FileAlreadyLinkedException(
    fileId: Long,
) : BusinessException(
        FileErrorCode.FILE_ALREADY_LINKED,
        "이미 다른 곳에서 사용 중인 파일입니다. (fileId=$fileId)",
    )

class FileCountExceededException(
    maxCount: Int,
) : BusinessException(
        FileErrorCode.FILE_COUNT_EXCEEDED,
        "첨부할 수 있는 파일 개수를 초과했습니다. (최대=${maxCount}개)",
    )

/**
 * Object Storage 처리 실패다.
 *
 * [GlobalExceptionHandler][team.inreok.getiserver.global.error.GlobalExceptionHandler]가
 * [BusinessException]의 Message를 **그대로 응답에 노출**하므로 이 예외는 Message를 재정의하지
 * 않고 [FileErrorCode.FILE_STORAGE_ERROR]의 기본 문구만 쓴다. Bucket 이름, `objectKey`, SDK
 * 예외 Message 같은 내부 정보가 사용자에게 새어나가면 안 되기 때문이다(지시서 §26/§42).
 *
 * 실패 원인은 [operation]과 [cause]로 남기며 로그에서만 확인한다.
 */
class FileStorageException(
    /** 실패한 Storage 작업(`upload`, `delete`, `presign` 등). 로그 전용이며 응답에 나가지 않는다. */
    val operation: String,
    cause: Throwable? = null,
) : BusinessException(FileErrorCode.FILE_STORAGE_ERROR) {
    init {
        cause?.let { initCause(it) }
    }
}
