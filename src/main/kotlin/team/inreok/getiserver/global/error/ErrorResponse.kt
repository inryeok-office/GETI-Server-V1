package team.inreok.getiserver.global.error

import team.inreok.getiserver.global.web.ResponseMeta
import java.time.Instant

// GETI Notion API 명세서의 success/error/meta.requestId Wrapper 구조를 따른다
// (docs/audit/notion-repository-sync.md DECISION_REQUIRED 반영). status/path/timestamp는
// Notion 명세에는 없지만 Log/Trace 연계에 유용해 error 하위에 추가 Field로 유지한다.
data class ErrorResponse(
    val success: Boolean = false,
    val error: ErrorBody,
    val meta: ResponseMeta = ResponseMeta(),
) {
    companion object {
        fun of(
            errorCode: ErrorCode,
            path: String,
            message: String = errorCode.defaultMessage,
            fieldErrors: List<FieldErrorResponse> = emptyList(),
        ): ErrorResponse =
            ErrorResponse(
                error =
                    ErrorBody(
                        code = errorCode.code,
                        message = message,
                        status = errorCode.status.value(),
                        path = path,
                        timestamp = Instant.now(),
                        fieldErrors = fieldErrors,
                    ),
            )
    }
}

data class ErrorBody(
    val code: String,
    val message: String,
    val status: Int,
    val path: String,
    val timestamp: Instant,
    val fieldErrors: List<FieldErrorResponse> = emptyList(),
)

data class FieldErrorResponse(
    val field: String,
    val reason: String,
)
