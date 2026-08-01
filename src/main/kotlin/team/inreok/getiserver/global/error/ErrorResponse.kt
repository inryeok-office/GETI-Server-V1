package team.inreok.getiserver.global.error

import io.swagger.v3.oas.annotations.media.Schema
import team.inreok.getiserver.global.web.ResponseMeta
import java.time.Instant

// GETI Notion API 명세서의 success/error/meta.requestId Wrapper 구조를 따른다
// (docs/audit/notion-repository-sync.md DECISION_REQUIRED 반영). status/path/timestamp는
// Notion 명세에는 없지만 Log/Trace 연계에 유용해 error 하위에 추가 Field로 유지한다.
@Schema(description = "공통 오류 응답 Wrapper")
data class ErrorResponse(
    @param:Schema(description = "요청 성공 여부(오류 응답은 항상 false)", example = "false")
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

@Schema(description = "오류 상세 정보")
data class ErrorBody(
    @param:Schema(description = "Error Code(각 Endpoint의 발생 가능한 값은 API 문서 참고)", example = "MEMBER_NOT_FOUND")
    val code: String,
    @param:Schema(description = "사용자에게 보여줄 수 있는 오류 메시지", example = "요청한 회원을 찾을 수 없습니다.")
    val message: String,
    @param:Schema(description = "HTTP Status Code", example = "404")
    val status: Int,
    @param:Schema(description = "오류가 발생한 요청 경로", example = "/api/v1/members/999")
    val path: String,
    @param:Schema(description = "오류 발생 시각(UTC)")
    val timestamp: Instant,
    @param:Schema(description = "요청 값 검증 실패 시 Field별 상세 오류 목록")
    val fieldErrors: List<FieldErrorResponse> = emptyList(),
)

@Schema(description = "요청 값 검증 실패 Field 상세")
data class FieldErrorResponse(
    @param:Schema(description = "검증에 실패한 요청 Field명", example = "refreshToken")
    val field: String,
    @param:Schema(description = "실패 사유", example = "refreshToken은 필수입니다.")
    val reason: String,
)
