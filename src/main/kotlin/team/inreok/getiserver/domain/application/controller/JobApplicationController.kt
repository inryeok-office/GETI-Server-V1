package team.inreok.getiserver.domain.application.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import team.inreok.getiserver.domain.application.dto.CreateJobApplicationRequest
import team.inreok.getiserver.domain.application.dto.JobApplicationDraftResponse
import team.inreok.getiserver.domain.application.dto.JobEligibilityResponse
import team.inreok.getiserver.domain.application.service.JobApplicationService
import team.inreok.getiserver.global.openapi.BEARER_AUTH_SCHEME
import team.inreok.getiserver.global.web.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponse as SwaggerApiResponse

@Tag(
    name = "Application - 학생 지원가능여부·초안",
    description = "학생이 공고에 지원 가능한지 확인하고 지원서 초안을 생성한다. 필요 권한: 인증된 사용자(학생 기준).",
)
@SecurityRequirement(name = BEARER_AUTH_SCHEME)
// SecurityConfig가 /api/v1/jobs/**를 인증 필수로 지정하므로, 여기 도달했다는 것은 이미 유효한
// Access Token으로 인증됐다는 뜻이다. 재학생 여부(NOT_ENROLLED)는 Role이 아니라 학적 상태라
// checkEligibility/createDraft가 직접 판단한다.
@RestController
@RequestMapping("/api/v1/jobs/{jobId}")
class JobApplicationController(
    private val jobApplicationService: JobApplicationService,
) {
    @Operation(
        summary = "학생 지원 가능 여부 조회",
        description = """
            서버가 계산한 지원 가능 여부를 반환한다(클라이언트는 직접 계산하지 않는다). 공고가
            없거나 지원할 수 없는 상태여도 오류가 아니라 canApply=false와 사유를 담아 200으로
            응답한다.
        """,
    )
    @ApiResponses(
        SwaggerApiResponse(responseCode = "200", description = "조회 성공"),
        SwaggerApiResponse(responseCode = "401", description = "Access Token이 없거나 유효하지 않음 (UNAUTHORIZED)"),
        SwaggerApiResponse(responseCode = "500", description = "서버 내부 오류"),
    )
    @GetMapping("/application-eligibility")
    fun checkEligibility(
        authentication: Authentication,
        @Parameter(description = "대상 공고 ID", example = "1") @PathVariable jobId: Long,
    ): ApiResponse<JobEligibilityResponse> {
        val studentMemberId = authentication.principal as Long
        return ApiResponse.of(jobApplicationService.checkEligibility(jobId, studentMemberId))
    }

    @Operation(
        summary = "지원서 초안 생성",
        description = """
            지원 가능 여부를 서버에서 다시 검증한 뒤 DRAFT 상태의 지원서를 생성한다.
            `prefillProfileFields=true`면 회원 프로필에서 사용 가능한 필드를 자동 입력하며,
            이후 회원 프로필이 바뀌어도 지원서에 저장된 값은 바뀌지 않는다(스냅샷). 동일 공고에
            이미 활성 지원서가 있으면 거부한다.
        """,
    )
    @ApiResponses(
        SwaggerApiResponse(responseCode = "201", description = "생성 성공"),
        SwaggerApiResponse(responseCode = "400", description = "지원할 수 없는 공고 (JOB_NOT_APPLICABLE)"),
        SwaggerApiResponse(responseCode = "401", description = "Access Token이 없거나 유효하지 않음 (UNAUTHORIZED)"),
        SwaggerApiResponse(responseCode = "409", description = "이미 진행 중인 지원서가 있음 (ACTIVE_APPLICATION_EXISTS)"),
        SwaggerApiResponse(responseCode = "500", description = "서버 내부 오류"),
    )
    @PostMapping("/applications")
    @ResponseStatus(HttpStatus.CREATED)
    fun createDraft(
        authentication: Authentication,
        @Parameter(description = "지원할 공고 ID", example = "1") @PathVariable jobId: Long,
        @Valid @RequestBody request: CreateJobApplicationRequest,
    ): ApiResponse<JobApplicationDraftResponse> {
        val studentMemberId = authentication.principal as Long
        return ApiResponse.of(jobApplicationService.createDraft(jobId, studentMemberId, request))
    }
}
