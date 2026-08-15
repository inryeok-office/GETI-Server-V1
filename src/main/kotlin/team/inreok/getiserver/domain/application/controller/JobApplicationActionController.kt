package team.inreok.getiserver.domain.application.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import team.inreok.getiserver.domain.application.dto.JobApplicationActionRequest
import team.inreok.getiserver.domain.application.dto.JobApplicationDraftResponse
import team.inreok.getiserver.domain.application.dto.JobApplicationStatusHistoryResponse
import team.inreok.getiserver.domain.application.service.JobApplicationService
import team.inreok.getiserver.global.openapi.BEARER_AUTH_SCHEME
import team.inreok.getiserver.global.web.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponse as SwaggerApiResponse

@Tag(
    name = "Application - 지원서 제출·수정요청·재제출·철회",
    description =
        "학생 본인의 지원서에 SUBMIT/REQUEST_EDIT/RESUBMIT/WITHDRAW Action을 수행한다(Issue " +
            "#124). 필요 권한: 인증된 사용자(본인 소유 지원서만).",
)
@SecurityRequirement(name = BEARER_AUTH_SCHEME)
// SecurityConfig가 /api/v1/job-applications/**를 인증 필수로 지정하므로, 여기 도달했다는 것은
// 이미 유효한 Access Token으로 인증됐다는 뜻이다. 소유권·상태 전이 검증은 Role로 알 수 없어
// JobApplicationService가 별도로 수행한다.
@RestController
@RequestMapping("/api/v1/job-applications")
class JobApplicationActionController(
    private val jobApplicationService: JobApplicationService,
) {
    @Operation(
        summary = "지원서 Action 수행",
        description = """
            action 값에 따라 다음을 수행한다.
            - SUBMIT: DRAFT 지원서를 제출한다(SUBMITTED). 필수 항목 답변이 비어 있으면 거부한다.
            - REQUEST_EDIT: 제출한(SUBMITTED) 지원서의 수정을 요청한다(EDIT_REQUESTED).
            - RESUBMIT: 교사가 재작성을 허용/요청한(EDIT_ALLOWED/REVISION_REQUESTED) 지원서를
              다시 제출한다(SUBMITTED). 재작성은 PATCH .../{applicationId}로 먼저 반영한다.
              SUBMIT과 동일하게 필수 항목 답변을 검증한다.
            - WITHDRAW: 최종 상태(APPROVED/REJECTED/WITHDRAWN)가 아닌 지원서를 철회한다(WITHDRAWN).
            각 Action이 허용되지 않는 현재 상태에서 호출하면 409로 거부한다.
        """,
    )
    @ApiResponses(
        SwaggerApiResponse(responseCode = "200", description = "Action 수행 성공"),
        SwaggerApiResponse(
            responseCode = "400",
            description = "SUBMIT/RESUBMIT 시 필수 항목 답변 누락 (APPLICATION_REQUIRED_ANSWER_MISSING)",
        ),
        SwaggerApiResponse(responseCode = "401", description = "Access Token이 없거나 유효하지 않음 (UNAUTHORIZED)"),
        SwaggerApiResponse(responseCode = "403", description = "본인의 지원서가 아님 (APPLICATION_ACCESS_FORBIDDEN)"),
        SwaggerApiResponse(responseCode = "404", description = "지원서가 없음 (APPLICATION_NOT_FOUND)"),
        SwaggerApiResponse(
            responseCode = "409",
            description = "현재 상태에서 수행할 수 없는 Action (APPLICATION_ACTION_NOT_AVAILABLE)",
        ),
        SwaggerApiResponse(responseCode = "500", description = "서버 내부 오류"),
    )
    @PostMapping("/{applicationId}/actions")
    fun executeAction(
        authentication: Authentication,
        @Parameter(description = "Action을 수행할 지원서 ID", example = "1") @PathVariable applicationId: Long,
        @Valid @RequestBody request: JobApplicationActionRequest,
    ): ApiResponse<JobApplicationDraftResponse> {
        val studentMemberId = authentication.principal as Long
        return ApiResponse.of(jobApplicationService.executeAction(applicationId, studentMemberId, request))
    }

    @Operation(
        summary = "지원서 상태 변경 이력 조회",
        description = "본인 지원서의 상태 변경 이력(from/to 상태, Action, 수행자, 사유, 시각)을 오래된 순으로 반환한다(Issue #133).",
    )
    @ApiResponses(
        SwaggerApiResponse(responseCode = "200", description = "조회 성공(이력이 없으면 빈 목록)"),
        SwaggerApiResponse(responseCode = "401", description = "Access Token이 없거나 유효하지 않음 (UNAUTHORIZED)"),
        SwaggerApiResponse(responseCode = "403", description = "본인의 지원서가 아님 (APPLICATION_ACCESS_FORBIDDEN)"),
        SwaggerApiResponse(responseCode = "404", description = "지원서가 없음 (APPLICATION_NOT_FOUND)"),
        SwaggerApiResponse(responseCode = "500", description = "서버 내부 오류"),
    )
    @GetMapping("/{applicationId}/history")
    fun getHistory(
        authentication: Authentication,
        @Parameter(description = "이력을 조회할 지원서 ID", example = "1") @PathVariable applicationId: Long,
    ): ApiResponse<List<JobApplicationStatusHistoryResponse>> {
        val studentMemberId = authentication.principal as Long
        return ApiResponse.of(jobApplicationService.getHistory(applicationId, studentMemberId))
    }
}
