package team.inreok.getiserver.domain.application.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.data.domain.Pageable
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import team.inreok.getiserver.domain.application.dto.JobApplicationAdminActionRequest
import team.inreok.getiserver.domain.application.dto.JobApplicationAdminListResponse
import team.inreok.getiserver.domain.application.dto.JobApplicationDraftResponse
import team.inreok.getiserver.domain.application.dto.JobApplicationStatusHistoryResponse
import team.inreok.getiserver.domain.application.entity.type.JobApplicationStatus
import team.inreok.getiserver.domain.application.service.JobApplicationAdminService
import team.inreok.getiserver.global.openapi.BEARER_AUTH_SCHEME
import team.inreok.getiserver.global.web.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponse as SwaggerApiResponse

@Tag(
    name = "Application - 교사 지원서 검토",
    description =
        "교사·개발자가 제출된 지원서를 조회하고 검토 Action(ALLOW_EDIT/REQUEST_REVISION/APPROVE/" +
            "REJECT)을 수행한다(Issue #125). 조회는 모든 교사·개발자가 가능하고, 상태 변경은 " +
            "해당 공고의 등록자·담당 교사·개발자만 가능하다.",
)
@SecurityRequirement(name = BEARER_AUTH_SCHEME)
// SecurityConfig가 이 경로를 TEACHER 또는 DEVELOPER 역할 필수로 지정하므로, 여기 도달했다는 것은
// 이미 유효한 Access Token으로 인증되고 두 역할 중 하나를 가졌다는 뜻이다. 상태 변경의 담당자 검증은
// Role만으로 알 수 없어 JobApplicationAdminService가 별도로 수행한다.
@RestController
@RequestMapping("/api/v1/admin/job-applications")
class JobApplicationAdminController(
    private val jobApplicationAdminService: JobApplicationAdminService,
) {
    @Operation(
        summary = "지원서 목록 조회",
        description = """
            제출된 지원서를 조회·검색한다. jobId·status 필터는 함께 조합할 수 있고(AND), 지정하지
            않은 Filter는 적용하지 않는다. DRAFT(임시저장 중, 미제출) 상태는 status에 명시 지정해도
            항상 결과에서 제외된다. 담당 공고 여부와 무관하게 모든 교사·개발자가 조회할 수 있다.
            기본 page=0, size=20이며 최신 등록순으로 고정된다.
        """,
    )
    @ApiResponses(
        SwaggerApiResponse(responseCode = "200", description = "조회 성공(결과가 없으면 빈 목록)"),
        SwaggerApiResponse(responseCode = "401", description = "Access Token이 없거나 유효하지 않음 (UNAUTHORIZED)"),
        SwaggerApiResponse(responseCode = "403", description = "교사 또는 개발자 권한이 없음 (FORBIDDEN)"),
        SwaggerApiResponse(responseCode = "500", description = "서버 내부 오류"),
    )
    @GetMapping
    fun listApplications(
        @Parameter(description = "공고 ID 필터(선택)") @RequestParam(required = false) jobId: Long?,
        @Parameter(description = "지원 상태 필터(선택)") @RequestParam(required = false) status: JobApplicationStatus?,
        @Parameter(description = "Pagination(page: 0부터 시작, size: 기본 20)") pageable: Pageable,
    ): ApiResponse<JobApplicationAdminListResponse> =
        ApiResponse.of(jobApplicationAdminService.list(jobId, status, pageable))

    @Operation(
        summary = "지원서 상세 조회",
        description =
            "지원서 1건의 전체 내용(답변 포함)을 조회한다. 담당 공고 여부와 무관하게 모든 교사·개발자가 조회할 수 있다. " +
                "DRAFT(임시저장 중, 미제출) 상태의 지원서는 존재하지 않는 것으로 취급되어 404를 반환한다.",
    )
    @ApiResponses(
        SwaggerApiResponse(responseCode = "200", description = "조회 성공"),
        SwaggerApiResponse(responseCode = "401", description = "Access Token이 없거나 유효하지 않음 (UNAUTHORIZED)"),
        SwaggerApiResponse(responseCode = "403", description = "교사 또는 개발자 권한이 없음 (FORBIDDEN)"),
        SwaggerApiResponse(responseCode = "404", description = "지원서가 없음(DRAFT 상태 포함) (APPLICATION_NOT_FOUND)"),
        SwaggerApiResponse(responseCode = "500", description = "서버 내부 오류"),
    )
    @GetMapping("/{applicationId}")
    fun getApplication(
        @Parameter(description = "조회할 지원서 ID", example = "1") @PathVariable applicationId: Long,
    ): ApiResponse<JobApplicationDraftResponse> = ApiResponse.of(jobApplicationAdminService.getDetail(applicationId))

    @Operation(
        summary = "지원서 검토 Action 수행",
        description = """
            action 값에 따라 다음을 수행한다.
            - ALLOW_EDIT: 학생이 수정을 요청한(EDIT_REQUESTED) 지원서의 재작성을 허용한다(EDIT_ALLOWED).
            - REQUEST_REVISION: 제출된(SUBMITTED) 지원서에 보완을 요구한다(REVISION_REQUESTED),
              reason이 statusReason으로 기록된다.
            - APPROVE: 제출된(SUBMITTED) 지원서를 승인한다(APPROVED, 최종 상태).
            - REJECT: 제출된(SUBMITTED) 지원서를 거절한다(REJECTED, 최종 상태),
              reason이 statusReason으로 기록된다.
            해당 공고의 등록자·담당 교사, 또는 개발자만 수행할 수 있다. 각 Action이 허용되지 않는
            현재 상태에서 호출하면 409로 거부한다.
        """,
    )
    @ApiResponses(
        SwaggerApiResponse(responseCode = "200", description = "Action 수행 성공"),
        SwaggerApiResponse(responseCode = "401", description = "Access Token이 없거나 유효하지 않음 (UNAUTHORIZED)"),
        SwaggerApiResponse(
            responseCode = "403",
            description =
                "교사·개발자가 아님(FORBIDDEN), 해당 공고의 등록자·담당 교사·개발자가 아님(APPLICATION_REVIEW_FORBIDDEN)",
        ),
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
        @Valid @RequestBody request: JobApplicationAdminActionRequest,
    ): ApiResponse<JobApplicationDraftResponse> {
        val requesterMemberId = authentication.principal as Long
        val isDeveloper = authentication.authorities.any { it.authority == "ROLE_DEVELOPER" }
        return ApiResponse.of(
            jobApplicationAdminService.executeAction(applicationId, requesterMemberId, isDeveloper, request),
        )
    }

    @Operation(
        summary = "지원서 상태 변경 이력 조회",
        description =
            "지원서의 상태 변경 이력(from/to 상태, Action, 수행자, 사유, 시각)을 오래된 순으로 반환한다(Issue #133). " +
                "담당 공고 여부와 무관하게 모든 교사·개발자가 조회할 수 있다. DRAFT 지원서는 상세 조회와 동일하게 404를 반환한다.",
    )
    @ApiResponses(
        SwaggerApiResponse(responseCode = "200", description = "조회 성공(이력이 없으면 빈 목록)"),
        SwaggerApiResponse(responseCode = "401", description = "Access Token이 없거나 유효하지 않음 (UNAUTHORIZED)"),
        SwaggerApiResponse(responseCode = "403", description = "교사 또는 개발자 권한이 없음 (FORBIDDEN)"),
        SwaggerApiResponse(responseCode = "404", description = "지원서가 없음(DRAFT 상태 포함) (APPLICATION_NOT_FOUND)"),
        SwaggerApiResponse(responseCode = "500", description = "서버 내부 오류"),
    )
    @GetMapping("/{applicationId}/history")
    fun getHistory(
        @Parameter(description = "이력을 조회할 지원서 ID", example = "1") @PathVariable applicationId: Long,
    ): ApiResponse<List<JobApplicationStatusHistoryResponse>> =
        ApiResponse.of(jobApplicationAdminService.getHistory(applicationId))
}
