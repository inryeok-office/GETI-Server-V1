package team.inreok.getiserver.global.error

import org.slf4j.MDC
import team.inreok.getiserver.global.web.RequestIdFilter
import java.time.Instant

data class ErrorResponse(
    val code: String,
    val message: String,
    val status: Int,
    val path: String,
    val timestamp: Instant,
    val requestId: String?,
    val fieldErrors: List<FieldErrorResponse> = emptyList(),
) {
    companion object {
        fun of(
            errorCode: ErrorCode,
            path: String,
            message: String = errorCode.defaultMessage,
            fieldErrors: List<FieldErrorResponse> = emptyList(),
        ): ErrorResponse =
            ErrorResponse(
                code = errorCode.name,
                message = message,
                status = errorCode.status.value(),
                path = path,
                timestamp = Instant.now(),
                requestId = MDC.get(RequestIdFilter.REQUEST_ID_MDC_KEY),
                fieldErrors = fieldErrors,
            )
    }
}

data class FieldErrorResponse(
    val field: String,
    val reason: String,
)
